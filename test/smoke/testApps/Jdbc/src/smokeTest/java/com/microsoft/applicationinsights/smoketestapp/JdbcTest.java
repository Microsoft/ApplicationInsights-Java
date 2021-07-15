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
import com.microsoft.applicationinsights.smoketest.DependencyContainer;
import com.microsoft.applicationinsights.smoketest.TargetUri;
import com.microsoft.applicationinsights.smoketest.UseAgent;
import com.microsoft.applicationinsights.smoketest.WithDependencyContainers;
import com.microsoft.applicationinsights.smoketest.schemav2.Data;
import com.microsoft.applicationinsights.smoketest.schemav2.Envelope;
import com.microsoft.applicationinsights.smoketest.schemav2.RemoteDependencyData;
import com.microsoft.applicationinsights.smoketest.schemav2.RequestData;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;
import org.junit.Ignore;
import org.junit.Test;

@UseAgent
@WithDependencyContainers({
  @DependencyContainer(
      value = "mysql:5",
      environmentVariables = {"MYSQL_ROOT_PASSWORD=password"},
      portMapping = "3306",
      hostnameEnvironmentVariable = "MYSQL"),
  @DependencyContainer(
      value = "postgres:11",
      portMapping = "5432",
      environmentVariables = {"POSTGRES_PASSWORD=passw0rd2"},
      hostnameEnvironmentVariable = "POSTGRES"),
  @DependencyContainer(
      value = "mcr.microsoft.com/mssql/server:2017-latest",
      environmentVariables = {"ACCEPT_EULA=Y", "SA_PASSWORD=Password1"},
      portMapping = "1433",
      hostnameEnvironmentVariable = "SQLSERVER")
})
public class JdbcTest extends AiSmokeTest {

  @Test
  @TargetUri("/hsqldbPreparedStatement")
  public void hsqldbPreparedStatement() throws Exception {
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

    assertEquals("select * from abc where xyz = ?", rdd.getName());
    assertEquals("SQL", rdd.getType());
    assertEquals("testdb", rdd.getTarget());
    assertEquals("select * from abc where xyz = ?", rdd.getData());
    assertTrue(rdd.getProperties().isEmpty());
    assertTrue(rdd.getSuccess());

    assertParentChild(rd, rdEnvelope, rddEnvelope, "GET /Jdbc/*");
  }

  @Test
  @TargetUri("/hsqldbStatement")
  public void hsqldbStatement() throws Exception {
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

    assertEquals("select * from abc", rdd.getName());
    assertEquals("SQL", rdd.getType());
    assertEquals("testdb", rdd.getTarget());
    assertEquals("select * from abc", rdd.getData());
    assertTrue(rdd.getProperties().isEmpty());
    assertTrue(rdd.getSuccess());

    assertParentChild(rd, rdEnvelope, rddEnvelope, "GET /Jdbc/*");
  }

  @Test
  @TargetUri("/hsqldbLargeStatement")
  public void hsqldbLargeStatement() throws Exception {
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

    StringBuilder a2000 = new StringBuilder();
    for (int i = 0; i < 2000; i++) {
      a2000.append("a");
    }
    String largeStr = " /*" + a2000 + "*/";
    String query = "select * from abc" + largeStr;
    String truncatedQuery = query.substring(0, Math.min(query.length(), 1024));

    assertTrue(rd.getProperties().isEmpty());
    assertTrue(rd.getSuccess());

    assertEquals(truncatedQuery, rdd.getName());
    assertEquals("SQL", rdd.getType());
    assertEquals("testdb", rdd.getTarget());
    assertEquals(query, rdd.getData());
    assertTrue(rdd.getProperties().isEmpty());
    assertTrue(rdd.getSuccess());

    assertParentChild(rd, rdEnvelope, rddEnvelope, "GET /Jdbc/*");
  }

  @Test
  @TargetUri("/hsqldbBatchPreparedStatement")
  public void hsqldbBatchPreparedStatement() throws Exception {
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

    assertEquals("insert into abc (xyz) values (?)", rdd.getName());
    assertEquals("SQL", rdd.getType());
    assertEquals("testdb", rdd.getTarget());
    assertEquals("insert into abc (xyz) values (?)", rdd.getData());
    // assertEquals(" [Batch of 3]", rdd.getProperties().get("Args"));
    assertTrue(rdd.getProperties().isEmpty());
    assertTrue(rdd.getSuccess());

    assertParentChild(rd, rdEnvelope, rddEnvelope, "GET /Jdbc/*");
  }

