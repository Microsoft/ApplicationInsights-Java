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

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import com.google.common.base.CaseFormat;
import com.google.common.base.Charsets;
import com.google.common.base.Stopwatch;
import com.google.common.collect.HashMultimap;
import com.google.common.collect.Multimap;
import com.google.common.io.Resources;
import com.microsoft.applicationinsights.smoketest.docker.AiDockerClient;
import com.microsoft.applicationinsights.smoketest.docker.ContainerInfo;
import com.microsoft.applicationinsights.smoketest.exceptions.SmokeTestException;
import com.microsoft.applicationinsights.smoketest.exceptions.TimeoutException;
import com.microsoft.applicationinsights.smoketest.fixtures.AfterWithParams;
import com.microsoft.applicationinsights.smoketest.fixtures.BeforeWithParams;
import com.microsoft.applicationinsights.smoketest.fixtures.ParameterizedRunnerWithFixturesFactory;
import com.microsoft.applicationinsights.smoketest.schemav2.Data;
import com.microsoft.applicationinsights.smoketest.schemav2.Domain;
import com.microsoft.applicationinsights.smoketest.schemav2.Envelope;
import com.microsoft.applicationinsights.smoketest.schemav2.RequestData;
import com.microsoft.applicationinsights.test.fakeingestion.MockedAppInsightsIngestionServer;
import com.microsoft.applicationinsights.test.fakeingestion.MockedAppInsightsIngestionServlet;
import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.nio.file.Files;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Properties;
import java.util.Random;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Predicate;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.junit.After;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.FixMethodOrder;
import org.junit.Rule;
import org.junit.rules.TestWatcher;
import org.junit.runner.Description;
import org.junit.runner.RunWith;
import org.junit.runners.MethodSorters;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;
import org.junit.runners.Parameterized.UseParametersRunnerFactory;

/** This is the base class for smoke tests. */
@RunWith(Parameterized.class)
@UseParametersRunnerFactory(ParameterizedRunnerWithFixturesFactory.class)
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
@SuppressWarnings({"SystemOut", "InterruptedExceptionSwallowed"})
public abstract class AiSmokeTest {

  // region: parameterization
  @Parameters(name = "{index}: {0}, {1}, {2}")
  public static Collection<Object[]> parameterGenerator() throws IOException {
    List<String> appServers =
        Resources.readLines(Resources.getResource("appServers.txt"), Charsets.UTF_8);
    System.out.println("Target appservers=" + Arrays.toString(appServers.toArray()));
    String os = System.getProperty("applicationinsights.smoketest.os", "linux");
    URL jreExcludesUrl =
        Thread.currentThread().getContextClassLoader().getResource("jre.excludes.txt");
    List<String> jreExcludes;
    if (jreExcludesUrl == null) {
      jreExcludes = new ArrayList<>();
    } else {
      jreExcludes = Resources.readLines(jreExcludesUrl, Charsets.UTF_8);
    }
    Multimap<String, String> appServers2jres = HashMultimap.create();
    for (String appServer : appServers) {
      List<String> serverJres;
      try {
        serverJres = getAppServerJres(appServer, jreExcludes);
      } catch (Exception e) {
        System.err.printf("SKIPPING '%s'. Could not configure jres: %s%n", appServer, e);
        continue;
      }
      appServers2jres.putAll(appServer, serverJres);
    }

    Collection<Object[]> rval = new ArrayList<>();

    // keys = appServers, values = jres supported by appServer
    for (Entry<String, String> entry : appServers2jres.entries()) {
      rval.add(new Object[] {entry.getKey(), os, entry.getValue()});
    }
    System.out.println(
        "Configured appservers=" + Arrays.toString(appServers2jres.keySet().toArray()));

    return rval;
  }

  private static List<String> getAppServerJres(String appServer, List<String> jreExcludes)
      throws IOException {
    List<String> jres = new ArrayList<>();
    for (String jre :
        Resources.readLines(Resources.getResource(appServer + ".jre.txt"), Charsets.UTF_8)) {
      if (!jreExcludes.contains(jre)) {
        jres.add(jre.replaceAll("[:/]", "_"));
      }
    }
    return jres;
  }

  @Parameter(0)
  public String appServer;

  @Parameter(1)
  public String os;

  @Parameter(2)
  public String jreVersion;
  // endregion

