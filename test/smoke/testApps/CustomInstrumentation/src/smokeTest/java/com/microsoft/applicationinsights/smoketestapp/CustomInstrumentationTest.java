package com.microsoft.applicationinsights.smoketestapp;

import java.util.List;

import com.microsoft.applicationinsights.smoketest.schemav2.Data;
import com.microsoft.applicationinsights.smoketest.schemav2.Envelope;
import com.microsoft.applicationinsights.smoketest.schemav2.ExceptionData;
import com.microsoft.applicationinsights.smoketest.schemav2.ExceptionDetails;
import com.microsoft.applicationinsights.smoketest.schemav2.RemoteDependencyData;
import com.microsoft.applicationinsights.smoketest.schemav2.RequestData;
import com.microsoft.applicationinsights.smoketest.AiSmokeTest;
import com.microsoft.applicationinsights.smoketest.TargetUri;
import com.microsoft.applicationinsights.smoketest.UseAgent;
import org.junit.Test;

import static org.junit.Assert.*;

@UseAgent("custominstrumentation")
public class CustomInstrumentationTest extends AiSmokeTest {

  @Test
  @TargetUri("/customInstrumentationOne")
  public void customInstrumentationOne() throws Exception {
    List<Envelope> rdList = mockedIngestion.waitForItems("RequestData", 1);
    List<Envelope> rddList = mockedIngestion.waitForItemsInRequest("RemoteDependencyData", 1);

    Envelope rdEnvelope = rdList.get(0);
    Envelope rddEnvelope = rddList.get(0);

    RequestData rd = (RequestData) ((Data<?>) rdEnvelope.getData()).getBaseData();
    RemoteDependencyData rdd =
        (RemoteDependencyData) ((Data<?>) rddEnvelope.getData()).getBaseData();

    assertTrue(rd.getSuccess());
    assertEquals(rdd.getName(), "com/microsoft/applicationinsights/smoketestapp/TargetObject.one");
    assertEquals(rdd.getType(), "OTHER");
    assertEquals(rdd.getSuccess(), true);

    assertParentChild(rd, rdEnvelope, rddEnvelope, "GET /CustomInstrumentation/*");
  }

  @Test
  @TargetUri("/customInstrumentationTwo")
  public void customInstrumentationTwo() throws Exception {
    List<Envelope> rdList = mockedIngestion.waitForItems("RequestData", 1);
    List<Envelope> rddList = mockedIngestion.waitForItemsInRequest("RemoteDependencyData", 1);

    Envelope rdEnvelope = rdList.get(0);
    Envelope rddEnvelope = rddList.get(0);

    RequestData rd = (RequestData) ((Data<?>) rdEnvelope.getData()).getBaseData();
    RemoteDependencyData rdd =
        (RemoteDependencyData) ((Data<?>) rddEnvelope.getData()).getBaseData();

    assertTrue(rd.getSuccess());
    assertEquals(rdd.getName(), "com/microsoft/applicationinsights/smoketestapp/TargetObject.two");
    assertEquals(rdd.getType(), "OTHER");
    assertEquals(rdd.getSuccess(), true);

    assertParentChild(rd, rdEnvelope, rddEnvelope, "GET /CustomInstrumentation/*");
  }

  @Test
  @TargetUri("/customInstrumentationThree")
  public void customInstrumentationThree() throws Exception {
    List<Envelope> rdList = mockedIngestion.waitForItems("RequestData", 1);
    List<Envelope> rddList = mockedIngestion.waitForItemsInRequest("RemoteDependencyData", 1);
    List<Envelope> edList = mockedIngestion.waitForItemsInRequest("ExceptionData", 1);

    Envelope rdEnvelope = rdList.get(0);
    Envelope rddEnvelope = rddList.get(0);
    Envelope edEnvelope = edList.get(0);

    RequestData rd = (RequestData) ((Data<?>) rdEnvelope.getData()).getBaseData();
    RemoteDependencyData rdd =
        (RemoteDependencyData) ((Data<?>) rddEnvelope.getData()).getBaseData();
    ExceptionData ed = (ExceptionData) ((Data<?>) edEnvelope.getData()).getBaseData();

    assertTrue(rd.getSuccess());
    assertEquals(
        rdd.getName(), "com/microsoft/applicationinsights/smoketestapp/TargetObject.three");
    assertEquals(rdd.getType(), "OTHER");
    assertEquals(rdd.getSuccess(), false);

    List<ExceptionDetails> exceptions = ed.getExceptions();
    assertEquals(exceptions.size(), 1);
    assertEquals(exceptions.get(0).getMessage(), "Three");

    assertParentChild(rd, rdEnvelope, rddEnvelope, "GET /CustomInstrumentation/*");
    assertParentChild(rd, rdEnvelope, edEnvelope, "GET /CustomInstrumentation/*");
  }

