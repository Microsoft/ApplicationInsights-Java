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

package com.microsoft.applicationinsights.agent.internal.model;

import java.net.MalformedURLException;
import java.text.ParseException;
import java.util.Date;
import java.util.List;
import java.util.Map;

import com.google.common.base.Splitter;
import com.microsoft.applicationinsights.TelemetryClient;
import com.microsoft.applicationinsights.agent.internal.utils.Global;
import com.microsoft.applicationinsights.extensibility.context.CloudContext;
import com.microsoft.applicationinsights.extensibility.context.UserContext;
import com.microsoft.applicationinsights.internal.util.DateTimeUtils;
import com.microsoft.applicationinsights.telemetry.Duration;
import com.microsoft.applicationinsights.telemetry.ExceptionTelemetry;
import com.microsoft.applicationinsights.telemetry.RequestTelemetry;
import com.microsoft.applicationinsights.telemetry.TelemetryContext;
import com.microsoft.applicationinsights.web.internal.ThreadContext;
import com.microsoft.applicationinsights.web.internal.correlation.TelemetryCorrelationUtilsCore;
import com.microsoft.applicationinsights.web.internal.correlation.TelemetryCorrelationUtilsCore.ResponseHeaderSetter;
import com.microsoft.applicationinsights.web.internal.correlation.TraceContextCorrelationCore;
import org.checkerframework.checker.nullness.qual.MonotonicNonNull;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.glowroot.xyzzy.engine.bytecode.api.ThreadContextThreadLocal;
import org.glowroot.xyzzy.engine.impl.NopTransactionService;
import org.glowroot.xyzzy.instrumentation.api.Getter;
import org.glowroot.xyzzy.instrumentation.api.MessageSupplier;
import org.glowroot.xyzzy.instrumentation.api.Setter;
import org.glowroot.xyzzy.instrumentation.api.Span;
import org.glowroot.xyzzy.instrumentation.api.ThreadContext.ServletRequestInfo;
import org.glowroot.xyzzy.instrumentation.api.Timer;
import org.glowroot.xyzzy.instrumentation.api.internal.ReadableMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static com.google.common.base.Preconditions.checkNotNull;

// currently only for "Web" transactions
public class IncomingSpanImpl implements Span {

    private static final Logger logger = LoggerFactory.getLogger(IncomingSpanImpl.class);

    private static final Splitter cookieSplitter = Splitter.on('|');

    private final MessageSupplier messageSupplier;
    private final ThreadContextThreadLocal.Holder threadContextHolder;
    private final long startTimeMillis;

    private volatile @MonotonicNonNull ServletRequestInfo servletRequestInfo;

    private volatile @MonotonicNonNull Throwable exception;

    private volatile @MonotonicNonNull TwoPartCompletion asyncCompletion;

    private final RequestTelemetry requestTelemetry;

    public IncomingSpanImpl(MessageSupplier messageSupplier, ThreadContextThreadLocal.Holder threadContextHolder,
                            long startTimeMillis, RequestTelemetry requestTelemetry) {
        this.messageSupplier = messageSupplier;
        this.threadContextHolder = threadContextHolder;
        this.startTimeMillis = startTimeMillis;
        this.requestTelemetry = requestTelemetry;
    }

    @Nullable
    ServletRequestInfo getServletRequestInfo() {
        return servletRequestInfo;
    }

    void setServletRequestInfo(ServletRequestInfo servletRequestInfo) {
        this.servletRequestInfo = servletRequestInfo;

        // guaranteed to have telemetry client at this point (see check in AgentImpl.startIncomingSpan())
        TelemetryClient telemetryClient = checkNotNull(Global.getTelemetryClient());

        CloudContext cloud = telemetryClient.getContext().getCloud();
        if (cloud.getRole() == null) {
            // hasn't been set yet
            String contextPath = servletRequestInfo.getContextPath();
            if (!contextPath.isEmpty()) {
                cloud.setRole(contextPath.substring(1));
            }
        }
        // TODO this won't be needed once xyzzy servlet instrumentation passes in METHOD as part of transactionName
        requestTelemetry.setName(servletRequestInfo.getMethod() + " " + servletRequestInfo.getUri());
    }

    void setAsync() {
        asyncCompletion = new TwoPartCompletion();
    }

    void setAsyncComplete() {
        checkNotNull(asyncCompletion);
        if (asyncCompletion.setPart1()) {
            send();
        }
    }

    void setTransactionName(String transactionName) {
        String tn = transactionName.replace('#', '/');
        if (servletRequestInfo != null) {
            tn = servletRequestInfo.getMethod() + " " + tn;
        }
        requestTelemetry.setName(tn);
    }

    void setException(Throwable t) {
        if (exception != null) {
            exception = t;
        }
    }

    @Override
    public void end() {
        endInternal();
    }

    @Override
    public void endWithLocationStackTrace(long thresholdNanos) {
        endInternal();
    }

    @Override
    public void endWithError(Throwable t) {
        exception = t;
        endInternal();
    }

    @Override
    public Timer extend() {
        // extend() shouldn't be called on incoming span
        return NopTransactionService.TIMER;
    }

    @Override
    public Object getMessageSupplier() {
        return messageSupplier;
    }

