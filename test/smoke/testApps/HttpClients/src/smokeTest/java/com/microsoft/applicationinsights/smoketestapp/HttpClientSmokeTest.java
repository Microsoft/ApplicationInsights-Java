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

package com.microsoft.applicationinsights.smoketestapp;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.microsoft.applicationinsights.smoketest.AiSmokeTest;
import com.microsoft.applicationinsights.smoketest.TargetUri;
import com.microsoft.applicationinsights.smoketest.UseAgent;
import com.microsoft.applicationinsights.smoketest.schemav2.Data;
import com.microsoft.applicationinsights.smoketest.schemav2.Envelope;
import com.microsoft.applicationinsights.smoketest.schemav2.RemoteDependencyData;
import com.microsoft.applicationinsights.smoketest.schemav2.RequestData;
import java.util.List;
import org.junit.Ignore;
import org.junit.Test;

@UseAgent
public class HttpClientSmokeTest extends AiSmokeTest {

  @Test
  @TargetUri("/apacheHttpClient4")
  public void testApacheHttpClient4() throws Exception {
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

    assertTrue(rd.getProperties().isEmpty());
    assertTrue(rd.getSuccess());

    assertEquals("HTTP GET", rdd.getName());
    assertEquals("Http", rdd.getType());
    assertEquals("www.bing.com", rdd.getTarget());
    assertEquals("https://www.bing.com/search?q=spaces%20test", rdd.getData());
    assertTrue(rdd.getProperties().isEmpty());
    assertTrue(rdd.getSuccess());

    assertParentChild(rd, rdEnvelope, rddEnvelope, "GET /HttpClients/*");
  }

  @Test
  @TargetUri("/apacheHttpClient4WithResponseHandler")
  public void testApacheHttpClient4WithResponseHandler() throws Exception {
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

    assertTrue(rd.getProperties().isEmpty());
    assertTrue(rd.getSuccess());

    assertEquals("HTTP GET", rdd.getName());
    assertEquals("Http", rdd.getType());
    assertEquals("www.bing.com", rdd.getTarget());
    assertEquals("https://www.bing.com/search?q=spaces%20test", rdd.getData());
    assertTrue(rdd.getProperties().isEmpty());
    assertTrue(rdd.getSuccess());

    assertParentChild(rd, rdEnvelope, rddEnvelope, "GET /HttpClients/*");
  }

  @Test
  @TargetUri("/apacheHttpClient3")
  public void testApacheHttpClient3() throws Exception {
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

    assertTrue(rd.getProperties().isEmpty());
    assertTrue(rd.getSuccess());

    assertEquals("HTTP GET", rdd.getName());
    assertEquals("Http", rdd.getType());
    assertEquals("www.bing.com", rdd.getTarget());
    assertEquals("https://www.bing.com/search?q=spaces%20test", rdd.getData());
    assertTrue(rdd.getProperties().isEmpty());
    assertTrue(rdd.getSuccess());

    assertParentChild(rd, rdEnvelope, rddEnvelope, "GET /HttpClients/*");
  }

  @Test
  @TargetUri("/apacheHttpAsyncClient")
  public void testApacheHttpAsyncClient() throws Exception {
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

    assertTrue(rd.getProperties().isEmpty());
    assertTrue(rd.getSuccess());

    assertEquals("HTTP GET", rdd.getName());
    assertEquals("Http", rdd.getType());
    assertEquals("www.bing.com", rdd.getTarget());
    assertEquals("https://www.bing.com/search?q=spaces%20test", rdd.getData());
    assertTrue(rdd.getProperties().isEmpty());
    assertTrue(rdd.getSuccess());

    assertParentChild(rd, rdEnvelope, rddEnvelope, "GET /HttpClients/*");
  }

  @Test
  @TargetUri("/okHttp3")
  public void testOkHttp3() throws Exception {
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

    assertTrue(rd.getProperties().isEmpty());
    assertTrue(rd.getSuccess());

    assertEquals("HTTP GET", rdd.getName());
    assertEquals("Http", rdd.getType());
    assertEquals("www.bing.com", rdd.getTarget());
    assertEquals("https://www.bing.com/search?q=spaces%20test", rdd.getData());
    assertTrue(rdd.getProperties().isEmpty());
    assertTrue(rdd.getSuccess());

    assertParentChild(rd, rdEnvelope, rddEnvelope, "GET /HttpClients/*");
  }

  @Ignore // OpenTelemetry Auto-Instrumentation does not support OkHttp 2
  @Test
  @TargetUri("/okHttp2")
  public void testOkHttp2() throws Exception {
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

    assertTrue(rd.getProperties().isEmpty());
    assertTrue(rd.getSuccess());

    assertEquals("HTTP GET", rdd.getName());
    assertEquals("Http", rdd.getType());
    assertEquals("www.bing.com", rdd.getTarget());
    assertEquals("https://www.bing.com/search?q=spaces%20test", rdd.getData());
    assertTrue(rdd.getProperties().isEmpty());
    assertTrue(rdd.getSuccess());

    assertParentChild(rd, rdEnvelope, rddEnvelope, "GET /HttpClients/*");
  }

  @Test
  @TargetUri("/httpUrlConnection")
  public void testHttpUrlConnection() throws Exception {
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

    assertTrue(rd.getProperties().isEmpty());
    assertTrue(rd.getSuccess());

    assertEquals("HTTP GET", rdd.getName());
    assertEquals("Http", rdd.getType());
    assertEquals("www.bing.com", rdd.getTarget());
    assertEquals("https://www.bing.com/search?q=spaces%20test", rdd.getData());
    assertTrue(rdd.getProperties().isEmpty());
    assertTrue(rdd.getSuccess());

    assertParentChild(rd, rdEnvelope, rddEnvelope, "GET /HttpClients/*");
  }

  @Test
  @TargetUri("/springWebClient")
  public void testSpringWebClient() throws Exception {
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

    assertTrue(rd.getProperties().isEmpty());
    assertTrue(rd.getSuccess());

    assertEquals("HTTP GET", rdd.getName());
    assertEquals("Http", rdd.getType());
    assertEquals("www.bing.com", rdd.getTarget());
    // TODO investigate why %2520 is captured instead of %20
    assertEquals("https://www.bing.com/search?q=spaces%2520test", rdd.getData());
    assertTrue(rdd.getProperties().isEmpty());
    assertTrue(rdd.getSuccess());

    assertParentChild(rd, rdEnvelope, rddEnvelope, "GET /HttpClients/*");
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
