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
import org.junit.Test;

import static org.junit.Assert.*;

/**
 * Created by gupele on 8/18/2016.
 */
public class RuntimeExceptionDeciderTest {
    @Test
    public void testNullConfData() {
        RuntimeExceptionDecider tested = new RuntimeExceptionDecider();

        RuntimeExceptionDecider.ValidationResult result = tested.isValid(new RuntimeException());

        assertNotNull(result);
        assertFalse(result.valid);
    }

    @Test
    public void testConfDataDisabled() {
        RuntimeExceptionDecider tested = new RuntimeExceptionDecider();
        DataOfConfigurationForException data = new DataOfConfigurationForException();
        data.setEnabled(false);
        tested.setExceptionData(data);

        RuntimeExceptionDecider.ValidationResult result = tested.isValid(new RuntimeException());

        assertNotNull(result);
        assertFalse(result.valid);
    }

    @Test
    public void testConfDataEnabled() {
        RuntimeExceptionDecider tested = new RuntimeExceptionDecider(false);
        DataOfConfigurationForException data = new DataOfConfigurationForException();
        data.setEnabled(true);
        tested.setExceptionData(data);

        RuntimeExceptionDecider.ValidationResult result = tested.isValid(new RuntimeException());

        assertNotNull(result);
        assertTrue(result.valid);
        assertNull(result.maxStackSize);
        assertNull(result.maxExceptionTraceLength);
    }

    @Test
    public void testConfDataEnabledWithSuppressedData() {
        RuntimeExceptionDecider tested = new RuntimeExceptionDecider();
        DataOfConfigurationForException data = new DataOfConfigurationForException();
        data.setEnabled(true);
        data.setMaxStackSize(1);
        data.getSuppressedExceptions().add("java.lang.RuntimeException");
        tested.setExceptionData(data);

        RuntimeExceptionDecider.ValidationResult result = tested.isValid(new RuntimeException());

        assertNotNull(result);
        assertFalse(result.valid);
    }

    @Test
    public void testConfDataEnabledWithSuppressedDataNotRelevant() {
        RuntimeExceptionDecider tested = new RuntimeExceptionDecider(false);
        DataOfConfigurationForException data = new DataOfConfigurationForException();
        data.setEnabled(true);
        data.setMaxStackSize(1);
        data.getSuppressedExceptions().add("aa.aa");
        tested.setExceptionData(data);

        RuntimeExceptionDecider.ValidationResult result = tested.isValid(new RuntimeException());

        assertNotNull(result);
        assertTrue(result.valid);
        assertEquals(1, (int)result.maxStackSize);
    }
}
