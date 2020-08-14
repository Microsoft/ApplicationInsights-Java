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

package com.microsoft.applicationinsights.log4j.v2.internal;

import com.microsoft.applicationinsights.log4j.v2.ApplicationInsightsAppender;
import com.microsoft.applicationinsights.telemetry.SeverityLevel;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.Marker;
import org.apache.logging.log4j.ThreadContext;
import org.apache.logging.log4j.core.Appender;
import org.apache.logging.log4j.core.Layout;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.impl.ThrowableProxy;
import org.apache.logging.log4j.message.Message;
import org.junit.Test;

import java.util.Map;

import static org.junit.Assert.assertEquals;

public final class ApplicationInsightsLogEventTest {
    @Test
    public void testFatalSeverityLevel() {
        testSeverityLevel(Level.FATAL, SeverityLevel.Critical);
    }

    @Test
    public void testErrorSeverityLevel() {
        testSeverityLevel(Level.ERROR, SeverityLevel.Error);
    }

    @Test
    public void testInfoSeverityLevel() {
        testSeverityLevel(Level.INFO, SeverityLevel.Information);
    }

    @Test
    public void testWarningSeverityLevel() {
        testSeverityLevel(Level.WARN, SeverityLevel.Warning);
    }

    @Test
    public void testDebugSeverityLevel() {
        testSeverityLevel(Level.DEBUG, SeverityLevel.Verbose);
    }

    @Test
    public void testTraceSeverityLevel() {
        testSeverityLevel(Level.TRACE, SeverityLevel.Verbose);
    }

    @Test
    public void testAllSeverityLevel() {
        testSeverityLevel(Level.ALL, SeverityLevel.Verbose);
    }

    private static void testSeverityLevel(final Level level, SeverityLevel expected) {
        org.apache.logging.log4j.core.LogEvent logEvent = new org.apache.logging.log4j.core.AbstractLogEvent() {
            @Override
            public Level getLevel() {
                return level;
            }
        };

        Logger logger = LogManager.getRootLogger();
        org.apache.logging.log4j.core.Logger coreLogger = (org.apache.logging.log4j.core.Logger)logger;

        Map<String, Appender> appenderMap = coreLogger.getAppenders();
        ApplicationInsightsAppender appender = (ApplicationInsightsAppender) appenderMap.get("test");

        ApplicationInsightsLogEvent event = new ApplicationInsightsLogEvent(logEvent,appender.getLayout());

        assertEquals(expected, event.getNormalizedSeverityLevel());
    }
}