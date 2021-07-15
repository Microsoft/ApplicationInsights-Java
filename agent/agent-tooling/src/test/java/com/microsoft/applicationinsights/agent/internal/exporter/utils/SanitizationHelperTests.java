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

package com.microsoft.applicationinsights.agent.internal.exporter.utils;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

public class SanitizationHelperTests {

  @Test
  public void testValidPropertiesData() {
    // given
    Map<String, String> properties = new HashMap<>();
    properties.put("key1", "value1");
    properties.put("key2", "value2");

    // when
    Map<String, String> sanitized = SanitizationHelper.sanitizeProperties(properties);

    // then
    assertThat(sanitized == properties).isTrue();
  }

  @Test
  public void testValidMeasurementsData() {
    // given
    Map<String, Double> measurements = new HashMap<>();
    measurements.put("key1", 1.0);
    measurements.put("key2", 2.0);

    // when
    Map<String, Double> sanitized = SanitizationHelper.sanitizeMeasurements(measurements);

    // then
    assertThat(sanitized).containsEntry("key1", 1.0);
    assertThat(sanitized).containsEntry("key2", 2.0);
  }

  @Test
  public void testEmptyKeyInPropertiesData() {
    // given
    Map<String, String> properties = new HashMap<>();
    properties.put("", "value1");
    properties.put("key2", "value2");

    // when
    Map<String, String> sanitized = SanitizationHelper.sanitizeProperties(properties);

    // then
    assertThat(sanitized).doesNotContainKey("");
    assertThat(sanitized).containsEntry("empty", "value1");
    assertThat(sanitized).containsEntry("key2", "value2");
  }

  @Test
  public void testEmptyKeyInMeasurementsData() {
    // given
    Map<String, Double> measurements = new HashMap<>();
    measurements.put("", 1.0);
    measurements.put("key2", 2.0);

    // when
    Map<String, Double> sanitized = SanitizationHelper.sanitizeMeasurements(measurements);

    // then
    assertThat(sanitized).doesNotContainKey("");
    assertThat(sanitized).containsEntry("empty", 1.0);
    assertThat(sanitized).containsEntry("key2", 2.0);
  }

  @Test
  public void testEmptyValueInPropertiesData() {
    // given
    Map<String, String> properties = new HashMap<>();
    properties.put("key1", "");
    properties.put("key2", "value2");

    // when
    Map<String, String> sanitized = SanitizationHelper.sanitizeProperties(properties);

    // then
    assertThat(sanitized).doesNotContainKey("key1");
    assertThat(sanitized).containsEntry("key2", "value2");
  }

  @Test
  public void testVeryLongKeyInPropertiesData() {
    // given
    Map<String, String> properties = new HashMap<>();
    StringBuilder sb = new StringBuilder();
    for (int i = 0; i < SanitizationHelper.MAX_KEY_LENGTH + 1; i++) {
      sb.append('a');
    }
    String longKey = sb.toString();
    properties.put(longKey, "value1");
    properties.put("key2", "value2");

    // when
    Map<String, String> sanitized = SanitizationHelper.sanitizeProperties(properties);

    // then
    assertThat(sanitized).doesNotContainKey(longKey);
    assertThat(sanitized)
        .containsEntry(longKey.substring(0, SanitizationHelper.MAX_KEY_LENGTH), "value1");
    assertThat(sanitized).containsEntry("key2", "value2");
  }

  @Test
  public void testVeryLongKeyInMeasurementsData() {
    // given
    Map<String, Double> measurements = new HashMap<>();
    StringBuilder sb = new StringBuilder();
    for (int i = 0; i < SanitizationHelper.MAX_KEY_LENGTH + 1; i++) {
      sb.append('a');
    }
    String longKey = sb.toString();
    measurements.put(longKey, 1.0);
    measurements.put("key2", 2.0);

    // when
    Map<String, Double> sanitized = SanitizationHelper.sanitizeMeasurements(measurements);

    // then
    assertThat(sanitized).doesNotContainKey(longKey);
    assertThat(sanitized)
        .containsEntry(longKey.substring(0, SanitizationHelper.MAX_KEY_LENGTH), 1.0);
    assertThat(sanitized).containsEntry("key2", 2.0);
  }

  @Test
  public void testVeryLongValueInPropertiesData() {
    // given
    Map<String, String> properties = new HashMap<>();
    StringBuilder sb = new StringBuilder();
    for (int i = 0; i < SanitizationHelper.MAX_VALUE_LENGTH + 1; i++) {
      sb.append('a');
    }
    String longValue = sb.toString();
    properties.put("key1", longValue);
    properties.put("key2", "value2");

    // when
    Map<String, String> sanitized = SanitizationHelper.sanitizeProperties(properties);

    // then
    assertThat(sanitized.get("key1").length()).isEqualTo(SanitizationHelper.MAX_VALUE_LENGTH);
    assertThat(sanitized).containsEntry("key2", "value2");
  }

  @Test
  public void testVeryLongDuplicateKeyInPropertiesData() {
    // given
    Map<String, String> properties = new HashMap<>();
    StringBuilder sb = new StringBuilder();
    for (int i = 0; i < SanitizationHelper.MAX_KEY_LENGTH + 1; i++) {
      sb.append('a');
    }
    String longKey1 = sb.append("key1").toString();
    String longKey2 = sb.append("key2").toString();
    properties.put(longKey1, "value1");
    properties.put(longKey2, "value1");
    properties.put("key2", "value2");

    // when
    Map<String, String> sanitized = SanitizationHelper.sanitizeProperties(properties);

    // then
    assertThat(sanitized).doesNotContainKey(longKey1);
    assertThat(sanitized).doesNotContainKey(longKey2);
    assertThat(sanitized)
        .containsEntry(longKey1.substring(0, SanitizationHelper.MAX_KEY_LENGTH), "value1");
    assertThat(sanitized)
        .containsEntry(
            longKey2.substring(
                    0,
                    SanitizationHelper.MAX_KEY_LENGTH
                        - SanitizationHelper.UNIQUE_KEY_TRUNCATION_LENGTH)
                + "1",
            "value1");
    assertThat(sanitized).containsEntry("key2", "value2");
  }

  @Test
  public void testMakeUniqueKey() {
    // given
    Map<String, String> properties = new HashMap<>();
    StringBuilder sb = new StringBuilder();
    for (int i = 0; i < SanitizationHelper.MAX_KEY_LENGTH + 1; i++) {
      sb.append('a');
    }
    for (int i = 0; i < 1001; i++) {
      properties.put(sb.append("key").append(i).toString(), "value" + i);
    }

    // when
    Map<String, String> sanitized = SanitizationHelper.sanitizeProperties(properties);

    // then
    assertThat(sanitized.keySet().size()).isEqualTo(1001);
    for (String key : sanitized.keySet()) {
      assertThat(key.length()).isLessThanOrEqualTo(SanitizationHelper.MAX_KEY_LENGTH);
    }
  }
}