  // region: container fields
  private static final short BASE_PORT_NUMBER = 28080;
  private static final String TEST_CONFIG_FILENAME = "testInfo.properties";

  private static final AiDockerClient docker = AiDockerClient.createLinuxClient();

  protected static void stopContainer(ContainerInfo info) throws Exception {
    System.out.printf("Stopping container: %s%n", info);
    Stopwatch killTimer = Stopwatch.createUnstarted();
    try {
      killTimer.start();
      docker.stopContainer(info.getContainerId());
      System.out.printf(
          "Container stopped (%s) in %dms%n", info, killTimer.elapsed(TimeUnit.MILLISECONDS));
    } catch (Exception e) {
      System.err.printf(
          "Error stopping container (in %dms): %s%n",
          killTimer.elapsed(TimeUnit.MILLISECONDS), info);
      throw e;
    }
  }

  protected static short currentPortNumber = BASE_PORT_NUMBER;

  private static final List<DependencyContainer> dependencyImages = new ArrayList<>();
  protected static AtomicReference<ContainerInfo> currentContainerInfo = new AtomicReference<>();
  protected static Deque<ContainerInfo> allContainers = new ArrayDeque<>();
  protected static String currentImageName;
  protected static short appServerPort;
  protected static String warFileName;
  protected static String agentMode;
  protected static String networkId;
  protected static String networkName = "aismoke-net";
  protected static boolean requestCaptureEnabled = true; // we will assume request capturing is on
  // endregion

  // region: application fields
  protected String targetUri;
  protected String httpMethod;
  protected long targetUriDelayMs;
  protected long targetUriCallCount;
  protected long targetUriTimeoutMs;
  // endregion

  // region: options
  public static final int APPLICATION_READY_TIMEOUT_SECONDS = 120;
  public static final int TELEMETRY_RECEIVE_TIMEOUT_SECONDS = 60;
  public static final int DELAY_AFTER_CONTAINER_STOP_MILLISECONDS = 1500;
  public static final int HEALTH_CHECK_RETRIES = 2;
  public static final int APPSERVER_HEALTH_CHECK_TIMEOUT = 75;
  // endregion

  private static final Properties testProps = new Properties();

  protected static final MockedAppInsightsIngestionServer mockedIngestion =
      new MockedAppInsightsIngestionServer();

  /**
   * This rule does a few things: 1. failure detection: logs are only grabbed when the test fails.
   * 2. reads test metadata from annotations
   */
  @Rule
  public TestWatcher theWatchman =
      new TestWatcher() {

        @Override
        protected void starting(Description description) {
          System.out.println("Configuring test...");
          TargetUri targetUri = description.getAnnotation(TargetUri.class);
          AiSmokeTest thiz = AiSmokeTest.this;
          if (targetUri == null) {
            thiz.targetUri = null;
            thiz.httpMethod = null;
            thiz.targetUriDelayMs = 0L;
            thiz.targetUriCallCount = 1;
            thiz.targetUriTimeoutMs = TELEMETRY_RECEIVE_TIMEOUT_SECONDS * 1000;
          } else {
            thiz.targetUri = targetUri.value();
            if (!thiz.targetUri.startsWith("/")) {
              thiz.targetUri = "/" + thiz.targetUri;
            }
            thiz.httpMethod = targetUri.method().toUpperCase();
            thiz.targetUriDelayMs = targetUri.delay();
            thiz.targetUriCallCount = targetUri.callCount();
            thiz.targetUriTimeoutMs =
                targetUri.timeout() > 0
                    ? targetUri.timeout()
                    : TELEMETRY_RECEIVE_TIMEOUT_SECONDS * 1000;
          }
        }

        @Override
        protected void failed(Throwable t, Description description) {
          // NOTE this happens after @After :)
          String containerId = currentContainerInfo.get().getContainerId();
          System.out.println("Test failure detected.");
          printContainerLogs(containerId);
        }

        private void printContainerLogs(String containerId) {
          try {
            System.out.println("\nFetching container logs for " + containerId);
            AiDockerClient.printContainerLogs(containerId);
          } catch (Exception e) {
            System.err.println("Error copying logs to stream");
            e.printStackTrace();
          } finally {
            System.out.println("\nFinished gathering logs.");
          }
        }
      };

