/*
 * ApplicationInsights-Java
 * Copyright (c) Microsoft Corporation
 * All rights reserved.
 *
 * MIT License
 * Permission is hereby granted, free of charge, to any person obtaining a copy of this
 * software and associated documentation files (the ""Software""), to deal in the Software
 * without restriction, including without limitation the rights to use, copy, modify, merge,
 * publish, distribute, sublicense, and/or sell copies of the Software, and to permit
 * persons to whom the Software is furnished to do so, subject to the following conditions:
 * The above copyright notice and this permission notice shall be included in all copies or
 * substantial portions of the Software.
 * THE SOFTWARE IS PROVIDED *AS IS*, WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED,
 * INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR
 * PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE
 * FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR
 * OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER
 * DEALINGS IN THE SOFTWARE.
 */

package com.microsoft.applicationinsights.agent.internal;

import com.google.common.base.Strings;
import com.microsoft.applicationinsights.TelemetryClient;
import com.microsoft.applicationinsights.TelemetryConfiguration;
import com.microsoft.applicationinsights.agent.bootstrap.BytecodeUtil;
import com.microsoft.applicationinsights.agent.bootstrap.diagnostics.DiagnosticsHelper;
import com.microsoft.applicationinsights.agent.bootstrap.diagnostics.SdkVersionFinder;
import com.microsoft.applicationinsights.agent.internal.instrumentation.sdk.*;
import com.microsoft.applicationinsights.agent.internal.wasbootstrap.MainEntryPoint;
import com.microsoft.applicationinsights.agent.internal.wasbootstrap.configuration.Configuration;
import com.microsoft.applicationinsights.agent.internal.wasbootstrap.configuration.Configuration.JmxMetric;
import com.microsoft.applicationinsights.agent.internal.wasbootstrap.configuration.Configuration.ProcessorConfig;
import com.microsoft.applicationinsights.agent.internal.wasbootstrap.configuration.Configuration.ProfilerConfiguration;
import com.microsoft.applicationinsights.agent.internal.wasbootstrap.configuration.RpConfiguration;
import com.microsoft.applicationinsights.common.CommonUtils;
import com.microsoft.applicationinsights.customExceptions.FriendlyException;
import com.microsoft.applicationinsights.extensibility.initializer.ResourceAttributesContextInitializer;
import com.microsoft.applicationinsights.extensibility.initializer.SdkVersionContextInitializer;
import com.microsoft.applicationinsights.internal.channel.common.LazyHttpClient;
import com.microsoft.applicationinsights.internal.config.AddTypeXmlElement;
import com.microsoft.applicationinsights.internal.config.ApplicationInsightsXmlConfiguration;
import com.microsoft.applicationinsights.internal.config.JmxXmlElement;
import com.microsoft.applicationinsights.internal.config.ParamXmlElement;
import com.microsoft.applicationinsights.internal.config.TelemetryConfigurationFactory;
import com.microsoft.applicationinsights.internal.config.TelemetryModulesXmlElement;
import com.microsoft.applicationinsights.internal.config.connection.ConnectionString;
import com.microsoft.applicationinsights.internal.config.connection.InvalidConnectionStringException;
import com.microsoft.applicationinsights.internal.profiler.GcEventMonitor;
import com.microsoft.applicationinsights.internal.profiler.ProfilerServiceInitializer;
import com.microsoft.applicationinsights.internal.statsbeat.StatsbeatModule;
import com.microsoft.applicationinsights.internal.system.SystemInformation;
import com.microsoft.applicationinsights.internal.util.PropertyHelper;
import com.microsoft.applicationinsights.profiler.config.ServiceProfilerServiceConfig;
import io.opentelemetry.instrumentation.api.aisdk.AiLazyConfiguration;
import io.opentelemetry.instrumentation.api.config.Config;
import io.opentelemetry.javaagent.extension.AgentListener;
import org.apache.http.HttpHost;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.management.ObjectName;
import java.io.File;
import java.lang.instrument.Instrumentation;
import java.net.URI;
import java.util.ArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.stream.Collectors;

import static java.util.concurrent.TimeUnit.MINUTES;
import static java.util.concurrent.TimeUnit.SECONDS;

public class AiComponentInstaller implements AgentListener {

    private static final Logger startupLogger = LoggerFactory.getLogger("com.microsoft.applicationinsights.agent");

    // TODO move to "agent builder" and then can inject this in the constructor
    //  or convert to ByteBuddy and use ByteBuddyAgentCustomizer
    private static Instrumentation instrumentation;

