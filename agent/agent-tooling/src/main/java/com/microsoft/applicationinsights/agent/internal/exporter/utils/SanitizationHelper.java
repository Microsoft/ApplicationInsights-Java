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
import java.util.HashMap;
import java.util.Map;

public class SanitizationHelper {
  public static final int MAX_KEY_LENGTH = 150;
  public static final int MAX_VALUE_LENGTH = 8192;
  public static final int MAX_NAME_LENGTH = 1024;
  public static final int MAX_ID_LENGTH = 512;
  public static final int MAX_MESSAGE_LENGTH = 32768;
  public static final int MAX_URL_LENGTH = 2048;
  public static final int UNIQUE_KEY_TRUNCATION_LENGTH = 6;

  /** Function to sanitize both key and value in properties. */
  @SuppressWarnings("ReturnsNullCollection")
  public static Map<String, String> sanitizeProperties(Map<String, String> properties) {
    if (properties == null) {
      return null;
    }
    if (!needsSanitizingForProperties(properties)) {
      // this is an optimization to avoid any memory allocation in the normal case
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

  private static boolean needsSanitizingForProperties(Map<String, String> properties) {
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

  private static boolean needsSanitizingForMeasurements(Map<String, Double> measurements) {
    for (Map.Entry<String, Double> entry : measurements.entrySet()) {
      String key = entry.getKey();
      if (Strings.isNullOrEmpty(key) || key.length() > MAX_KEY_LENGTH) {
        return true;
      }
    }
    return false;
  }

  /** Function to create unique key. Generates 1000000 unique keys for */
  private static String makeKeyUnique(String key, Map<String, ?> map) {
    if (!map.containsKey(key)) {
      return key;
    }
    String truncatedKey = Strings.truncate(key, MAX_KEY_LENGTH - UNIQUE_KEY_TRUNCATION_LENGTH);
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

  /** Function to sanitize both key and value in Measurements. */
  @SuppressWarnings("ReturnsNullCollection")
  public static Map<String, Double> sanitizeMeasurements(Map<String, Double> measurements) {
    if (measurements == null) {
      return null;
    }
    if (!needsSanitizingForMeasurements(measurements)) {
      // this is an optimization to avoid any memory allocation in the normal case
      return measurements;
    }
    Map<String, Double> sanitized = new HashMap<>();
    for (Map.Entry<String, Double> entry : measurements.entrySet()) {
      String sanitizedKey = sanitizeKey(entry.getKey());
      String uniqueKey = makeKeyUnique(sanitizedKey, sanitized);
      sanitized.put(uniqueKey, entry.getValue());
    }
    return sanitized;
  }

  private SanitizationHelper() {}
}
