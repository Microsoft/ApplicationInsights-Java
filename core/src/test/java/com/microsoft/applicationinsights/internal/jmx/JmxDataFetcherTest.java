package com.microsoft.applicationinsights.internal.jmx;

import org.junit.*;

import javax.management.*;
import java.lang.management.ManagementFactory;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.*;

public class JmxDataFetcherTest {

    @SuppressWarnings("unused")
    public interface StubMXBean {
        int getIntSample();
        double getDoubleSample();
        long getLongSample();
    }

    @SuppressWarnings("unused")
    public static class TestStub implements StubMXBean {
        public int i;
        public double d;
        public long l;
        public TestStub(int i, double d, long l) {
            this.i = i;
            this.d = d;
            this.l = l;
        }

        @Override
        public int getIntSample() {
            return i;
        }

        @Override
        public double getDoubleSample() {
            return d;
        }

        @Override
        public long getLongSample() {
            return l;
        }
    }

    @Test
    public void testBadAttributeName() throws Exception {
        MBeanServer server = ManagementFactory.getPlatformMBeanServer();
        ObjectName mxbeanName = new ObjectName("JSDKTests:type=TestStub3");
        TestStub testStub = new TestStub(1, 2.0, 3L);
        server.registerMBean(testStub, mxbeanName);
        List<JmxAttributeData> attributes = new ArrayList<>();
        attributes.add(new JmxAttributeData("Int", "WrongNameIntSample"));
        attributes.add(new JmxAttributeData("Double", "WrongNameDoubleSample"));
        attributes.add(new JmxAttributeData("Long", "WrongNameLongSample"));
        JmxDataFetcher.fetch("JSDKTests:type=TestStub3", attributes);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testBadName() throws Exception {
        MBeanServer server = ManagementFactory.getPlatformMBeanServer();
        ObjectName mxbeanName = new ObjectName("JSDKTests:type=TestStub1");
        TestStub testStub = new TestStub(1, 2.0, 3L);
        server.registerMBean(testStub, mxbeanName);
        List<JmxAttributeData> attributes = new ArrayList<>();
        attributes.add(new JmxAttributeData("Int", "IntSample"));
        JmxDataFetcher.fetch("JSDKTests:type=TestStub", attributes);
    }

    @Test
    public void testWithChange() throws Exception {
        MBeanServer server = ManagementFactory.getPlatformMBeanServer();
        ObjectName mxbeanName = new ObjectName("JSDKTests:type=TestStub");
        TestStub testStub = new TestStub(1, 2.0, 3L);
        server.registerMBean(testStub, mxbeanName);

        List<JmxAttributeData> attributes = new ArrayList<>();
        attributes.add(new JmxAttributeData("Int", "IntSample"));
        attributes.add(new JmxAttributeData("Double", "DoubleSample"));
        attributes.add(new JmxAttributeData("Long", "LongSample"));

        performTest(attributes, 1.0, 2.0, 3.0);

        testStub.i = 1000;
        testStub.d = 2000.0;
        testStub.l = 3000L;

        performTest(attributes, 1000.0, 2000.0, 3000.0);
    }

    private static void performTest(
            List<JmxAttributeData> attributes,
            double expectedInt,
            double expectedDouble,
            double expectedLong) throws Exception {
        Map<String, Collection<Object>> result = JmxDataFetcher.fetch("JSDKTests:type=TestStub", attributes);

        assertNotNull(result);
        assertEquals(3, result.size());

        verify(result, "Int", expectedInt);
        verify(result, "Double", expectedDouble);
        verify(result, "Long", expectedLong);
    }

    private static void verify(Map<String, Collection<Object>> result, String key, double expectedValue) {
        Collection<Object> objects = result.get(key);
        assertNotNull(objects);
        assertEquals(1, objects.size());
        double value = 0.0;
        for (Object obj : objects) {
            try {
                value += Double.parseDouble(String.valueOf(obj));
            } catch (Exception e) {
                Assert.fail("Exception thrown: "+e);
            }
        }

        assertEquals(value, expectedValue, 0.0);
    }
}