  @Test
  @TargetUri("/customInstrumentationFour")
  public void customInstrumentationFour() throws Exception {
    List<Envelope> rdList = mockedIngestion.waitForItems("RequestData", 1);
    List<Envelope> rddList = mockedIngestion.waitForItemsInRequest("RemoteDependencyData", 1);

    Envelope rdEnvelope = rdList.get(0);
    Envelope rddEnvelope = rddList.get(0);

    RequestData rd = (RequestData) ((Data<?>) rdEnvelope.getData()).getBaseData();
    RemoteDependencyData rdd =
        (RemoteDependencyData) ((Data<?>) rddEnvelope.getData()).getBaseData();

    assertTrue(rd.getSuccess());
    assertEquals(
        rdd.getName(),
        "com/microsoft/applicationinsights/smoketestapp/TargetObject$NestedObject.four");
    assertEquals(rdd.getType(), "OTHER");
    assertEquals(rdd.getSuccess(), true);

    assertParentChild(rd, rdEnvelope, rddEnvelope, "GET /CustomInstrumentation/*");
  }

  @Test
  @TargetUri("/customInstrumentationFive")
  public void customInstrumentationFive() throws Exception {
    List<Envelope> rdList = mockedIngestion.waitForItems("RequestData", 1);
    List<Envelope> rddList = mockedIngestion.waitForItemsInRequest("RemoteDependencyData", 4);

    Envelope rdEnvelope = rdList.get(0);
    RequestData rd = (RequestData) ((Data<?>) rdEnvelope.getData()).getBaseData();

    Envelope fiveEnvelope = null;
    Envelope sixEnvelope = null;
    Envelope oneEnvelope = null;
    Envelope twoEnvelope = null;
    RemoteDependencyData fiveRdd = null;
    RemoteDependencyData sixRdd = null;
    RemoteDependencyData oneRdd = null;
    RemoteDependencyData twoRdd = null;
    for (Envelope loopEnvelope : rddList) {
      RemoteDependencyData loopData =
          (RemoteDependencyData) ((Data<?>) loopEnvelope.getData()).getBaseData();
      if (loopData.getName().endsWith(".five")) {
        fiveEnvelope = loopEnvelope;
        fiveRdd = loopData;
      } else if (loopData.getName().endsWith(".six")) {
        sixEnvelope = loopEnvelope;
        sixRdd = loopData;
      } else if (loopData.getName().endsWith(".one")) {
        oneEnvelope = loopEnvelope;
        oneRdd = loopData;
      } else if (loopData.getName().endsWith(".two")) {
        twoEnvelope = loopEnvelope;
        twoRdd = loopData;
      } else {
        throw new IllegalStateException("Unexpected remote dependency: " + loopData.getName());
      }
    }

    assertTrue(rd.getSuccess());

    assertNotNull(fiveRdd);
    assertEquals(
        fiveRdd.getName(), "com/microsoft/applicationinsights/smoketestapp/TargetObject.five");
    assertEquals(fiveRdd.getType(), "OTHER");
    assertEquals(fiveRdd.getSuccess(), true);
    assertParentChild(rd, rdEnvelope, fiveEnvelope, "GET /CustomInstrumentation/*");

    assertNotNull(sixRdd);
    assertEquals(
        sixRdd.getName(), "com/microsoft/applicationinsights/smoketestapp/TargetObject.six");
    assertEquals(sixRdd.getType(), "OTHER");
    assertEquals(sixRdd.getSuccess(), true);
    assertParentChild(rd, rdEnvelope, sixEnvelope, "GET /CustomInstrumentation/*");

    assertNotNull(oneRdd);
    assertEquals(
        oneRdd.getName(), "com/microsoft/applicationinsights/smoketestapp/TargetObject.one");
    assertEquals(oneRdd.getType(), "OTHER");
    assertEquals(oneRdd.getSuccess(), true);
    assertParentChild(rd, rdEnvelope, oneEnvelope, "GET /CustomInstrumentation/*");

    assertNotNull(twoRdd);
    assertEquals(
        twoRdd.getName(), "com/microsoft/applicationinsights/smoketestapp/TargetObject.two");
    assertEquals(twoRdd.getType(), "OTHER");
    assertEquals(twoRdd.getSuccess(), true);
    assertParentChild(rd, rdEnvelope, twoEnvelope, "GET /CustomInstrumentation/*");
  }

