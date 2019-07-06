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

package com.microsoft.applicationinsights.autoconfigure;

import static org.slf4j.LoggerFactory.getLogger;

import com.microsoft.applicationinsights.TelemetryClient;
import com.microsoft.applicationinsights.TelemetryConfiguration;
import com.microsoft.applicationinsights.autoconfigure.ApplicationInsightsProperties.Channel.InProcess;
import com.microsoft.applicationinsights.autoconfigure.ApplicationInsightsProperties.Channel.LocalForwarder;
import com.microsoft.applicationinsights.autoconfigure.conditionals.InstrumentationKeyCondition;
import com.microsoft.applicationinsights.channel.TelemetryChannel;
import com.microsoft.applicationinsights.channel.concrete.inprocess.InProcessTelemetryChannel;
import com.microsoft.applicationinsights.channel.concrete.localforwarder.LocalForwarderTelemetryChannel;
import com.microsoft.applicationinsights.exceptions.IllegalConfigurationException;
import com.microsoft.applicationinsights.extensibility.ContextInitializer;
import com.microsoft.applicationinsights.extensibility.TelemetryInitializer;
import com.microsoft.applicationinsights.extensibility.TelemetryModule;
import com.microsoft.applicationinsights.extensibility.TelemetryProcessor;
import com.microsoft.applicationinsights.internal.logger.InternalLogger;
import com.microsoft.applicationinsights.internal.perfcounter.PerformanceCounterContainer;
import com.microsoft.applicationinsights.internal.quickpulse.QuickPulse;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.microsoft.applicationinsights.telemetry.ExceptionTelemetryOptions;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.AutoConfigureBefore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.DependsOn;
import org.springframework.context.annotation.Import;
import org.springframework.core.env.Environment;

/**
 * <h1>The central class for configuring and creating initialized {@link TelemetryConfiguration} </h1>
 * This class is supposed to perform auto-configuration before Micrometer.
 *
 * @author Arthur Gavlyukovskiy, Dhaval Doshi
 */
@Configuration
@Conditional(InstrumentationKeyCondition.class)
@EnableConfigurationProperties(ApplicationInsightsProperties.class)
@ConditionalOnClass(TelemetryConfiguration.class)
@Import({
        ApplicationInsightsModuleConfiguration.class,
        ApplicationInsightsWebModuleConfiguration.class
})
@AutoConfigureBefore(name = {
    "io.micrometer.spring.autoconfigure.export.azuremonitor.AzureMonitorMetricsExportAutoConfiguration",
    "com.microsoft.azure.spring.autoconfigure.metrics.AzureMonitorMetricsExportAutoConfiguration"
})
public class ApplicationInsightsTelemetryAutoConfiguration {

    private static final Logger log = getLogger(ApplicationInsightsTelemetryAutoConfiguration.class);

    private ApplicationInsightsProperties applicationInsightsProperties;

    private Collection<ContextInitializer> contextInitializers;

    private Collection<TelemetryInitializer> telemetryInitializers;

    private Collection<TelemetryModule> telemetryModules;

    private Collection<TelemetryProcessor> telemetryProcessors;

    @Autowired
    private Environment environment;

    @Autowired
    public ApplicationInsightsTelemetryAutoConfiguration(
        ApplicationInsightsProperties applicationInsightsProperties) {
        this.applicationInsightsProperties = applicationInsightsProperties;
    }

    @Autowired(required = false)
    public void setContextInitializers(
        Collection<ContextInitializer> contextInitializers) {
        this.contextInitializers = contextInitializers;
    }

    @Autowired(required = false)
    public void setTelemetryInitializers(
        Collection<TelemetryInitializer> telemetryInitializers) {
        this.telemetryInitializers = telemetryInitializers;
    }

    @Autowired(required = false)
    public void setTelemetryModules(
        Collection<TelemetryModule> telemetryModules) {
        this.telemetryModules = telemetryModules;
    }

    @Autowired(required = false)
    public void setTelemetryProcessors(
        Collection<TelemetryProcessor> telemetryProcessors) {
        this.telemetryProcessors = telemetryProcessors;
    }

    @Bean
    @DependsOn("internalLogger")
    public TelemetryConfiguration telemetryConfiguration(TelemetryChannel telemetryChannel) {
        TelemetryConfiguration telemetryConfiguration = TelemetryConfiguration.getActiveWithoutInitializingConfig();
        telemetryConfiguration.setTrackingIsDisabled(!applicationInsightsProperties.isEnabled());
        telemetryConfiguration.setInstrumentationKey(applicationInsightsProperties.getInstrumentationKey());
        if (contextInitializers != null) {
            telemetryConfiguration.getContextInitializers().addAll(contextInitializers);
        }
        if (telemetryInitializers != null) {
            telemetryConfiguration.getTelemetryInitializers().addAll(telemetryInitializers);
        }
        if (telemetryModules != null) {
            telemetryConfiguration.getTelemetryModules().addAll(telemetryModules);
        }
        if (telemetryProcessors != null) {
            telemetryConfiguration.getTelemetryProcessors().addAll(telemetryProcessors);
        }
        telemetryConfiguration.setChannel(telemetryChannel);
        initializeComponents(telemetryConfiguration);
        initializePerformanceCounterContainer();

        ApplicationInsightsProperties.Exceptions exceptionsConfig = applicationInsightsProperties.getExceptions();
        ExceptionTelemetryOptions options;
        if (exceptionsConfig != null) {
            options = new ExceptionTelemetryOptions(
                    exceptionsConfig.getMaxStackSize(),
                    exceptionsConfig.getMaxExceptionTraceLength()
            );
        }else{
            options = ExceptionTelemetryOptions.empty();
        }
        telemetryConfiguration.setExceptionTelemetryOptions(options);
        log.debug("Exception Handling Options: {}", options);

        return telemetryConfiguration;
    }

