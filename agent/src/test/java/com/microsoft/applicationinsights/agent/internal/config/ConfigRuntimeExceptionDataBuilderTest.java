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

package com.microsoft.applicationinsights.agent.internal.config;

import org.junit.Assert;
import org.junit.Test;
import org.mockito.Mockito;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;

import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import static org.mockito.Mockito.mock;

/**
 * Created by gupele on 8/18/2016.
 */
public class ConfigRuntimeExceptionDataBuilderTest {
    @Test
    public void testEnabledExceptionWithSuppressedAndValidDataTag() {
        final Element suppressedElement = createMockElement("name", "aa.aa");
        final Element validElement = createMockElement("name", "bb.bb");

        final NodeList suppressedNodeList = createMockNodeList(suppressedElement);
        final NodeList validNodeList = createMockNodeList(validElement);

        final Element exceptionTag = createMockElement("enabled", "true");
        addAttribute(exceptionTag, "stackSize", " 1 ");
        addAttribute(exceptionTag, "exceptionTraceLength", " 2 ");
        addMockNodeList(exceptionTag, "Suppress", suppressedNodeList);
        addMockNodeList(exceptionTag, "Valid", validNodeList);

        final NodeList nodeList = createMockNodeList(exceptionTag);

        Element mainTag = createMockElementWithNodeList(nodeList);

        ConfigRuntimeExceptionDataBuilder tested = new ConfigRuntimeExceptionDataBuilder();

        AgentBuiltInConfigurationBuilder builder = new AgentBuiltInConfigurationBuilder();
        builder.setEnabled(true);

        tested.setRuntimeExceptionData(mainTag, builder);

        AgentBuiltInConfiguration confData = builder.create();

        Assert.assertTrue(confData.isEnabled());
        DataOfConfigurationForException exceptionData = confData.getDataOfConfigurationForException();

        Assert.assertNotNull(exceptionData);

        Assert.assertTrue(exceptionData.isEnabled());
        Assert.assertEquals(exceptionData.getSuppressedExceptions().size(), 1);
        Assert.assertEquals(exceptionData.getSuppressedExceptions().iterator().next(), "aa.aa");

        Assert.assertEquals(exceptionData.getValidPathForExceptions().size(), 1);
        Assert.assertEquals(exceptionData.getValidPathForExceptions().iterator().next(), "bb.bb");

        Assert.assertEquals(1, (int)exceptionData.getMaxStackSize());
        Assert.assertEquals(2, (int)exceptionData.getMaxExceptionTraceLength());
    }

    @Test
    public void testNoExceptionTag() {
        Element mainTag = mock(Element.class);
        Mockito.doAnswer(new Answer() {
            @Override
            public Object answer(InvocationOnMock invocation) throws Throwable {
                return null;
            }
        }).when(mainTag).getElementsByTagName("RuntimeException");

        ConfigRuntimeExceptionDataBuilder tested = new ConfigRuntimeExceptionDataBuilder();

        AgentBuiltInConfigurationBuilder builder = new AgentBuiltInConfigurationBuilder();
        tested.setRuntimeExceptionData(mainTag, builder);

        AgentBuiltInConfiguration confData = builder.create();

        DataOfConfigurationForException exceptionData = confData.getDataOfConfigurationForException();

        Assert.assertNotNull(exceptionData);

        Assert.assertFalse(exceptionData.isEnabled());
    }

    @Test
    public void testNotEnabledExceptionTag() {
        final Element exceptionTag = createMockElement("enabled", "false");

        final NodeList nodeList = createMockNodeList(exceptionTag);

        Element mainTag = createMockElementWithNodeList(nodeList);

        ConfigRuntimeExceptionDataBuilder tested = new ConfigRuntimeExceptionDataBuilder();

        AgentBuiltInConfigurationBuilder builder = new AgentBuiltInConfigurationBuilder();
        tested.setRuntimeExceptionData(mainTag, builder);

        AgentBuiltInConfiguration confData = builder.create();

        DataOfConfigurationForException exceptionData = confData.getDataOfConfigurationForException();

        Assert.assertNotNull(exceptionData);

        Assert.assertFalse(exceptionData.isEnabled());
    }

    @Test
    public void fetchIntTest(){
        Element element = createMockElement("i", "3");
        Integer i = ConfigRuntimeExceptionDataBuilder.fetchInteger(element, "i");
        Assert.assertEquals(3, (int)i);

        element = createMockElement("i", "");
        i = ConfigRuntimeExceptionDataBuilder.fetchInteger(element, "i");
        Assert.assertNull(i);

        element = createMockElement("i", "FuLL ");
        i = ConfigRuntimeExceptionDataBuilder.fetchInteger(element, "i");
        Assert.assertEquals(Integer.MAX_VALUE, (int)i);

        element = createMockElement("i", "NotAnInt");
        i = ConfigRuntimeExceptionDataBuilder.fetchInteger(element, "i");
        Assert.assertNull(i);
    }

    private static Element createMockElement(String attributeName, final String attributeValue) {
        final Element mockElement = mock(Element.class);
        Mockito.doAnswer(new Answer() {
            @Override
            public Object answer(InvocationOnMock invocation) throws Throwable {
                return Node.ELEMENT_NODE;
            }
        }).when(mockElement).getNodeType();
        addAttribute(mockElement, attributeName, attributeValue);

        return mockElement;
    }

    private static NodeList createMockNodeList(final Element element) {
        final NodeList nodeList = mock(NodeList.class);
        Mockito.doAnswer(new Answer() {
            @Override
            public Object answer(InvocationOnMock invocation) throws Throwable {
                return 1;
            }
        }).when(nodeList).getLength();

        Mockito.doAnswer(new Answer() {
            @Override
            public Object answer(InvocationOnMock invocation) throws Throwable {
                return element;
            }
        }).when(nodeList).item(0);

        return nodeList;
    }

    private static Element createMockElementWithNodeList(final NodeList nodeList) {
        Element mockElement = mock(Element.class);
        addMockNodeList(mockElement, "RuntimeException", nodeList);

        return mockElement;
    }

    private static void addMockNodeList(Element element, String name, final NodeList nodeList) {
        Mockito.doAnswer(new Answer() {
            @Override
            public Object answer(InvocationOnMock invocation) throws Throwable {
                return nodeList;
            }
        }).when(element).getElementsByTagName(name);
    }

    private static void addAttribute(final Element element, final String attributeName, final String attributeValue) {
        Mockito.doAnswer(new Answer() {
            @Override
            public Object answer(InvocationOnMock invocation) throws Throwable {
                return attributeValue;
            }
        }).when(element).getAttribute(attributeName);
    }
}
