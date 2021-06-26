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

package com.microsoft.applicationinsights.smoketest;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import com.microsoft.applicationinsights.smoketest.schemav2.Data;
import com.microsoft.applicationinsights.smoketest.schemav2.Envelope;
import com.microsoft.applicationinsights.smoketest.schemav2.ExceptionData;
import com.microsoft.applicationinsights.smoketest.schemav2.MessageData;
import com.microsoft.applicationinsights.smoketest.schemav2.RequestData;
import com.microsoft.applicationinsights.smoketest.schemav2.SeverityLevel;
import java.util.Comparator;
import java.util.List;
import org.junit.Test;

@UseAgent("logging")
public class TraceJavaUtilLoggingTest extends AiSmokeTest {

  @Test
  @TargetUri("/traceJavaUtilLogging")
  public void testTraceJavaUtilLogging() throws Exception {
    List<Envelope> rdList = mockedIngestion.waitForItems("RequestData", 1);
    List<Envelope> mdList = mockedIngestion.waitForMessageItemsInRequest(2);

    Envelope rdEnvelope = rdList.get(0);
    Envelope mdEnvelope1 = mdList.get(0);
    Envelope mdEnvelope2 = mdList.get(1);

    RequestData rd = (RequestData) ((Data<?>) rdEnvelope.getData()).getBaseData();

    List<MessageData> logs = mockedIngestion.getMessageDataInRequest();
    logs.sort(Comparator.comparing(MessageData::getSeverityLevel));

    MessageData md1 = logs.get(0);
    MessageData md2 = logs.get(1);

    assertEquals("This is jul warning.", md1.getMessage());
    assertEquals(SeverityLevel.Warning, md1.getSeverityLevel());
    assertEquals("Logger", md1.getProperties().get("SourceType"));
    assertEquals("WARNING", md1.getProperties().get("LoggingLevel"));
    assertEquals("smoketestapp", md1.getProperties().get("LoggerName"));
    assertEquals(3, md1.getProperties().size());

    assertEquals("This is jul severe.", md2.getMessage());
    assertEquals(SeverityLevel.Error, md2.getSeverityLevel());
    assertEquals("Logger", md2.getProperties().get("SourceType"));
    assertEquals("SEVERE", md2.getProperties().get("LoggingLevel"));
    assertEquals("smoketestapp", md1.getProperties().get("LoggerName"));
    assertEquals(3, md2.getProperties().size());

    assertParentChild(
        rd, rdEnvelope, mdEnvelope1, "GET /TraceJavaUtilLoggingUsingAgent/traceJavaUtilLogging");
    assertParentChild(
        rd, rdEnvelope, mdEnvelope2, "GET /TraceJavaUtilLoggingUsingAgent/traceJavaUtilLogging");
  }

  @Test
  @TargetUri("traceJavaUtilLoggingWithException")
  public void testTraceJavaUtilLoggingWithExeption() throws Exception {
    List<Envelope> rdList = mockedIngestion.waitForItems("RequestData", 1);

    Envelope rdEnvelope = rdList.get(0);
    String operationId = rdEnvelope.getTags().get("ai.operation.id");
    List<Envelope> edList =
        mockedIngestion.waitForItemsInOperation("ExceptionData", 1, operationId);
    assertEquals(0, mockedIngestion.getCountForType("EventData"));

    Envelope edEnvelope = edList.get(0);

    RequestData rd = (RequestData) ((Data<?>) rdEnvelope.getData()).getBaseData();

    ExceptionData ed = (ExceptionData) ((Data<?>) edEnvelope.getData()).getBaseData();

    assertEquals("Fake Exception", ed.getExceptions().get(0).getMessage());
    assertEquals(SeverityLevel.Error, ed.getSeverityLevel());
    assertEquals("This is an exception!", ed.getProperties().get("Logger Message"));
    assertEquals("Logger", ed.getProperties().get("SourceType"));
    assertEquals("SEVERE", ed.getProperties().get("LoggingLevel"));
    assertEquals("smoketestapp", ed.getProperties().get("LoggerName"));
    assertEquals(4, ed.getProperties().size());

    assertParentChild(
        rd,
        rdEnvelope,
        edEnvelope,
        "GET /TraceJavaUtilLoggingUsingAgent/traceJavaUtilLoggingWithException");
  }

  private static void assertParentChild(
      RequestData rd, Envelope rdEnvelope, Envelope childEnvelope, String operationName) {
    String operationId = rdEnvelope.getTags().get("ai.operation.id");
    assertNotNull(operationId);
    assertEquals(operationId, childEnvelope.getTags().get("ai.operation.id"));

    String operationParentId = rdEnvelope.getTags().get("ai.operation.parentId");
    assertNull(operationParentId);

    assertEquals(rd.getId(), childEnvelope.getTags().get("ai.operation.parentId"));

    assertEquals(operationName, rdEnvelope.getTags().get("ai.operation.name"));
    assertEquals(operationName, childEnvelope.getTags().get("ai.operation.name"));
  }
}
