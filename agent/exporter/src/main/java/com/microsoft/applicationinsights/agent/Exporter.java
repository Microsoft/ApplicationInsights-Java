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
package com.microsoft.applicationinsights.agent;

import java.net.URI;
import java.net.URISyntaxException;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.azure.monitor.opentelemetry.exporter.implementation.models.*;
import com.microsoft.applicationinsights.TelemetryUtil;
import com.google.common.base.Joiner;
import com.google.common.base.Strings;
import com.microsoft.applicationinsights.TelemetryClient;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.SpanId;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.instrumentation.api.aisdk.AiAppId;
import io.opentelemetry.api.trace.TraceState;
import io.opentelemetry.sdk.common.CompletableResultCode;
import io.opentelemetry.sdk.trace.data.EventData;
import io.opentelemetry.sdk.trace.data.LinkData;
import io.opentelemetry.sdk.trace.data.SpanData;
import io.opentelemetry.sdk.trace.export.SpanExporter;
import io.opentelemetry.semconv.trace.attributes.SemanticAttributes;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static java.util.concurrent.TimeUnit.*;

public class Exporter implements SpanExporter {

    private static final Logger logger = LoggerFactory.getLogger(Exporter.class);

    private static final Pattern COMPONENT_PATTERN = Pattern
            .compile("io\\.opentelemetry\\.javaagent\\.([^0-9]*)(-[0-9.]*)?");

    private static final Set<String> SQL_DB_SYSTEMS;

    private static final Set<String> STANDARD_ATTRIBUTE_PREFIXES;

    static {
        Set<String> dbSystems = new HashSet<>();
        dbSystems.add("db2");
        dbSystems.add("derby");
        dbSystems.add("mariadb");
        dbSystems.add("mssql");
        dbSystems.add("mysql");
        dbSystems.add("oracle");
        dbSystems.add("postgresql");
        dbSystems.add("sqlite");
        dbSystems.add("other_sql");
        dbSystems.add("hsqldb");
        dbSystems.add("h2");

        SQL_DB_SYSTEMS = Collections.unmodifiableSet(dbSystems);

        // TODO need to keep this list in sync as new semantic conventions are defined
        // TODO make this opt-in for javaagent
        Set<String> standardAttributesPrefix = new HashSet<>();
        standardAttributesPrefix.add("http");
        standardAttributesPrefix.add("db");
        standardAttributesPrefix.add("message");
        standardAttributesPrefix.add("messaging");
        standardAttributesPrefix.add("rpc");
        standardAttributesPrefix.add("enduser");
        standardAttributesPrefix.add("net");
        standardAttributesPrefix.add("peer");
        standardAttributesPrefix.add("exception");
        standardAttributesPrefix.add("thread");
        standardAttributesPrefix.add("faas");

        STANDARD_ATTRIBUTE_PREFIXES = Collections.unmodifiableSet(standardAttributesPrefix);
    }

    private static final Joiner JOINER = Joiner.on(", ");

    private static final AttributeKey<Boolean> AI_LOG_KEY = AttributeKey.booleanKey("applicationinsights.internal.log");

    private static final AttributeKey<String> AI_SPAN_SOURCE_APP_ID_KEY = AttributeKey.stringKey(AiAppId.SPAN_SOURCE_APP_ID_ATTRIBUTE_NAME);
    private static final AttributeKey<String> AI_SPAN_TARGET_APP_ID_KEY = AttributeKey.stringKey(AiAppId.SPAN_TARGET_APP_ID_ATTRIBUTE_NAME);

    // this is only used by the 2.x web interop bridge
    // for ThreadContext.getRequestTelemetryContext().getRequestTelemetry().setSource()
    private static final AttributeKey<String> AI_SPAN_SOURCE_KEY = AttributeKey.stringKey("applicationinsights.internal.source");

    private static final AttributeKey<String> AI_LOG_LEVEL_KEY = AttributeKey.stringKey("applicationinsights.internal.log_level");
    private static final AttributeKey<String> AI_LOGGER_NAME_KEY = AttributeKey.stringKey("applicationinsights.internal.logger_name");
    private static final AttributeKey<String> AI_LOG_ERROR_STACK_KEY = AttributeKey.stringKey("applicationinsights.internal.log_error_stack");

    // note: this gets filtered out of user dimensions automatically since it shares official "peer." prefix
    // (even though it's not an official semantic convention attribute)
    private static final AttributeKey<String> AZURE_SDK_PEER_ADDRESS = AttributeKey.stringKey("peer.address");
    private static final AttributeKey<String> AZURE_SDK_MESSAGE_BUS_DESTINATION = AttributeKey.stringKey("message_bus.destination");

