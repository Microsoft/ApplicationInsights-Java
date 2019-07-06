package com.microsoft.applicationinsights.telemetry;

/**
 * Provides maxStackSize and maxExceptionTraceLength for exception truncation.
 * <p/>
 * Default values: Integer.MAX
 */
public class ExceptionTelemetryOptions {

    public static int DEFAULT_MAX_STACK_SIZE = Integer.MAX_VALUE;
    public static int DEFAULT_MAX_EXCEPTION_TRACE_LENGTH = Integer.MAX_VALUE;

    private int maxStackSize;
    private int maxExceptionTraceLength;

    public ExceptionTelemetryOptions(Integer maxStackSize, Integer maxExceptionTraceLength) {
        this.maxStackSize = maxStackSize != null ? maxStackSize : DEFAULT_MAX_STACK_SIZE;
        this.maxExceptionTraceLength = maxExceptionTraceLength != null
                ? maxExceptionTraceLength
                : DEFAULT_MAX_EXCEPTION_TRACE_LENGTH;
    }

    public static ExceptionTelemetryOptions empty() {
        return new ExceptionTelemetryOptions(null, null);
    }

    public int getMaxStackSize() {
        return maxStackSize;
    }

    public int getMaxExceptionTraceLength() {
        return maxExceptionTraceLength;
    }

    @Override
    public String toString() {
        return "{maxStackSize=" + maxStackSize +
                ", maxExceptionTraceLength=" + maxExceptionTraceLength +
                '}';
    }
}
