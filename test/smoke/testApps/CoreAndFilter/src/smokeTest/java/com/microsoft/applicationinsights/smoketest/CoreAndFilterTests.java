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

import static com.microsoft.applicationinsights.smoketest.matchers.ExceptionDataMatchers.ExceptionDetailsMatchers.withMessage;
import static com.microsoft.applicationinsights.smoketest.matchers.ExceptionDataMatchers.hasException;
import static com.microsoft.applicationinsights.smoketest.matchers.ExceptionDataMatchers.hasMeasurement;
import static com.microsoft.applicationinsights.smoketest.matchers.ExceptionDataMatchers.hasSeverityLevel;
import static com.microsoft.applicationinsights.smoketest.matchers.RequestDataMatchers.hasDuration;
import static com.microsoft.applicationinsights.smoketest.matchers.RequestDataMatchers.hasName;
import static com.microsoft.applicationinsights.smoketest.matchers.RequestDataMatchers.hasResponseCode;
import static com.microsoft.applicationinsights.smoketest.matchers.RequestDataMatchers.hasSuccess;
import static com.microsoft.applicationinsights.smoketest.matchers.RequestDataMatchers.hasUrl;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.both;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.lessThan;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;

import com.microsoft.applicationinsights.smoketest.matchers.ExceptionDataMatchers;
import com.microsoft.applicationinsights.smoketest.matchers.TraceDataMatchers;
import com.microsoft.applicationinsights.smoketest.schemav2.Data;
import com.microsoft.applicationinsights.smoketest.schemav2.DataPoint;
import com.microsoft.applicationinsights.smoketest.schemav2.DataPointType;
import com.microsoft.applicationinsights.smoketest.schemav2.Domain;
import com.microsoft.applicationinsights.smoketest.schemav2.Envelope;
import com.microsoft.applicationinsights.smoketest.schemav2.EventData;
import com.microsoft.applicationinsights.smoketest.schemav2.ExceptionData;
import com.microsoft.applicationinsights.smoketest.schemav2.ExceptionDetails;
import com.microsoft.applicationinsights.smoketest.schemav2.MessageData;
import com.microsoft.applicationinsights.smoketest.schemav2.MetricData;
import com.microsoft.applicationinsights.smoketest.schemav2.PageViewData;
import com.microsoft.applicationinsights.smoketest.schemav2.RemoteDependencyData;
import com.microsoft.applicationinsights.smoketest.schemav2.RequestData;
import com.microsoft.applicationinsights.smoketest.schemav2.SeverityLevel;
import com.microsoft.applicationinsights.smoketest.telemetry.Duration;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;
import org.junit.Test;

@UseAgent
public class CoreAndFilterTests extends AiSmokeTest {

  @Test
  @TargetUri("/trackDependency")
  public void trackDependency() throws Exception {
    List<Envelope> rdList = mockedIngestion.waitForItems("RequestData", 1);

    Envelope rdEnvelope = rdList.get(0);
    String operationId = rdEnvelope.getTags().get("ai.operation.id");

    List<Envelope> rddList =
        mockedIngestion.waitForItemsInOperation("RemoteDependencyData", 1, operationId);
    assertEquals(0, mockedIngestion.getCountForType("EventData"));

    Envelope rddEnvelope = rddList.get(0);

    RequestData rd = (RequestData) ((Data<?>) rdEnvelope.getData()).getBaseData();
    RemoteDependencyData rdd =
        (RemoteDependencyData) ((Data<?>) rddEnvelope.getData()).getBaseData();

    final String expectedName = "DependencyTest";
    final String expectedData = "commandName";
    Duration expectedDuration = new Duration(0, 0, 1, 1, 1);

    assertEquals(expectedName, rdd.getName());
    assertEquals(expectedData, rdd.getData());
    assertEquals(expectedDuration, rdd.getDuration());
    assertTrue(rdd.getProperties().isEmpty());
    assertTrue(rdd.getSuccess());

    assertParentChild(rd, rdEnvelope, rddEnvelope, "GET /CoreAndFilter/trackDependency");
  }