  @BeforeClass
  public static void configureShutdownHook() {
    // NOTE the JUnit runner (or gradle) forces this to happen. The syncronized block and check for
    // empty should avoid any issues
    Runtime.getRuntime()
        .addShutdownHook(
            new Thread(
                new Runnable() {
                  @Override
                  public void run() {
                    ContainerInfo containerInfo = currentContainerInfo.get();
                    if (containerInfo == null) {
                      return;
                    }
                    try {
                      stopContainer(containerInfo);
                    } catch (Exception e) {
                      System.err.println(
                          "Error while stopping container id="
                              + containerInfo.getContainerId()
                              + ". This must be stopped manually.");
                      e.printStackTrace();
                    }
                  }
                }));
  }

  @ClassRule
  public static TestWatcher classOptionsLoader =
      new TestWatcher() {
        @Override
        protected void starting(Description description) {
          System.out.println("Configuring test class...");
          UseAgent ua = description.getAnnotation(UseAgent.class);
          if (ua != null) {
            agentMode = ua.value();
            System.out.println("AGENT MODE: " + agentMode);
          }
          WithDependencyContainers wdc = description.getAnnotation(WithDependencyContainers.class);
          if (wdc != null) {
            for (DependencyContainer container : wdc.value()) {
              if (StringUtils.isBlank(container.value())) { // checks for null
                System.err.printf(
                    "WARNING: skipping dependency container with invalid name: '%s'%n",
                    container.value());
                continue;
              }
              dependencyImages.add(container);
            }
          }

          RequestCapturing cr = description.getAnnotation(RequestCapturing.class);
          if (cr != null) {
            requestCaptureEnabled = cr.enabled();
            System.out.println(
                "Request capturing is " + (requestCaptureEnabled ? "enabled." : "disabled."));
          }
        }

        @Override
        protected void finished(Description description) {
          String message = "";
          if (agentMode != null) {
            message += "Resetting agentMode. ";
          }
          if (!dependencyImages.isEmpty()) {
            message += "Clearing dependency images. ";
          }
          System.out.printf("Finished test class. %s%n", message);
          dependencyImages.clear();
          agentMode = null;
        }
      };

  @BeforeWithParams
  public static void configureEnvironment(String appServer, String os, String jreVersion)
      throws Exception {
    System.out.println("Preparing environment...");
    try {
      ContainerInfo containerInfo = currentContainerInfo.get();
      if (containerInfo != null) {
        // test cleanup didn't take...try to clean up
        if (docker.isContainerRunning(containerInfo.getContainerId())) {
          System.err.println("From last test run, container is still running: " + containerInfo);
          try {
            docker.stopContainer(containerInfo.getContainerId());
          } catch (Exception e) {
            System.err.println("Couldn't clean up environment. Must be done manually.");
            throw e;
          }
        } else {
          // container must have stopped after timeout reached.
          currentContainerInfo.set(null);
        }
      }
      checkParams(appServer, os, jreVersion);
      setupProperties(appServer, os, jreVersion);
      startMockedIngestion();
      createDockerNetwork();
      startAllContainers();
      waitForApplicationToStart();
      System.out.println("Environment preparation complete.");
    } catch (Exception e) {
      String additionalMessage;
      if (e instanceof TimeoutException) {
        additionalMessage = e.getLocalizedMessage();
      } else {
        additionalMessage = ExceptionUtils.getStackTrace(e);
      }
      System.err.printf("Could not configure environment: %s%n", additionalMessage);
      throw e;
    }
  }

  @Before
  public void setupTest() throws Exception {
    callTargetUriAndWaitForTelemetry();
  }

  // region: before test helper methods
  protected static String getAppContext() {
    if (warFileName.endsWith(".jar")) {
      // spring boot jar
      return "";
    } else {
      return warFileName.replace(".war", "");
    }
  }

  protected static String getBaseUrl() {
    String appContext = getAppContext();
    if (appContext.isEmpty()) {
      return "http://localhost:" + appServerPort;
    } else {
      return "http://localhost:" + appServerPort + "/" + appContext;
    }
  }

