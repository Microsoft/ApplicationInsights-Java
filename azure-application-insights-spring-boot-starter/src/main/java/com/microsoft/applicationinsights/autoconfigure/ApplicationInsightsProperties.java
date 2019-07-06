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

package com.microsoft.applicationinsights.autoconfigure;

import com.microsoft.applicationinsights.autoconfigure.helpers.IkeyResolver;
import com.microsoft.applicationinsights.channel.concrete.TelemetryChannelBase;
import com.microsoft.applicationinsights.channel.concrete.inprocess.InProcessTelemetryChannel;
import com.microsoft.applicationinsights.internal.channel.common.TransmissionFileSystemOutput;
import com.microsoft.applicationinsights.internal.channel.common.TransmissionNetworkOutput;
import com.microsoft.applicationinsights.internal.channel.samplingV2.FixedRateSamplingTelemetryProcessor;
import com.microsoft.applicationinsights.internal.heartbeat.HeartBeatProvider;
import com.microsoft.applicationinsights.internal.jmx.JmxAttributeData;
import com.microsoft.applicationinsights.internal.logger.InternalLogger;
import com.microsoft.applicationinsights.internal.logger.InternalLogger.LoggerOutputType;
import com.microsoft.applicationinsights.internal.logger.InternalLogger.LoggingLevel;
import com.microsoft.applicationinsights.internal.perfcounter.JmxMetricPerformanceCounter;
import com.microsoft.applicationinsights.internal.perfcounter.PerformanceCounterContainer;
import com.microsoft.applicationinsights.web.extensibility.initializers.WebOperationIdTelemetryInitializer;
import com.microsoft.applicationinsights.web.extensibility.initializers.WebOperationNameTelemetryInitializer;
import com.microsoft.applicationinsights.web.extensibility.initializers.WebSessionTelemetryInitializer;
import com.microsoft.applicationinsights.web.extensibility.initializers.WebUserAgentTelemetryInitializer;
import com.microsoft.applicationinsights.web.extensibility.initializers.WebUserTelemetryInitializer;
import com.microsoft.applicationinsights.web.extensibility.modules.WebRequestTrackingTelemetryModule;
import com.microsoft.applicationinsights.web.extensibility.modules.WebSessionTrackingTelemetryModule;
import com.microsoft.applicationinsights.web.extensibility.modules.WebUserTrackingTelemetryModule;
import com.microsoft.applicationinsights.web.internal.perfcounter.WebPerformanceCounterModule;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * <h1>This class is the container for Configuration Properties of Application Insights</h1>
 *
 * {@link ConfigurationProperties} for configuring application insights.
 *
 * @author Arthur Gavlyukovskiy, Dhaval Doshi
 */
@ConfigurationProperties("azure.application-insights")
public class ApplicationInsightsProperties {

  /** Enables application insights auto-configuration. */
  private boolean enabled = true;
  /** Instrumentation key from Azure Portal. */
  private String instrumentationKey;
  /** Telemetry transmission channel configuration. */
  private Channel channel = new Channel();
  /** Built in telemetry processors configuration. */
  private TelemetryProcessor telemetryProcessor = new TelemetryProcessor();
  /** Web plugins settings. */
  private Web web = new Web();
  /** Quick Pulse settings. */
  private QuickPulse quickPulse = new QuickPulse();
  /** Logger properties. */
  private Logger logger = new Logger();

  /** Performance Counter Container Properties */
  private PerformanceCounter performanceCounter = new PerformanceCounter();

  /** Jmx Counter container */
  private Jmx jmx = new Jmx();

  /** Heartbeat Properties container */
  private HeartBeat heartBeat = new HeartBeat();
  /**Exceptions Properties container */
  private Exceptions exceptions = new Exceptions();

  public boolean isEnabled() {
    return enabled;
  }

  public void setEnabled(boolean enabled) {
    this.enabled = enabled;
  }

  public String getInstrumentationKey() {
    // First try getting ikey from application.properties
    if (StringUtils.isNotBlank(instrumentationKey)) {
      return instrumentationKey;
    }

    // If above fails try getting ikey from environment variables or system properties
    String v = IkeyResolver.getIkeyFromEnvironmentVariables();
    if (v == null) {
      throw new IllegalStateException("Instrumentation Key must be set to report telemetry");
    }
    return v;
  }

  public void setInstrumentationKey(String instrumentationKey) {
    this.instrumentationKey = instrumentationKey;
  }

  public Channel getChannel() {
    return channel;
  }

  public void setChannel(Channel channel) {
    this.channel = channel;
  }

