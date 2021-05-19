
# agent-profiler

The agent-profiler subsystem provides a system for:
1. Generating profiles of the current JVM
1. Monitoring local telemetry and generating an alertBreach if certain conditions are
   met (such as breaching a threshold)


# Modules

- agent-alerting-api - The API that defines an alerting system that can monitor telemetry and alertBreach
  if certain conditions are met
- agent-alerting - An implementation of the agent-alerting-api which implements monitoring of telemetery
  applying a rolling average to the telemetry and then comparing against a threshold, and if breached
  issuing an alertBreach.
- agent-profiler-api - Defines the interface for a profiler subsystem.
- agent-profiler - Implements the agent-profiler-api providing a implementation that profiles using JFR and
  publishes profiles to the azure service profiler.


# USAGE

## Warning

The JFR profiler by default executes the "profile" profile from JFRs inbuilt configurations. This configuration includes
some potentially sensitive information such as environment variables, arguments provided to the JVM and processes
running on the system. If you wish to remove these from profiles that are uploaded you can do this by editing
the `lib/jfr/profile.jfc` file inside your Java installation. Profiles can also be generated/edited in the JDK Mission 
Control (JMC) user interface under the `Window->Flight Recording Template Manager` menu and control over individual 
flags is found inside `Edit->Advanced` of this user interface.

Some flags you may wish to disable are:

- jdk.JVMInformation
- jdk.InitialSystemProperty
- jdk.OSInformation
- jdk.InitialEnvironmentVariable
- jdk.SystemProcess

However, you should review all required flags.

## Requirements

- A JVM with JFR available.
- The application insights agent installed on your application and an application insights instance created.

## Configuration

Configuration of the profiler triggering settings, such as thresholds and profiling periods can be performed within the
ApplicationInsights UI under the Performance, Profiler, Triggers UI.

- APPLICATIONINSIGHTS_PROFILER_ENABLED: boolean (default false)
    - Can be set as an environment variable to enable the profiler.
    - While profiling is in beta it must be actively enabled.
- APPLICATIONINSIGHTS_PREVIEW_METRIC_INTERVAL_SECONDS: int (default 60)
    - Can be set as an environment variabl.
    - Period in seconds that performance counters are sampled. This directly impacts the frequency that
      Alerting thresholds are evaluated. Alerting thresholds by default apply an average over the last 2 min,
      at the default of 60 seconds this will be 2 samples.

Profiler may also be enabled via adding a config section to applicationinsights.json:

```
    preview {
        profiler {
            configPollPeriodSeconds: 60,
            enabled: true
        }
    }
```

The profiler periodically polls for configuration changes made within the UI. This can be adjusted via
`configPollPeriod`.



