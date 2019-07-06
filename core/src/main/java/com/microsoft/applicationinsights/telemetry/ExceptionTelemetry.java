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

package com.microsoft.applicationinsights.telemetry;

import com.google.common.base.Strings;
import com.microsoft.applicationinsights.internal.schemav2.ExceptionData;
import com.microsoft.applicationinsights.internal.schemav2.ExceptionDetails;
import com.microsoft.applicationinsights.internal.schemav2.StackFrame;
import com.microsoft.applicationinsights.internal.util.Sanitizer;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentMap;

/**
 * Telemetry type used to track exceptions sent to Azure Application Insights.
 */
public final class ExceptionTelemetry extends BaseSampleSourceTelemetry<ExceptionData> {

    private Double samplingPercentage;
    private final ExceptionData data;
    private Throwable throwable;

    /**
     * Envelope Name for this telemetry.
     */
    public static final String ENVELOPE_NAME = "Exception";


    /**
     * Base Type for this telemetry.
     */
    public static final String BASE_TYPE = "ExceptionData";


    public ExceptionTelemetry() {
        super();
        data = new ExceptionData();
        initialize(data.getProperties());
    }

    public ExceptionTelemetry(Throwable exception) {
        this(exception, null);
    }

    /**
     * Initializes a new instance.
     * @param maxStackSize The max stack size to report.
     * @param throwable The throwable to track.
     */
    public ExceptionTelemetry(Throwable throwable, int maxStackSize) {
        this(throwable, new ExceptionTelemetryOptions(maxStackSize, null));
    }

    /**
     * Initializes a new instance.
     *
     * @param options   restrictions in throwable size to report
     * @param throwable The throwable to track.
     */
    public ExceptionTelemetry(Throwable throwable, ExceptionTelemetryOptions options) {
        this();
        updateException(throwable, options != null ? options : ExceptionTelemetryOptions.empty());
    }

    @Override
    public int getVer() {
        return getData().getVer();
    }

    public Exception getException() {
        return throwable instanceof Exception ? (Exception) throwable : null;
    }

    public Throwable getThrowable() {
        return throwable;
    }

    public void setException(Throwable throwable) {
        setException(throwable, null);
    }

    public void setException(Throwable throwable, Integer maxStackSize) {
        updateException(throwable, new ExceptionTelemetryOptions(maxStackSize, null));
    }

    /**
     * @return The value indicating the exception
     * @deprecated Gets the value indicated where the exception was handled.
     */
    @Deprecated
    public ExceptionHandledAt getExceptionHandledAt() {
        return ExceptionHandledAt.Unhandled;
    }

    /**
     * @param value The value indicating the exception
     * @deprecated Sets the value indicated where the exception was handled.
     */
    @Deprecated
    public void setExceptionHandledAt(ExceptionHandledAt value) {

    }

    /**
     * Gets a map of application-defined exception metrics.
     * The metrics appear along with the exception in Analytics, but under Custom Metrics in Metrics Explorer.
     *
     * @return The map of metrics
     */
    public ConcurrentMap<String, Double> getMetrics() {
        return data.getMeasurements();
    }

    public void setSeverityLevel(SeverityLevel severityLevel) {
        data.setSeverityLevel(severityLevel == null ? null : com.microsoft.applicationinsights.internal.schemav2.SeverityLevel.values()[severityLevel.getValue()]);
    }

    public SeverityLevel getSeverityLevel() {
        return data.getSeverityLevel() == null ? null : SeverityLevel.values()[data.getSeverityLevel().getValue()];
    }

    @Override
    public Double getSamplingPercentage() {
        return samplingPercentage;
    }

    @Override
    public void setSamplingPercentage(Double samplingPercentage) {
        this.samplingPercentage = samplingPercentage;
    }

    @Deprecated
    @Override
    protected void additionalSanitize() {
        Sanitizer.sanitizeMeasurements(this.getMetrics());
    }

    @Override
    protected ExceptionData getData() {
        return data;
    }

    public List<ExceptionDetails> getExceptions() {
        return data.getExceptions();
    }

    private void updateException(Throwable throwable, ExceptionTelemetryOptions options) {
        this.throwable = throwable;
        ArrayList<ExceptionDetails> exceptions = new ArrayList<ExceptionDetails>();
        convertExceptionTree(throwable, null, exceptions, options.getMaxStackSize(), options.getMaxExceptionTraceLength());

        data.setExceptions(exceptions);
    }

    private static void convertExceptionTree(Throwable exception, ExceptionDetails parentExceptionDetails,
                                             List<ExceptionDetails> exceptions,
                                             int maxStackSize,
                                             int maxExceptionTraceLength) {
        if (exception == null) {
            exception = new Exception("");
        }

        if (maxStackSize == 0) {
            return;
        }

        ExceptionDetails exceptionDetails = createWithStackInfo(exception, parentExceptionDetails, maxExceptionTraceLength);
        exceptions.add(exceptionDetails);

        if (exception.getCause() != null) {
            convertExceptionTree(exception.getCause(), exceptionDetails, exceptions, maxStackSize - 1, maxExceptionTraceLength);
        }
    }

    private static ExceptionDetails createWithStackInfo(Throwable exception,
                                                        ExceptionDetails parentExceptionDetails,
                                                        int maxExceptionTraceLength) {
        if (exception == null) {
            throw new IllegalArgumentException("exception cannot be null");
        }

        ExceptionDetails exceptionDetails = new ExceptionDetails();
        exceptionDetails.setId(exception.hashCode());
        exceptionDetails.setTypeName(exception.getClass().getName());

        String exceptionMessage = exception.getMessage();
        if (Strings.isNullOrEmpty(exceptionMessage)) {
            exceptionMessage = exception.getClass().getName();
        }
        exceptionDetails.setMessage(exceptionMessage);

        if (parentExceptionDetails != null) {
            exceptionDetails.setOuterId(parentExceptionDetails.getId());
        }

        StackTraceElement[] trace = exception.getStackTrace();

        if (trace != null && trace.length > 0) {
            List<StackFrame> stack = exceptionDetails.getParsedStack();

            // We need to present the stack trace in reverse order.

            int length = Math.min(trace.length, maxExceptionTraceLength);
            for (int idx = 0; idx < length; idx++) {
                StackTraceElement elem = trace[idx];

                if (elem.isNativeMethod()) {
                    continue;
                }

                String className = elem.getClassName();

                StackFrame frame = new StackFrame();
                frame.setLevel(idx);
                frame.setFileName(elem.getFileName());
                frame.setLine(elem.getLineNumber());

                if (!Strings.isNullOrEmpty(className)) {
                    frame.setMethod(elem.getClassName() + "." + elem.getMethodName());
                } else {
                    frame.setMethod(elem.getMethodName());
                }

                stack.add(frame);
            }

            exceptionDetails.setHasFullStack(true); // TODO: sanitize and trim exception stack trace.
        }

        return exceptionDetails;
    }

    @Override
    public String getEnvelopName() {
        return ENVELOPE_NAME;
    }

    @Override
    public String getBaseTypeName() {
        return BASE_TYPE;
    }

    public String getProblemId() {
        return getData().getProblemId();
    }
}