  protected static void waitForApplicationToStart() throws Exception {
    ContainerInfo containerInfo = currentContainerInfo.get();
    try {
      System.out.printf("Test app health check: Waiting for %s to start...%n", warFileName);
      String contextRootUrl = getBaseUrl() + "/";
      waitForUrlWithRetries(
          contextRootUrl,
          APPLICATION_READY_TIMEOUT_SECONDS,
          TimeUnit.SECONDS,
          String.format("%s on %s", getAppContext(), containerInfo.getImageName()),
          HEALTH_CHECK_RETRIES);
      System.out.println("Test app health check complete.");
      waitForHealthCheckTelemetryIfNeeded(contextRootUrl);
    } catch (Exception e) {
      AiDockerClient.printContainerLogs(containerInfo.getContainerId());
      throw e;
    } finally {
      mockedIngestion.resetData();
    }
  }

  private static void waitForHealthCheckTelemetryIfNeeded(String contextRootUrl)
      throws InterruptedException, ExecutionException {
    if (!requestCaptureEnabled) {
      return;
    }

    Stopwatch receivedTelemetryTimer = Stopwatch.createStarted();
    int requestTelemetryFromHealthCheckTimeout;
    if (currentImageName.startsWith("javase_")) {
      requestTelemetryFromHealthCheckTimeout = APPLICATION_READY_TIMEOUT_SECONDS;
    } else {
      requestTelemetryFromHealthCheckTimeout = TELEMETRY_RECEIVE_TIMEOUT_SECONDS;
    }
    try {
      mockedIngestion.waitForItem(
          new Predicate<Envelope>() {
            @Override
            public boolean test(Envelope input) {
              if (!"RequestData".equals(input.getData().getBaseType())) {
                return false;
              }
              RequestData data = (RequestData) ((Data<?>) input.getData()).getBaseData();
              return contextRootUrl.equals(data.getUrl()) && "200".equals(data.getResponseCode());
            }
          },
          requestTelemetryFromHealthCheckTimeout,
          TimeUnit.SECONDS);
      System.out.printf(
          "Received request telemetry after %.3f seconds...%n",
          receivedTelemetryTimer.elapsed(TimeUnit.MILLISECONDS) / 1000.0);
      System.out.println("Clearing any RequestData from health check.");
    } catch (java.util.concurrent.TimeoutException e) {
      throw new TimeoutException(
          "request telemetry from application health check",
          requestTelemetryFromHealthCheckTimeout,
          TimeUnit.SECONDS,
          e);
    }
  }

  protected void callTargetUriAndWaitForTelemetry() throws Exception {
    if (targetUri == null) {
      System.out.println("targetUri==null: automated testapp request disabled");
      return;
    }
    if (targetUriDelayMs > 0) {
      System.out.printf("Waiting %.3fs before calling uri...%n", targetUriDelayMs / 1000.0);
      System.out.flush();
      TimeUnit.MILLISECONDS.sleep(targetUriDelayMs);
    }
    System.out.println("Calling " + targetUri + " ...");
    String url = getBaseUrl() + targetUri;
    if (targetUriCallCount == 1) {
      System.out.println("calling " + url);
    } else {
      System.out.println("calling " + url + " " + targetUriCallCount + " times");
    }
    for (int i = 0; i < targetUriCallCount; i++) {
      String content;
      switch (httpMethod) {
        case "GET":
          content = HttpHelper.get(url);
          break;
        default:
          throw new UnsupportedOperationException(
              String.format("http method '%s' is not currently supported", httpMethod));
      }
      String expectationMessage = "The base context in testApps should return a nonempty response.";
      assertNotNull(
          String.format("Null response from targetUri: '%s'. %s", targetUri, expectationMessage),
          content);
      assertTrue(
          String.format("Empty response from targetUri: '%s'. %s", targetUri, expectationMessage),
          content.length() > 0);
    }
    if (this.targetUriTimeoutMs > 0) {
      Stopwatch sw = Stopwatch.createStarted();
      mockedIngestion.awaitAnyItems(this.targetUriTimeoutMs, TimeUnit.MILLISECONDS);
      System.out.printf(
          "Telemetry received after %.3f seconds.%n", sw.elapsed(TimeUnit.MILLISECONDS) / 1000.0);
    }
    System.out.println("Starting validation...");
    assertTrue("mocked ingestion has no data", mockedIngestion.hasData());
  }

  protected static void checkParams(String appServer, String os, String jreVersion) {
    String fmt =
        "Missing required framework parameter: %s - this indicates an error in the parameter generator";
    assertNotNull(String.format(fmt, "appServer"), appServer);
    assertNotNull(String.format(fmt, "os"), os);
    assertNotNull(String.format(fmt, "jreVersion"), jreVersion);
  }