  @Test
  @TargetUri("/trackEvent")
  public void testTrackEvent() throws Exception {
    List<Envelope> rdList = mockedIngestion.waitForItems("RequestData", 1);

    Envelope rdEnvelope = rdList.get(0);
    String operationId = rdEnvelope.getTags().get("ai.operation.id");
    List<Envelope> edList = mockedIngestion.waitForItemsInOperation("EventData", 2, operationId);

    Envelope edEnvelope1 = edList.get(0);
    Envelope edEnvelope2 = edList.get(1);

    RequestData rd = (RequestData) ((Data<?>) rdEnvelope.getData()).getBaseData();

    List<EventData> events = mockedIngestion.getTelemetryDataByTypeInRequest("EventData");
    events.sort(
        new Comparator<EventData>() {
          @Override
          public int compare(EventData o1, EventData o2) {
            return o1.getName().compareTo(o2.getName());
          }
        });

    EventData ed1 = events.get(0);
    EventData ed2 = events.get(1);

    assertEquals("EventDataPropertyTest", ed1.getName());
    assertEquals("value", ed1.getProperties().get("key"));
    assertEquals((Double) 1.0, ed1.getMeasurements().get("key"));

    assertEquals("EventDataTest", ed2.getName());

    assertParentChild(rd, rdEnvelope, edEnvelope1, "GET /CoreAndFilter/trackEvent");
    assertParentChild(rd, rdEnvelope, edEnvelope2, "GET /CoreAndFilter/trackEvent");
  }

  @Test
  @TargetUri("/trackException")
  public void testTrackException() throws Exception {
    List<Envelope> rdList = mockedIngestion.waitForItems("RequestData", 1);

    Envelope rdEnvelope = rdList.get(0);
    String operationId = rdEnvelope.getTags().get("ai.operation.id");
    List<Envelope> edList =
        mockedIngestion.waitForItemsInOperation("ExceptionData", 3, operationId);
    assertEquals(0, mockedIngestion.getCountForType("EventData"));

    Envelope edEnvelope1 = edList.get(0);
    Envelope edEnvelope2 = edList.get(1);
    Envelope edEnvelope3 = edList.get(2);

    RequestData rd = (RequestData) ((Data<?>) rdEnvelope.getData()).getBaseData();

    final String expectedName = "This is track exception.";
    final String expectedProperties = "value";
    final Double expectedMetrice = 1d;

    List<ExceptionData> exceptions =
        mockedIngestion.getTelemetryDataByTypeInRequest("ExceptionData");
    assertThat(exceptions, hasItem(hasException(withMessage(expectedName))));
    assertThat(
        exceptions,
        hasItem(
            allOf(
                hasException(withMessage(expectedName)),
                ExceptionDataMatchers.hasProperty("key", expectedProperties),
                hasMeasurement("key", expectedMetrice))));
    assertThat(
        exceptions,
        hasItem(
            allOf(hasException(withMessage(expectedName)), hasSeverityLevel(SeverityLevel.Error))));

    assertParentChild(rd, rdEnvelope, edEnvelope1, "GET /CoreAndFilter/trackException");
    assertParentChild(rd, rdEnvelope, edEnvelope2, "GET /CoreAndFilter/trackException");
    assertParentChild(rd, rdEnvelope, edEnvelope3, "GET /CoreAndFilter/trackException");
  }