  public TelemetryProcessor getTelemetryProcessor() {
    return telemetryProcessor;
  }

  public void setTelemetryProcessor(TelemetryProcessor telemetryProcessor) {
    this.telemetryProcessor = telemetryProcessor;
  }

  public Web getWeb() {
    return web;
  }

  public void setWeb(Web web) {
    this.web = web;
  }

  public QuickPulse getQuickPulse() {
    return quickPulse;
  }

  public void setQuickPulse(QuickPulse quickPulse) {
    this.quickPulse = quickPulse;
  }

  public Logger getLogger() {
    return logger;
  }

  public void setLogger(Logger logger) {
    this.logger = logger;
  }

  public PerformanceCounter getPerformanceCounter() {
    return performanceCounter;
  }

  public void setPerformanceCounter(PerformanceCounter performanceCounter) {
    this.performanceCounter = performanceCounter;
  }

  public Jmx getJmx() {
    return jmx;
  }

  public void setJmx(Jmx jmx) {
    this.jmx = jmx;
  }

  public HeartBeat getHeartBeat() {
    return heartBeat;
  }

  public void setHeartBeat(HeartBeat heartBeat) {
    this.heartBeat = heartBeat;
  }

  public Exceptions getExceptions() {
    return exceptions;
  }

  public void setExceptions(Exceptions exceptions) {
    this.exceptions = exceptions;
  }

  static class Channel {
    /** Configuration of {@link InProcessTelemetryChannel}. */
    private InProcess inProcess = new InProcess();

    /** Configuration of {@link com.microsoft.applicationinsights.channel.concrete.localforwarder.LocalForwarderTelemetryChannel}*/
    private LocalForwarder localForwarder = new LocalForwarder();

    public LocalForwarder getLocalForwarder() {
      return localForwarder;
    }

    public void setLocalForwarder(LocalForwarder localForwarder) {
      this.localForwarder = localForwarder;
    }

    public InProcess getInProcess() {
      return inProcess;
    }

    public void setInProcess(InProcess inProcess) {
      this.inProcess = inProcess;
    }

    static class InProcess {
      /**
       * Enables developer mode, all telemetry will be sent immediately without batching.
       * Significantly affects performance and should be used only in developer environment.
       */
      private boolean developerMode = false;
      /** Endpoint address. */
      private String endpointAddress = TransmissionNetworkOutput.DEFAULT_SERVER_URI;
      /**
       * Maximum count of telemetries that will be batched before sending. Must be between 1 and
       * 1000.
       */
      private int maxTelemetryBufferCapacity =
          TelemetryChannelBase.DEFAULT_MAX_TELEMETRY_BUFFER_CAPACITY;
      /** Interval to send telemetry. Must be between 1 and 300. */
      private int flushIntervalInSeconds =
          TelemetryChannelBase.DEFAULT_FLUSH_BUFFER_TIMEOUT_IN_SECONDS;
      /**
       * Size of disk space that Application Insights can use to store telemetry in case of network
       * outage. Must be between 1 and 1000.
       */
      private int maxTransmissionStorageFilesCapacityInMb =
          TransmissionFileSystemOutput.DEFAULT_CAPACITY_MEGABYTES;
      /** Enables throttling on sending telemetry data. */
      private boolean throttling = true;

      /** Sets the size of maximum instant retries without delay */
      private int maxInstantRetry = TelemetryChannelBase.DEFAULT_MAX_INSTANT_RETRY;

      public boolean isDeveloperMode() {
        return developerMode;
      }

      public void setDeveloperMode(boolean developerMode) {
        this.developerMode = developerMode;
      }

      public int getMaxInstantRetry() {
        return maxInstantRetry;
      }

      public void setMaxInstantRetry(int maxInstantRetry) {
        this.maxInstantRetry = maxInstantRetry;
      }

      public String getEndpointAddress() {
        return endpointAddress;
      }

      public void setEndpointAddress(String endpointAddress) {
        this.endpointAddress = endpointAddress;
      }

      public int getMaxTelemetryBufferCapacity() {
        return maxTelemetryBufferCapacity;
      }

      public void setMaxTelemetryBufferCapacity(int maxTelemetryBufferCapacity) {
        this.maxTelemetryBufferCapacity = maxTelemetryBufferCapacity;
      }

      public int getFlushIntervalInSeconds() {
        return flushIntervalInSeconds;
      }

      public void setFlushIntervalInSeconds(int flushIntervalInSeconds) {
        this.flushIntervalInSeconds = flushIntervalInSeconds;
      }

