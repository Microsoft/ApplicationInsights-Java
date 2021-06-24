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
import static org.junit.Assert.assertTrue;

import com.microsoft.applicationinsights.internal.schemav2.Data;
import com.microsoft.applicationinsights.internal.schemav2.Envelope;
import com.microsoft.applicationinsights.internal.schemav2.RemoteDependencyData;
import com.microsoft.applicationinsights.internal.schemav2.RequestData;
import java.util.List;
import org.junit.Test;

@UseAgent("opentelemetryapisupport")
public class OpenTelemetryApiSupportTest extends AiSmokeTest {

  @Test
  @TargetUri("/test-api")
  public void testApi() throws Exception {
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

    assertEquals("GET /OpenTelemetryApiSupport/test-api", rd.getName());
    assertTrue(rd.getProperties().isEmpty());
    assertTrue(rd.getSuccess());

    assertEquals("myspanname", rdd.getName());

    // ideally want the properties below on rd, but can't get SERVER span yet
    // see
    // https://github.com/open-telemetry/opentelemetry-java-instrumentation/issues/1726#issuecomment-731890267

    // checking that instrumentation key, cloud role name, cloud role instance, and sdk version are
    // from the agent
    assertEquals("00000000-0000-0000-0000-0FEEDDADBEEF", rddEnvelope.getIKey());
    assertEquals("testrolename", rddEnvelope.getTags().get("ai.cloud.role"));
    assertEquals("testroleinstance", rddEnvelope.getTags().get("ai.cloud.roleInstance"));
    assertTrue(rddEnvelope.getTags().get("ai.internal.sdkVersion").startsWith("java:3."));
    assertEquals("myuser", rddEnvelope.getTags().get("ai.user.id"));
    assertEquals("myvalue1", rdd.getProperties().get("myattr1"));
    assertEquals("myvalue2", rdd.getProperties().get("myattr2"));
    assertEquals(2, rdd.getProperties().size());
    assertTrue(rdd.getSuccess());

    assertParentChild(rd.getId(), rdEnvelope, rddEnvelope);
  }

  @Test
  @TargetUri("/test-overriding-ikey-etc")
  public void testOverridingIkeyEtc() throws Exception {
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

    assertEquals("GET /OpenTelemetryApiSupport/test-overriding-ikey-etc", rd.getName());
    assertTrue(rd.getProperties().isEmpty());
    assertTrue(rd.getSuccess());

    // ideally want the properties below on rd, but can't get SERVER span yet
    // see
    // https://github.com/open-telemetry/opentelemetry-java-instrumentation/issues/1726#issuecomment-731890267

    // checking that instrumentation key, cloud role name, cloud role instance, and sdk version are
    // from the agent
    assertEquals("12341234-1234-1234-1234-123412341234", rddEnvelope.getIKey());
    assertEquals("role-name-here", rddEnvelope.getTags().get("ai.cloud.role"));
    assertEquals("role-instance-here", rddEnvelope.getTags().get("ai.cloud.roleInstance"));
    assertEquals("application-version-here", rddEnvelope.getTags().get("ai.application.ver"));
    assertTrue(rddEnvelope.getTags().get("ai.internal.sdkVersion").startsWith("java:3."));
    assertTrue(rdd.getProperties().isEmpty());
    assertTrue(rdd.getSuccess());

    assertParentChild(rd.getId(), rdEnvelope, rddEnvelope);
  }

  @Test
  @TargetUri("/test-annotations")
  public void testAnnotations() throws Exception {
    List<Envelope> rdList = mockedIngestion.waitForItems("RequestData", 1);

    Envelope rdEnvelope = rdList.get(0);
    String operationId = rdEnvelope.getTags().get("ai.operation.id");
    List<Envelope> rddList =
        mockedIngestion.waitForItemsInOperation("RemoteDependencyData", 2, operationId);
    assertEquals(0, mockedIngestion.getCountForType("EventData"));

    Envelope rddEnvelope1 = rddList.get(0);
    Envelope rddEnvelope2 = rddList.get(1);

    RequestData rd = (RequestData) ((Data<?>) rdEnvelope.getData()).getBaseData();
    RemoteDependencyData rdd1 =
        (RemoteDependencyData) ((Data<?>) rddEnvelope1.getData()).getBaseData();
    RemoteDependencyData rdd2 =
        (RemoteDependencyData) ((Data<?>) rddEnvelope2.getData()).getBaseData();

    if (!rdd1.getName().equals("TestController.testAnnotations")) {
      RemoteDependencyData rddTemp = rdd1;
      rdd1 = rdd2;
      rdd2 = rddTemp;

      Envelope rddEnvelopeTemp = rddEnvelope1;
      rddEnvelope1 = rddEnvelope2;
      rddEnvelope2 = rddEnvelopeTemp;
    }

    assertEquals("GET /OpenTelemetryApiSupport/test-annotations", rd.getName());
    assertTrue(rd.getProperties().isEmpty());
    assertTrue(rd.getSuccess());

    assertEquals("TestController.testAnnotations", rdd1.getName());
    assertTrue(rdd1.getProperties().isEmpty());
    assertTrue(rdd1.getSuccess());

    assertEquals("TestController.underAnnotation", rdd2.getName());
    assertTrue(rdd2.getProperties().isEmpty());
    assertTrue(rdd2.getSuccess());

    assertParentChild(rd.getId(), rdEnvelope, rddEnvelope1);
    assertParentChild(rdd1.getId(), rddEnvelope1, rddEnvelope2);
  }

  private static void assertParentChild(
      String parentId, Envelope parentEnvelope, Envelope childEnvelope) {
    String operationId = parentEnvelope.getTags().get("ai.operation.id");

    assertNotNull(operationId);

    assertEquals(operationId, childEnvelope.getTags().get("ai.operation.id"));
    assertEquals(parentId, childEnvelope.getTags().get("ai.operation.parentId"));
  }
}
