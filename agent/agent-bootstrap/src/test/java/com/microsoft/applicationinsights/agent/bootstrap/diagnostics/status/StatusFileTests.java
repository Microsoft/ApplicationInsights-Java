package com.microsoft.applicationinsights.agent.bootstrap.diagnostics.status;

import java.io.File;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import com.microsoft.applicationinsights.agent.bootstrap.diagnostics.AgentExtensionVersionFinder;
import com.microsoft.applicationinsights.agent.bootstrap.diagnostics.DiagnosticsHelper;
import com.microsoft.applicationinsights.agent.bootstrap.diagnostics.DiagnosticsTestHelper;
import com.squareup.moshi.JsonAdapter;
import com.squareup.moshi.Moshi.Builder;
import org.hamcrest.Matchers;
import org.junit.*;
import org.junit.contrib.java.lang.system.*;
import org.junit.rules.*;

import static com.microsoft.applicationinsights.agent.bootstrap.diagnostics.status.StatusFile.initLogDir;
import static org.hamcrest.Matchers.*;
import static org.junit.Assert.*;
import static org.junit.Assume.assumeTrue;

public class StatusFileTests {

    @Rule
    public TemporaryFolder tempFolder = new TemporaryFolder();

    @Rule
    public EnvironmentVariables envVars = new EnvironmentVariables();

    @Rule
    public ClearSystemProperties clearProp = new ClearSystemProperties("site.logdir");

    private final String testIkey = "fake-ikey-123";
    private final String fakeVersion = "0.0.1-test";

    @Before
    public void setup() {
        // TODO these tests currently only pass on windows
        assumeTrue(DiagnosticsHelper.isOsWindows());
        envVars.set("APPINSIGHTS_INSTRUMENTATIONKEY", testIkey);
        envVars.set(AgentExtensionVersionFinder.AGENT_EXTENSION_VERSION_ENVIRONMENT_VARIABLE, fakeVersion);
    }

    @After
    public void resetStaticVariables() {
        DiagnosticsTestHelper.reset();
    }

    @Test
    public void defaultDirectoryIsCorrect() {
        // TODO this test doesn't pass inside of windows + bash because bash sets HOME env
        assumeTrue(System.getenv(StatusFile.HOME_ENV_VAR) == null);
        assertEquals("./LogFiles/ApplicationInsights", initLogDir());
    }

    @Test
    public void siteLogDirPropertyUpdatesBaseDir() {
        String parentDir = "/temp/test/prop";
        System.setProperty("site.logdir", parentDir);
        assertEquals("/temp/test/prop/ApplicationInsights", StatusFile.initLogDir());
    }

    @Test
    public void homeEnvVarUpdatesBaseDir() {
        String homeDir = "/temp/test";
        envVars.set(StatusFile.HOME_ENV_VAR, homeDir);
        assertEquals("/temp/test/LogFiles/ApplicationInsights", StatusFile.initLogDir());
    }

    @Test
    public void siteLogDirHasPrecedenceOverHome() {
        String homeDir = "/this/is/wrong";
        envVars.set(StatusFile.HOME_ENV_VAR, homeDir);
        System.setProperty("site.logdir", "/the/correct/dir");
        assertEquals("/the/correct/dir/ApplicationInsights", StatusFile.initLogDir());
    }

    @Test
    public void mapHasExpectedValues() {
        final Map<String, Object> jsonMap = StatusFile.getJsonMap();
        System.out.println("Map contents: " + Arrays.toString(jsonMap.entrySet().toArray()));

        assertMapHasExpectedInformation(jsonMap);
    }

    void assertMapHasExpectedInformation(Map<String, Object> inputMap) {
        assertMapHasExpectedInformation(inputMap, null, null);
    }

    void assertMapHasExpectedInformation(Map<String, Object> inputMap, String key, String value) {
        int size = 6;
        if (key != null) {
            size = 7;
            assertThat(inputMap, Matchers.<String, Object>hasEntry(key, value));
        }
        assertThat(inputMap.entrySet(), hasSize(size));
        assertThat(inputMap, hasKey("MachineName"));
        assertThat(inputMap, Matchers.<String, Object>hasEntry("Ikey", testIkey));
        assertThat(inputMap, hasKey("PID"));
        assertThat(inputMap, Matchers.<String, Object>hasEntry("AppType", "java"));
        assertThat(inputMap, hasKey("SdkVersion"));
        assertThat(inputMap, Matchers.<String, Object>hasEntry("ExtensionVersion", fakeVersion));
    }