    @Override
    @Deprecated
    public <R> void propagateToResponse(R response, Setter<R> setter) {
        if (Global.isOutboundW3CEnabled) {
            // TODO eliminate wrapper object instantiation
            TraceContextCorrelationCore.resolveCorrelationForResponse(response,
                    new ResponseHeaderSetterImpl<>(setter));
        } else {
            // TODO eliminate wrapper object instantiation
            TelemetryCorrelationUtilsCore.resolveCorrelationForResponse(response,
                    new ResponseHeaderSetterImpl<>(setter));
        }
    }

    @Override
    @Deprecated
    public <R> void extractFromResponse(R response, Getter<R> getter) {
    }

    private void endInternal() {
        threadContextHolder.set(null);
        if (asyncCompletion == null || asyncCompletion.setPart2()) {
            send();
        }
        // need to wait to clear thread local until after client.track() is called, since some telemetry initializers
        // (e.g. WebOperationNameTelemetryInitializer) use it
        ThreadContext.setRequestTelemetryContext(null);
    }

    private void send() {
        long endTimeMillis = System.currentTimeMillis();

        // guaranteed to have telemetry client at this point (see check in AgentImpl.startIncomingSpan())
        TelemetryClient telemetryClient = checkNotNull(Global.getTelemetryClient());

        if (exception != null) {
            telemetryClient.track(toExceptionTelemetry(endTimeMillis, requestTelemetry.getContext()));
        }
        finishBuildingTelemetry(endTimeMillis);
        telemetryClient.track(requestTelemetry);
    }

    private void finishBuildingTelemetry(long endTimeMillis) {

        ReadableMessage message = (ReadableMessage) messageSupplier.get();
        Map<String, ?> detail = message.getDetail();

        requestTelemetry.setDuration(new Duration(endTimeMillis - startTimeMillis));

        try {
            requestTelemetry.setUrl(removeSessionIdFromUri(getUrl(detail)));
        } catch (MalformedURLException e) {
            logger.error(e.getMessage());
            logger.debug(e.getMessage(), e);
        }

        Integer responseCode = (Integer) detail.get("Response code");
        if (responseCode != null) {
            requestTelemetry.setResponseCode(Integer.toString(responseCode));
            // TODO base this on exception presence?
            requestTelemetry.setSuccess(responseCode < 400);
        }

        processCookies(detail);
    }

    private ExceptionTelemetry toExceptionTelemetry(long endTimeMillis, TelemetryContext telemetryContext) {
        ExceptionTelemetry exceptionTelemetry = new ExceptionTelemetry(exception);
        exceptionTelemetry.setTimestamp(new Date(endTimeMillis));
        TelemetryContext context = exceptionTelemetry.getContext();
        context.initialize(telemetryContext);
        return exceptionTelemetry;
    }

    private void processCookies(Map<String, ?> detail) {
        Map<String, String> cookies = (Map<String, String>) detail.get("Request cookies");
        if (cookies == null) {
            return;
        }
        // cookie names are case sensitive
        String aiUser = cookies.get("ai_user");
        if (aiUser != null) {
            processAiUserCookie(aiUser);
        }
        String aiSession = cookies.get("ai_session");
        if (aiSession != null) {
            processAiSessionCookie(aiSession);
        }
    }

    private void processAiUserCookie(String aiUser) {
        List<String> split = cookieSplitter.splitToList(aiUser);
        if (split.size() < 2) {
            logger.warn("ai_user cookie is not in the correct format: {}", aiUser);
        }
        String userId = split.get(0);
        Date acquisitionDate;
        try {
            acquisitionDate = DateTimeUtils.parseRoundTripDateString(split.get(1));
        } catch (ParseException e) {
            logger.warn("could not parse ai_user cookie: {}", aiUser);
            return;
        }
        UserContext userContext = requestTelemetry.getContext().getUser();
        userContext.setId(userId);
        userContext.setAcquisitionDate(acquisitionDate);
    }

    private void processAiSessionCookie(String aiSession) {
        List<String> split = cookieSplitter.splitToList(aiSession);
        String sessionId = split.get(0);
        requestTelemetry.getContext().getSession().setId(sessionId);
    }

    private static String getUrl(Map<String, ?> detail) {
        String scheme = (String) detail.get("Request scheme");
        String host = (String) detail.get("Request server hostname");
        Integer port = (Integer) detail.get("Request server port");
        String uri = (String) detail.get("Request uri");
        String query = (String) detail.get("Request query string");

        StringBuilder sb = new StringBuilder();
        sb.append(scheme);
        sb.append("://");
        sb.append(host);
        sb.append(":");
        sb.append(port);
        sb.append(uri);
        if (query != null) {
            sb.append("?");
            sb.append(query);
        }
        return sb.toString();
    }

    private static String removeSessionIdFromUri(String uri) {
        int separatorIndex = uri.indexOf(';');
        if (separatorIndex != -1) {
            return uri.substring(0, separatorIndex);
        }
        return uri;
    }

    private static class ResponseHeaderSetterImpl<Res> implements ResponseHeaderSetter<Res> {

        private final Setter<Res> setter;

        private ResponseHeaderSetterImpl(Setter<Res> setter) {
            this.setter = setter;
        }

        @Override
        public boolean containsHeader(Res response, String name) {
            return false;
        }

        @Override
        public void addHeader(Res response, String name, String value) {
            setter.put(response, name, value);
        }
    }
}