    public static void setInstrumentation(Instrumentation inst) {
        instrumentation = inst;
    }

    @Override
    public void beforeAgent(Config config) {
        start(instrumentation);
        // add sdk instrumentation after ensuring Global.getTelemetryClient() will not return null
        instrumentation.addTransformer(new TelemetryClientClassFileTransformer());
        instrumentation.addTransformer(new DependencyTelemetryClassFileTransformer());
        instrumentation.addTransformer(new RequestTelemetryClassFileTransformer());
        instrumentation.addTransformer(new PerformanceCounterModuleClassFileTransformer());
        instrumentation.addTransformer(new QuickPulseClassFileTransformer());
        instrumentation.addTransformer(new HeartBeatModuleClassFileTransformer());
        instrumentation.addTransformer(new ApplicationInsightsAppenderClassFileTransformer());
        instrumentation.addTransformer(new WebRequestTrackingFilterClassFileTransformer());
        instrumentation.addTransformer(new RequestNameHandlerClassFileTransformer());
        instrumentation.addTransformer(new DuplicateAgentClassFileTransformer());
    }

    @Override
    public void afterAgent(Config config) {
        // only safe now to resolve app id because SSL initialization
        // triggers loading of java.util.logging (starting with Java 8u231)
        // and JBoss/Wildfly need to install their own JUL manager before JUL is initialized.
        // Delay registering and starting AppId retrieval to later when the connection string becomes available
        // for Linux Consumption Plan.
        if (!"java".equals(System.getenv("FUNCTIONS_WORKER_RUNTIME"))) {
            AppIdSupplier.registerAndStartAppIdRetrieval();
        }
    }

    private static void start(Instrumentation instrumentation) {

        String codelessSdkNamePrefix = getCodelessSdkNamePrefix();
        if (codelessSdkNamePrefix != null) {
            PropertyHelper.setSdkNamePrefix(codelessSdkNamePrefix);
        }

        File javaTmpDir = new File(System.getProperty("java.io.tmpdir"));
        File tmpDir = new File(javaTmpDir, "applicationinsights-java");
        if (!tmpDir.exists() && !tmpDir.mkdirs()) {
            throw new IllegalStateException("Could not create directory: " + tmpDir.getAbsolutePath());
        }

        Configuration config = MainEntryPoint.getConfiguration();
        if (!hasConnectionStringOrInstrumentationKey(config)) {
            if (!("java".equals(System.getenv("FUNCTIONS_WORKER_RUNTIME")))) {
                throw new FriendlyException("No connection string or instrumentation key provided",
                        "Please provide connection string or instrumentation key.");
            }
        }
        // Function to validate user provided processor configuration
        validateProcessorConfiguration(config);

        // FIXME do something with config

        // FIXME set doNotWeavePrefixes = "com.microsoft.applicationinsights.agent."

        // FIXME set tryToLoadInBootstrapClassLoader = "com.microsoft.applicationinsights.agent."
        // (maybe not though, this is only needed for classes in :agent:agent-bootstrap)

        String jbossHome = System.getenv("JBOSS_HOME");
        if (!Strings.isNullOrEmpty(jbossHome)) {
            // this is used to delay SSL initialization because SSL initialization triggers loading of
            // java.util.logging (starting with Java 8u231)
            // and JBoss/Wildfly need to install their own JUL manager before JUL is initialized
            LazyHttpClient.safeToInitLatch = new CountDownLatch(1);
            instrumentation.addTransformer(new JulListeningClassFileTransformer(LazyHttpClient.safeToInitLatch));
        }

        if (config.proxy.host != null) {
            LazyHttpClient.proxy = new HttpHost(config.proxy.host, config.proxy.port);
        }
        AppIdSupplier appIdSupplier = AppIdSupplier.INSTANCE;

        TelemetryConfiguration configuration = TelemetryConfiguration.getActiveWithoutInitializingConfig();
        TelemetryConfigurationFactory.INSTANCE.initialize(configuration, buildXmlConfiguration(config));
        configuration.getContextInitializers().add(new SdkVersionContextInitializer());
        configuration.getContextInitializers().add(new ResourceAttributesContextInitializer(config.customDimensions));
        configuration.setMetricFilters(config.preview.processors.stream()
                .filter(processor -> processor.type == Configuration.ProcessorType.METRIC_FILTER)
                .map(Configuration.ProcessorConfig::toMetricFilter)
                .collect(Collectors.toList()));

        try {
            ConnectionString.updateStatsbeatConnectionString(config.internal.statsbeat.instrumentationKey, config.internal.statsbeat.endpoint, configuration);
        } catch (InvalidConnectionStringException ex) {
            startupLogger.warn("Statsbeat endpoint is invalid. {}", ex.getMessage());
        }

        Global.setSamplingPercentage(config.sampling.percentage);
        final TelemetryClient telemetryClient = new TelemetryClient();
        Global.setTelemetryClient(telemetryClient);

        ProfilerServiceInitializer.initialize(
                appIdSupplier::get,
                SystemInformation.INSTANCE.getProcessId(),
                formServiceProfilerConfig(config.preview.profiler),
                configuration.getRoleInstance(),
                configuration.getRoleName(),
                // TODO this will not work with Azure Spring Cloud updating connection string at runtime
                configuration.getInstrumentationKey(),
                telemetryClient,
                formApplicationInsightsUserAgent(),
                formGcEventMonitorConfiguration(config.preview.gcEvents)
        );

        // this is for Azure Function Linux consumption plan support.
        if ("java".equals(System.getenv("FUNCTIONS_WORKER_RUNTIME"))) {
            AiLazyConfiguration.setAccessor(new LazyConfigurationAccessor());
        }

        // this is currently used by Micrometer instrumentation in addition to 2.x SDK
        BytecodeUtil.setDelegate(new BytecodeUtilImpl());
        Runtime.getRuntime().addShutdownHook(new Thread() {
            @Override
            public void run() {
                startupLogger.debug("running shutdown hook");
                try {
                    telemetryClient.flush();
                    telemetryClient.shutdown(5, SECONDS);
                    startupLogger.debug("completed shutdown hook");
                } catch (InterruptedException e) {
                    startupLogger.debug("interrupted while flushing telemetry during shutdown");
                } catch (Throwable t) {
                    startupLogger.debug(t.getMessage(), t);
                }
            }
        });

        RpConfiguration rpConfiguration = MainEntryPoint.getRpConfiguration();
        if (rpConfiguration != null) {
            RpConfigurationPolling.startPolling(rpConfiguration, config);
        }

        // initialize StatsbeatModule
        StatsbeatModule.initialize(telemetryClient, config.internal.statsbeat.intervalSeconds, config.internal.statsbeat.featureIntervalSeconds);
    }