  protected static void setupProperties(String appServer, String os, String jreVersion)
      throws Exception {
    testProps.load(
        Files.newBufferedReader(
            new File(Resources.getResource(TEST_CONFIG_FILENAME).toURI()).toPath(), UTF_8));
    currentImageName = String.format("%s_%s_%s", appServer, os, jreVersion);
    appServerPort = currentPortNumber++;
  }

  protected static void startMockedIngestion() throws Exception {
    mockedIngestion.addIngestionFilter(
        new Predicate<Envelope>() {
          @Override
          public boolean test(Envelope input) {
            if (input == null) {
              return false;
            }
            String deviceId = input.getTags().get("ai.device.id");
            if (deviceId == null) {
              return true;
            }
            ContainerInfo containerInfo = currentContainerInfo.get();
            if (containerInfo == null) { // ignore telemetry in after container is cleaned up.
              return false;
            }
            boolean belongsToCurrentContainer = containerInfo.getContainerId().startsWith(deviceId);
            if (!belongsToCurrentContainer) {
              System.out.println("Telemetry from previous container");
            }
            return belongsToCurrentContainer;
          }
        });
    mockedIngestion.startServer();
    TimeUnit.SECONDS.sleep(2);
    checkMockedIngestionHealth();
  }

  protected static void checkMockedIngestionHealth() throws Exception {
    String ok = HttpHelper.get("http://localhost:" + mockedIngestion.getPort() + "/");
    assertEquals(MockedAppInsightsIngestionServlet.ENDPOINT_HEALTH_CHECK_RESPONSE, ok);
    String postResponse =
        HttpHelper.post("http://localhost:6060/v2.1/track", MockedAppInsightsIngestionServlet.PING);
    assertEquals(MockedAppInsightsIngestionServlet.PONG, postResponse);
  }

  private static void createDockerNetwork() throws Exception {
    try {
      System.out.printf("Creating network '%s'...%n", networkName);
      networkId = docker.createNetwork(networkName);
    } catch (Exception e) {
      System.err.printf("Error creating network named '%s'%n", networkName);
      e.printStackTrace();
      throw e;
    }
  }

  private static void cleanUpDockerNetwork() {
    if (networkId == null) {
      System.out.println("No network id....nothing to clean up");
      return;
    }
    try {
      System.out.printf("Deleting network '%s'...%n", networkName);
      docker.deleteNetwork(networkName);
    } catch (Exception e) {
      System.err.printf("Error deleting network named '%s' (%s)%n", networkName, networkId);
      e.printStackTrace();
    } finally {
      networkId = null;
    }
  }

  private static void startAllContainers() throws Exception {
    startDependencyContainers();
    startTestApplicationContainer();
  }

  private static void startDependencyContainers() throws IOException, InterruptedException {
    if (dependencyImages.isEmpty()) {
      System.out.println("No dependency containers to start.");
      return;
    }

    Map<String, String> hostnameEnvVars = new HashMap<>();
    for (DependencyContainer dc : dependencyImages) {
      String imageName = dc.imageName().isEmpty() ? dc.value() : dc.imageName();
      System.out.printf("Starting container: %s%n", imageName);
      String containerName = "dependency" + new Random().nextInt(Integer.MAX_VALUE);
      String[] envVars = substitue(dc.environmentVariables(), hostnameEnvVars, containerName);
      String containerId =
          AiDockerClient.startDependencyContainer(
              imageName, envVars, dc.portMapping(), networkId, containerName);
      if (containerId == null || containerId.isEmpty()) {
        throw new AssertionError(
            "'containerId' was null/empty attempting to start container: " + imageName);
      }
      System.out.printf("Dependency container started: %s (%s)%n", imageName, containerId);

      if (!dc.hostnameEnvironmentVariable().isEmpty()) {
        hostnameEnvVars.put(dc.hostnameEnvironmentVariable(), containerName);
      }
      ContainerInfo depConInfo = new ContainerInfo(containerId, containerName);
      depConInfo.setContainerName(containerName);
      depConInfo.setDependencyContainerInfo(dc);
      System.out.printf("Dependency container name for %s: %s%n", imageName, containerName);
      allContainers.push(depConInfo);
      TimeUnit.MILLISECONDS.sleep(500); // wait a bit after starting a server.
    }
  }

