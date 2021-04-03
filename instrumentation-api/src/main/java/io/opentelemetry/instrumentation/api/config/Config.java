/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.config;

import static java.util.Objects.requireNonNull;

import com.google.auto.value.AutoValue;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.function.Function;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@AutoValue
public abstract class Config {

  // NOTE it's important not to use slf4j in this class, because this class is used before slf4j is
  // configured, and so using slf4j here would initialize slf4j-simple before we have a chance to
  // configure the logging levels

  // lazy initialized, so that javaagent can set it, and library instrumentation can fall back and
  // read system properties
  private static volatile Config INSTANCE = null;

  private static final Logger logger = LoggerFactory.getLogger(Config.class);
  private static final String LOG_CAPTURE_THRESHOLD = "otel.experimental.log.capture.threshold";

  /**
   * Sets the agent configuration singleton. This method is only supposed to be called once, from
   * the agent classloader just before the first instrumentation is loaded (and before {@link
   * Config#get()} is used for the first time).
   */
  public static void internalInitializeConfig(Config config) {
    if (INSTANCE != null) {
      return;
    }
    INSTANCE = requireNonNull(config);
  }

  public static Config get() {
    if (INSTANCE == null) {
      // this should only happen in library instrumentation
      //
      // no need to synchronize because worst case is creating INSTANCE more than once
      INSTANCE = new ConfigBuilder().readEnvironmentVariables().readSystemProperties().build();
    }
    return INSTANCE;
  }

  public static Config create(Map<String, String> allProperties) {
    return new AutoValue_Config(allProperties);
  }

  abstract Map<String, String> getAllProperties();

  /**
   * Returns a string property value or null if a property with name {@code name} did not exist.
   *
   * @see #getProperty(String, String)
   */
  @Nullable
  public String getProperty(String name) {
    return getProperty(name, null);
  }

  /**
   * Retrieves a property value from the agent configuration consisting of system properties,
   * environment variables and contents of the agent configuration file (see {@code
   * ConfigInitializer} and {@code ConfigBuilder}).
   *
   * <p>In case any {@code get*Property()} method variant gets called for the same property more
   * than once (e.g. each time an advice executes) it is suggested to cache the result instead of
   * repeatedly calling {@link Config}. Agent configuration does not change during the runtime so
   * retrieving the property once and storing its result in e.g. static final field allows JIT to do
   * its magic and remove some code branches.
   *
   * @return A string property value or {@code defaultValue} if a property with name {@code name}
   *     did not exist.
   */
  public String getProperty(String name, String defaultValue) {
    return getAllProperties().getOrDefault(NamingConvention.DOT.normalize(name), defaultValue);
  }

  /**
   * Returns a boolean property value or {@code defaultValue} if a property with name {@code name}
   * did not exist.
   *
   * @see #getProperty(String, String)
   */
  public boolean getBooleanProperty(String name, boolean defaultValue) {
    return getTypedProperty(name, Boolean::parseBoolean, defaultValue);
  }

  /**
   * Returns a list-of-strings property value or empty list if a property with name {@code name} did
   * not exist.
   *
   * @see #getProperty(String, String)
   */
  public List<String> getListProperty(String name) {
    return getListProperty(name, Collections.emptyList());
  }

  /**
   * Returns a list-of-strings property value or {@code defaultValue} if a property with name {@code
   * name} did not exist.
   *
   * @see #getProperty(String, String)
   */
  public List<String> getListProperty(String name, List<String> defaultValue) {
    return getTypedProperty(name, CollectionParsers::parseList, defaultValue);
  }

  /**
   * Returns a map-of-strings property value or empty map if a property with name {@code name} did
   * not exist.
   *
   * @see #getProperty(String, String)
   */
  public Map<String, String> getMapProperty(String name) {
    return getTypedProperty(name, CollectionParsers::parseMap, Collections.emptyMap());
  }

  private <T> T getTypedProperty(String name, Function<String, T> parser, T defaultValue) {
    String value = getProperty(name);
    if (value == null || value.trim().isEmpty()) {
      return defaultValue;
    }
    try {
      return parser.apply(value);
    } catch (Throwable t) {
      return defaultValue;
    }
  }

  public boolean isInstrumentationEnabled(
      Iterable<String> instrumentationNames, boolean defaultEnabled) {
    return isInstrumentationPropertyEnabled(instrumentationNames, "enabled", defaultEnabled);
  }

  public boolean isInstrumentationPropertyEnabled(
      Iterable<String> instrumentationNames, String suffix, boolean defaultEnabled) {
    // If default is enabled, we want to enable individually,
    // if default is disabled, we want to disable individually.
    boolean anyEnabled = defaultEnabled;
    for (String name : instrumentationNames) {
      String propertyName = "otel.instrumentation." + name + '.' + suffix;
      boolean enabled = getBooleanProperty(propertyName, defaultEnabled);

      if (defaultEnabled) {
        anyEnabled &= enabled;
      } else {
        anyEnabled |= enabled;
      }
    }
    return anyEnabled;
  }

  public Properties asJavaProperties() {
    Properties properties = new Properties();
    properties.putAll(getAllProperties());
    return properties;
  }

  public boolean isAgentDebugEnabled() {
    return getBooleanProperty("otel.javaagent.debug", false);
  }

  public void updateLogCaptureThreshold(String property, String value) {
    logger.debug("############################## updateLogCaptureThreshold start ##############################");
    if (LOG_CAPTURE_THRESHOLD.equals(property)) {
      String level = getProperty(property);
      if (value != null && !value.isEmpty() && !value.equalsIgnoreCase(level)) {
        getAllProperties().replace(LOG_CAPTURE_THRESHOLD, level, value.toUpperCase());
        assert (getProperty(LOG_CAPTURE_THRESHOLD).equalsIgnoreCase(value));
        logger.debug("############################## update log capture threshold from {} to {}", level, value);
      }
    }
    logger.debug("############################## updateLogCaptureThreshold end ##############################");
  }
}