  @Test
  @TargetUri("/trackHttpRequest")
  public void testHttpRequest() throws Exception {
    mockedIngestion.waitForItems("RequestData", 5);

    int totalItems = mockedIngestion.getItemCount();
    int expectedItems = 5;
    assertEquals(
        String.format("There were %d extra telemetry items received.", totalItems - expectedItems),
        expectedItems,
        totalItems);

    // TODO get HttpRequest data envelope and verify value
    List<Domain> requests = mockedIngestion.getTelemetryDataByType("RequestData");
    // true
    assertThat(
        requests,
        hasItem(
            allOf(
                hasName("HttpRequestDataTest"),
                hasResponseCode("200"),
                hasDuration(new Duration(4711)),
                hasSuccess(true))));
    assertThat(
        requests,
        hasItem(
            allOf(
                hasName("PingTest"),
                hasResponseCode("200"),
                hasDuration(new Duration(1)),
                hasSuccess(true),
                hasUrl("http://tempuri.org/ping"))));

    // false
    assertThat(
        requests,
        hasItem(
            allOf(
                hasName("FailedHttpRequest"),
                hasResponseCode("404"),
                hasDuration(new Duration(6666)),
                hasSuccess(false))));
    assertThat(
        requests,
        hasItem(
            allOf(
                hasName("FailedHttpRequest2"),
                hasResponseCode("505"),
                hasDuration(new Duration(8888)),
                hasSuccess(false),
                hasUrl("https://www.bingasdasdasdasda.com/"))));
  }

  @Test
  @TargetUri("/trackMetric")
  public void trackMetric() throws Exception {
    List<Envelope> rdList = mockedIngestion.waitForItems("RequestData", 1);
    List<Envelope> mdList = mockedIngestion.waitForItems("MetricData", 1);

    Envelope rdEnvelope = rdList.get(0);
    Envelope mdEnvelope = mdList.get(0);

    RequestData rd = (RequestData) ((Data<?>) rdEnvelope.getData()).getBaseData();
    MetricData md = (MetricData) ((Data<?>) mdEnvelope.getData()).getBaseData();

    List<DataPoint> metrics = md.getMetrics();
    assertEquals(1, metrics.size());
    DataPoint dp = metrics.get(0);

    final double expectedValue = 111222333.0;
    double epsilon = Math.ulp(expectedValue);
    assertEquals(DataPointType.Measurement, dp.getKind());
    assertEquals(expectedValue, dp.getValue(), epsilon);
    assertEquals("TimeToRespond", dp.getName());

    assertNull("getCount was non-null", dp.getCount());
    assertNull("getMin was non-null", dp.getMin());
    assertNull("getMax was non-null", dp.getMax());
    assertNull("getStdDev was non-null", dp.getStdDev());

    assertParentChild(rd, rdEnvelope, mdEnvelope, "GET /CoreAndFilter/trackMetric");
  }

  @Test
  @TargetUri("/trackTrace")
  public void testTrackTrace() throws Exception {
    List<Envelope> rdList = mockedIngestion.waitForItems("RequestData", 1);
    List<Envelope> mdList = mockedIngestion.waitForMessageItemsInRequest(3);

    Envelope rdEnvelope = rdList.get(0);
    Envelope mdEnvelope1 = mdList.get(0);
    Envelope mdEnvelope2 = mdList.get(1);
    Envelope mdEnvelope3 = mdList.get(2);

    RequestData rd = (RequestData) ((Data<?>) rdEnvelope.getData()).getBaseData();

    List<MessageData> messages = mockedIngestion.getMessageDataInRequest();
    assertThat(messages, hasItem(TraceDataMatchers.hasMessage("This is first trace message.")));

    assertThat(
        messages,
        hasItem(
            allOf(
                TraceDataMatchers.hasMessage("This is second trace message."),
                TraceDataMatchers.hasSeverityLevel(SeverityLevel.Error))));

    assertThat(
        messages,
        hasItem(
            allOf(
                TraceDataMatchers.hasMessage("This is third trace message."),
                TraceDataMatchers.hasSeverityLevel(SeverityLevel.Information),
                TraceDataMatchers.hasProperty("key", "value"))));

    assertParentChild(rd, rdEnvelope, mdEnvelope1, "GET /CoreAndFilter/trackTrace");
    assertParentChild(rd, rdEnvelope, mdEnvelope2, "GET /CoreAndFilter/trackTrace");
    assertParentChild(rd, rdEnvelope, mdEnvelope3, "GET /CoreAndFilter/trackTrace");
  }

