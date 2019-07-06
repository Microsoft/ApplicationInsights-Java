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

package com.microsoft.applicationinsights.internal.common;

import java.util.Map;

import com.microsoft.applicationinsights.TelemetryClient;
import com.microsoft.applicationinsights.telemetry.ExceptionTelemetry;
import com.microsoft.applicationinsights.telemetry.Telemetry;
import com.microsoft.applicationinsights.telemetry.TraceTelemetry;
import com.microsoft.applicationinsights.internal.util.LocalStringsUtils;

/**
 * This class encapsulates all the common logic for sending AI telemetry.
 * This class is used by all Appenders, Listeners etc and therefore keeping them without
 * any logic.
 */
public class LogTelemetryClientProxy implements TelemetryClientProxy {

    // region Members

    private boolean isInitialized = false;
    private TelemetryClient telemetryClient;

    // endregion Members

    // region Constructor

    /**
     * Constructs new telemetry client proxy instance with the given client.
     * @param telemetryClient The telemetry client.
     * @param instrumentationKey The instrumentation key.
     */
    public LogTelemetryClientProxy(TelemetryClient telemetryClient, String instrumentationKey) {
        try {
            this.telemetryClient = telemetryClient;
            if (!LocalStringsUtils.isNullOrEmpty(instrumentationKey)) {
                this.telemetryClient.getContext().setInstrumentationKey(instrumentationKey);
            }

            this.isInitialized = true;
        } catch (Exception e) {
            // Catching all exceptions so in case of a failure the calling appender won't throw exception.
            // TODO: Assert.Debug/warning on exception?
        }
    }

    /**
     * Constructs new telemetry client proxy instance.
     * @param instrumentationKey The instrumentation key for sending the events.
     */
    public LogTelemetryClientProxy(String instrumentationKey) {
        this(new TelemetryClient(), instrumentationKey);
    }

    // endregion Constructor

    // region Public methods

    public boolean isInitialized() {
        return this.isInitialized;
    }

    /**
     * Sends the given event to AI.
     *
     * @param event The event to send.
     */
    public void sendEvent(ApplicationInsightsEvent event) {

        String formattedMessage = event.getMessage();

        Map<String, String> customParameters = event.getCustomParameters();


        if (event.isException()) {
            telemetryClient.trackException(event.getException(),
                    event.getNormalizedSeverityLevel(),
                    customParameters,
                    null);
        } else {
            TraceTelemetry telemetry = new TraceTelemetry(formattedMessage);
            telemetry.setSeverityLevel(event.getNormalizedSeverityLevel());
            telemetry.getContext().getProperties().putAll(customParameters);
            telemetryClient.track(telemetry);
        }
    }

    /**
     * Gets the telemetry client.
     *
     * @return Telemetry client
     */
    public TelemetryClient getTelemetryClient() {
        return this.telemetryClient;
    }

    // endregion Public methods
}