      public int getMaxTransmissionStorageFilesCapacityInMb() {
        return maxTransmissionStorageFilesCapacityInMb;
      }

      public void setMaxTransmissionStorageFilesCapacityInMb(
          int maxTransmissionStorageFilesCapacityInMb) {
        this.maxTransmissionStorageFilesCapacityInMb = maxTransmissionStorageFilesCapacityInMb;
      }

      public boolean isThrottling() {
        return throttling;
      }

      public void setThrottling(boolean throttling) {
        this.throttling = throttling;
      }
    }

    /**
     * The config class to encapsulate the LocalForwarder properties.
     */
    static class LocalForwarder {

      /** Local Forwarder Endpoint address. */
      private String endpointAddress;

      /**
       * Maximum count of telemetries that will be batched before sending. Must be between 1 and
       * 1000.
       */
      private int maxTelemetryBufferCapacity =
          TelemetryChannelBase.DEFAULT_MAX_TELEMETRY_BUFFER_CAPACITY;

      /** Interval to send telemetry. Must be between 1 and 300. */
      private int flushIntervalInSeconds =
          TelemetryChannelBase.DEFAULT_FLUSH_BUFFER_TIMEOUT_IN_SECONDS;

      public int getMaxTelemetryBufferCapacity() {
        return maxTelemetryBufferCapacity;
      }

      public void setMaxTelemetryBufferCapacity(int maxTelemetryBufferCapacity) {
        this.maxTelemetryBufferCapacity = maxTelemetryBufferCapacity;
      }

      public int getFlushIntervalInSeconds() {
        return flushIntervalInSeconds;
      }

      public void setFlushIntervalInSeconds(int flushIntervalInSeconds) {
        this.flushIntervalInSeconds = flushIntervalInSeconds;
      }

      public String getEndpointAddress() {
        return endpointAddress;
      }

      public void setEndpointAddress(String endpointAddress) {
        this.endpointAddress = endpointAddress;
      }
    }
  }

  static class TelemetryProcessor {

    /** Configuration of {@link FixedRateSamplingTelemetryProcessor}. */
    private Sampling sampling = new Sampling();

    public Sampling getSampling() {
      return sampling;
    }

    public void setSampling(Sampling sampling) {
      this.sampling = sampling;
    }

    static class Sampling {

      private boolean enabled = false;

      /**
       * Percent of telemetry events that will be sent to Application Insights. Percentage must be
       * close to 100/N where N is an integer. E.g. 50 (=100/2), 33.33 (=100/3), 25 (=100/4), 20, 1
       * (=100/100), 0.1 (=100/1000).
       */
      private double percentage = FixedRateSamplingTelemetryProcessor.DEFAULT_SAMPLING_PERCENTAGE;
      /** If set only telemetry of specified types will be included. */
      private List<String> include = new ArrayList<>();
      /** If set telemetry of specified type will be excluded. */
      private List<String> exclude = new ArrayList<>();

      public boolean isEnabled() {
        return enabled;
      }

      public void setEnabled(boolean enabled) {
        this.enabled = enabled;
      }

      public double getPercentage() {
        return percentage;
      }

      public void setPercentage(double percentage) {
        this.percentage = percentage;
      }

      public List<String> getInclude() {
        return include;
      }

      public void setInclude(List<String> include) {
        this.include = include;
      }

      public List<String> getExclude() {
        return exclude;
      }

      public void setExclude(List<String> exclude) {
        this.exclude = exclude;
      }
    }
  }

  static class Web {
    /**
     * Enables Web telemetry modules.
     *
     * <p>Implicitly affects modules: - {@link WebRequestTrackingTelemetryModule} - {@link
     * WebSessionTrackingTelemetryModule} - {@link WebUserTrackingTelemetryModule} - {@link
     * WebPerformanceCounterModule} - {@link WebOperationIdTelemetryInitializer} - {@link
     * WebOperationNameTelemetryInitializer} - {@link WebSessionTelemetryInitializer} - {@link
     * WebUserTelemetryInitializer} - {@link WebUserAgentTelemetryInitializer}
     *
     * <p>False means that all those modules will be disabled regardless of the enabled property of
     * concrete module.
     */
    private boolean enabled = true;

    /**
     * Flag to enable/disable enableW3C headers. It is disabled by default.
     */
    private boolean enableW3C = false;

    /**
     * Flag to enable backward compatibility mode for W3C. By default this is
     * enabled.
     */
    private boolean enableW3CBackcompatMode = true;

    public boolean isEnableW3CBackcompatMode() {
      return enableW3CBackcompatMode;
    }