    // TODO: copy-paste from TelemetryConfigurationFactory, move to TelemetryConfiguration?
    private void initializeComponents(TelemetryConfiguration configuration) {
        List<TelemetryModule> telemetryModules = configuration.getTelemetryModules();

        for (TelemetryModule module : telemetryModules) {
            try {
                module.initialize(configuration);
            }
            catch (Exception e) {
                log.error("Failed to initialized telemetry module " + module.getClass().getSimpleName(), e);
            }
        }
    }

    @Bean
    public TelemetryClient telemetryClient(TelemetryConfiguration configuration) {
        return new TelemetryClient(configuration);
    }



    @Bean
    @ConditionalOnMissingBean
    public TelemetryChannel telemetryChannel() throws IllegalConfigurationException {
        String localForwarderEndpoint = environment.getProperty("azure.application-insights.channel.local-forwarder.endpoint-address");
        String inProcessEndPoint = environment.getProperty("azure.application-insights.channel.in-process.endpoint-address");
        if (StringUtils.isNotBlank(localForwarderEndpoint) && StringUtils.isNotBlank(inProcessEndPoint)) {
            throw new IllegalConfigurationException("SDK cannot have two channels, please either remove Local Forwarder Endpoint, "
                + "or In Process Endpoint");
        }

        // If local forwarder endpoint is present configure local forwarder channel
        if (StringUtils.isNotBlank(localForwarderEndpoint)) {
            LocalForwarder lf = applicationInsightsProperties.getChannel().getLocalForwarder();

            return new LocalForwarderTelemetryChannel(lf.getEndpointAddress(), false, lf.getMaxTelemetryBufferCapacity() ,
                lf.getFlushIntervalInSeconds());
        }

        InProcess inProcess = applicationInsightsProperties.getChannel().getInProcess();
        return new InProcessTelemetryChannel(inProcess.getEndpointAddress(),
                String.valueOf(inProcess.getMaxTransmissionStorageFilesCapacityInMb()), inProcess.isDeveloperMode(),
                inProcess.getMaxTelemetryBufferCapacity(), inProcess.getFlushIntervalInSeconds(), inProcess.isThrottling(),
            inProcess.getMaxInstantRetry());
    }

    // If local forwarder channel is present quick pulse would not be initialized.
    @Bean
    @ConditionalOnProperty(value = "azure.application-insights.quick-pulse.enabled", havingValue = "true", matchIfMissing = true)
    @DependsOn("telemetryConfiguration")
    public QuickPulse quickPulse() {
        String inProcessEndPoint = environment.getProperty("azure.application-insights.channel.local-forwarder.endpoint-address");
        if (StringUtils.isNotBlank(inProcessEndPoint)) {
          return QuickPulse.INSTANCE;
        }
        QuickPulse.INSTANCE.initialize();
        return QuickPulse.INSTANCE;
    }

    @Bean
    public InternalLogger internalLogger() {
        Map<String, String> loggerParameters = new HashMap<>();
        ApplicationInsightsProperties.Logger logger = applicationInsightsProperties.getLogger();
        loggerParameters.put("Level", logger.getLevel().name());
        loggerParameters.put("UniquePrefix", logger.getUniquePrefix());
        loggerParameters.put("BaseFolderPath", logger.getBaseFolderPath());
        loggerParameters.put("NumberOfFiles", logger.getNumberOfFiles());
        loggerParameters.put("NumberOfTotalSizeInMB", logger.getNumberOfTotalSizeInMB());
        InternalLogger.INSTANCE.initialize(logger.getType().name(), loggerParameters);
        return InternalLogger.INSTANCE;
    }

    // calling directly telemetry configuration call to have Internal logger configured first
     private void initializePerformanceCounterContainer() {
        ApplicationInsightsProperties.PerformanceCounter performanceCounter = applicationInsightsProperties.getPerformanceCounter();
        PerformanceCounterContainer.INSTANCE.setCollectionFrequencyInSec(performanceCounter.getCollectionFrequencyInSeconds());

        ApplicationInsightsProperties.Jmx jmx = applicationInsightsProperties.getJmx();
        if (jmx.getJmxCounters() !=null && jmx.getJmxCounters().size() > 0) {
            applicationInsightsProperties.processAndLoadJmxCounters(jmx.getJmxCounters());
        }
    }
}
