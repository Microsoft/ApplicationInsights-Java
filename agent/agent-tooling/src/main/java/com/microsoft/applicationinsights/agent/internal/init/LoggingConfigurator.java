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

package com.microsoft.applicationinsights.agent.internal.init;

import static org.slf4j.Logger.ROOT_LOGGER_NAME;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.encoder.PatternLayoutEncoder;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.Appender;
import ch.qos.logback.core.ConsoleAppender;
import ch.qos.logback.core.encoder.Encoder;
import ch.qos.logback.core.encoder.LayoutWrappingEncoder;
import ch.qos.logback.core.rolling.FixedWindowRollingPolicy;
import ch.qos.logback.core.rolling.RollingFileAppender;
import ch.qos.logback.core.rolling.SizeAndTimeBasedRollingPolicy;
import ch.qos.logback.core.rolling.SizeBasedTriggeringPolicy;
import ch.qos.logback.core.util.FileSize;
import com.microsoft.applicationinsights.agent.bootstrap.diagnostics.DiagnosticsHelper;
import com.microsoft.applicationinsights.agent.bootstrap.diagnostics.etw.EtwAppender;
import com.microsoft.applicationinsights.agent.bootstrap.diagnostics.log.ApplicationInsightsDiagnosticsLogFilter;
import com.microsoft.applicationinsights.agent.bootstrap.diagnostics.log.ApplicationInsightsJsonLayout;
import com.microsoft.applicationinsights.agent.bootstrap.diagnostics.log.MoshiJsonFormatter;
import com.microsoft.applicationinsights.agent.internal.configuration.Configuration;
import java.nio.file.Path;
import org.slf4j.LoggerFactory;

public class LoggingConfigurator {

  private final LoggerContext loggerContext;

  private final String destination;

  private final Path filePath;
  private final int fileMaxSizeMb;
  private final int fileMaxHistory;

  private final LoggingLevelConfigurator loggingLevelConfigurator;

  LoggingConfigurator(Configuration.SelfDiagnostics selfDiagnostics, Path agentPath) {
    loggerContext = (LoggerContext) LoggerFactory.getILoggerFactory();

    destination = selfDiagnostics.destination;
    filePath = agentPath.resolveSibling(selfDiagnostics.file.path);
    fileMaxSizeMb = selfDiagnostics.file.maxSizeMb;
    fileMaxHistory = selfDiagnostics.file.maxHistory;

    loggingLevelConfigurator = new LoggingLevelConfigurator(selfDiagnostics.level);
  }

  void configure() {
    loggerContext.getLogger(ROOT_LOGGER_NAME).detachAndStopAllAppenders();

    if (DiagnosticsHelper.useAppSvcRpIntegrationLogging()) {
      configureAppSvcs();
    } else if (destination == null || destination.equalsIgnoreCase("file+console")) {
      configureFileAndConsole();
    } else if (destination.equalsIgnoreCase("file")) {
      configureFile();
    } else if (destination.equalsIgnoreCase("console")) {
      configureConsole();
    } else {
      throw new IllegalStateException("Unknown self-diagnostics destination: " + destination);
    }
  }

  private void configureAppSvcs() {
    Logger rootLogger = loggerContext.getLogger(ROOT_LOGGER_NAME);
    rootLogger.addAppender(configureFileAppender());
    rootLogger.addAppender(configureConsoleAppender());

    // App Services linux environments set this environment variable to control where the internal
    // diagnostic log is written, so that App Services can consume that file and send those logging
    // events to an internal kusto store for internal alerting and diagnostics
    String diagnosticsOutputDirectory =
        System.getenv(DiagnosticsHelper.APPLICATIONINSIGHTS_DIAGNOSTICS_OUTPUT_DIRECTORY);
    if (diagnosticsOutputDirectory != null && !diagnosticsOutputDirectory.isEmpty()) {
      rootLogger.addAppender(configureDiagnosticAppender(diagnosticsOutputDirectory));
    }

    // App Services windows environments use ETW to consume internal diagnostics logging events and
    // to send those logging events to an internal kusto store for internal alerting and diagnostics
    if (DiagnosticsHelper.isOsWindows()) {
      rootLogger.addAppender(configureEtwAppender());
    }

    configureLoggingLevels();
  }

  private void configureFileAndConsole() {
    Logger rootLogger = loggerContext.getLogger(ROOT_LOGGER_NAME);
    rootLogger.addAppender(configureFileAppender());
    rootLogger.addAppender(configureConsoleAppender());

    configureLoggingLevels();
    // these messages are specifically designed for attach
    loggerContext.getLogger("applicationinsights.extension.diagnostics").setLevel(Level.OFF);
  }

  private void configureFile() {
    Logger rootLogger = loggerContext.getLogger(ROOT_LOGGER_NAME);
    rootLogger.addAppender(configureFileAppender());

    configureLoggingLevels();
    // these messages are specifically designed for attach
    loggerContext.getLogger("applicationinsights.extension.diagnostics").setLevel(Level.OFF);
  }

  private void configureConsole() {
    Logger rootLogger = loggerContext.getLogger(ROOT_LOGGER_NAME);
    rootLogger.addAppender(configureConsoleAppender());

    configureLoggingLevels();
    // these messages are specifically designed for attach
    loggerContext.getLogger("applicationinsights.extension.diagnostics").setLevel(Level.OFF);
  }