  @Ignore // OpenTelemetry auto-instrumentation does not support non- prepared statement batching
  // yet
  @Test
  @TargetUri("/hsqldbBatchStatement")
  public void hsqldbBatchStatement() throws Exception {
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

    assertEquals(
        "insert into abc (xyz) values ('t'); insert into abc (xyz) values ('u');"
            + " insert into abc (xyz) values ('v')",
        rdd.getName());
    assertEquals("SQL", rdd.getType());
    assertEquals("testdb", rdd.getTarget());
    assertEquals(
        "insert into abc (xyz) values ('t'); insert into abc (xyz) values ('u');"
            + " insert into abc (xyz) values ('v')",
        rdd.getData());
    assertEquals(" [Batch]", rdd.getProperties().get("Args"));
    assertEquals(1, rdd.getProperties().size());
    assertTrue(rdd.getSuccess());

    assertParentChild(rd, rdEnvelope, rddEnvelope, "GET /Jdbc/*");
  }

  @Test
  @TargetUri("/mysqlPreparedStatement")
  public void mysqlPreparedStatement() throws Exception {
    List<Envelope> rdList = mockedIngestion.waitForItems("RequestData", 1);

    Envelope rdEnvelope = rdList.get(0);
    RequestData rd = (RequestData) ((Data<?>) rdEnvelope.getData()).getBaseData();

    Envelope rddEnvelope =
        mockedIngestion.waitForItem(
            new Predicate<Envelope>() {
              @Override
              public boolean test(Envelope input) {
                if (!input.getData().getBaseType().equals("RemoteDependencyData")) {
                  return false;
                }
                RemoteDependencyData rdd =
                    (RemoteDependencyData) ((Data<?>) input.getData()).getBaseData();
                // the old agent captured several internal queries, e.g. "SHOW WARNINGS"
                return rdd.getData().equals("select * from abc where xyz = ?");
              }
            },
            10,
            TimeUnit.SECONDS);

    RemoteDependencyData rdd =
        (RemoteDependencyData) ((Data<?>) rddEnvelope.getData()).getBaseData();

    assertTrue(rd.getProperties().isEmpty());
    assertTrue(rd.getSuccess());

    assertTrue(rdd.getName().startsWith("select * from abc where xyz = ?"));
    assertEquals("SQL", rdd.getType());
    // not the best test, because this is both the db.name and db.system
    assertTrue(rdd.getTarget().matches("dependency[0-9]+/mysql"));
    assertEquals("select * from abc where xyz = ?", rdd.getData());
    assertTrue(rdd.getProperties().isEmpty());
    assertTrue(rdd.getSuccess());

    assertParentChild(rd, rdEnvelope, rddEnvelope, "GET /Jdbc/*");
  }

  @Test
  @TargetUri("/mysqlStatement")
  public void mysqlStatement() throws Exception {
    List<Envelope> rdList = mockedIngestion.waitForItems("RequestData", 1);

    Envelope rdEnvelope = rdList.get(0);
    RequestData rd = (RequestData) ((Data<?>) rdEnvelope.getData()).getBaseData();

    Envelope rddEnvelope =
        mockedIngestion.waitForItem(
            new Predicate<Envelope>() {
              @Override
              public boolean test(Envelope input) {
                if (!input.getData().getBaseType().equals("RemoteDependencyData")) {
                  return false;
                }
                RemoteDependencyData rdd =
                    (RemoteDependencyData) ((Data<?>) input.getData()).getBaseData();
                // the old agent captured several internal queries, e.g. "SHOW WARNINGS"
                return rdd.getData().equals("select * from abc");
              }
            },
            10,
            TimeUnit.SECONDS);

    RemoteDependencyData rdd =
        (RemoteDependencyData) ((Data<?>) rddEnvelope.getData()).getBaseData();

    assertTrue(rd.getProperties().isEmpty());
    assertTrue(rd.getSuccess());

    assertTrue(rdd.getName().startsWith("select * from abc"));
    assertEquals("SQL", rdd.getType());
    // not the best test, because this is both the db.name and db.system
    assertTrue(rdd.getTarget().matches("dependency[0-9]+/mysql"));
    assertEquals("select * from abc", rdd.getData());
    assertTrue(rdd.getProperties().isEmpty());
    assertTrue(rdd.getSuccess());

    assertParentChild(rd, rdEnvelope, rddEnvelope, "GET /Jdbc/*");
  }