  private static String[] substitue(
      String[] environmentVariables, Map<String, String> hostnameEnvVars, String containerName) {
    String[] envVars = new String[environmentVariables.length];
    for (int i = 0; i < environmentVariables.length; i++) {
      envVars[i] = substitute(environmentVariables[i], hostnameEnvVars, containerName);
    }
    return envVars;
  }

  private static String substitute(
      String environmentVariable, Map<String, String> hostnameEnvVars, String containerName) {
    String envVar = environmentVariable;
    for (Map.Entry<String, String> entry : hostnameEnvVars.entrySet()) {
      envVar = envVar.replace("${" + entry.getKey() + "}", entry.getValue());
    }
    envVar = envVar.replace("${CONTAINERNAME}", containerName);
    return envVar;
  }

  private static void startTestApplicationContainer() throws Exception {
    System.out.printf("Starting container: %s%n", currentImageName);
    Map<String, String> envVars = generateAppContainerEnvVarMap();
    String containerId =
        AiDockerClient.startContainer(
            currentImageName, appServerPort + ":8080", networkId, null, envVars, false);
    if (containerId == null || containerId.isEmpty()) {
      throw new AssertionError(
          "'containerId' was null/empty attempting to start container: " + currentImageName);
    }
    System.out.printf("Container started: %s (%s)%n", currentImageName, containerId);

    ContainerInfo containerInfo = new ContainerInfo(containerId, currentImageName);
    currentContainerInfo.set(containerInfo);
    if (currentImageName.startsWith("javase_")) {
      // can proceed straight to deploying the app
      // (there's nothing running at this point, unlike images based on servlet containers)
      allContainers.push(containerInfo);
    } else {
      try {
        String url = String.format("http://localhost:%s/", String.valueOf(appServerPort));
        System.out.printf("Verifying appserver has started (%s)...%n", url);
        allContainers.push(containerInfo);
        waitForUrlWithRetries(
            url,
            APPSERVER_HEALTH_CHECK_TIMEOUT,
            TimeUnit.SECONDS,
            String.format("app server on image '%s'", currentImageName),
            HEALTH_CHECK_RETRIES);
        System.out.println("App server is ready.");
      } catch (RuntimeException e) {
        System.err.println("Error starting app server");
        if (docker.isContainerRunning(containerInfo.getContainerId())) {
          System.out.println("Container is not running.");
          allContainers.remove(containerInfo);
        } else {
          System.out.println("Yet, the container is running.");
        }
        System.out.println("Printing container logs: ");
        System.out.println("# LOGS START =========================");
        AiDockerClient.printContainerLogs(containerInfo.getContainerId());
        System.out.println("# LOGS END ===========================");
        throw e;
      }
    }

    try {
      warFileName = getProperty("ai.smoketest.testAppWarFile");
      System.out.printf("Deploying test application: %s...%n", warFileName);
      docker.copyAndDeployToContainer(
          containerId, new File(Resources.getResource(warFileName).toURI()));
      System.out.println("Test application deployed.");
    } catch (Exception e) {
      System.err.println("Error deploying test application.");
      throw e;
    }
  }

  private static Map<String, String> generateAppContainerEnvVarMap() {
    Map<String, String> map = new HashMap<>();
    if (agentMode != null) {
      map.put("AI_AGENT_MODE", agentMode);
    }
    for (ContainerInfo info : allContainers) {
      if (!info.isDependency()) {
        continue;
      }
      DependencyContainer dc = info.getDependencyContainerInfo();
      String varname = dc.hostnameEnvironmentVariable();
      if (varname.isEmpty()) {
        varname = CaseFormat.LOWER_CAMEL.to(CaseFormat.UPPER_UNDERSCORE, dc.value());
      }
      String containerName = info.getContainerName();
      if (containerName == null || containerName.isEmpty()) {
        throw new SmokeTestException("Null/empty container name for dependency container");
      }
      map.put(varname, containerName);
      System.out.printf("Adding env var to test app container: %s=%s%n", varname, containerName);
    }
    return map;
  }
  // endregion

  @After
  public void resetMockedIngestion() {
    mockedIngestion.resetData();
    System.out.println("Mocked ingestion reset.");
  }