  @Test
  @TargetUri("/trackPageView")
  public void testTrackPageView() throws Exception {
    List<Envelope> rdList = mockedIngestion.waitForItems("RequestData", 1);

    Envelope rdEnvelope = rdList.get(0);
    List<Envelope> pvdList = mockedIngestion.waitForItems("PageViewData", 3);
    assertEquals(0, mockedIngestion.getCountForType("EventData"));

    RequestData rd = (RequestData) ((Data<?>) rdEnvelope.getData()).getBaseData();

    Envelope pvdEnvelope1 = null;
    Envelope pvdEnvelope2 = null;
    Envelope pvdEnvelope3 = null;

    for (Envelope pvdEnvelope : pvdList) {
      PageViewData pv = (PageViewData) ((Data<?>) pvdEnvelope.getData()).getBaseData();
      if (pv.getName().equals("test-page")) {
        pvdEnvelope1 = pvdEnvelope;
      } else if (pv.getName().equals("test-page-2")) {
        pvdEnvelope2 = pvdEnvelope;
      } else if (pv.getName().equals("test-page-3")) {
        pvdEnvelope3 = pvdEnvelope;
      } else {
        throw new AssertionError("Unexpected page view: " + pv.getName());
      }
    }

    PageViewData pv1 = (PageViewData) ((Data<?>) pvdEnvelope1.getData()).getBaseData();
    PageViewData pv2 = (PageViewData) ((Data<?>) pvdEnvelope2.getData()).getBaseData();
    PageViewData pv3 = (PageViewData) ((Data<?>) pvdEnvelope3.getData()).getBaseData();

    assertNotNull(pv1);
    assertEquals(new Duration(0), pv1.getDuration());
    // checking that instrumentation key, cloud role name, cloud role instance, and sdk version are
    // from the agent
    assertEquals("00000000-0000-0000-0000-0FEEDDADBEEF", pvdEnvelope1.getIKey());
    assertEquals("testrolename", pvdEnvelope1.getTags().get("ai.cloud.role"));
    assertEquals("testroleinstance", pvdEnvelope1.getTags().get("ai.cloud.roleInstance"));
    assertTrue(pvdEnvelope1.getTags().get("ai.internal.sdkVersion").startsWith("java:3."));

    assertNotNull(pv2);
    assertEquals(new Duration(123456), pv2.getDuration());
    assertEquals("2010-10-10T00:00:00Z", pvdEnvelope2.getTime());
    assertEquals("value", pv2.getProperties().get("key"));
    assertEquals("a-value", pv2.getProperties().get("a-prop"));
    assertEquals("another-value", pv2.getProperties().get("another-prop"));
    // operation name is verified below in assertParentChild()
    assertEquals("user-id-goes-here", pvdEnvelope2.getTags().get("ai.user.id"));
    assertEquals("account-id-goes-here", pvdEnvelope2.getTags().get("ai.user.accountId"));
    assertEquals("user-agent-goes-here", pvdEnvelope2.getTags().get("ai.user.userAgent"));
    assertEquals("os-goes-here", pvdEnvelope2.getTags().get("ai.device.os"));
    assertEquals("session-id-goes-here", pvdEnvelope2.getTags().get("ai.session.id"));
    assertEquals("1.2.3.4", pvdEnvelope2.getTags().get("ai.location.ip"));
    // checking that instrumentation key, cloud role name and cloud role instance are overridden
    assertEquals("12341234-1234-1234-1234-123412341234", pvdEnvelope2.getIKey());
    assertEquals("role-goes-here", pvdEnvelope2.getTags().get("ai.cloud.role"));
    assertEquals("role-instance-goes-here", pvdEnvelope2.getTags().get("ai.cloud.roleInstance"));
    // checking that sdk version is from the agent
    assertTrue(pvdEnvelope2.getTags().get("ai.internal.sdkVersion").startsWith("java:3."));

    assertNotNull(pv3);
    assertEquals(new Duration(123456), pv3.getDuration());
    assertEquals("2010-10-10T00:00:00Z", pvdEnvelope3.getTime());
    assertEquals("value", pv3.getProperties().get("key"));
    assertEquals("a-value", pv3.getProperties().get("a-prop"));
    assertEquals("another-value", pv3.getProperties().get("another-prop"));
    // operation name is verified below in assertParentChild()
    assertEquals("user-id-goes-here", pvdEnvelope3.getTags().get("ai.user.id"));
    assertEquals("account-id-goes-here", pvdEnvelope3.getTags().get("ai.user.accountId"));
    assertEquals("user-agent-goes-here", pvdEnvelope3.getTags().get("ai.user.userAgent"));
    assertEquals("os-goes-here", pvdEnvelope3.getTags().get("ai.device.os"));
    assertEquals("session-id-goes-here", pvdEnvelope3.getTags().get("ai.session.id"));
    assertEquals("1.2.3.4", pvdEnvelope3.getTags().get("ai.location.ip"));
    // checking that instrumentation key, cloud role name and cloud role instance are from the agent
    assertEquals("00000000-0000-0000-0000-0FEEDDADBEEF", pvdEnvelope3.getIKey());
    assertEquals("testrolename", pvdEnvelope3.getTags().get("ai.cloud.role"));
    assertEquals("testroleinstance", pvdEnvelope3.getTags().get("ai.cloud.roleInstance"));
    // checking that sdk version is from the agent
    assertTrue(pvdEnvelope3.getTags().get("ai.internal.sdkVersion").startsWith("java:3."));

    assertParentChild(rd, rdEnvelope, pvdEnvelope1, "GET /CoreAndFilter/trackPageView");

    assertEquals("operation-id-goes-here", pvdEnvelope2.getTags().get("ai.operation.id"));
    assertEquals(
        "operation-parent-id-goes-here", pvdEnvelope2.getTags().get("ai.operation.parentId"));
    assertEquals("operation-name-goes-here", pvdEnvelope2.getTags().get("ai.operation.name"));

    assertEquals("operation-id-goes-here", pvdEnvelope3.getTags().get("ai.operation.id"));
    assertEquals(
        "operation-parent-id-goes-here", pvdEnvelope3.getTags().get("ai.operation.parentId"));
    assertEquals("operation-name-goes-here", pvdEnvelope3.getTags().get("ai.operation.name"));
  }

