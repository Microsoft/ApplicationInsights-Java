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

package com.microsoft.applicationinsights.agent.internal.wasbootstrap.configuration;

import com.microsoft.applicationinsights.agent.bootstrap.diagnostics.DiagnosticsHelper;
import com.microsoft.applicationinsights.agent.bootstrap.diagnostics.status.StatusFile;
import com.microsoft.applicationinsights.customExceptions.FriendlyException;
import com.microsoft.applicationinsights.internal.config.connection.ConnectionString;
import com.microsoft.applicationinsights.internal.profiler.GcReportingLevel;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

import static java.util.concurrent.TimeUnit.DAYS;
import static java.util.concurrent.TimeUnit.MINUTES;

// an assumption is made throughout this file that user will not explicitly use `null` value in json file
// TODO how to pre-process or generally be robust in the face of explicit `null` value usage?
public class Configuration {

    public String connectionString;
    public Role role = new Role();
    public Map<String, String> customDimensions = new HashMap<>();
    public Sampling sampling = new Sampling();
    public List<JmxMetric> jmxMetrics = new ArrayList<>();
    public Instrumentation instrumentation = new Instrumentation();
    public Heartbeat heartbeat = new Heartbeat();
    public Proxy proxy = new Proxy();
    public SelfDiagnostics selfDiagnostics = new SelfDiagnostics();
    public PreviewConfiguration preview = new PreviewConfiguration();
    public InternalConfiguration internal = new InternalConfiguration();

    // this is just here to detect if using old format in order to give a helpful error message
    public Map<String, Object> instrumentationSettings;

    public enum MatchType {
        // Moshi JSON builder do not allow case insensitive mapping
        strict, regexp
    }

    public enum ProcessorActionType {
        // Moshi JSON builder do not allow case insensitive mapping
        insert, update, delete, hash, extract
    }

    public enum ProcessorType {
        // Moshi JSON builder do not allow case insensitive mapping
        attribute("an attribute"),
        log("a log"),
        span("a span");

        private final String anX;

        ProcessorType(String anX) {
            this.anX = anX;
        }
    }

    public static class Role {

        public String name;
        public String instance;
    }

    public static class Sampling {

        public double percentage = 100;
    }

    public static class SamplingPreview {

        public List<SamplingOverride> overrides = new ArrayList<>();
    }

    public static class JmxMetric {

        public String name;
        public String objectName;
        /* two versions are supported in json config file:
        1. attribute - earlier version when one objectName/attribute pair were configured,
        where displayed name is also specified
        2. attributes - allowing several attributes per objectName, where any number of attributes can be specified,
        the displayed name will be generated
         */
        public String attribute = "";
        public List<String> attributes = new ArrayList<>();
    }

    public static class Instrumentation {

        public CassandraInstrumentation cassandra = new CassandraInstrumentation();
        public JdbcInstrumentation jdbc = new JdbcInstrumentation();
        public JmsInstrumentation jms = new JmsInstrumentation();
        public KafkaInstrumentation kafka = new KafkaInstrumentation();
        public LoggingInstrumentation logging = new LoggingInstrumentation();
        public MicrometerInstrumentation micrometer = new MicrometerInstrumentation();
        public MongoInstrumentation mongo = new MongoInstrumentation();
        public RedisInstrumentation redis = new RedisInstrumentation();
        public SpringSchedulingInstrumentation springScheduling = new SpringSchedulingInstrumentation();
    }

    public static class CassandraInstrumentation {
        public boolean enabled = true;
    }

    public static class JdbcInstrumentation {
        public boolean enabled = true;
    }

    public static class JmsInstrumentation {
        public boolean enabled = true;
    }

    public static class KafkaInstrumentation {
        public boolean enabled = true;
    }

    public static class LoggingInstrumentation {
        public String level = "INFO";
    }

    public static class MicrometerInstrumentation {
        public boolean enabled = true;
        // this is just here to detect if using this old undocumented setting in order to give a helpful error message
        @Deprecated
        public int reportingIntervalSeconds = 60;
    }

    public static class MongoInstrumentation {
        public boolean enabled = true;
    }

    public static class RedisInstrumentation {
        public boolean enabled = true;
    }

    public static class SpringSchedulingInstrumentation {
        public boolean enabled = true;
    }

    public static class Heartbeat {
        public long intervalSeconds = MINUTES.toSeconds(15);
    }

    public static class Statsbeat {
        public String instrumentationKey = "c4a29126-a7cb-47e5-b348-11414998b11e"; //workspace-aistatsbeat
        public String endpoint = ConnectionString.Defaults.INGESTION_ENDPOINT; // this supports the government cloud
        public long intervalSeconds = MINUTES.toSeconds(15); // default to 15 minutes
        public long featureIntervalSeconds = DAYS.toSeconds(1); // default to daily
    }