    private final TelemetryClient telemetryClient;

    public Exporter(TelemetryClient telemetryClient) {
        this.telemetryClient = telemetryClient;
    }

    @Override
    public CompletableResultCode export(Collection<SpanData> spans) {
        if (Strings.isNullOrEmpty(TelemetryClient.getActive().getInstrumentationKey())) {
            logger.debug("Instrumentation key is null or empty.");
            return CompletableResultCode.ofSuccess();
        }

        try {
            for (SpanData span : spans) {
                logger.debug("exporting span: {}", span);
                export(span);
            }
            // batching, retry, throttling, and writing to disk on failure occur downstream
            // for simplicity not reporting back success/failure from this layer
            // only that it was successfully delivered to the next layer
            return CompletableResultCode.ofSuccess();
        } catch (Throwable t) {
            logger.error(t.getMessage(), t);
            return CompletableResultCode.ofFailure();
        }
    }

    @Override
    public CompletableResultCode flush() {
        return CompletableResultCode.ofSuccess();
    }

    @Override
    public CompletableResultCode shutdown() {
        return CompletableResultCode.ofSuccess();
    }

    private void export(SpanData span) {
        SpanKind kind = span.getKind();
        String instrumentationName = span.getInstrumentationLibraryInfo().getName();
        Matcher matcher = COMPONENT_PATTERN.matcher(instrumentationName);
        String stdComponent = matcher.matches() ? matcher.group(1) : null;
        if (kind == SpanKind.INTERNAL) {
            Boolean isLog = span.getAttributes().get(AI_LOG_KEY);
            if (isLog != null && isLog) {
                exportLogSpan(span);
            } else if ("spring-scheduling".equals(stdComponent) && !span.getParentSpanContext().isValid()) {
                // TODO (trask) need semantic convention for determining whether to map INTERNAL to request or
                //  dependency (or need clarification to use SERVER for this)
                exportRequest(span);
            } else {
                exportRemoteDependency(span, true);
            }
        } else if (kind == SpanKind.CLIENT || kind == SpanKind.PRODUCER) {
            exportRemoteDependency(span, false);
        } else if (kind == SpanKind.CONSUMER && !span.getParentSpanContext().isRemote()
                && !span.getName().equals("EventHubs.process") && !span.getName().equals("ServiceBus.process")) {
            // earlier versions of the azure sdk opentelemetry shim did not set remote parent
            // see https://github.com/Azure/azure-sdk-for-java/pull/21667

            // TODO need spec clarification, but it seems polling for messages can be CONSUMER also
            //  in which case the span will not have a remote parent and should be treated as a dependency instead of a request
            exportRemoteDependency(span, false);
        } else if (kind == SpanKind.SERVER || kind == SpanKind.CONSUMER) {
            exportRequest(span);
        } else {
            throw new UnsupportedOperationException(kind.name());
        }
    }

    private static List<TelemetryExceptionDetails> minimalParse(String errorStack) {
        TelemetryExceptionDetails details = new TelemetryExceptionDetails();
        String line = errorStack.split(System.lineSeparator())[0];
        int index = line.indexOf(": ");

        if (index != -1) {
            details.setTypeName(line.substring(0, index));
            details.setMessage(line.substring(index + 2));
        } else {
            details.setTypeName(line);
        }
        // TODO (trask): map OpenTelemetry exception to Application Insights exception better
        details.setStack(errorStack);
        return Collections.singletonList(details);
    }

    private void exportRemoteDependency(SpanData span, boolean inProc) {
        TelemetryItem telemetry = new TelemetryItem();
        RemoteDependencyData data = new RemoteDependencyData();
        telemetryClient.initRemoteDependencyTelemetry(telemetry, data);

        addLinks(data, span.getLinks());
        data.setName(getTelemetryName(span));

        if (inProc) {
            data.setType("InProc");
        } else {
            applySemanticConventions(span, data);
        }

        data.setId(span.getSpanId());
        telemetry.getTags().put(ContextTagKeys.AI_OPERATION_ID.toString(), span.getTraceId());
        String parentSpanId = span.getParentSpanId();
        if (SpanId.isValid(parentSpanId)) {
            telemetry.getTags().put(ContextTagKeys.AI_OPERATION_PARENT_ID.toString(), parentSpanId);
        }

        telemetry.setTime(getFormattedTime(span.getStartEpochNanos()));
        data.setDuration(getFormattedDuration(span.getEndEpochNanos() - span.getStartEpochNanos()));

        data.setSuccess(span.getStatus().getStatusCode() != StatusCode.ERROR);

        setExtraAttributes(telemetry, data, span.getAttributes());

        float samplingPercentage = getSamplingPercentage(span.getSpanContext().getTraceState());
        telemetry.setSampleRate(samplingPercentage);
        telemetryClient.trackAsync(telemetry);
        exportEvents(span, samplingPercentage);
    }