  @Test
  @TargetUri("/doPageView.jsp")
  public void testTrackPageViewJsp() throws Exception {
    List<Envelope> rdList = mockedIngestion.waitForItems("RequestData", 1);

    Envelope rdEnvelope = rdList.get(0);
    String operationId = rdEnvelope.getTags().get("ai.operation.id");
    List<Envelope> pvdList =
        mockedIngestion.waitForItemsInOperation("PageViewData", 1, operationId);
    assertEquals(0, mockedIngestion.getCountForType("EventData"));

    Envelope pvdEnvelope = pvdList.get(0);

    RequestData rd = (RequestData) ((Data<?>) rdEnvelope.getData()).getBaseData();

    PageViewData pv = (PageViewData) ((Data<?>) pvdEnvelope.getData()).getBaseData();
    assertEquals("doPageView", pv.getName());
    assertEquals(new Duration(0), pv.getDuration());

    assertParentChild(rd, rdEnvelope, pvdEnvelope, "GET /CoreAndFilter/doPageView.jsp");
  }

  @Test
  @TargetUri("/autoFailedRequestWithResultCode")
  public void testAutoFailedRequestWithResultCode() throws Exception {
    List<Envelope> rdList = mockedIngestion.waitForItems("RequestData", 1);

    Envelope rdEnvelope = rdList.get(0);

    RequestData rd = (RequestData) ((Data<?>) rdEnvelope.getData()).getBaseData();

    assertEquals(false, rd.getSuccess());
    assertEquals("404", rd.getResponseCode());

    assertEquals("GET /CoreAndFilter/*", rdEnvelope.getTags().get("ai.operation.name"));
  }