    public static class Proxy {

        public String host;
        public int port = 80;
    }

    public static class PreviewConfiguration {

        public boolean developerMode;
        public SamplingPreview sampling = new SamplingPreview();
        public List<ProcessorConfig> processors = new ArrayList<>();
        public boolean openTelemetryApiSupport;
        public PreviewInstrumentation instrumentation = new PreviewInstrumentation();
        // applies to perf counters, default custom metrics, jmx metrics, and micrometer metrics
        // not sure if we'll be able to have different metric intervals in future OpenTelemetry metrics world,
        // so safer to only allow single interval for now
        public int metricIntervalSeconds = 60;
        // ignoreRemoteParentNotSampled is currently needed
        // because .NET SDK always propagates trace flags "00" (not sampled)
        public boolean ignoreRemoteParentNotSampled = true;
        // this is just here to detect if using this old setting in order to give a helpful message
        @Deprecated
        public boolean httpMethodInOperationName;
        public LiveMetrics liveMetrics = new LiveMetrics();

        public ProfilerConfiguration profiler = new ProfilerConfiguration();
        public GcEventConfiguration gcEvents = new GcEventConfiguration();
    }

    public static class InternalConfiguration {
        // This is used for collecting internal stats
        public Statsbeat statsbeat = new Statsbeat();
    }

    public static class PreviewInstrumentation {
        public DisabledByDefaultInstrumentation azureSdk = new DisabledByDefaultInstrumentation();
        public DisabledByDefaultInstrumentation javaHttpClient = new DisabledByDefaultInstrumentation();
        public DisabledByDefaultInstrumentation jaxws = new DisabledByDefaultInstrumentation();
        public DisabledByDefaultInstrumentation rabbitmq = new DisabledByDefaultInstrumentation();
    }

    public static class DisabledByDefaultInstrumentation {
        public boolean enabled;
    }

    public static class LiveMetrics {
        public boolean enabled = true;
    }

    public static class SelfDiagnostics {

        public String level = "info";
        public String destination = "file+console";
        public DestinationFile file = new DestinationFile();
    }

    public static class DestinationFile {

        private static final String DEFAULT_NAME = "applicationinsights.log";

        public String path = getDefaultPath();
        public int maxSizeMb = 5;
        public int maxHistory = 1;

        private static String getDefaultPath() {
            if (!DiagnosticsHelper.isRpIntegration()) {
                // this will be relative to the directory where agent jar is located
                return DEFAULT_NAME;
            }
            if (DiagnosticsHelper.useAppSvcRpIntegrationLogging()) {
                return StatusFile.getLogDir() + "/" + DEFAULT_NAME;
            }
            if (DiagnosticsHelper.useFunctionsRpIntegrationLogging() && !DiagnosticsHelper.isOsWindows()) {
                return "/var/log/applicationinsights/" + DEFAULT_NAME;
            }
            // azure spring cloud
            return DEFAULT_NAME;
        }
    }

    public static class SamplingOverride {
        // not using include/exclude, because you can still get exclude with this by adding a second (exclude) override above it
        // (since only the first matching override is used)
        public List<SamplingOverrideAttribute> attributes = new ArrayList<>();
        public Double percentage;
        public String id; // optional, used for debugging purposes only

        public void validate() throws FriendlyException {
            if (attributes.isEmpty()) {
                // TODO add doc and go link, similar to telemetry processors
                throw new FriendlyException("A sampling override configuration has no attributes.",
                        "Please provide one or more attributes for the sampling override configuration.");
            }
            if (percentage == null) {
                // TODO add doc and go link, similar to telemetry processors
                throw new FriendlyException("A sampling override configuration is missing a \"percentage\".",
                        "Please provide a \"percentage\" for the sampling override configuration.");
            }
            if (percentage < 0 || percentage > 100) {
                // TODO add doc and go link, similar to telemetry processors
                throw new FriendlyException("A sampling override configuration has a \"percentage\" that is not between 0 and 100.",
                        "Please provide a \"percentage\" that is between 0 and 100 for the sampling override configuration.");
            }
            for (SamplingOverrideAttribute attribute : attributes) {
                attribute.validate();
            }
        }
    }

    public static class SamplingOverrideAttribute {
        public String key;
        public String value;
        public MatchType matchType;