  private Appender<ILoggingEvent> configureFileAppender() {
    Path logFileNamePath = filePath.getFileName();
    if (logFileNamePath == null) {
      throw new IllegalStateException("Unexpected empty self-diagnostics file path");
    }
    String logFileName = logFileNamePath.toString();
    String rollingFileName;
    int index = logFileName.lastIndexOf('.');
    if (index != -1) {
      rollingFileName = logFileName.substring(0, index) + ".%i" + logFileName.substring(index);
    } else {
      rollingFileName = logFileName + ".%i";
    }
    Path rollingFilePath = filePath.resolveSibling(rollingFileName);

    RollingFileAppender<ILoggingEvent> appender = new RollingFileAppender<>();
    appender.setContext(loggerContext);
    appender.setName("FILE");
    appender.setFile(filePath.toString());

    FixedWindowRollingPolicy rollingPolicy = new FixedWindowRollingPolicy();
    rollingPolicy.setContext(loggerContext);
    rollingPolicy.setFileNamePattern(rollingFilePath.toString());
    rollingPolicy.setMinIndex(1);
    rollingPolicy.setMaxIndex(fileMaxHistory);
    rollingPolicy.setParent(appender);
    rollingPolicy.start();

    appender.setRollingPolicy(rollingPolicy);

    SizeBasedTriggeringPolicy<ILoggingEvent> triggeringPolicy = new SizeBasedTriggeringPolicy<>();
    triggeringPolicy.setContext(loggerContext);
    triggeringPolicy.setMaxFileSize(new FileSize(fileMaxSizeMb * 1024L * 1024L));
    triggeringPolicy.start();

    appender.setTriggeringPolicy(triggeringPolicy);

    appender.setEncoder(createEncoder());
    appender.start();

    return appender;
  }

  private Appender<ILoggingEvent> configureConsoleAppender() {
    ConsoleAppender<ILoggingEvent> appender = new ConsoleAppender<>();
    appender.setContext(loggerContext);
    appender.setName("CONSOLE");

    appender.setEncoder(createEncoder());
    appender.start();

    return appender;
  }

  private Appender<ILoggingEvent> configureDiagnosticAppender(String diagnosticsOutputDirectory) {
    RollingFileAppender<ILoggingEvent> appender = new RollingFileAppender<>();
    appender.setContext(loggerContext);
    appender.setName("DIAGNOSTICS_FILE");
    appender.setFile(diagnosticsOutputDirectory + "/applicationinsights-extension.log");

    SizeAndTimeBasedRollingPolicy<ILoggingEvent> rollingPolicy =
        new SizeAndTimeBasedRollingPolicy<>();
    rollingPolicy.setContext(loggerContext);
    rollingPolicy.setFileNamePattern(
        diagnosticsOutputDirectory + "/applicationinsights-extension-%d{yyyy-MM-dd}.%i.log.old");
    rollingPolicy.setMaxHistory(1);
    rollingPolicy.setTotalSizeCap(new FileSize(10 * 1024 * 1024));
    rollingPolicy.setMaxFileSize(new FileSize(5 * 1024 * 1024));
    rollingPolicy.setParent(appender);
    rollingPolicy.start();

    appender.setRollingPolicy(rollingPolicy);

    LayoutWrappingEncoder<ILoggingEvent> encoder = new LayoutWrappingEncoder<>();
    encoder.setContext(loggerContext);

    ApplicationInsightsJsonLayout layout = new ApplicationInsightsJsonLayout();
    layout.setContext(loggerContext);
    layout.setTimestampFormat("yyyy-MM-dd'T'HH:mm:ss.SSSX");
    layout.setTimestampFormatTimezoneId("Etc/UTC");
    layout.setAppendLineSeparator(true);

    MoshiJsonFormatter jsonFormatter = new MoshiJsonFormatter();
    jsonFormatter.setPrettyPrint(false);

    layout.setJsonFormatter(jsonFormatter);
    layout.start();

    encoder.setLayout(layout);
    encoder.start();

    appender.setEncoder(encoder);

    ApplicationInsightsDiagnosticsLogFilter filter = new ApplicationInsightsDiagnosticsLogFilter();
    filter.setContext(loggerContext);
    filter.start();

    appender.addFilter(filter);
    appender.start();

    return appender;
  }

  private Appender<ILoggingEvent> configureEtwAppender() {
    EtwAppender appender = new EtwAppender();
    appender.setContext(loggerContext);
    appender.setName("ETW_PROVIDER");

    ApplicationInsightsDiagnosticsLogFilter filter = new ApplicationInsightsDiagnosticsLogFilter();
    filter.setContext(loggerContext);
    filter.start();

    appender.addFilter(filter);
    appender.start();

    return appender;
  }

  private void configureLoggingLevels() {
    loggingLevelConfigurator.updateLoggerLevel(loggerContext.getLogger("reactor.netty"));
    loggingLevelConfigurator.updateLoggerLevel(loggerContext.getLogger("io.grpc.Context"));
    loggingLevelConfigurator.updateLoggerLevel(loggerContext.getLogger("muzzleMatcher"));
    loggingLevelConfigurator.updateLoggerLevel(
        loggerContext.getLogger("com.microsoft.applicationinsights"));
    loggingLevelConfigurator.updateLoggerLevel(loggerContext.getLogger(ROOT_LOGGER_NAME));
  }

  private Encoder<ILoggingEvent> createEncoder() {
    PatternLayoutEncoder encoder = new PatternLayoutEncoder();
    encoder.setContext(loggerContext);
    encoder.setPattern("%d{yyyy-MM-dd HH:mm:ss.SSSX} %-5level %logger{36} - %msg%n");
    encoder.start();
    return encoder;
  }
}
