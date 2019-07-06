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

package com.microsoft.applicationinsights.internal.shared;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import com.microsoft.applicationinsights.telemetry.Telemetry;
import com.microsoft.applicationinsights.TelemetryClient;
import com.microsoft.applicationinsights.internal.common.ApplicationInsightsEvent;
import com.microsoft.applicationinsights.telemetry.SeverityLevel;
import com.microsoft.applicationinsights.telemetry.ExceptionTelemetry;
import com.microsoft.applicationinsights.telemetry.TelemetryContext;
import com.microsoft.applicationinsights.telemetry.TraceTelemetry;
import com.microsoft.applicationinsights.internal.common.LogTelemetryClientProxy;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Matchers;
import org.mockito.Mockito;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;

public class LogTelemetryClientProxyTests {

    // region Members

    private static final String INSTRUMENTATION_KEY = "Instrumentation_Key";
    private LogTelemetryClientProxy telemetryClientProxy;
    private TelemetryClient telemetryClientMock;
    private List<Telemetry> telemetriesSent;
    private List<Exception> exceptionsSent;

    // endregion Members

    // region Initialization

    @Before
    public void before() {
        this.telemetryClientMock = Mockito.mock(TelemetryClient.class);
        this.telemetriesSent = new LinkedList<Telemetry>();
        this.exceptionsSent = new LinkedList<Exception>();
        setupTelemetryClientMock();
        this.telemetryClientProxy = new LogTelemetryClientProxy(telemetryClientMock, INSTRUMENTATION_KEY);
    }

    // endregion Initialization

    // region Tests

    @Test
    public void testExceptionIsClassifiedAndSentCorrectly() {
        sendAIEventAndGetOutputTelemetry(true);

        Assert.assertEquals(0, telemetriesSent.size());
        Assert.assertEquals("Exactly one exception should be sent.", 1, exceptionsSent.size());
    }

    @Test
    public void testTraceIsClassifiedAndSentCorrectly() {
        sendAIEventAndGetOutputTelemetry(false);

        Assert.assertEquals(1, telemetriesSent.size());
        Assert.assertEquals(0, exceptionsSent.size());
        Assert.assertTrue("Exception telemetry should be sent.", telemetriesSent.get(0) instanceof TraceTelemetry);
    }

    @Test
    public void testWhenTelemetryClientProxyFailedToInitializeNoExceptionIsThrown() {

        // Setting telemetry client mock to fail proxy initialization when getting client context.
        Mockito.when(this.telemetryClientMock.getContext()).thenThrow(new NullPointerException("Exception!"));

        this.telemetryClientProxy = new LogTelemetryClientProxy(this.telemetryClientMock, INSTRUMENTATION_KEY);

        Assert.assertFalse("The proxy shouldn't have been initialized.", this.telemetryClientProxy.isInitialized());
    }

    @Test
    public void testTelemetryClientProxyInitializedCorrectlyWhenNullInstrumentationKeyProvided() {
        initializeTelemetryClientProxyWithInstrumentationKeyAndVerifyInitialization(null);
    }

    @Test
    public void testTelemetryClientProxyInitializedCorrectlyWhenEmptyInstrumentationKeyProvided() {
        initializeTelemetryClientProxyWithInstrumentationKeyAndVerifyInitialization("");
    }


    @Test
    public void testCustomParametersAddedByTelemetryClientProxy() {
        sendAIEventAndGetOutputTelemetry(false);

        // TODO: should custom parameters validated one-by-one for values?
        Map<String, String> customParameters = this.telemetriesSent.get(0).getContext().getProperties();

        Assert.assertTrue("Custom parameters list shouldn't be empty.", customParameters.size() > 0);
    }

    // endregion Tests

    // region Private methods

    private void sendAIEventAndGetOutputTelemetry(boolean isExceptionEvent) {
        this.telemetryClientProxy.sendEvent(createApplicationInsightEvent(isExceptionEvent));
    }

    private void initializeTelemetryClientProxyWithInstrumentationKeyAndVerifyInitialization(String key) {
        this.telemetryClientProxy = new LogTelemetryClientProxy(this.telemetryClientMock, key);

        Assert.assertTrue("Proxy should should have been initialized correctly.", this.telemetryClientProxy.isInitialized());
    }

    private ApplicationInsightsEvent createApplicationInsightEvent(boolean isExceptionEvent) {
        ApplicationInsightsEvent event = Mockito.mock(ApplicationInsightsEvent.class);
        Mockito.when(event.isException()).thenReturn(isExceptionEvent);
        Mockito.when(event.getException()).thenReturn(isExceptionEvent ? new Exception("Exception!") : null);
        Mockito.when(event.getCustomParameters()).thenReturn(new HashMap<String, String>() {{ put("Key", "Value"); }});

        return event;
    }

    private void setupTelemetryClientMock() {
        Mockito.when(this.telemetryClientMock.getContext()).thenReturn(new TelemetryContext());
        Mockito.doAnswer(new Answer() {
            @Override
            public Object answer(InvocationOnMock invocation) throws Throwable {
                Telemetry telemetry = ((Telemetry) invocation.getArguments()[0]);
                telemetriesSent.add(telemetry);

                return null;
            }
        }).when(this.telemetryClientMock).track(Matchers.any(Telemetry.class));

        Mockito.doAnswer(new Answer() {
            @Override
            public Object answer(InvocationOnMock invocation) throws Throwable {
                Exception e = ((Exception) invocation.getArguments()[0]);
                exceptionsSent.add(e);

                return null;
            }
        }).when(this.telemetryClientMock).trackException(Matchers.any(Exception.class),
                Matchers.any(SeverityLevel.class), Matchers.anyMapOf(String.class, String.class),
                Matchers.anyMapOf(String.class, Double.class));
    }

     // endregion Private methods
}