        private void validate() {
            if (isEmpty(key)) {
                // TODO add doc and go link, similar to telemetry processors
                throw new FriendlyException("A telemetry filter configuration has an attribute section that is missing a \"key\".",
                        "Please provide a \"key\" under the attribute section of the telemetry filter configuration.");
            }
            if (matchType == null) {
                throw new FriendlyException("A telemetry filter configuration has an attribute section that is missing a \"matchType\".",
                        "Please provide a \"matchType\" under the attribute section of the telemetry filter configuration.");
            }
            if (matchType == MatchType.regexp) {
                if (isEmpty(value)) {
                    // TODO add doc and go link, similar to telemetry processors
                    throw new FriendlyException("A telemetry filter configuration has an attribute with matchType regexp that is missing a \"value\".",
                            "Please provide a key under the attribute section of the filter configuration.");
                }
                validateRegex(value);
            }
        }

        private static void validateRegex(String value) throws FriendlyException {
            try {
                Pattern.compile(value);
            } catch (PatternSyntaxException exception) {
                // TODO add doc and go link, similar to telemetry processors
                throw new FriendlyException("A telemetry filter configuration has an invalid regex:" + value,
                        "Please provide a valid regex in the telemetry filter configuration.");
            }
        }
    }

    public static class ProcessorConfig {
        public ProcessorType type;
        public ProcessorIncludeExclude include;
        public ProcessorIncludeExclude exclude;
        public List<ProcessorAction> actions = new ArrayList<>(); // specific for processor type "attributes"
        public NameConfig name; // specific for processor types "log" and "span"
        public String id; // optional, used for debugging purposes only

        private static void isValidRegex(String value, ProcessorType processorType) throws FriendlyException {
            try {
                Pattern.compile(value);
            } catch (PatternSyntaxException exception) {
                // TODO different links for different processor types throughout?
                throw new FriendlyException(processorType.anX + " processor configuration has an invalid regex:" + value,
                        "Please provide a valid regex in the " + processorType + " processor configuration. " +
                                "Learn more about " + processorType + " processors here: https://go.microsoft.com/fwlink/?linkid=2151557");
            }
        }

        public void validate() throws FriendlyException {
            if (type == null) {
                throw new FriendlyException("A telemetry processor configuration is missing a \"type\".",
                        "Please provide a \"type\" in the telemetry processor configuration. " +
                                "Learn more about telemetry processors here: https://go.microsoft.com/fwlink/?linkid=2151557");
            }
            if (include != null) {
                include.validate(type, IncludeExclude.INCLUDE);
            }
            if (exclude != null) {
                exclude.validate(type, IncludeExclude.EXCLUDE);
            }
            validateAttributeProcessorConfig();
            validateLogOrSpanProcessorConfig();
        }

        public void validateAttributeProcessorConfig() throws FriendlyException {
            if (type == ProcessorType.attribute) {
                if (actions.isEmpty()) {
                    throw new FriendlyException("An attribute processor configuration has no actions.",
                            "Please provide at least one action in the attribute processor configuration. " +
                                    "Learn more about attribute processors here: https://go.microsoft.com/fwlink/?linkid=2151557");
                }
                // TODO validate name == null?
                for (ProcessorAction action : actions) {
                    action.validate();
                }
            }
        }

        public void validateLogOrSpanProcessorConfig() throws FriendlyException {
            if (type == ProcessorType.log || type == ProcessorType.span) {
                if (name == null) {
                    throw new FriendlyException(type.anX +  " processor configuration is missing a \"name\" section.",
                            "Please provide a \"name\" section in the " + type + " processor configuration. " +
                                    "Learn more about " + type + " processors here: https://go.microsoft.com/fwlink/?linkid=2151557");
                }
                // TODO validate actions.isEmpty()?
                name.validate(type);
            }
        }
    }

    public static class NameConfig {
        public List<String> fromAttributes = new ArrayList<>();
        public ToAttributeConfig toAttributes;
        public String separator;

        public void validate(ProcessorType processorType) throws FriendlyException {
            if (fromAttributes.isEmpty() && toAttributes == null) {
                // TODO different links for different processor types?
                throw new FriendlyException(processorType.anX + " processor configuration has \"name\" action with no \"fromAttributes\" and no \"toAttributes\".",
                        "Please provide at least one of \"fromAttributes\" or \"toAttributes\" under the name section of the " + processorType + " processor configuration. " +
                                "Learn more about " + processorType + " processors here: https://go.microsoft.com/fwlink/?linkid=2151557");
            }
            if (toAttributes != null) {
                toAttributes.validate(processorType);
            }
        }
    }

    public static class ToAttributeConfig {
        public List<String> rules = new ArrayList<>();