    private static GcEventMonitor.GcEventMonitorConfiguration formGcEventMonitorConfiguration(Configuration.GcEventConfiguration gcEvents) {
        return new GcEventMonitor.GcEventMonitorConfiguration(gcEvents.reportingLevel);
    }

    private static String formApplicationInsightsUserAgent() {
        String aiVersion = SdkVersionFinder.getTheValue();
        String javaVersion = System.getProperty("java.version");
        String osName = System.getProperty("os.name");
        String arch = System.getProperty("os.arch");
        String userName = "Microsoft-ApplicationInsights-Java-Profiler/" + aiVersion + "  (Java/" + javaVersion + "; " + osName + "; " + arch + ")";
        return userName;
    }

    private static ServiceProfilerServiceConfig formServiceProfilerConfig(ProfilerConfiguration configuration) {
        URI serviceProfilerFrontEndPoint = TelemetryConfiguration.getActive().getEndpointProvider().getProfilerEndpoint();
        return new ServiceProfilerServiceConfig(
                configuration.configPollPeriodSeconds,
                configuration.periodicRecordingDurationSeconds,
                configuration.periodicRecordingIntervalSeconds,
                serviceProfilerFrontEndPoint,
                configuration.enabled,
                configuration.memoryTriggeredSettings,
                configuration.cpuTriggeredSettings
        );
    }

    private static void validateProcessorConfiguration(Configuration config) throws FriendlyException {
        if (config.preview == null || config.preview.processors == null) return;
        for (ProcessorConfig processorConfig : config.preview.processors) {
            processorConfig.validate();
        }
    }

    @Nullable
    private static String getCodelessSdkNamePrefix() {
        if (!DiagnosticsHelper.isRpIntegration()) {
            return null;
        }
        StringBuilder sdkNamePrefix = new StringBuilder(4);
        sdkNamePrefix.append(DiagnosticsHelper.rpIntegrationChar());
        if (SystemInformation.INSTANCE.isWindows()) {
            sdkNamePrefix.append("w");
        } else if (SystemInformation.INSTANCE.isUnix()) {
            sdkNamePrefix.append("l");
        } else {
            startupLogger.warn("could not detect os: {}", System.getProperty("os.name"));
            sdkNamePrefix.append("u");
        }
        sdkNamePrefix.append("r_"); // "r" is for "recommended"
        return sdkNamePrefix.toString();
    }

