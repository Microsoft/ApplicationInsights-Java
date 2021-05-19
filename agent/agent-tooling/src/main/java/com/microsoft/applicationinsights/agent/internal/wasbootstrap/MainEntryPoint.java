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
package com.microsoft.applicationinsights.agent.internal.wasbootstrap;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.lang.instrument.Instrumentation;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Locale;

import com.microsoft.applicationinsights.agent.bootstrap.diagnostics.DiagnosticsHelper;
import com.microsoft.applicationinsights.agent.bootstrap.diagnostics.SdkVersionFinder;
import com.microsoft.applicationinsights.agent.bootstrap.diagnostics.status.StatusFile;
import com.microsoft.applicationinsights.agent.internal.AiComponentInstaller;
import com.microsoft.applicationinsights.agent.internal.wasbootstrap.configuration.Configuration;
import com.microsoft.applicationinsights.agent.internal.wasbootstrap.configuration.Configuration.SelfDiagnostics;
import com.microsoft.applicationinsights.agent.internal.wasbootstrap.configuration.ConfigurationBuilder;
import com.microsoft.applicationinsights.agent.internal.wasbootstrap.configuration.RpConfiguration;
import com.microsoft.applicationinsights.agent.internal.wasbootstrap.configuration.RpConfigurationBuilder;
import com.microsoft.applicationinsights.customExceptions.FriendlyException;
import io.opentelemetry.javaagent.tooling.AgentInstaller;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.slf4j.event.Level;

// this class initializes configuration and logging before passing control to opentelemetry-java-instrumentation
public class MainEntryPoint {

    private static RpConfiguration rpConfiguration;
    private static Configuration configuration;

    private MainEntryPoint() {
    }

    public static RpConfiguration getRpConfiguration() {
        return rpConfiguration;
    }

    public static Configuration getConfiguration() {
        return configuration;
    }

    // TODO turn this into an interceptor
    public static void start(Instrumentation instrumentation, URL bootstrapURL) {
        boolean success = false;
        Logger startupLogger = null;
        String version = SdkVersionFinder.readVersion();
        try {
            Path agentPath = new File(bootstrapURL.toURI()).toPath();
            DiagnosticsHelper.setAgentJarFile(agentPath);
            // configuration is only read this early in order to extract logging configuration
            rpConfiguration = RpConfigurationBuilder.create(agentPath);
            configuration = ConfigurationBuilder.create(agentPath, rpConfiguration);
            startupLogger = configureLogging(configuration.selfDiagnostics, agentPath);
            ConfigurationBuilder.logConfigurationWarnMessages();
            MDC.put(DiagnosticsHelper.MDC_PROP_OPERATION, "Startup");
            // TODO convert to agent builder concept
            AiComponentInstaller.setInstrumentation(instrumentation);
            AgentInstaller.installBytebuddyAgent(instrumentation, ConfigOverride.getConfig(configuration), false);
            startupLogger.info("ApplicationInsights Java Agent {} started successfully", version);
            success = true;
            LoggerFactory.getLogger(DiagnosticsHelper.DIAGNOSTICS_LOGGER_NAME)
                    .info("Application Insights Codeless Agent {} Attach Successful", version);
        } catch (ThreadDeath td) {
            throw td;
        } catch (Throwable t) {

            FriendlyException friendlyException = getFriendlyException(t);
            String banner = "ApplicationInsights Java Agent " + version + " failed to start";
            if (friendlyException != null) {
                logErrorMessage(startupLogger, friendlyException.getMessageWithBanner(banner), true, t, bootstrapURL);
            } else {
                logErrorMessage(startupLogger, banner, false, t, bootstrapURL);
            }

        } finally {
            try {
                StatusFile.putValueAndWrite("AgentInitializedSuccessfully", success, startupLogger != null);
            } catch (Exception e) {
                if (startupLogger != null) {
                    startupLogger.error("Error writing status.json", e);
                } else {
                    e.printStackTrace();
                }
            }
            MDC.clear();
        }
    }

    // visible for testing
    static FriendlyException getFriendlyException(Throwable t) {
        if (t instanceof FriendlyException) {
            return (FriendlyException) t;
        }
        Throwable cause = t.getCause();
        if (cause == null) {
            return null;
        }
        return getFriendlyException(cause);
    }

    private static void logErrorMessage(Logger startupLogger, String message, boolean isFriendlyException, Throwable t, URL bootstrapURL) {

        if (startupLogger != null) {
            if (isFriendlyException) {
                startupLogger.error(message);
            } else {
                startupLogger.error(message, t);
            }
        } else {
            try {
                // IF the startupLogger failed to be initialized due to configuration syntax error, try initializing it here
                Path agentPath = new File(bootstrapURL.toURI()).toPath();
                SelfDiagnostics selfDiagnostics = new SelfDiagnostics();
                selfDiagnostics.file.path = ConfigurationBuilder.overlayWithEnvVar(
                        ConfigurationBuilder.APPLICATIONINSIGHTS_SELF_DIAGNOSTICS_FILE_PATH, selfDiagnostics.file.path);
                startupLogger = configureLogging(selfDiagnostics, agentPath);
                if (isFriendlyException) {
                    startupLogger.error(message);
                } else {
                    startupLogger.error(message, t);
                }
            } catch (Throwable ignored) {
                // this is a last resort in cases where the JVM doesn't have write permission to the directory where the agent lives
                // and the user has not specified APPLICATIONINSIGHTS_SELF_DIAGNOSTICS_FILE_PATH

                // If the startupLogger still have some issues being initialized, print the error stack trace to stderr
                if (isFriendlyException) {
                    System.err.println(message);
                } else {
                    t.printStackTrace();
                }
                // and write to a temp file because some environments do not have (easy) access to stderr
                String tmpDir = System.getProperty("java.io.tmpdir");
                File file = new File(tmpDir, "applicationinsights.log");
                try {
                    Writer out = new OutputStreamWriter(new FileOutputStream(file), StandardCharsets.UTF_8);
                    out.write(message);
                    out.close();
                } catch (Throwable ignored2) {
                }
            }
        }
    }