        public void validate(ProcessorType processorType) throws FriendlyException {
            if (rules.isEmpty()) {
                throw new FriendlyException(processorType.anX + " processor configuration has \"toAttributes\" section with no \"rules\".",
                        "Please provide at least one rule under the \"toAttributes\" section of the " + processorType + " processor configuration. " +
                                "Learn more about " + processorType + " processors here: https://go.microsoft.com/fwlink/?linkid=2151557");
            }
            for (String rule : rules) {
                ProcessorConfig.isValidRegex(rule, processorType);
            }
        }
    }

    private enum IncludeExclude {
        INCLUDE,
        EXCLUDE;

        public String toString() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    public static class ProcessorIncludeExclude {
        public MatchType matchType;
        public List<String> spanNames = new ArrayList<>();
        public List<String> logNames = new ArrayList<>();
        public List<ProcessorAttribute> attributes = new ArrayList<>();

        public void validate(ProcessorType processorType, IncludeExclude includeExclude) throws FriendlyException {
            if (matchType == null) {
                throw new FriendlyException(processorType.anX + " processor configuration has an " + includeExclude + " section that is missing a \"matchType\".",
                        "Please provide a \"matchType\" under the " + includeExclude + " section of the " + processorType + " processor configuration. " +
                                "Learn more about " + processorType + " processors here: https://go.microsoft.com/fwlink/?linkid=2151557");
            }
            for (ProcessorAttribute attribute : attributes) {
                if (isEmpty(attribute.key)) {
                    throw new FriendlyException(processorType.anX + " processor configuration has an " + includeExclude + " section that is missing a \"key\".",
                            "Please provide a \"key\" under the " + includeExclude + " section of the " + processorType + " processor configuration. " +
                                    "Learn more about " + processorType + " processors here: https://go.microsoft.com/fwlink/?linkid=2151557");
                }
                if (matchType == MatchType.regexp && attribute.value != null) {
                    ProcessorConfig.isValidRegex(attribute.value, processorType);
                }
            }

            switch (processorType) {
                case attribute:
                    validAttributeProcessorIncludeExclude(includeExclude);
                    break;
                case log:
                    validateLogProcessorIncludeExclude(includeExclude);
                    break;
                case span:
                    validateSpanProcessorIncludeExclude(includeExclude);
                    break;
                default:
                    throw new IllegalStateException("Unexpected processor type: " + processorType);
            }
        }

        private void validAttributeProcessorIncludeExclude(IncludeExclude includeExclude) throws FriendlyException {
            if (spanNames.isEmpty() && attributes.isEmpty()) {
                throw new FriendlyException("An attribute processor configuration has an " + includeExclude + " section with no \"spanNames\" and no \"attributes\".",
                        "Please provide at least one of \"spanNames\" or \"attributes\" under the " + includeExclude + " section of the attribute processor configuration. " +
                                "Learn more about attribute processors here: https://go.microsoft.com/fwlink/?linkid=2151557");
            }
            if (matchType == MatchType.regexp) {
                for (String spanName : spanNames) {
                    ProcessorConfig.isValidRegex(spanName, ProcessorType.attribute);
                }
            }
        }

        private void validateLogProcessorIncludeExclude(IncludeExclude includeExclude) throws FriendlyException {
            if (logNames.isEmpty() && attributes.isEmpty()) {
                throw new FriendlyException("A log processor configuration has an " + includeExclude + " section with no \"logNames\" and no \"attributes\".",
                        "Please provide at least one of \"logNames\" or \"attributes\" under the " + includeExclude + " section of the log processor configuration. " +
                                "Learn more about log processors here: https://go.microsoft.com/fwlink/?linkid=2151557");
            }
            if (matchType == MatchType.regexp) {
                for (String logName : logNames) {
                    ProcessorConfig.isValidRegex(logName, ProcessorType.log);
                }
            }
        }

        private void validateSpanProcessorIncludeExclude(IncludeExclude includeExclude) throws FriendlyException {
            if (spanNames.isEmpty() && attributes.isEmpty()) {
                throw new FriendlyException("A span processor configuration has " + includeExclude + " section with no \"spanNames\" and no \"attributes\".",
                        "Please provide at least one of \"spanNames\" or \"attributes\" under the " + includeExclude + " section of the span processor configuration. " +
                                "Learn more about span processors here: https://go.microsoft.com/fwlink/?linkid=2151557");
            }
            if (matchType == MatchType.regexp) {
                for (String spanName : spanNames) {
                    ProcessorConfig.isValidRegex(spanName, ProcessorType.span);
                }
            }
        }
    }

    public static class ProcessorAttribute {
        public String key;
        public String value;
    }

    public static class ExtractAttribute {
        public final Pattern pattern;
        public final List<String> groupNames;