  @Test
  @TargetUri(
      value = "/requestSlow?sleeptime=25",
      timeout = 35_000) // the servlet sleeps for 25 seconds
  public void testRequestSlowWithResponseTime() throws Exception {
    validateSlowTest(25, "GET /CoreAndFilter/requestSlow");
  }

  @Test
  @TargetUri(
      value = "/slowLoop?responseTime=25",
      timeout = 35_000) // the servlet sleeps for 20 seconds
  public void testSlowRequestUsingCpuBoundLoop() throws Exception {
    validateSlowTest(25, "GET /CoreAndFilter/slowLoop");
  }

  @Test
  @TargetUri("/autoExceptionWithFailedRequest")
  public void testAutoExceptionWithFailedRequest() throws Exception {
    List<Envelope> rdList = mockedIngestion.waitForItems("RequestData", 1);

    Envelope rdEnvelope = rdList.get(0);
    String operationId = rdEnvelope.getTags().get("ai.operation.id");
    List<Envelope> edList =
        mockedIngestion.waitForItems(
            new Predicate<Envelope>() {
              @Override
              public boolean test(Envelope input) {
                if (!"ExceptionData".equals(input.getData().getBaseType())) {
                  return false;
                }
                if (!operationId.equals(input.getTags().get("ai.operation.id"))) {
                  return false;
                }
                // lastly, filter out ExceptionData captured from tomcat logger
                ExceptionData data = (ExceptionData) ((Data<?>) input.getData()).getBaseData();
                return !data.getProperties().containsKey("LoggerName");
              }
            },
            1,
            10,
            TimeUnit.SECONDS);

    Envelope edEnvelope = edList.get(0);

    RequestData rd = (RequestData) ((Data<?>) rdEnvelope.getData()).getBaseData();
    ExceptionData ed = (ExceptionData) ((Data<?>) edEnvelope.getData()).getBaseData();

    assertFalse(rd.getSuccess());

    ExceptionDetails details = getExceptionDetails(ed);
    assertEquals("This is a auto thrown exception !", details.getMessage());
  }

  @Test
  @TargetUri("/index.jsp")
  public void testRequestJsp() throws Exception {
    mockedIngestion.waitForItems("RequestData", 1);
  }

  private static ExceptionDetails getExceptionDetails(ExceptionData exceptionData) {
    List<ExceptionDetails> details = exceptionData.getExceptions();
    return details.get(0);
  }

  private void validateSlowTest(int expectedDurationSeconds, String operationName)
      throws Exception {
    List<Envelope> rdList = mockedIngestion.waitForItems("RequestData", 1);

    Envelope rdEnvelope = rdList.get(0);

    RequestData rd = (RequestData) ((Data<?>) rdEnvelope.getData()).getBaseData();

    long actual = rd.getDuration().getTotalMilliseconds();
    long expected = (new Duration(0, 0, 0, expectedDurationSeconds, 0).getTotalMilliseconds());
    long tolerance = 2 * 1000; // 2 seconds

    long min = expected - tolerance;
    long max = expected + tolerance;

    System.out.printf("Slow response time: expected=%d, actual=%d%n", expected, actual);
    assertThat(actual, both(greaterThanOrEqualTo(min)).and(lessThan(max)));

    assertEquals(operationName, rdEnvelope.getTags().get("ai.operation.name"));
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