    private static float getSamplingPercentage(TraceState traceState) {
        return TelemetryUtil.getSamplingPercentage(traceState, 100, true);
    }

    private void applySemanticConventions(SpanData span, RemoteDependencyData remoteDependencyData) {
        Attributes attributes = span.getAttributes();
        String httpMethod = attributes.get(SemanticAttributes.HTTP_METHOD);
        if (httpMethod != null) {
            applyHttpClientSpan(attributes, remoteDependencyData);
            return;
        }
        String rpcSystem = attributes.get(SemanticAttributes.RPC_SYSTEM);
        if (rpcSystem != null) {
            applyRpcClientSpan(attributes, remoteDependencyData, rpcSystem);
            return;
        }
        String dbSystem = attributes.get(SemanticAttributes.DB_SYSTEM);
        if (dbSystem != null) {
            applyDatabaseClientSpan(attributes, remoteDependencyData, dbSystem);
            return;
        }

        String messagingSystem = attributes.get(SemanticAttributes.MESSAGING_SYSTEM);
        if (messagingSystem != null) {
            applyMessagingClientSpan(attributes, remoteDependencyData, messagingSystem, span.getKind());
            return;
        }
        // TODO (trask) ideally EventHubs SDK should conform and fit the above path used for other messaging systems
        //  but no rush as messaging semantic conventions may still change
        //  https://github.com/Azure/azure-sdk-for-java/issues/21684
        String name = span.getName();
        if (name.equals("EventHubs.send") || name.equals("EventHubs.message")) {
            applyEventHubsSpan(attributes, remoteDependencyData);
            return;
        }
        // TODO (trask) ideally ServiceBus SDK should conform and fit the above path used for other messaging systems
        //  but no rush as messaging semantic conventions may still change
        //  https://github.com/Azure/azure-sdk-for-java/issues/21686
        if (name.equals("ServiceBus.message") || name.equals("ServiceBus.process")) {
            applyServiceBusSpan(attributes, remoteDependencyData);
            return;
        }
    }

    private void exportLogSpan(SpanData span) {
        String errorStack = span.getAttributes().get(AI_LOG_ERROR_STACK_KEY);
        if (errorStack == null) {
            trackTrace(span);
        } else {
            trackTraceAsException(span, errorStack);
        }
    }

    private void trackTrace(SpanData span) {
        Attributes attributes = span.getAttributes();
        String level = attributes.get(AI_LOG_LEVEL_KEY);
        String loggerName = attributes.get(AI_LOGGER_NAME_KEY);

        TelemetryItem telemetry = new TelemetryItem();
        MessageData data = new MessageData();
        telemetryClient.initMessageTelemetry(telemetry, data);

        data.setVersion(2);
        data.setSeverityLevel(toSeverityLevel(level));
        data.setMessage(span.getName());

        if (span.getParentSpanContext().isValid()) {
            telemetry.getTags().put(ContextTagKeys.AI_OPERATION_ID.toString(), span.getTraceId());
            telemetry.getTags().put(ContextTagKeys.AI_OPERATION_PARENT_ID.toString(), span.getParentSpanId());
        }

        setLoggerProperties(data, level, loggerName);
        setExtraAttributes(telemetry, data, attributes);
        telemetry.setTime(getFormattedTime(span.getStartEpochNanos()));

        float samplingPercentage = getSamplingPercentage(span.getSpanContext().getTraceState());
        telemetry.setSampleRate(samplingPercentage);
        telemetryClient.trackAsync(telemetry);
    }