  @Test
  @TargetUri("/customInstrumentationSeven")
  public void customInstrumentationSeven() throws Exception {
    List<Envelope> rdList = mockedIngestion.waitForItems("RequestData", 1);
    List<Envelope> rddList = mockedIngestion.waitForItemsInRequest("RemoteDependencyData", 1);

    Envelope rdEnvelope = rdList.get(0);
    Envelope rddEnvelope = rddList.get(0);

    RequestData rd = (RequestData) ((Data<?>) rdEnvelope.getData()).getBaseData();
    RemoteDependencyData rdd =
        (RemoteDependencyData) ((Data<?>) rddEnvelope.getData()).getBaseData();

    assertTrue(rd.getSuccess());
    assertEquals(
        rdd.getName(), "com/microsoft/applicationinsights/smoketestapp/TargetObject.seven");
    assertEquals(rdd.getType(), "OTHER");
    assertEquals(rdd.getSuccess(), true);
    assertParentChild(rd, rdEnvelope, rddEnvelope, "GET /CustomInstrumentation/*");
  }

  @Test
  @TargetUri("/customInstrumentationEight")
  public void customInstrumentationEight() throws Exception {
    List<Envelope> rdList = mockedIngestion.waitForItems("RequestData", 1);
    List<Envelope> rddList = mockedIngestion.waitForItemsInRequest("RemoteDependencyData", 2);

    Envelope rdEnvelope = rdList.get(0);
    Envelope rddEnvelope1 = rddList.get(0);
    Envelope rddEnvelope2 = rddList.get(1);

    RequestData rd = (RequestData) ((Data<?>) rdEnvelope.getData()).getBaseData();
    RemoteDependencyData rdd1 =
        (RemoteDependencyData) ((Data<?>) rddEnvelope1.getData()).getBaseData();
    RemoteDependencyData rdd2 =
        (RemoteDependencyData) ((Data<?>) rddEnvelope2.getData()).getBaseData();

    assertTrue(rd.getSuccess());

    assertEquals(
        rdd1.getName(), "com/microsoft/applicationinsights/smoketestapp/TargetObject.eight");
    assertEquals(rdd1.getType(), "OTHER");
    assertEquals(rdd1.getSuccess(), true);
    assertParentChild(rd, rdEnvelope, rddEnvelope1, "GET /CustomInstrumentation/*");

    assertEquals(
        rdd2.getName(), "com/microsoft/applicationinsights/smoketestapp/TargetObject.eight");
    assertEquals(rdd2.getType(), "OTHER");
    assertEquals(rdd2.getSuccess(), true);
    assertParentChild(rd, rdEnvelope, rddEnvelope2, "GET /CustomInstrumentation/*");
  }

  @Test
  @TargetUri("/customInstrumentationNine")
  public void customInstrumentationNine() throws Exception {
    List<Envelope> rdList = mockedIngestion.waitForItems("RequestData", 1);
    List<Envelope> rddList = mockedIngestion.waitForItemsInRequest("RemoteDependencyData", 2);

    Envelope rdEnvelope = rdList.get(0);
    RequestData rd = (RequestData) ((Data<?>) rdEnvelope.getData()).getBaseData();

    Envelope nineEnvelope = null;
    Envelope httpEnvelope = null;
    RemoteDependencyData nineRdd = null;
    RemoteDependencyData httpRdd = null;
    for (Envelope loopEnvelope : rddList) {
      RemoteDependencyData loopData =
          (RemoteDependencyData) ((Data<?>) loopEnvelope.getData()).getBaseData();
      if (loopData.getType().equals("OTHER")) {
        nineEnvelope = loopEnvelope;
        nineRdd = loopData;
      } else if (loopData.getType().equals("Http (tracked component)")) {
        httpEnvelope = loopEnvelope;
        httpRdd = loopData;
      } else {
        throw new IllegalStateException("Unexpected remote dependency type: " + loopData.getType());
      }
    }

    assertTrue(rd.getSuccess());

    assertNotNull(nineRdd);
    assertEquals(
        nineRdd.getName(), "com/microsoft/applicationinsights/smoketestapp/TargetObject.nine");
    assertEquals(nineRdd.getType(), "OTHER");
    assertEquals(nineRdd.getSuccess(), true);
    assertParentChild(rd, rdEnvelope, nineEnvelope, "GET /CustomInstrumentation/*");

    assertNotNull(httpRdd);
    assertParentChild(rd, rdEnvelope, httpEnvelope, "GET /CustomInstrumentation/*");
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
