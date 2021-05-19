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
package com.microsoft.applicationinsights.internal.profiler;

import java.util.Iterator;
import java.util.ServiceLoader;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.function.Consumer;
import java.util.function.Supplier;

import com.microsoft.applicationinsights.TelemetryClient;
import com.microsoft.applicationinsights.alerting.AlertingSubsystem;
import com.microsoft.applicationinsights.alerting.alert.AlertBreach;
import com.microsoft.applicationinsights.extensibility.initializer.TelemetryObservers;
import com.microsoft.applicationinsights.internal.channel.common.LazyHttpClient;
import com.microsoft.applicationinsights.internal.util.ThreadPoolUtils;
import com.microsoft.applicationinsights.profileUploader.UploadCompleteHandler;
import com.microsoft.applicationinsights.profiler.ProfilerConfigurationHandler;
import com.microsoft.applicationinsights.profiler.ProfilerService;
import com.microsoft.applicationinsights.profiler.ProfilerServiceFactory;
import com.microsoft.applicationinsights.profiler.config.AlertConfigParser;
import com.microsoft.applicationinsights.profiler.config.ServiceProfilerServiceConfig;
import com.microsoft.applicationinsights.telemetry.EventTelemetry;
import org.apache.http.impl.client.CloseableHttpClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Service profiler main entry point, wires up:
 * - Alerting telemetry monitor subsystem
 * - JFR Profiling service
 * - JFR Uploader service
 */
public class ProfilerServiceInitializer {

    private static final Logger LOGGER = LoggerFactory.getLogger(ProfilerServiceInitializer.class);

    private static boolean initialized = false;
    private static com.microsoft.applicationinsights.profiler.ProfilerService profilerService;


    public synchronized static void initialize(Supplier<String> appIdSupplier,
                                               String processId,
                                               ServiceProfilerServiceConfig config,
                                               String machineName,
                                               String instrumentationKey,
                                               TelemetryClient client,
                                               String userAgent,
                                               GcEventMonitor.GcEventMonitorConfiguration gcEventMonitorConfiguration) {
        initialize(
                appIdSupplier,
                processId,
                config,
                machineName,
                instrumentationKey,
                client,
                LazyHttpClient.getInstance(),
                userAgent,
                gcEventMonitorConfiguration
        );
    }

    public synchronized static void initialize(Supplier<String> appIdSupplier,
                                               String processId,
                                               ServiceProfilerServiceConfig config,
                                               String machineName,
                                               String instrumentationKey,
                                               TelemetryClient client,
                                               CloseableHttpClient httpClient,
                                               String userAgent,
                                               GcEventMonitor.GcEventMonitorConfiguration gcEventMonitorConfiguration) {
        if (!initialized && config.enabled()) {
            initialized = true;
            ProfilerServiceFactory factory = null;

            try {
                factory = loadProfilerServiceFactory();
            } catch (Exception e) {
                LOGGER.error("Failed to load profiler factory", e);
            }

            if (factory == null) {
                LOGGER.error("Profiling has been enabled however no profiler implementation was provided. Please install an ApplicationInsights agent which provides a profiler.");
                return;
            }

            ScheduledExecutorService serviceProfilerExecutorService = Executors.newScheduledThreadPool(2,
                    ThreadPoolUtils.createDaemonThreadFactory(ProfilerServiceFactory.class, "ServiceProfilerService")
            );

            ScheduledExecutorService alertServiceExecutorService = Executors.newScheduledThreadPool(2,
                    ThreadPoolUtils.createDaemonThreadFactory(ProfilerServiceFactory.class, "ServiceProfilerAlertingService")
            );

            AlertingSubsystem alerting = createAlertMonitor(alertServiceExecutorService, client, gcEventMonitorConfiguration);

            Future<ProfilerService> future = factory.initialize(
                    appIdSupplier,
                    sendServiceProfilerIndex(client),
                    updateAlertingConfig(alerting),
                    processId,
                    config,
                    machineName,
                    instrumentationKey,
                    httpClient,
                    serviceProfilerExecutorService,
                    userAgent
            );

            serviceProfilerExecutorService.submit(() -> {
                try {
                    profilerService = future.get();
                } catch (Exception e) {
                    LOGGER.error("Unable to obtain JFR connection, this may indicate that your JVM does not have JFR enabled. JFR profiling system will shutdown", e);
                    alertServiceExecutorService.shutdown();
                    serviceProfilerExecutorService.shutdown();
                }
            });
        }
    }

    private static ProfilerServiceFactory loadProfilerServiceFactory() {
        return findServiceLoader(ProfilerServiceFactory.class);
    }

    protected static <T> T findServiceLoader(Class<T> clazz) {
        ServiceLoader<T> factory = ServiceLoader.load(clazz);
        Iterator<T> iterator = factory.iterator();
        if (iterator.hasNext()) {
            return iterator.next();
        }
        return null;
    }

    static ProfilerConfigurationHandler updateAlertingConfig(AlertingSubsystem alertingSubsystem) {
        return config -> alertingSubsystem.updateConfiguration(AlertConfigParser.toAlertingConfig(config));
    }

    static UploadCompleteHandler sendServiceProfilerIndex(TelemetryClient telemetryClient) {
        return done -> {
            EventTelemetry event = new EventTelemetry("ServiceProfilerIndex");
            event.getProperties().putAll(done.getServiceProfilerIndex().getProperties());
            event.getMetrics().putAll(done.getServiceProfilerIndex().getMetrics());
            telemetryClient.track(event);
        };
    }

    static AlertingSubsystem createAlertMonitor(
            ScheduledExecutorService alertServiceExecutorService,
            TelemetryClient telemetryClient,
            GcEventMonitor.GcEventMonitorConfiguration gcEventMonitorConfiguration) {
        return AlertingServiceFactory.create(alertAction(), TelemetryObservers.INSTANCE, telemetryClient, alertServiceExecutorService, gcEventMonitorConfiguration);
    }

    private static Consumer<AlertBreach> alertAction() {
        return alert -> {
            if (profilerService != null) {
                profilerService.getProfiler().accept(alert);
            }
        };
    }

}
