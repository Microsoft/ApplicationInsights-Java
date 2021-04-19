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

package com.microsoft.applicationinsights.internal.statsbeat;

import com.microsoft.applicationinsights.TelemetryClient;
import com.microsoft.applicationinsights.internal.util.ThreadPoolUtils;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static com.microsoft.applicationinsights.internal.statsbeat.Constants.CUSTOM_DIMENSIONS_FEATURE;
import static com.microsoft.applicationinsights.internal.statsbeat.Constants.DEFAULT_STATSBEAT_INTERVAL;
import static com.microsoft.applicationinsights.internal.statsbeat.Constants.FEATURE;
import static com.microsoft.applicationinsights.internal.statsbeat.Constants.JAVA_VENDOR_OTHER;
import static com.microsoft.applicationinsights.internal.statsbeat.StatsbeatHelper.FEATURE_MAP;

public class FeatureStatsbeat extends BaseStatsbeat {

    private static Set<String> featureList = new HashSet<>(64);

    public FeatureStatsbeat() {
        super();
        initFeatureList();
        scheduledExecutor = Executors.newSingleThreadScheduledExecutor(ThreadPoolUtils.createDaemonThreadFactory(FeatureStatsbeat.class));
        setFrequencyInterval(TimeUnit.DAYS.toSeconds(1));
    }

    /**
     * @return a 64-bit long that represents a list of features enabled. Each bitfield maps to a feature.
     */
    private long getFeature() {
        return StatsbeatHelper.encodeFeature(featureList);
    }

    @Override
    public void send(TelemetryClient telemetryClient) {
        StatsbeatTelemetry statsbeatTelemetry = createStatsbeatTelemetry(FEATURE, 0);
        statsbeatTelemetry.getProperties().put(CUSTOM_DIMENSIONS_FEATURE, String.valueOf(getFeature()));
        telemetryClient.track(statsbeatTelemetry);
    }

    private void initFeatureList() {
        // track java distribution
        String javaVendor = System.getProperty("java.vendor");
        if (javaVendor != null && !javaVendor.isEmpty()) {
            if (FEATURE_MAP.get(javaVendor) == null) {
                featureList.add(JAVA_VENDOR_OTHER);
            } else {
                featureList.add(javaVendor);
            }
        }
    }
}