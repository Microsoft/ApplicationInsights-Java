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

package com.microsoft.applicationinsights.agent.internal.coresync.impl;

import com.microsoft.applicationinsights.agent.internal.config.DataOfConfigurationForException;

import java.util.HashSet;

/**
 * The class will evaluate exceptions and will decide whether or not they are 'interesting', i.e. valid
 *
 * Created by gupele on 8/17/2016.
 */
final class RuntimeExceptionDecider {

    public static class ValidationResult {
        public final boolean valid;
        public final Integer maxStackSize;
        public final Integer maxExceptionTraceLength;

        public static ValidationResult createValidResult(Integer maxStackSize, Integer maxExceptionTraceLength) {
            return new ValidationResult(true, maxStackSize, maxExceptionTraceLength);
        }

        public static ValidationResult createNonValidResult() {
            return new ValidationResult(false, -1, -1);
        }

        private ValidationResult(boolean valid, Integer maxStackSize, Integer maxExceptionTraceLength) {
            this.valid = valid;
            this.maxStackSize = maxStackSize;
            this.maxExceptionTraceLength = maxExceptionTraceLength;
        }
    }

    public final HashSet<String> suppressStackExceptions = new HashSet<String>();
    private final boolean blockInternalExceptions;
    private DataOfConfigurationForException exceptionData;

    public RuntimeExceptionDecider() {
        this(true);
    }

    public RuntimeExceptionDecider(boolean blockInternalExceptions) {
        this.blockInternalExceptions = blockInternalExceptions;
        suppressStackExceptions.add("com.microsoft.applicationinsights.web.internal.WebRequestTrackingFilter");
    }

    public ValidationResult isValid(Exception e) {
        if (exceptionData == null || !exceptionData.isEnabled()) {
            return new ValidationResult(false, -1, -1);
        }

        boolean valid = false;
        StackTraceElement[] traces = e.getStackTrace();
        for (StackTraceElement trace : traces) {
            for (String nonValid : exceptionData.getSuppressedExceptions()) {
                String traceClassName = trace.getClassName();
                if (traceClassName.startsWith(nonValid)) {
                    if (!suppressStackExceptions.contains(traceClassName)) {
                        return ValidationResult.createNonValidResult();
                    }
                }
            }
            if (valid) {
                continue;
            }
            if (exceptionData.getValidPathForExceptions().contains(trace.getClassName())) {
                valid = true;
            }
        }

        if (!exceptionData.getValidPathForExceptions().isEmpty() && !valid) {
            return ValidationResult.createNonValidResult();
        }

        return ValidationResult.createValidResult(exceptionData.getMaxStackSize(),
                exceptionData.getMaxExceptionTraceLength());
    }

    public void setExceptionData(DataOfConfigurationForException exceptionData) {
        this.exceptionData = exceptionData;
        if (exceptionData != null) {
            if (blockInternalExceptions) {
                exceptionData.getSuppressedExceptions().add("com.microsoft.applicationinsights");
            }
        }
    }
}