    private void trackTraceAsException(SpanData span, String errorStack) {
        Attributes attributes = span.getAttributes();
        String level = attributes.get(AI_LOG_LEVEL_KEY);
        String loggerName = attributes.get(AI_LOGGER_NAME_KEY);

        TelemetryItem telemetry = new TelemetryItem();
        TelemetryExceptionData data = new TelemetryExceptionData();
        telemetryClient.initExceptionTelemetry(telemetry, data);

        if (span.getParentSpanContext().isValid()) {
            telemetry.getTags().put(ContextTagKeys.AI_OPERATION_ID.toString(), span.getTraceId());
            telemetry.getTags().put(ContextTagKeys.AI_OPERATION_PARENT_ID.toString(), span.getParentSpanId());
        }

        data.setExceptions(Exceptions.minimalParse(errorStack));
        data.setSeverityLevel(toSeverityLevel(level));
        TelemetryUtil.getProperties(data).put("Logger Message", span.getName());
        setLoggerProperties(data, level, loggerName);
        setExtraAttributes(telemetry, data, attributes);
        telemetry.setTime(getFormattedTime(span.getStartEpochNanos()));

        float samplingPercentage = getSamplingPercentage(span.getSpanContext().getTraceState());
        telemetry.setSampleRate(samplingPercentage);
        telemetryClient.trackAsync(telemetry);
    }

    private static void setLoggerProperties(MonitorDomain data, String level, String loggerName) {
        if (level != null) {
            // TODO are these needed? level is already reported as severityLevel, sourceType maybe needed for exception telemetry only?
            Map<String, String> properties = TelemetryUtil.getProperties(data);
            properties.put("SourceType", "Logger");
            properties.put("LoggingLevel", level);
        }
        if (loggerName != null) {
            TelemetryUtil.getProperties(data).put("LoggerName", loggerName);
        }
    }

    private static void applyHttpClientSpan(Attributes attributes, RemoteDependencyData telemetry) {

        // from the spec, at least one of the following sets of attributes is required:
        // * http.url
        // * http.scheme, http.host, http.target
        // * http.scheme, net.peer.name, net.peer.port, http.target
        // * http.scheme, net.peer.ip, net.peer.port, http.target
        String scheme = attributes.get(SemanticAttributes.HTTP_SCHEME);
        int defaultPort;
        if ("http".equals(scheme)) {
            defaultPort = 80;
        } else if ("https".equals(scheme)) {
            defaultPort = 443;
        } else {
            defaultPort = 0;
        }
        String target = getTargetFromPeerAttributes(attributes, defaultPort);
        if (target == null) {
            target = attributes.get(SemanticAttributes.HTTP_HOST);
        }
        String url = attributes.get(SemanticAttributes.HTTP_URL);
        if (target == null && url != null) {
            try {
                URI uri = new URI(url);
                target = uri.getHost();
                if (uri.getPort() != 80 && uri.getPort() != 443 && uri.getPort() != -1) {
                    target += ":" + uri.getPort();
                }
            } catch (URISyntaxException e) {
                // TODO (trask) "log once"
                logger.error(e.getMessage());
                logger.debug(e.getMessage(), e);
            }
        }
        if (target == null) {
            // this should not happen, just a failsafe
            target = "Http";
        }

        String targetAppId = attributes.get(AI_SPAN_TARGET_APP_ID_KEY);

        if (targetAppId == null || AiAppId.getAppId().equals(targetAppId)) {
            telemetry.setType("Http");
            telemetry.setTarget(target);
        } else {
            // using "Http (tracked component)" is important for dependencies that go cross-component (have an appId in their target field)
            // if you use just HTTP, Breeze will remove appid from the target
            // TODO (trask) remove this once confirmed by zakima that it is no longer needed
            telemetry.setType("Http (tracked component)");
            telemetry.setTarget(target + " | " + targetAppId);
        }

        Long httpStatusCode = attributes.get(SemanticAttributes.HTTP_STATUS_CODE);
        if (httpStatusCode != null) {
            telemetry.setResultCode(Long.toString(httpStatusCode));
        }

        telemetry.setData(url);
    }

    private static String getTargetFromPeerAttributes(Attributes attributes, int defaultPort) {
        String target = attributes.get(SemanticAttributes.PEER_SERVICE);
        if (target != null) {
            // do not append port if peer.service is provided
            return target;
        }
        target = attributes.get(SemanticAttributes.NET_PEER_NAME);
        if (target == null) {
            target = attributes.get(SemanticAttributes.NET_PEER_IP);
        }
        if (target == null) {
            return null;
        }
        // append net.peer.port to target
        Long port = attributes.get(SemanticAttributes.NET_PEER_PORT);
        if (port != null && port != defaultPort) {
            return target + ":" + port;
        }
        return target;
    }