        public ExtractAttribute(Pattern pattern, List<String> groupNames) {
            this.pattern = pattern;
            this.groupNames = groupNames;
        }

        // TODO: Handle empty patterns or groupNames are not populated gracefully
        public void validate() {
            if (groupNames.isEmpty()) {
                throw new FriendlyException("An attribute processor configuration does not have valid regex to extract attributes: " + pattern,
                        "Please provide a valid regex of the form (?<name>X) where X is the usual regular expression. " +
                                "Learn more about attribute processors here: https://go.microsoft.com/fwlink/?linkid=2151557");
            }
        }
    }

    public static class ProcessorAction {
        public String key;
        public ProcessorActionType action;
        public String value;
        public String fromAttribute;
        public ExtractAttribute extractAttribute;

        public void validate() throws FriendlyException {

            if (isEmpty(key)) {
                throw new FriendlyException("An attribute processor configuration has an action section that is missing a \"key\".",
                        "Please provide a \"key\" under the action section of the attribute processor configuration. " +
                                "Learn more about attribute processors here: https://go.microsoft.com/fwlink/?linkid=2151557");
            }
            if (action == null) {
                throw new FriendlyException("An attribute processor configuration has an action section that is missing an \"action\".",
                        "Please provide an \"action\" under the action section of the attribute processor configuration. " +
                                "Learn more about attribute processors here: https://go.microsoft.com/fwlink/?linkid=2151557");
            }
            if (action == ProcessorActionType.insert || action == ProcessorActionType.update) {
                if (isEmpty(value) && isEmpty(fromAttribute)) {
                    throw new FriendlyException("An attribute processor configuration has an " + action + " action that is missing a \"value\" or a \"fromAttribute\".",
                            "Please provide exactly one of \"value\" or \"fromAttributes\" under the " + action + " action. " +
                                    "Learn more about attribute processors here: https://go.microsoft.com/fwlink/?linkid=2151557");
                }
                if (!isEmpty(value) && !isEmpty(fromAttribute)) {
                    throw new FriendlyException("An attribute processor configuration has an " + action + " action that has both a \"value\" and a \"fromAttribute\".",
                            "Please provide exactly one of \"value\" or \"fromAttributes\" under the " + action + " action. " +
                                    "Learn more about attribute processors here: https://go.microsoft.com/fwlink/?linkid=2151557");
                }
                if (extractAttribute != null) {
                    throw new FriendlyException("An attribute processor configuration has an " + action + " action with an \"extractAttribute\" section.",
                            "Please do not provide an \"extractAttribute\" under the " + action + " action. " +
                                    "Learn more about attribute processors here: https://go.microsoft.com/fwlink/?linkid=2151557");
                }
            }

            if (action == ProcessorActionType.extract) {
                if (extractAttribute == null) {
                    throw new FriendlyException("An attribute processor configuration has an extract action that is missing an \"extractAttributes\" section.",
                            "Please provide an \"extractAttributes\" section under the extract action. " +
                                    "Learn more about attribute processors here: https://go.microsoft.com/fwlink/?linkid=2151557");
                }
                if (!isEmpty(value)) {
                    throw new FriendlyException("An attribute processor configuration has an " + action + " action with a \"value\".",
                            "Please do not provide a \"value\" under the " + action + " action. " +
                                    "Learn more about attribute processors here: https://go.microsoft.com/fwlink/?linkid=2151557");
                }
                if (!isEmpty(fromAttribute)) {
                    throw new FriendlyException("An attribute processor configuration has an " + action + " action with a \"fromAttribute\".",
                            "Please do not provide a \"fromAttribute\" under the " + action + " action. " +
                                    "Learn more about attribute processors here: https://go.microsoft.com/fwlink/?linkid=2151557");
                }
                extractAttribute.validate();
            }
        }
    }

    public static class ProcessorActionJson {
        public String key;
        public ProcessorActionType action;
        public String value;
        public String fromAttribute;
        public String pattern;
    }

    public static class ProfilerConfiguration {
        public int configPollPeriodSeconds = 60;
        public int periodicRecordingDurationSeconds = 120;
        public int periodicRecordingIntervalSeconds = 60 * 60;
        public String serviceProfilerFrontEndPoint = null;
        public boolean enabled = false;
        public String memoryTriggeredSettings = "profile";
        public String cpuTriggeredSettings = "profile";
    }

    public static class GcEventConfiguration {
        public GcReportingLevel reportingLevel = GcReportingLevel.TENURED_ONLY;
    }

    private static boolean isEmpty(String str) {
        return str == null || str.trim().isEmpty();
    }
}
