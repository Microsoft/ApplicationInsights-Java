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

import com.microsoft.applicationinsights.agent.internal.common.Strings;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public class SanitizationHelper {
  public static final int MAX_KEY_LENGTH = 150;
  public static final int MAX_VALUE_LENGTH = 8192;
  public static final int MAX_NAME_LENGTH = 1024;
  public static final int MAX_ID_LENGTH = 512;
  public static final int MAX_MESSAGE_LENGTH = 32768;
  public static final int MAX_URL_LENGTH = 2048;

  /** Function to sanitize both key and value in properties. */
  public static Map<String, String> sanitizeProperties(Map<String, String> properties) {
    if (properties == null) {
      // TODO (trask) never pass null
      return Collections.emptyMap();
    }
    if (!needsSanitizing(properties)) {
      return properties;
    }
    Map<String, String> sanitized = new HashMap<>();
    for (Map.Entry<String, String> entry : properties.entrySet()) {
      String sanitizedKey = sanitizeKey(entry.getKey());
      String sanitizedValue = sanitizeValue(entry.getValue());
      if (!Strings.isNullOrEmpty(sanitizedValue)) {
        String uniqueKey = makeKeyUnique(sanitizedKey, sanitized);
        sanitized.put(uniqueKey, sanitizedValue);
      }
    }
    return sanitized;
  }

  private static boolean needsSanitizing(Map<String, String> properties) {
    for (Map.Entry<String, String> entry : properties.entrySet()) {
      String key = entry.getKey();
      String value = entry.getValue();
      if (Strings.isNullOrEmpty(key)
          || Strings.isNullOrEmpty(value)
          || key.length() > MAX_KEY_LENGTH
          || value.length() > MAX_VALUE_LENGTH) {
        return true;
      }
    }
    return false;
  }

  /** Function to create unique key. */
  private static String makeKeyUnique(String key, Map<String, ?> map) {
    if (!map.containsKey(key)) {
      return key;
    }
    String truncatedKey = Strings.truncate(key, MAX_KEY_LENGTH - 3);
    int candidate = 1;
    do {
      key = truncatedKey + candidate;
      ++candidate;
    } while (map.containsKey(key));
    return key;
  }

  /** Function to sanitize value. */
  private static String sanitizeValue(String value) {
    return Strings.trimAndTruncate(value, MAX_VALUE_LENGTH);
  }

  /** Function to sanitize key. */
  private static String sanitizeKey(String key) {
    String sanitizedKey = Strings.trimAndTruncate(key, MAX_KEY_LENGTH);
    return makeKeyNonEmpty(sanitizedKey);
  }

  /** Function to return non empty key. */
  private static String makeKeyNonEmpty(String sanitizedKey) {
    // TODO check if this is valid. Below code is based on .Net implementation of the same
    return Strings.isNullOrEmpty(sanitizedKey) ? "empty" : sanitizedKey;
  }

  /** Function to sanitize key value pair in measurements. */
  public static void sanitizeMeasurements(Map<String, Double> measurements) {
    if (measurements == null) {
      return;
    }
    Map<String, SanitizedEntry<Double>> sanitizedMeasurements = new HashMap<>();
    for (Map.Entry<String, Double> entry : measurements.entrySet()) {
      String sanitizedKey = sanitizeKey(entry.getKey());
      // TODO sanitize value ?
      if (!sanitizedKey.equals(entry.getKey())) {
        sanitizedMeasurements.put(
            entry.getKey(), new SanitizedEntry<>(sanitizedKey, entry.getValue()));
      }
    }
    for (Map.Entry<String, SanitizedEntry<Double>> entry : sanitizedMeasurements.entrySet()) {
      measurements.remove(entry.getKey());
      String uniqueKey = makeKeyUnique(entry.getValue().getKey(), measurements);
      measurements.put(uniqueKey, entry.getValue().getValue());
    }
  }

  private SanitizationHelper() {}

  static class SanitizedEntry<V> {
    private final String key;
    private final V value;

    public SanitizedEntry(String key, V value) {
      this.key = key;
      this.value = value;
    }

    public String getKey() {
      return key;
    }

    public V getValue() {
      return value;
    }
  }
}