    @Test
    public void connectionStringWorksToo() {
        String ikey = "a-different-ikey-456789";
        envVars.clear("APPINSIGHTS_INSTRUMENTATIONKEY");
        envVars.set("APPLICATIONINSIGHTS_CONNECTION_STRING", "InstrumentationKey=" + ikey);
        final Map<String, Object> jsonMap = StatusFile.getJsonMap();
        System.out.println("Map contents: " + Arrays.toString(jsonMap.entrySet().toArray()));
        assertThat(jsonMap, Matchers.<String, Object>hasEntry("Ikey", ikey));
    }

    @Test
    public void writesCorrectFile() throws Exception {
        DiagnosticsTestHelper.setIsAppSvcAttachForLoggingPurposes(true);
        runWriteFileTest(true);
    }

    private void runWriteFileTest(boolean enabled) throws Exception {
        final File tempFolder = this.tempFolder.newFolder();
        assertTrue("Verify temp folder is directory", tempFolder.isDirectory());
        assertThat("Verify temp folder is empty", tempFolder.list(), emptyArray());

        StatusFile.directory = tempFolder.getAbsolutePath();
        StatusFile.write();
        pauseForFileWrite();

        if (enabled) {
            assertThat(tempFolder.list(), arrayWithSize(1));
            final Map map = parseJsonFile(tempFolder);
            assertMapHasExpectedInformation(map);
        } else {
            assertThat(tempFolder.list(), emptyArray());
        }
    }

    private void pauseForFileWrite() throws InterruptedException {
        TimeUnit.SECONDS.sleep(5);
    }

    Map parseJsonFile(File tempFolder) throws java.io.IOException {
        final JsonAdapter<Map> adapter = new Builder().build().adapter(Map.class);
        final String fileName = StatusFile.constructFileName(StatusFile.getJsonMap());
        final String contents = new String(Files.readAllBytes(new File(tempFolder, fileName).toPath()));
        System.out.println("file contents (" + fileName + "): " + contents);
        return adapter.fromJson(contents);
    }

    @Test
    public void doesNotWriteIfNotAppService() throws Exception {
        DiagnosticsTestHelper.setIsAppSvcAttachForLoggingPurposes(false); // just to be sure

        final File tempFolder = this.tempFolder.newFolder();
        StatusFile.directory = tempFolder.getAbsolutePath();
        assertTrue(tempFolder.isDirectory());
        assertThat("Before write()", tempFolder.list(), emptyArray());
        StatusFile.write();
        pauseForFileWrite();
        assertThat("After write()", tempFolder.list(), emptyArray());
        StatusFile.putValueAndWrite("shouldNot", "write");
        pauseForFileWrite();
        assertThat("After write()", tempFolder.list(), emptyArray());
    }

    @Test
    public void putValueAndWriteOverwritesCurrentFile() throws Exception {
        final String key = "write-test";
        try {
            DiagnosticsTestHelper.setIsAppSvcAttachForLoggingPurposes(true);


            final File tempFolder = this.tempFolder.newFolder();
            StatusFile.directory = tempFolder.getAbsolutePath();
            assertTrue(tempFolder.isDirectory());
            assertThat(tempFolder.list(), emptyArray());
            StatusFile.write();
            pauseForFileWrite();
            assertThat(tempFolder.list(), arrayWithSize(1));
            Map map = parseJsonFile(tempFolder);
            assertMapHasExpectedInformation(map);

            final String value = "value123";
            StatusFile.putValueAndWrite(key, value);
            pauseForFileWrite();
            assertThat(tempFolder.list(), arrayWithSize(1));
            map = parseJsonFile(tempFolder);
            assertMapHasExpectedInformation(map, key, value);

        } finally {
            DiagnosticsTestHelper.setIsAppSvcAttachForLoggingPurposes(false);
            StatusFile.CONSTANT_VALUES.remove(key);
        }
    }

    @Test
    public void fileNameHasMachineNameAndPid() {
        final Map<String, Object> jsonMap = StatusFile.getJsonMap();
        final String s = StatusFile.constructFileName(jsonMap);
        assertThat(s, startsWith(StatusFile.FILENAME_PREFIX));
        assertThat(s, endsWith(StatusFile.FILE_EXTENSION));
        assertThat(s, containsString(jsonMap.get("MachineName").toString()));
        assertThat(s, containsString(jsonMap.get("PID").toString()));
    }
}
