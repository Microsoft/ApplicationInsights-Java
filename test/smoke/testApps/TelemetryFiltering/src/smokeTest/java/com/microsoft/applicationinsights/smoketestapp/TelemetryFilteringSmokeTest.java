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

import static java.util.concurrent.TimeUnit.NANOSECONDS;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.lessThanOrEqualTo;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;

import com.microsoft.applicationinsights.smoketest.AiSmokeTest;
import com.microsoft.applicationinsights.smoketest.TargetUri;
import com.microsoft.applicationinsights.smoketest.UseAgent;
import com.microsoft.applicationinsights.smoketest.schemav2.Data;
import com.microsoft.applicationinsights.smoketest.schemav2.Envelope;
import com.microsoft.applicationinsights.smoketest.schemav2.RemoteDependencyData;
import com.microsoft.applicationinsights.smoketest.schemav2.RequestData;
import java.util.List;
import org.junit.Test;

@UseAgent("telemetryfiltering")
public class TelemetryFilteringSmokeTest extends AiSmokeTest {

  @Test
  @TargetUri(value = "/health-check", callCount = 100)
  public void testSampling() throws Exception {
    // super super low chance that number of sampled requests is less than 25
    long start = System.nanoTime();
    while (mockedIngestion.getCountForType("RequestData") < 25
        && NANOSECONDS.toSeconds(System.nanoTime() - start) < 10) {}
    // wait ten more seconds to before checking that we didn't receive too many
    Thread.sleep(SECONDS.toMillis(10));
    int requestCount = mockedIngestion.getCountForType("RequestData");
    int dependencyCount = mockedIngestion.getCountForType("RemoteDependencyData");
    // super super low chance that number of sampled requests/dependencies
    // is less than 25 or greater than 75
    assertThat(requestCount, greaterThanOrEqualTo(25));
    assertThat(dependencyCount, greaterThanOrEqualTo(25));
    assertThat(requestCount, lessThanOrEqualTo(75));
    assertThat(dependencyCount, lessThanOrEqualTo(75));
  }

  @Test
  @TargetUri("/noisy-jdbc")
  public void testNoisyJdbc() throws Exception {
    List<Envelope> rdList = mockedIngestion.waitForItems("RequestData", 1);
    Thread.sleep(10000);
    assertEquals(0, mockedIngestion.getCountForType("RemoteDependencyData"));

    Envelope rdEnvelope = rdList.get(0);

    RequestData rd = (RequestData) ((Data<?>) rdEnvelope.getData()).getBaseData();

    assertTrue(rd.getSuccess());
  }

  @Test
  @TargetUri("/regular-jdbc")
  public void testRegularJdbc() throws Exception {
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

    assertTrue(rd.getSuccess());
    assertEquals("SQL", rdd.getType());
    assertEquals("testdb", rdd.getTarget());
    assertEquals("select * from abc", rdd.getName());
    assertEquals("select * from abc", rdd.getData());
    assertTrue(rdd.getSuccess());

    assertParentChild(rd, rdEnvelope, rddEnvelope, "GET /TelemetryFiltering/*");
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