    private static void applyRpcClientSpan(Attributes attributes, RemoteDependencyData telemetry, String rpcSystem) {
        telemetry.setType(rpcSystem);
        String target = getTargetFromPeerAttributes(attributes, 0);
        // not appending /rpc.service for now since that seems too fine-grained
        if (target == null) {
            target = rpcSystem;
        }
        telemetry.setTarget(target);
    }

    private static void applyDatabaseClientSpan(Attributes attributes, RemoteDependencyData telemetry, String dbSystem) {
        String dbStatement = attributes.get(SemanticAttributes.DB_STATEMENT);
        String type;
        if (SQL_DB_SYSTEMS.contains(dbSystem)) {
            type = "SQL";
            // keeping existing behavior that was release in 3.0.0 for now
            // not going with new jdbc instrumentation span name of "<db.operation> <db.name>.<db.sql.table>" for now
            // just in case this behavior is reversed due to spec:
            // "It is not recommended to attempt any client-side parsing of `db.statement` just to get these properties,
            // they should only be used if the library being instrumented already provides them."
            // also need to discuss with other AI language exporters
            //
            // if we go to shorter span name now, and it gets reverted, no way for customers to get the shorter name back
            // whereas if we go to shorter span name in future, and they still prefer more cardinality, they can get that
            // back using telemetry processor to copy db.statement into span name
            telemetry.setName(dbStatement);
        } else {
            type = dbSystem;
        }
        telemetry.setType(type);
        telemetry.setData(dbStatement);
        String target = nullAwareConcat(getTargetFromPeerAttributes(attributes, getDefaultPortForDbSystem(dbSystem)),
                attributes.get(SemanticAttributes.DB_NAME), "/");
        if (target == null) {
            target = dbSystem;
        }
        telemetry.setTarget(target);
    }

    private void applyMessagingClientSpan(Attributes attributes, RemoteDependencyData telemetry, String messagingSystem, SpanKind spanKind) {
        if (spanKind == SpanKind.PRODUCER) {
            telemetry.setType("Queue Message | " + messagingSystem);
        } else {
            // e.g. CONSUMER kind (without remote parent) and CLIENT kind
            telemetry.setType(messagingSystem);
        }
        String destination = attributes.get(SemanticAttributes.MESSAGING_DESTINATION);
        if (destination != null) {
            telemetry.setTarget(destination);
        } else {
            telemetry.setTarget(messagingSystem);
        }
    }

    // TODO (trask) ideally EventHubs SDK should conform and fit the above path used for other messaging systems
    //  but no rush as messaging semantic conventions may still change
    //  https://github.com/Azure/azure-sdk-for-java/issues/21684
    private void applyEventHubsSpan(Attributes attributes, RemoteDependencyData telemetry) {
        telemetry.setType("Microsoft.EventHub");
        String peerAddress = attributes.get(AZURE_SDK_PEER_ADDRESS);
        String destination = attributes.get(AZURE_SDK_MESSAGE_BUS_DESTINATION);
        telemetry.setTarget(peerAddress + "/" + destination);
    }

    // TODO (trask) ideally ServiceBus SDK should conform and fit the above path used for other messaging systems
    //  but no rush as messaging semantic conventions may still change
    //  https://github.com/Azure/azure-sdk-for-java/issues/21686
    private void applyServiceBusSpan(Attributes attributes, RemoteDependencyData telemetry) {
        telemetry.setType("AZURE SERVICE BUS");
        String peerAddress = attributes.get(AZURE_SDK_PEER_ADDRESS);
        String destination = attributes.get(AZURE_SDK_MESSAGE_BUS_DESTINATION);
        telemetry.setTarget(peerAddress + "/" + destination);
    }

    private static int getDefaultPortForDbSystem(String dbSystem) {
        switch (dbSystem) {
            // jdbc default ports are from io.opentelemetry.javaagent.instrumentation.jdbc.JdbcConnectionUrlParser
            // TODO make these ports constants (at least in JdbcConnectionUrlParser) so they can be used here
            case SemanticAttributes.DbSystemValues.MONGODB:
                return 27017;
            case SemanticAttributes.DbSystemValues.CASSANDRA:
                return 9042;
            case SemanticAttributes.DbSystemValues.REDIS:
                return 6379;
            case SemanticAttributes.DbSystemValues.MARIADB:
            case SemanticAttributes.DbSystemValues.MYSQL:
                return 3306;
            case SemanticAttributes.DbSystemValues.MSSQL:
                return 1433;
            case SemanticAttributes.DbSystemValues.DB2:
                return 50000;
            case SemanticAttributes.DbSystemValues.ORACLE:
                return 1521;
            case SemanticAttributes.DbSystemValues.H2:
                return 8082;
            case SemanticAttributes.DbSystemValues.DERBY:
                return 1527;
            case SemanticAttributes.DbSystemValues.POSTGRESQL:
                return 5432;
            default:
                return 0;
        }
    }