    private static boolean hasConnectionStringOrInstrumentationKey(Configuration config) {
        return !Strings.isNullOrEmpty(config.connectionString);
    }

    private static ApplicationInsightsXmlConfiguration buildXmlConfiguration(Configuration config) {

        ApplicationInsightsXmlConfiguration xmlConfiguration = new ApplicationInsightsXmlConfiguration();

        if (!Strings.isNullOrEmpty(config.connectionString)) {
            xmlConfiguration.setConnectionString(config.connectionString);
        }
        if (!Strings.isNullOrEmpty(config.role.name)) {
            xmlConfiguration.setRoleName(config.role.name);
        }
        if (!Strings.isNullOrEmpty(config.role.instance)) {
            xmlConfiguration.setRoleInstance(config.role.instance);
        } else {
            String hostname = CommonUtils.getHostName();
            xmlConfiguration.setRoleInstance(hostname == null ? "unknown" : hostname);
        }

        // configure heartbeat module
        AddTypeXmlElement heartbeatModule = new AddTypeXmlElement();
        heartbeatModule.setType("com.microsoft.applicationinsights.internal.heartbeat.HeartBeatModule");
        // do not allow interval longer than 15 minutes, since we use the heartbeat data for usage telemetry
        long intervalSeconds = Math.min(config.heartbeat.intervalSeconds, MINUTES.toSeconds(15));
        heartbeatModule.getParameters().add(newParamXml("HeartBeatInterval", Long.toString(intervalSeconds)));
        ArrayList<AddTypeXmlElement> modules = new ArrayList<>();
        modules.add(heartbeatModule);
        TelemetryModulesXmlElement modulesXml = new TelemetryModulesXmlElement();
        modulesXml.setAdds(modules);
        xmlConfiguration.setModules(modulesXml);

        // configure custom jmx metrics
        ArrayList<JmxXmlElement> jmxXmls = new ArrayList<>();
        for (JmxMetric jmxMetric : config.jmxMetrics) {
            if (!jmxMetric.attribute.isEmpty()) {
                JmxXmlElement jmxXml = new JmxXmlElement();
                jmxXml.setName(jmxMetric.name);
                jmxXml.setObjectName(jmxMetric.objectName);
                jmxXml.setAttribute(jmxMetric.attribute);
                jmxXmls.add(jmxXml);
            }
            else if (!jmxMetric.attributes.isEmpty()) {
                String displayName = getJmxDisplayName(jmxMetric.objectName);
                for (String attribute : jmxMetric.attributes) {
                    JmxXmlElement jmxXml = new JmxXmlElement();
                    jmxXml.setName(displayName + attribute);
                    jmxXml.setObjectName(jmxMetric.objectName);
                    jmxXml.setAttribute(attribute);
                    jmxXmls.add(jmxXml);
                }
            }
        }
        xmlConfiguration.getPerformance().setJmxXmlElements(jmxXmls);

        xmlConfiguration.getPerformance().setCollectionFrequencyInSec(config.preview.metricIntervalSeconds);

        xmlConfiguration.getQuickPulse().setEnabled(config.preview.liveMetrics.enabled);

        if (config.preview.developerMode) {
            xmlConfiguration.getChannel().setDeveloperMode(true);
        }
        return xmlConfiguration;
    }

    static String getJmxDisplayName(String attribute) {
        StringBuilder name = new StringBuilder("");
        try
        {
            ObjectName nameObject = new ObjectName(attribute);
            name.append(nameObject.getDomain()).append(" /");
            Hashtable properties = nameObject.getKeyPropertyList();

            Set types = properties.entrySet();
            SortedMap<Integer, String> orderedTypes = new TreeMap<>();


            for(Object typeEntry : types) {
                String type = ((Map.Entry<String, String>)typeEntry).getKey();
                int typeIndex = attribute.indexOf(type);
                orderedTypes.put(typeIndex, (String)properties.get(type));
            }
            StringBuilder valuesString = new StringBuilder(orderedTypes.values().toString().replaceAll(", ", " /"));
            valuesString.deleteCharAt(0);
            valuesString.deleteCharAt(valuesString.length()-1);
            name.append(valuesString).append(" /");
        }
        catch(Exception e) {
            startupLogger.debug("Could not generate default metric name for attribute " + attribute);
        }
            return name.toString();
        }

    private static ParamXmlElement newParamXml(String name, String value) {
        ParamXmlElement paramXml = new ParamXmlElement();
        paramXml.setName(name);
        paramXml.setValue(value);
        return paramXml;
    }
}