    public void setEnableW3CBackcompatMode(boolean enableW3CBackcompatMode) {
      this.enableW3CBackcompatMode = enableW3CBackcompatMode;
    }

    public boolean isEnabled() {
      return enabled;
    }

    public void setEnabled(boolean enabled) {
      this.enabled = enabled;
    }

    public boolean isEnableW3C() {
      return enableW3C;
    }

    public void setEnableW3C(boolean enableW3C) {
      this.enableW3C = enableW3C;
    }
  }

  public static class QuickPulse {
    /** Enables Quick Pulse integration. */
    private boolean enabled = true;

    public boolean isEnabled() {
      return enabled;
    }

    public void setEnabled(boolean enabled) {
      this.enabled = enabled;
    }
  }

  static class Logger {
    /** Type of application insights logger. */
    private LoggerOutputType type = LoggerOutputType.CONSOLE;
    /** Minimal level of application insights logger. */
    private LoggingLevel level = LoggingLevel.OFF;

    private String uniquePrefix = "AI";

    private String BaseFolderPath;

    private String numberOfFiles;

    private String numberOfTotalSizeInMB;

    public String getUniquePrefix() {
      return uniquePrefix;
    }

    public void setUniquePrefix(String uniquePrefix) {
      this.uniquePrefix = uniquePrefix;
    }

    public String getBaseFolderPath() {
      return BaseFolderPath;
    }

    public void setBaseFolderPath(String baseFolderPath) {
      BaseFolderPath = baseFolderPath;
    }

    public String getNumberOfFiles() {
      return numberOfFiles;
    }

    public void setNumberOfFiles(String numberOfFiles) {
      this.numberOfFiles = numberOfFiles;
    }

    public String getNumberOfTotalSizeInMB() {
      return numberOfTotalSizeInMB;
    }

    public void setNumberOfTotalSizeInMB(String numberOfTotalSizeInMB) {
      this.numberOfTotalSizeInMB = numberOfTotalSizeInMB;
    }

    public LoggerOutputType getType() {
      return type;
    }

    public void setType(LoggerOutputType type) {
      this.type = type;
    }

    public LoggingLevel getLevel() {
      return level;
    }

    public void setLevel(LoggingLevel level) {
      this.level = level;
    }
  }

  static class PerformanceCounter {

    /** Default collection frequency of performance counters */
    private long collectionFrequencyInSeconds =
        PerformanceCounterContainer.DEFAULT_COLLECTION_FREQUENCY_IN_SEC;

    public long getCollectionFrequencyInSeconds() {
      return collectionFrequencyInSeconds;
    }

    public void setCollectionFrequencyInSeconds(long collectionFrequencyInSeconds) {
      this.collectionFrequencyInSeconds = collectionFrequencyInSeconds;
    }
  }

  static class Jmx {

    /** List of JMX counters */
    private List<String> jmxCounters = new ArrayList<>();

    public List<String> getJmxCounters() {
      return jmxCounters;
    }

    public void setJmxCounters(List<String> jmxCounters) {
      this.jmxCounters = jmxCounters;
    }
  }

  static class Exceptions {
    /**
     * Maximum depth of exceptions graph
     */
    private Integer maxStackSize;
    /**
     * Maximum length of trace per exception
     */
    private Integer maxExceptionTraceLength;

    public Integer getMaxStackSize() {
      return maxStackSize;
    }

    public void setMaxStackSize(Integer maxStackSize) {
      this.maxStackSize = maxStackSize;
    }

    public Integer getMaxExceptionTraceLength() {
      return maxExceptionTraceLength;
    }

    public void setMaxExceptionTraceLength(Integer maxExceptionTraceLength) {
      this.maxExceptionTraceLength = maxExceptionTraceLength;
    }
  }

  static class HeartBeat {

    /**
     * Switch to enable / disable heartbeat
     */
    private boolean enabled = false;

    /**
     * The heartbeat interval in seconds.
     */
    private long heartBeatInterval = HeartBeatProvider.DEFAULT_HEARTBEAT_INTERVAL;

    /**
     * List of excluded heartbeat properties
     */
    private List<String> excludedHeartBeatProviderList = new ArrayList<>();

    /**
     * List of excluded heartbeat providers
     */
    private List<String> excludedHeartBeatPropertiesList = new ArrayList<>();

    public boolean isEnabled() {
      return enabled;
    }

    public void setEnabled(boolean enabled) {
      this.enabled = enabled;
    }

    public long getHeartBeatInterval() {
      return heartBeatInterval;
    }