    private void exportRequest(SpanData span) {
        TelemetryItem telemetry = new TelemetryItem();
        RequestData data = new RequestData();
        telemetryClient.initRequestTelemetry(telemetry, data);

        String source = null;
        Attributes attributes = span.getAttributes();

        String sourceAppId = attributes.get(AI_SPAN_SOURCE_APP_ID_KEY);

        if (sourceAppId != null && !AiAppId.getAppId().equals(sourceAppId)) {
            source = sourceAppId;
        }
        if (source == null) {
            String messagingSystem = attributes.get(SemanticAttributes.MESSAGING_SYSTEM);
            if (messagingSystem != null) {
                // TODO (trask) should this pass default port for messaging.system?
                source = nullAwareConcat(getTargetFromPeerAttributes(attributes, 0),
                        attributes.get(SemanticAttributes.MESSAGING_DESTINATION), "/");
                if (source == null) {
                    source = messagingSystem;
                }
            }
        }
        if (source == null) {
            // this is only used by the 2.x web interop bridge
            // for ThreadContext.getRequestTelemetryContext().getRequestTelemetry().setSource()

            source = attributes.get(AI_SPAN_SOURCE_KEY);
        }
        data.setSource(source);

        addLinks(data, span.getLinks());
        Long httpStatusCode = attributes.get(SemanticAttributes.HTTP_STATUS_CODE);
        if (httpStatusCode != null) {
            data.setResponseCode(Long.toString(httpStatusCode));
        } else {
            // TODO (trask) what should the default value be?
            data.setResponseCode("200");
        }

        String httpUrl = attributes.get(SemanticAttributes.HTTP_URL);
        if (httpUrl != null) {
            data.setUrl(httpUrl);
        }

        String name = getTelemetryName(span);
        data.setName(name);
        telemetry.getTags().put(ContextTagKeys.AI_OPERATION_NAME.toString(), name);
        data.setId(span.getSpanId());
        telemetry.getTags().put(ContextTagKeys.AI_OPERATION_ID.toString(), span.getTraceId());

        String locationIp = attributes.get(SemanticAttributes.HTTP_CLIENT_IP);
        if (locationIp == null) {
            // only use net.peer.ip if http.client_ip is not available
            locationIp = attributes.get(SemanticAttributes.NET_PEER_IP);
        }
        if (locationIp != null) {
            telemetry.getTags().put(ContextTagKeys.AI_LOCATION_IP.toString(), locationIp);
        }

        String aiLegacyParentId = span.getSpanContext().getTraceState().get("ai-legacy-parent-id");
        if (aiLegacyParentId != null) {
            // see behavior specified at https://github.com/microsoft/ApplicationInsights-Java/issues/1174
            telemetry.getTags().put(ContextTagKeys.AI_OPERATION_PARENT_ID.toString(), aiLegacyParentId);
            String aiLegacyOperationId = span.getSpanContext().getTraceState().get("ai-legacy-operation-id");
            if (aiLegacyOperationId != null) {
                telemetry.getTags().putIfAbsent("ai_legacyRootID", aiLegacyOperationId);
            }
        } else {
            String parentSpanId = span.getParentSpanId();
            if (SpanId.isValid(parentSpanId)) {
                telemetry.getTags().put(ContextTagKeys.AI_OPERATION_PARENT_ID.toString(), parentSpanId);
            }
        }

        long startEpochNanos = span.getStartEpochNanos();
        telemetry.setTime(getFormattedTime(startEpochNanos));

        data.setDuration(getFormattedDuration(span.getEndEpochNanos() - startEpochNanos));

        data.setSuccess(span.getStatus().getStatusCode() != StatusCode.ERROR);

        setExtraAttributes(telemetry, data, attributes);

        float samplingPercentage = getSamplingPercentage(span.getSpanContext().getTraceState());
        telemetry.setSampleRate(samplingPercentage);
        telemetryClient.trackAsync(telemetry);
        exportEvents(span, samplingPercentage);
    }

    private String getTelemetryName(SpanData span) {
        String name = span.getName();
        if (!name.startsWith("/")) {
            return name;
        }
        String httpMethod = span.getAttributes().get(SemanticAttributes.HTTP_METHOD);
        if (Strings.isNullOrEmpty(httpMethod)) {
            return name;
        }
        return httpMethod + " " + name;
    }

