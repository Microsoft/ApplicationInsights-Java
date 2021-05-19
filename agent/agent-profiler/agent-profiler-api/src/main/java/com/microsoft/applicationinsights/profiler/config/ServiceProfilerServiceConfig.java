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
package com.microsoft.applicationinsights.profiler.config;


/**
 * Configuration of the service profiler subsystem
 */
public class ServiceProfilerServiceConfig {
    public static final int DEFAULT_CONFIG_POLL_PERIOD_IN_MS = 60000;
    public static final int DEFAULT_PERIODIC_RECORDING_DURATION_IN_S = 120;
    public static final int DEFAULT_PERIODIC_RECORDING_INTERVAL_IN_S = 60 * 60;

    // duration between polls for configuration changes
    private final int configPollPeriod;

    // default duration of periodic profiles
    private final int periodicRecordingDuration;

    // default interval of periodic profiles
    private final int periodicRecordingInterval;

    private final String serviceProfilerFrontEndPoint;

    // Enable entire service profiler subsystem
    private final boolean enabled;

    public ServiceProfilerServiceConfig(int configPollPeriod, int periodicRecordingDuration, int periodicRecordingInterval, String serviceProfilerFrontEndPoint, boolean enabled) {
        this.configPollPeriod = configPollPeriod;
        this.periodicRecordingDuration = periodicRecordingDuration;
        this.periodicRecordingInterval = periodicRecordingInterval;
        this.serviceProfilerFrontEndPoint = serviceProfilerFrontEndPoint;
        this.enabled = enabled;
    }

    public int getConfigPollPeriod() {
        return configPollPeriod != -1 ? configPollPeriod * 1000 : DEFAULT_CONFIG_POLL_PERIOD_IN_MS;
    }

    public long getPeriodicRecordingDuration() {
        return periodicRecordingDuration != -1 ? periodicRecordingDuration : DEFAULT_PERIODIC_RECORDING_DURATION_IN_S;
    }

    public long getPeriodicRecordingInterval() {
        return periodicRecordingInterval != -1 ? periodicRecordingInterval : DEFAULT_PERIODIC_RECORDING_INTERVAL_IN_S;
    }

    public String getServiceProfilerFrontEndPoint() {
        return serviceProfilerFrontEndPoint != null ? serviceProfilerFrontEndPoint : FrontendEndpoints.PRODUCT_GLOBAL;
    }

    public boolean enabled() {
        return enabled;
    }
}