  @Test
  @TargetUri("/postgresPreparedStatement")
  public void postgresPreparedStatement() throws Exception {
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

    assertTrue(rdd.getName().startsWith("select * from abc where xyz = ?"));
    assertEquals("SQL", rdd.getType());
    // not the best test, because this is both the db.name and db.system
    assertTrue(rdd.getTarget().matches("dependency[0-9]+/postgres"));
    assertEquals("select * from abc where xyz = ?", rdd.getData());
    assertTrue(rdd.getProperties().isEmpty());
    assertTrue(rdd.getSuccess());

    assertParentChild(rd, rdEnvelope, rddEnvelope, "GET /Jdbc/*");
  }

  @Test
  @TargetUri("/postgresStatement")
  public void postgresStatement() throws Exception {
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

    assertTrue(rdd.getName().startsWith("select * from abc"));
    assertEquals("SQL", rdd.getType());
    // not the best test, because this is both the db.name and db.system
    assertTrue(rdd.getTarget().matches("dependency[0-9]+/postgres"));
    assertEquals("select * from abc", rdd.getData());
    assertTrue(rdd.getProperties().isEmpty());
    assertTrue(rdd.getSuccess());

    assertParentChild(rd, rdEnvelope, rddEnvelope, "GET /Jdbc/*");
  }

  @Test
  @TargetUri("/sqlServerPreparedStatement")
  public void sqlServerPreparedStatement() throws Exception {
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

    assertTrue(rdd.getName().startsWith("select * from abc where xyz = ?"));
    assertEquals("SQL", rdd.getType());
    assertTrue(rdd.getTarget().matches("dependency[0-9]+"));
    assertEquals("select * from abc where xyz = ?", rdd.getData());
    assertTrue(rdd.getProperties().isEmpty());
    assertTrue(rdd.getSuccess());

    assertParentChild(rd, rdEnvelope, rddEnvelope, "GET /Jdbc/*");
  }

  @Test
  @TargetUri("/sqlServerStatement")
  public void sqlServerStatement() throws Exception {
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

    assertTrue(rdd.getName().startsWith("select * from abc"));
    assertEquals("SQL", rdd.getType());
    assertTrue(rdd.getTarget().matches("dependency[0-9]+"));
    assertEquals("select * from abc", rdd.getData());
    assertTrue(rdd.getProperties().isEmpty());
    assertTrue(rdd.getSuccess());

    assertParentChild(rd, rdEnvelope, rddEnvelope, "GET /Jdbc/*");
  }

  @Ignore("FIXME: need custom container with oracle db")
  @Test
  @TargetUri("/oraclePreparedStatement")
  public void oraclePreparedStatement() throws Exception {
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

    assertTrue(rdd.getName().startsWith("select * from abc where xyz = ?"));
    assertEquals("SQL", rdd.getType());
    assertTrue(rdd.getTarget().matches("dependency[0-9]+"));
    assertEquals("select * from abc where xyz = ?", rdd.getData());
    assertTrue(rdd.getProperties().isEmpty());
    assertTrue(rdd.getSuccess());

    assertParentChild(rd, rdEnvelope, rddEnvelope, "GET /Jdbc/*");
  }

  @Ignore("FIXME: need custom container with oracle db")
  @Test
  @TargetUri("/oracleStatement")
  public void oracleStatement() throws Exception {
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

    assertTrue(rdd.getName().startsWith("select * from abc"));
    assertEquals("SQL", rdd.getType());
    assertTrue(rdd.getTarget().matches("dependency[0-9]+"));
    assertEquals("select * from abc", rdd.getData());
    assertTrue(rdd.getProperties().isEmpty());
    assertTrue(rdd.getSuccess());

    assertParentChild(rd, rdEnvelope, rddEnvelope, "GET /Jdbc/*");
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
    assertNull(childEnvelope.getTags().get("ai.operation.name"));
  }
}