    private static Logger configureLogging(SelfDiagnostics selfDiagnostics, Path agentPath) {
        String logbackXml;
        String destination = selfDiagnostics.destination;
        if (DiagnosticsHelper.useAppSvcRpIntegrationLogging()) {
            // Diagnostics IPA log file location. Disabled by default.
            final String internalLogOutputLocation = System.getenv(DiagnosticsHelper.APPLICATIONINSIGHTS_DIAGNOSTICS_OUTPUT_DIRECTORY);
            if (internalLogOutputLocation == null || internalLogOutputLocation.isEmpty()) {
                System.setProperty("ai.config.appender.diagnostics.location", "");
            }

            // Diagnostics IPA ETW provider. Windows-only.
            if (!DiagnosticsHelper.isOsWindows()) {
                System.setProperty("ai.config.appender.etw.location", "");
            }
            logbackXml = "applicationinsights.appsvc.logback.xml";
        } else if (destination == null || destination.equalsIgnoreCase("file+console")) {
            logbackXml = "applicationinsights.file-and-console.logback.xml";
        } else if (destination.equalsIgnoreCase("file")) {
            logbackXml = "applicationinsights.file.logback.xml";
        } else if (destination.equalsIgnoreCase("console")) {
            logbackXml = "applicationinsights.console.logback.xml";
        } else {
            throw new IllegalStateException("Unknown self-diagnostics destination: " + destination);
        }
        ClassLoader cl = MainEntryPoint.class.getClassLoader();
        if (cl == null) {
            cl = ClassLoader.getSystemClassLoader();
        }
        final URL configurationFile = cl.getResource(logbackXml);

        Level level = getLevel(selfDiagnostics.level);

        Path logFilePath = agentPath.resolveSibling(selfDiagnostics.file.path);
        Path logFileNamePath = logFilePath.getFileName();
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
        Path rollingFilePath = logFilePath.resolveSibling(rollingFileName);

        // never want to log apache http at trace or debug, it's just way to verbose
        Level atLeastInfoLevel = getMaxLevel(level, Level.INFO);

        Level otherLibsLevel = level == Level.INFO ? Level.WARN : level;

        // TODO need something more reliable, currently will log too much WARN if "muzzleMatcher" logger name changes
        // muzzleMatcher logs at WARN level in order to make them visible, but really should only be enabled when debugging
        Level muzzleMatcherLevel = level.toInt() <= Level.DEBUG.toInt() ? level : getMaxLevel(level, Level.ERROR);

        try {
            System.setProperty("applicationinsights.logback.configurationFile", configurationFile.toString());

            System.setProperty("applicationinsights.logback.file.path", logFilePath.toString());
            System.setProperty("applicationinsights.logback.file.rollingPath", rollingFilePath.toString());
            System.setProperty("applicationinsights.logback.file.maxSize", selfDiagnostics.file.maxSizeMb + "MB");
            System.setProperty("applicationinsights.logback.file.maxIndex", Integer.toString(selfDiagnostics.file.maxHistory));

            System.setProperty("applicationinsights.logback.level", level.toString());
            System.setProperty("applicationinsights.logback.level.other", otherLibsLevel.toString());
            System.setProperty("applicationinsights.logback.level.atLeastInfo", atLeastInfoLevel.toString());
            System.setProperty("applicationinsights.logback.level.muzzleMatcher", muzzleMatcherLevel.toString());

            return LoggerFactory.getLogger("com.microsoft.applicationinsights.agent");
        } finally {
            System.clearProperty("applicationinsights.logback.configurationFile");
            System.clearProperty("applicationinsights.logback.file.path");
            System.clearProperty("applicationinsights.logback.file.rollingPath");
            System.clearProperty("applicationinsights.logback.file.maxSize");
            System.clearProperty("applicationinsights.logback.file.maxIndex");
            System.clearProperty("applicationinsights.logback.level");
            System.clearProperty("applicationinsights.logback.level.other");
            System.clearProperty("applicationinsights.logback.level.atLeastInfo");
            System.clearProperty("applicationinsights.logback.level.muzzleMatcher");
        }
    }

    private static Level getMaxLevel(Level level1, Level level2) {
        return level1.toInt() >= level2.toInt() ? level1 : level2;
    }

    private static Level getLevel(String levelStr) {
        try {
            return Level.valueOf(levelStr.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException("Unexpected self-diagnostic level: " + levelStr);
        }
    }
}