  @AfterWithParams
  public static void tearDownContainer(
      @SuppressWarnings("unused") String appServer,
      @SuppressWarnings("unused") String os,
      @SuppressWarnings("unused") String jreVersion)
      throws Exception {
    stopAllContainers();
    cleanUpDockerNetwork();
    TimeUnit.MILLISECONDS.sleep(DELAY_AFTER_CONTAINER_STOP_MILLISECONDS);
    System.out.println("Stopping mocked ingestion...");
    try {
      mockedIngestion.stopServer();
    } catch (Exception e) {
      System.err.println("Exception stopping mocked ingestion: " + e);
    }
  }

  public static void stopAllContainers() throws Exception {
    if (allContainers.isEmpty()) {
      System.out.println("No containers to stop");
      return;
    }

    System.out.printf("Stopping %d containers...", allContainers.size());
    List<ContainerInfo> failedToStop = new ArrayList<>();
    while (!allContainers.isEmpty()) {
      ContainerInfo c = allContainers.pop();
      if (c.equals(currentContainerInfo.get())) {
        System.out.println("Cleaning up app container");
        currentContainerInfo.set(null);
      }
      stopContainer(c);
      if (docker.isContainerRunning(c.getContainerId())) {
        System.err.printf("ERROR: Container failed to stop: %s%n", c.toString());
        failedToStop.add(c);
      }
    }

    ContainerInfo containerInfo = currentContainerInfo.get();
    if (containerInfo != null) {
      System.err.println("Could not find app container in stack. Stopping...");
      stopContainer(containerInfo);
      if (!docker.isContainerRunning(containerInfo.getContainerId())) {
        currentContainerInfo.set(null);
      }
    }

    if (!failedToStop.isEmpty()) {
      System.err.println("Some containers failed to stop. Subsequent tests may fail.");
      for (ContainerInfo c : failedToStop) {
        if (docker.isContainerRunning(c.getContainerId())) {
          System.err.println("Failed to stop: " + c.toString());
        }
      }
    }
  }

  // region: test helper methods
  /// This section has methods to be used inside tests ///

  protected static String getProperty(String key) {
    String rval = testProps.getProperty(key);
    if (rval == null) {
      throw new SmokeTestException(String.format("test property not found '%s'", key));
    }
    return rval;
  }

  @SuppressWarnings("TypeParameterUnusedInFormals")
  protected static <T extends Domain> T getBaseData(Envelope envelope) {
    return ((Data<T>) envelope.getData()).getBaseData();
  }

  protected static void waitForUrl(String url, long timeout, TimeUnit timeoutUnit, String appName)
      throws InterruptedException {
    int rval = 404;
    Stopwatch watch = Stopwatch.createStarted();
    do {
      if (watch.elapsed(timeoutUnit) > timeout) {
        throw new TimeoutException(appName, timeout, timeoutUnit);
      }

      try {
        TimeUnit.MILLISECONDS.sleep(250);
        rval = HttpHelper.getResponseCodeEnsuringSampled(url);
      } catch (InterruptedException e) {
        throw e;
      } catch (Exception ignored) {
      }
    } while (rval == 404);
    assertEquals(200, rval);
  }

  protected static void waitForUrlWithRetries(
      String url, long timeout, TimeUnit timeoutUnit, String appName, int numberOfRetries) {
    int triedCount = 0;
    boolean success = false;
    Throwable lastThrowable = null;
    do {
      try {
        waitForUrl(url, timeout, timeoutUnit, appName);
        success = true;
      } catch (ThreadDeath td) {
        throw td;
      } catch (Throwable t) {
        lastThrowable = t;
        System.out.printf(
            "WARNING: '%s' health check failed (%s). %d retries left. Exception: %s%n",
            appName, url, numberOfRetries - triedCount, t);
      }
    } while (!success && triedCount++ < numberOfRetries);
    if (!success) {
      throw new TimeoutException(
          appName,
          timeout * triedCount,
          timeoutUnit,
          lastThrowable,
          String.format("Tried %d times to hit %s", triedCount, url));
    }
  }

  @SuppressWarnings("TypeParameterUnusedInFormals")
  protected <T extends Domain> T getTelemetryDataForType(int index, String type) {
    return mockedIngestion.getBaseDataForType(index, type);
  }
  // endregion
}