    public void setHeartBeatInterval(long heartBeatInterval) {
      this.heartBeatInterval = heartBeatInterval;
    }

    public List<String> getExcludedHeartBeatProviderList() {
      return excludedHeartBeatProviderList;
    }

    public void setExcludedHeartBeatProviderList(List<String> excludedHeartBeatProviderList) {
      this.excludedHeartBeatProviderList = excludedHeartBeatProviderList;
    }

    public List<String> getExcludedHeartBeatPropertiesList() {
      return excludedHeartBeatPropertiesList;
    }

    public void setExcludedHeartBeatPropertiesList(List<String> excludedHeartBeatPropertiesList) {
      this.excludedHeartBeatPropertiesList = excludedHeartBeatPropertiesList;
    }
  }

  /**
   * This method is used to process and load list of JmxCounters provided in the configuration.
   * @param jmxCounterList
   */
   void processAndLoadJmxCounters(List<String> jmxCounterList) {

    try {
      Map<String, List<JmxAttributeData>> data = new HashMap<>();
      for (String jmxCounter : jmxCounterList) {
        CompositeJmxData compositeJmxData = convertToCompositeJmxData(jmxCounter);
        if (compositeJmxData == null) {
          InternalLogger.INSTANCE.warn("unable to add Jmx counter %s", jmxCounter);
        } else {
          List<JmxAttributeData> collection = data.get(compositeJmxData.getObjectName());
          if (collection == null) {
            collection = new ArrayList<>();
            data.put(compositeJmxData.getObjectName(), collection);
          }
          collection.add(new JmxAttributeData(compositeJmxData.getDisplayName(),
                  compositeJmxData.getAttributeName(), compositeJmxData.getType()));
        }
      }

      //Register each entry in performance counter container
      for (Map.Entry<String, List<JmxAttributeData>> entry : data.entrySet()) {
        try {
          if (PerformanceCounterContainer.INSTANCE.register(new JmxMetricPerformanceCounter(
                  entry.getKey(), entry.getKey(), entry.getValue()
          ))) {
            InternalLogger.INSTANCE.trace("Registered Jmx performance counter %s",
                    entry.getKey());
          }
          else {
            InternalLogger.INSTANCE.trace("Failed to register Jmx performance"
                    + " counter %s", entry.getKey());
          }
        }
        catch (Exception e) {
          InternalLogger.INSTANCE.warn("Failed to register Jmx performance counter,"
                  + " of object name %s Stack trace is %s", entry.getKey(), ExceptionUtils.getStackTrace(e));
        }
      }
    }
    catch (Exception e) {
      InternalLogger.INSTANCE.warn("Unable to add Jmx performance counter. Exception is"
              + " %s", ExceptionUtils.getStackTrace(e));
    }
  }

  /**
   * This Internal class is used to represent the Jmx Object Structure
   */
   private static class CompositeJmxData {
    String displayName;
    String objectName;
    String attributeName;
    String type;

    String getDisplayName() {
      return displayName;
    }

    void setDisplayName(String displayName) {
      this.displayName = displayName;
    }

    String getObjectName() {
      return objectName;
    }

    void setObjectName(String objectName) {
      this.objectName = objectName;
    }

    String getAttributeName() {
      return attributeName;
    }

    void setAttributeName(String attributeName) {
      this.attributeName = attributeName;
    }

    String getType() {
      return type;
    }

    void setType(String type) {
      this.type = type;
      if (this.type != null) {
        this.type = this.type.toUpperCase();
      }
    }
  }

  /**
   * This converts jmxCounter String to {@link CompositeJmxData} object
   * @param jmxCounter
   * @return CompositeJmxData object
   */
  private CompositeJmxData convertToCompositeJmxData(String jmxCounter) {
    if (jmxCounter != null && jmxCounter.length() > 0) {
      String[] attributes = jmxCounter.split("/");
      if (attributes.length < 3) {
        InternalLogger.INSTANCE.warn("Missing either objectName or attributeName or"
                + " display name. Jmx counter %s will not be added" , jmxCounter);
        return null;
      }
      CompositeJmxData data = new CompositeJmxData();
      for (int i = 0; i < attributes.length; ++i) {
        if (i > 3) break;
        if (i == 0) {
          data.setObjectName(attributes[0]);
        }
        else if (i == 1) {
          data.setAttributeName(attributes[1]);
        }
        else if (i == 2) {
          data.setDisplayName(attributes[2]);
        }
        else {
          data.setType(attributes[3]);
        }
      }
      return data;
    }
    return null;
  }
}
