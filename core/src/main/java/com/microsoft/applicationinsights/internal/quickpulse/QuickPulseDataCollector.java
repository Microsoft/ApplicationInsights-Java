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

package com.microsoft.applicationinsights.internal.quickpulse;

import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;

import java.time.Duration;
import java.util.StringTokenizer;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import com.azure.monitor.opentelemetry.exporter.implementation.models.*;
import com.microsoft.applicationinsights.TelemetryConfiguration;
import com.microsoft.applicationinsights.internal.perfcounter.CpuPerformanceCounterCalculator;
import org.slf4j.LoggerFactory;

/**
 * Created by gupele on 12/5/2016.
 */
public enum QuickPulseDataCollector {
    INSTANCE;

    private TelemetryConfiguration config;

    static class FinalCounters {
        public final double exceptions;
        public final long requests;
        public final double requestsDuration;
        public final long unsuccessfulRequests;
        public final long rdds;
        public final double rddsDuration;
        public final long unsuccessfulRdds;
        public final long memoryCommitted;
        public final double cpuUsage;

        public FinalCounters(Counters currentCounters, MemoryMXBean memory, CpuPerformanceCounterCalculator cpuPerformanceCounterCalculator) {
            if (memory != null && memory.getHeapMemoryUsage() != null) {
                memoryCommitted = memory.getHeapMemoryUsage().getCommitted();
            } else {
                memoryCommitted = -1;
            }

            Double cpuDatum;
            if (cpuPerformanceCounterCalculator != null
                    && (cpuDatum = cpuPerformanceCounterCalculator.getProcessCpuUsage()) != null) {
                // normally I wouldn't do this, but I prefer to avoid code duplication more than one-liners :)
                cpuUsage = cpuDatum;
            } else {
                cpuUsage = -1;
            }
            exceptions = currentCounters.exceptions.get();

            CountAndDuration countAndDuration = Counters.decodeCountAndDuration(currentCounters.requestsAndDurations.get());
            requests = countAndDuration.count;
            this.requestsDuration = countAndDuration.duration;
            this.unsuccessfulRequests = currentCounters.unsuccessfulRequests.get();

            countAndDuration = Counters.decodeCountAndDuration(currentCounters.rddsAndDuations.get());
            this.rdds = countAndDuration.count;
            this.rddsDuration = countAndDuration.duration;
            this.unsuccessfulRdds = currentCounters.unsuccessfulRdds.get();
        }
    }

    static class CountAndDuration {
        public final long count;
        public final long duration;

        private CountAndDuration(long count, long duration) {
            this.count = count;
            this.duration = duration;
        }
    }

    static class Counters {
        private static final long MAX_COUNT = 524287L;
        private static final long MAX_DURATION = 17592186044415L;

        public final AtomicInteger exceptions = new AtomicInteger(0);

        final AtomicLong requestsAndDurations = new AtomicLong(0);
        final AtomicInteger unsuccessfulRequests = new AtomicInteger(0);

        final AtomicLong rddsAndDuations = new AtomicLong(0);
        final AtomicInteger unsuccessfulRdds = new AtomicInteger(0);

        static long encodeCountAndDuration(long  count, long duration) {
            if (count > MAX_COUNT || duration > MAX_DURATION) {
                return 0;
            }

            return (count << 44) + duration;
        }

        static CountAndDuration decodeCountAndDuration(long countAndDuration) {
            return new CountAndDuration(countAndDuration >> 44, countAndDuration & MAX_DURATION);
        }
    }

    private final AtomicReference<Counters> counters = new AtomicReference<>(null);
    private final MemoryMXBean memory;
    private final CpuPerformanceCounterCalculator cpuPerformanceCounterCalculator;

    QuickPulseDataCollector() {
        CpuPerformanceCounterCalculator temp;
        try {
            temp = new CpuPerformanceCounterCalculator();
        } catch (ThreadDeath td) {
            throw td;
        } catch (Throwable t) {
            try {
                LoggerFactory.getLogger(QuickPulseDataCollector.class)
                        .error("Could not initialize {}", CpuPerformanceCounterCalculator.class.getSimpleName(), t);
            } catch (ThreadDeath td) {
                throw td;
            } catch (Throwable t2) {
                // chomp
            }
            temp = null;
        }
        cpuPerformanceCounterCalculator = temp;
        memory = ManagementFactory.getMemoryMXBean();
    }

    public synchronized void disable() {
        counters.set(null);
    }

    public synchronized void enable(TelemetryConfiguration config) {
        this.config = config;
        counters.set(new Counters());
    }

    public synchronized FinalCounters getAndRestart() {
        final Counters currentCounters = counters.getAndSet(new Counters());
        if (currentCounters != null) {
            return new FinalCounters(currentCounters, memory, cpuPerformanceCounterCalculator);
        }

        return null;
    }

    /*@VisibleForTesting*/
    synchronized FinalCounters peek() {
        final Counters currentCounters = this.counters.get(); // this should be the only differece
        if (currentCounters != null) {
            return new FinalCounters(currentCounters, memory, cpuPerformanceCounterCalculator);
        }
        return null;
    }

    public void add(TelemetryItem telemetryItem) {
        if (!telemetryItem.getInstrumentationKey().equals(getInstrumentationKey())) {
            return;
        }

        MonitorDomain data = telemetryItem.getData().getBaseData();
        if (data instanceof RequestData) {
            RequestData requestTelemetry = (RequestData)data;
            addRequest(requestTelemetry);
        } else if (data instanceof RemoteDependencyData) {
            addDependency((RemoteDependencyData) data);
        } else if (data instanceof TelemetryExceptionData) {
            addException();
        }
    }

    private synchronized String getInstrumentationKey() {
        return config.getInstrumentationKey();
    }

    private void addDependency(RemoteDependencyData telemetry) {
        Counters counters = this.counters.get();
        if (counters == null) {
            return;
        }
        counters.rddsAndDuations.addAndGet(
                Counters.encodeCountAndDuration(1, toMilliseconds(telemetry.getDuration())));
        Boolean success = telemetry.isSuccess();
        if (success != null && !success) { // success should not be null
            counters.unsuccessfulRdds.incrementAndGet();
        }
    }

    private void addException() {
        Counters counters = this.counters.get();
        if (counters == null) {
            return;
        }

        counters.exceptions.incrementAndGet();
    }

    private void addRequest(RequestData requestTelemetry) {
        Counters counters = this.counters.get();
        if (counters == null) {
            return;
        }

        counters.requestsAndDurations.addAndGet(Counters.encodeCountAndDuration(1, toMilliseconds(requestTelemetry.getDuration())));
        if (!requestTelemetry.isSuccess()) {
            counters.unsuccessfulRequests.incrementAndGet();
        }
    }

    // FIXME (trask) move live metrics request capture to OpenTelemetry layer so don't have to parse String duration?
    private static long toMilliseconds(String duration) {
        // format is DD.HH:MM:SS.MMMMMM
        StringTokenizer tokenizer = new StringTokenizer(duration, ".:");
        int days = Integer.parseInt(tokenizer.nextToken());
        int hours = Integer.parseInt(tokenizer.nextToken());
        int minutes = Integer.parseInt(tokenizer.nextToken());
        int seconds = Integer.parseInt(tokenizer.nextToken());
        int microseconds = Integer.parseInt(tokenizer.nextToken());

        long x = Duration.ofDays(days)
                .plusHours(hours)
                .plusMinutes(minutes)
                .plusSeconds(seconds)
                .plusNanos(microseconds * 1000L)
                .toMillis();
        System.out.println(duration + " --> " + x);
        return x;
    }
}
