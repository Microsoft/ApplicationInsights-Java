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

package com.microsoft.applicationinsights.internal.config;

import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlAttribute;

/**
 * Created by gupele on 3/13/2015.
 */
@XmlRootElement(name="ApplicationInsights")
public class ApplicationInsightsXmlConfiguration {
    private String instrumentationKey;

    private boolean disableTelemetry = false;

    private TelemetryInitializersXmlElement telemetryInitializers;
    private TelemetryProcessorsXmlElement telemetryProcessors;
    private ContextInitializersXmlElement contextInitializers;
    private ChannelXmlElement channel = new ChannelXmlElement();
    private TelemetryModulesXmlElement modules;
    private PerformanceCountersXmlElement performance = new PerformanceCountersXmlElement();
    private SDKLoggerXmlElement sdkLogger;
    private SamplerXmlElement sampler;
    private QuickPulseXmlElement quickPulse;
    private ExceptionsXmlElement exceptions;

    private String schemaVersion;

    public String getInstrumentationKey() {
        return instrumentationKey;
    }

    @XmlElement(name="InstrumentationKey")
    public void setInstrumentationKey(String instrumentationKey) {
        this.instrumentationKey = instrumentationKey;
    }

    public String getSchemaVersion() {
        return schemaVersion;
    }

    @XmlAttribute
    public void setSchemaVersion(String schemaVersion) {
        this.schemaVersion = schemaVersion;
    }

    public TelemetryInitializersXmlElement getTelemetryInitializers() {
        return telemetryInitializers;
    }

    @XmlElement(name="TelemetryInitializers")
    public void setTelemetryInitializers(TelemetryInitializersXmlElement telemetryInitializers) {
        this.telemetryInitializers = telemetryInitializers;
    }

    public ContextInitializersXmlElement getContextInitializers() {
        return contextInitializers;
    }

    @XmlElement(name="TelemetryProcessors")
    public void setTelemetryProcessors(TelemetryProcessorsXmlElement telemetryProcessors) {
        this.telemetryProcessors = telemetryProcessors;
    }

    public TelemetryProcessorsXmlElement getTelemetryProcessors() {
        return telemetryProcessors;
    }

    @XmlElement(name="ContextInitializers")
    public void setContextInitializers(ContextInitializersXmlElement contextInitializers) {
        this.contextInitializers = contextInitializers;
    }

    public ChannelXmlElement getChannel() {
        return channel;
    }

    @XmlElement(name="Channel")
    public void setChannel(ChannelXmlElement channel) {
        this.channel = channel;
    }

    public SamplerXmlElement getSampler() {
        return sampler;
    }

    @XmlElement(name="Sampling")
    public void setSampler(SamplerXmlElement sampler) {
        this.sampler = sampler;
    }

    public QuickPulseXmlElement getQuickPulse() {
        if (quickPulse == null) {
            quickPulse = new QuickPulseXmlElement();
        }
        return quickPulse;
    }

    @XmlElement(name="QuickPulse")
    public void setQuickPulse(QuickPulseXmlElement quickPulse) {
        this.quickPulse = quickPulse;
    }

    public SDKLoggerXmlElement getSdkLogger() {
        return sdkLogger;
    }

    @XmlElement(name="SDKLogger")
    public void setSdkLogger(SDKLoggerXmlElement sdkLogger) {
        this.sdkLogger = sdkLogger;
    }

    public boolean isDisableTelemetry() {
        return disableTelemetry;
    }

    @XmlElement(name="DisableTelemetry")
    public void setDisableTelemetry(boolean disableTelemetry) {
        this.disableTelemetry = disableTelemetry;
    }

    public ExceptionsXmlElement getExceptions() {
        return exceptions;
    }

    @XmlElement(name="Exceptions")
    public void setExceptions(ExceptionsXmlElement exceptions) {
        this.exceptions = exceptions;
    }

    public TelemetryModulesXmlElement getModules() {
        return modules;
    }

    @XmlElement(name="TelemetryModules")
    public void setModules(TelemetryModulesXmlElement modules) {
        this.modules = modules;
    }

    public PerformanceCountersXmlElement getPerformance() {
        return performance;
    }

    @XmlElement(name="PerformanceCounters")
    public void setPerformance(PerformanceCountersXmlElement performance) {
        this.performance = performance;
    }
}