    private static String nullAwareConcat(String str1, String str2, String separator) {
        if (str1 == null) {
            return str2;
        }
        if (str2 == null) {
            return str1;
        }
        return str1 + separator + str2;
    }

    private void exportEvents(SpanData span, float samplingPercentage) {
        for (EventData event : span.getEvents()) {
            boolean lettuce51 =
                    span.getInstrumentationLibraryInfo().getName().equals("io.opentelemetry.javaagent.lettuce-5.1");
            if (lettuce51 && event.getName().startsWith("redis.encode.")) {
                // special case as these are noisy and come from the underlying library itself
                continue;
            }

            TelemetryItem telemetry = new TelemetryItem();
            TelemetryEventData data = new TelemetryEventData();
            telemetryClient.initEventTelemetry(telemetry, data);

            String operationId = span.getTraceId();
            telemetry.getTags().put(ContextTagKeys.AI_OPERATION_ID.toString(), operationId);
            telemetry.getTags().put(ContextTagKeys.AI_OPERATION_PARENT_ID.toString(), span.getSpanId());
            telemetry.setTime(getFormattedTime(event.getEpochNanos()));
            setExtraAttributes(telemetry, data, event.getAttributes());

            if (event.getAttributes().get(SemanticAttributes.EXCEPTION_TYPE) != null
                    || event.getAttributes().get(SemanticAttributes.EXCEPTION_MESSAGE) != null) {
                // TODO map OpenTelemetry exception to Application Insights exception better
                String stacktrace = event.getAttributes().get(SemanticAttributes.EXCEPTION_STACKTRACE);
                if (stacktrace != null) {
                    trackException(stacktrace, span, operationId, span.getSpanId(), samplingPercentage);
                }
            } else {
                telemetry.setSampleRate(samplingPercentage);
                telemetryClient.trackAsync(telemetry);
            }
        }
    }

    private void trackException(String errorStack, SpanData span, String operationId,
                                String id, float samplingPercentage) {
        TelemetryItem telemetry = new TelemetryItem();
        TelemetryExceptionData data = new TelemetryExceptionData();
        telemetryClient.initExceptionTelemetry(telemetry, data);

        telemetry.getTags().put(ContextTagKeys.AI_OPERATION_ID.toString(), operationId);
        telemetry.getTags().put(ContextTagKeys.AI_OPERATION_PARENT_ID.toString(), id);
        telemetry.setTime(getFormattedTime(span.getEndEpochNanos()));
        telemetry.setSampleRate(samplingPercentage);
        data.setExceptions(minimalParse(errorStack));
        telemetryClient.trackAsync(telemetry);
    }

    private static final long NANOSECONDS_PER_DAY = DAYS.toNanos(1);
    private static final long NANOSECONDS_PER_HOUR = HOURS.toNanos(1);
    private static final long NANOSECONDS_PER_MINUTE = MINUTES.toNanos(1);
    private static final long NANOSECONDS_PER_SECOND = SECONDS.toNanos(1);

    public static String getFormattedDuration(long durationNanos) {
        long remainingNanos = durationNanos;

        long days = remainingNanos / NANOSECONDS_PER_DAY;
        remainingNanos = remainingNanos % NANOSECONDS_PER_DAY;

        long hours = remainingNanos / NANOSECONDS_PER_HOUR;
        remainingNanos = remainingNanos % NANOSECONDS_PER_HOUR;

        long minutes = remainingNanos / NANOSECONDS_PER_MINUTE;
        remainingNanos = remainingNanos % NANOSECONDS_PER_MINUTE;

        long seconds = remainingNanos / NANOSECONDS_PER_SECOND;
        remainingNanos = remainingNanos % NANOSECONDS_PER_SECOND;

        // FIXME (trask) is min two digits really required by breeze?
        StringBuilder sb = new StringBuilder();
        appendMinTwoDigits(sb, days);
        sb.append('.');
        appendMinTwoDigits(sb, hours);
        sb.append(':');
        appendMinTwoDigits(sb, minutes);
        sb.append(':');
        appendMinTwoDigits(sb, seconds);
        sb.append('.');
        appendMinSixDigits(sb, NANOSECONDS.toMicros(remainingNanos));

        return sb.toString();
    }

    private static void appendMinTwoDigits(StringBuilder sb, long value) {
        if (value < 10) {
            sb.append("0");
        }
        sb.append(value);
    }

