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

package com.microsoft.applicationinsights.log4j.v2;

import com.microsoft.applicationinsights.internal.common.LogTelemetryClientProxy;
import com.microsoft.applicationinsights.internal.common.TelemetryClientProxy;
import com.microsoft.applicationinsights.internal.logger.InternalLogger;
import com.microsoft.applicationinsights.log4j.v2.internal.ApplicationInsightsLogEvent;
import org.apache.logging.log4j.core.*;
import org.apache.logging.log4j.core.appender.AbstractAppender;
import org.apache.logging.log4j.core.config.plugins.Plugin;
import org.apache.logging.log4j.core.config.plugins.PluginAttribute;
import org.apache.logging.log4j.core.config.plugins.PluginFactory;
import org.apache.logging.log4j.core.config.plugins.PluginBuilderFactory;
import org.apache.logging.log4j.core.config.plugins.PluginElement;
import org.apache.logging.log4j.core.config.plugins.validation.constraints.Required;
import org.apache.logging.log4j.core.config.plugins.PluginBuilderAttribute;
import org.apache.commons.lang3.exception.ExceptionUtils;
import java.io.Serializable;

@Plugin(name="ApplicationInsightsAppender", category = "Core", elementType = "appender")
public class ApplicationInsightsAppender extends AbstractAppender {

    //region Members

    private boolean isInitialized = false;
    private transient TelemetryClientProxy telemetryClientProxy;
    private static final long serialVersionUID = 1L;

    //Builder to create ApplicationInsights Appender Plugin, used by default by log4j if present
    public static class Builder implements org.apache.logging.log4j.core.util.Builder<ApplicationInsightsAppender> {

        @PluginBuilderAttribute
        @Required(message = "No name provided for ApplicationInsightsAppender")
        private String name;

        //This is only needed when seperatly using Application Insights log4j2 appender
        //without application insights core module. otherwise AI-core module will pick up Instrumentation-Key
        @PluginBuilderAttribute
        private String instrumentationKey;

        @PluginBuilderAttribute
        private boolean ignoreExceptions;

        @PluginElement("Layout")
        private Layout<? extends Serializable> layout;

        @PluginElement("Filter")
        private Filter filter;

        public Builder setName(final String name) {
            this.name = name;
            return this;
        }

        public Builder setInstrumentationKey(final String instrumentationKey) {
            this.instrumentationKey = instrumentationKey;
            return this;
        }

        public Builder setIgnoreExceptions(boolean ignoreExceptions) {
            this.ignoreExceptions = ignoreExceptions;
            return this;
        }

        public Builder setLayout(final Layout<? extends Serializable> layout) {
            this.layout = layout;
            return this;
        }

        public Builder setFilter(Filter filter) {
            this.filter = filter;
            return this;
        }

        @Override
        public ApplicationInsightsAppender build() {
            return new ApplicationInsightsAppender(name, instrumentationKey, filter, layout, ignoreExceptions);
        }
    }

    //endregion Members

    //region Ctor

    /**
     * Constructs new Application Insights appender.
     * @param name The appender name.
     * @param instrumentationKey The instrumentation key.
     */
    protected ApplicationInsightsAppender(String name, String instrumentationKey) {
        super(name, null, null);

        try {
            telemetryClientProxy = new LogTelemetryClientProxy(instrumentationKey);
            this.isInitialized = true;
        } catch (Exception e) {
            // Appender failure must not fail the running application.
            this.isInitialized = false;
            InternalLogger.INSTANCE.error("Failed to initialize appender with exception: %s. Generated Stack trace is %s.", e.toString(), ExceptionUtils.getStackTrace(e));
        }
    }


    /**
     * @param name The Appender name
     * @param instrumentationKey The AI-resource iKey
     * @param filter log4j2 Filter object
     * @param layout Log4j2 Layout object
     * @param ignoreExceptions true/false to determine if exceptions should be ignored
     */
    protected ApplicationInsightsAppender(String name, String instrumentationKey, Filter filter, Layout<? extends Serializable> layout,
                                          boolean ignoreExceptions) {

        super(name, filter, layout, ignoreExceptions);

        try {
            telemetryClientProxy = new LogTelemetryClientProxy(instrumentationKey);
            this.isInitialized = true;
        } catch (Exception e) {
            // Appender failure must not fail the running application.
            this.isInitialized = false;
            InternalLogger.INSTANCE.error("Failed to initialize appender with exception: %s. Generated Stack trace is %s.", e.toString(), ExceptionUtils.getStackTrace(e));
        }
    }

    //endregion Ctor

    //region Public methods

    public LogTelemetryClientProxy getTelemetryClientProxy() {
        return (LogTelemetryClientProxy)this.telemetryClientProxy;
    }

    /**
     * Returns a plugin Builder object which is used internally by Log4j2 to create plugin
     * @return
     */
    @PluginBuilderFactory
    public static Builder newBuilder() {
        return new Builder();
    }

    /**
     * Creates new appender with the given name and instrumentation key.
     * This method is being called on the application startup upon Log4j system initialization.
     * @param name The appender name.
     * @param instrumentationKey The instrumentation key.
     * @return New Application Insights appender.
     */
    @PluginFactory
    public static ApplicationInsightsAppender createAppender(
            @PluginAttribute("name") String name,
            @PluginAttribute("instrumentationKey") String instrumentationKey) {

        return new ApplicationInsightsAppender(name, instrumentationKey);
    }

    /**
     * Logs a LogEvent using whatever logic this Appender wishes to use. It is typically recommended to use a
     * bridge pattern not only for the benefits from decoupling an Appender from its implementation, but it is also
     * handy for sharing resources which may require some form of locking.
     *
     * @param event The LogEvent.
     */
    @Override
    public void append(LogEvent event) {
        if (!this.isStarted() || !this.isInitialized) {

            // TODO: trace not started or not initialized.
            return;
        }

        try {
            //Formats a logging event using the layout
            ApplicationInsightsLogEvent aiEvent = new ApplicationInsightsLogEvent(event,getLayout());
            this.telemetryClientProxy.sendEvent(aiEvent);
        } catch (Exception e) {
            // Appender failure must not fail the running application.
            InternalLogger.INSTANCE.error("Something unexpected happened while sending logs %s", ExceptionUtils.getStackTrace(e));
        }
    }

    /**
     * This method is being called on object initialization.
     */
    @Override
    public void start() {
        super.start();
    }

    //endregion Public methods
}