    private static void appendMinSixDigits(StringBuilder sb, long value) {
        sb.append(String.format("%06d", value));
    }

    private static String getFormattedTime(long epochNanos) {
        return Instant.ofEpochMilli(NANOSECONDS.toMillis(epochNanos))
            .atOffset(ZoneOffset.UTC)
            .format(DateTimeFormatter.ISO_DATE_TIME);
    }

    private static void addLinks(MonitorDomain data, List<LinkData> links) {
        if (links.isEmpty()) {
            return;
        }
        StringBuilder sb = new StringBuilder();
        sb.append("[");
        boolean first = true;
        for (LinkData link : links) {
            if (!first) {
                sb.append(",");
            }
            sb.append("{\"operation_Id\":\"");
            sb.append(link.getSpanContext().getTraceId());
            sb.append("\",\"id\":\"");
            sb.append(link.getSpanContext().getSpanId());
            sb.append("\"}");
            first = false;
        }
        sb.append("]");
        TelemetryUtil.getProperties(data).put("_MS.links", sb.toString());
    }

    private static String getStringValue(AttributeKey<?> attributeKey, Object value) {
        switch (attributeKey.getType()) {
            case STRING:
            case BOOLEAN:
            case LONG:
            case DOUBLE:
                return String.valueOf(value);
            case STRING_ARRAY:
            case BOOLEAN_ARRAY:
            case LONG_ARRAY:
            case DOUBLE_ARRAY:
                return JOINER.join((List<?>) value);
            default:
                logger.warn("unexpected attribute type: {}", attributeKey.getType());
                return null;
        }
    }

    private static void setExtraAttributes(TelemetryItem telemetry, MonitorDomain data,
                                           Attributes attributes) {
        attributes.forEach((key, value) -> {
            String stringKey = key.getKey();
            if (stringKey.startsWith("applicationinsights.internal.")) {
                return;
            }
            // TODO use az.namespace for something?
            if (stringKey.equals(AZURE_SDK_MESSAGE_BUS_DESTINATION.getKey())
                    || stringKey.equals("az.namespace")) {
                // these are from azure SDK
                return;
            }
            // special case mappings
            if (key.equals(SemanticAttributes.ENDUSER_ID) && value instanceof String) {
                telemetry.getTags().put(ContextTagKeys.AI_USER_ID.toString(), (String) value);
                return;
            }
            if (key.equals(SemanticAttributes.HTTP_USER_AGENT) && value instanceof String) {
                telemetry.getTags().put("ai.user.userAgent", (String) value);
                return;
            }
            if (stringKey.equals("ai.preview.instrumentation_key") && value instanceof String) {
                telemetry.setInstrumentationKey((String) value);
                return;
            }
            if (stringKey.equals("ai.preview.service_name") && value instanceof String) {
                telemetry.getTags().put(ContextTagKeys.AI_CLOUD_ROLE.toString(), (String) value);
                return;
            }
            if (stringKey.equals("ai.preview.service_instance_id") && value instanceof String) {
                telemetry.getTags().put(ContextTagKeys.AI_CLOUD_ROLE_INSTANCE.toString(), (String) value);
                return;
            }
            if (stringKey.equals("ai.preview.service_version") && value instanceof String) {
                telemetry.getTags().put(ContextTagKeys.AI_APPLICATION_VER.toString(), (String) value);
                return;
            }
            int index = stringKey.indexOf(".");
            String prefix = index == -1 ? stringKey : stringKey.substring(0, index);
            if (STANDARD_ATTRIBUTE_PREFIXES.contains(prefix)) {
                return;
            }
            String val = getStringValue(key, value);
            if (value != null) {
                TelemetryUtil.getProperties(data).put(key.getKey(), val);
            }
        });
    }

    private static SeverityLevel toSeverityLevel(String level) {
        if (level == null) {
            return null;
        }
        switch (level) {
            case "FATAL":
                return SeverityLevel.CRITICAL;
            case "ERROR":
            case "SEVERE":
                return SeverityLevel.ERROR;
            case "WARN":
            case "WARNING":
                return SeverityLevel.WARNING;
            case "INFO":
                return SeverityLevel.INFORMATION;
            case "DEBUG":
            case "TRACE":
            case "CONFIG":
            case "FINE":
            case "FINER":
            case "FINEST":
            case "ALL":
                return SeverityLevel.VERBOSE;
            default:
                // TODO (trask) is this good fallback?
                logger.debug("Unexpected level {}, using VERBOSE level as default", level);
                return SeverityLevel.VERBOSE;
        }
    }
}
