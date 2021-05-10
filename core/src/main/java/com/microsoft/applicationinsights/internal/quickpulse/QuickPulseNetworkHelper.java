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

import java.util.Date;


import com.azure.core.http.HttpHeader;
import com.azure.core.http.HttpHeaders;
import com.azure.core.http.HttpMethod;
import com.azure.core.http.HttpRequest;
import com.azure.core.http.HttpResponse;
import com.microsoft.applicationinsights.internal.util.LocalStringsUtils;

/**
 * Created by gupele on 12/12/2016.
 */
final class QuickPulseNetworkHelper {
    private final static long TICKS_AT_EPOCH = 621355968000000000L;
    private static final String HEADER_TRANSMISSION_TIME = "x-ms-qps-transmission-time";
    private final static String QPS_STATUS_HEADER = "x-ms-qps-subscribed";
    private final static String QPS_SERVICE_POLLING_INTERVAL_HINT  = "x-ms-qps-service-polling-interval-hint";
    private final static String QPS_SERVICE_ENDPOINT_REDIRECT   = "x-ms-qps-service-endpoint-redirect";
    private static final String QPS_INSTANCE_NAME_HEADER = "x-ms-qps-instance-name";
    private static final String QPS_STREAM_ID_HEADER = "x-ms-qps-stream-id";
    private static final String QPS_MACHINE_NAME_HEADER = "x-ms-qps-machine-name";
    private static final String QPS_ROLE_NAME_HEADER = "x-ms-qps-role-name";
    private static final String QPS_INVARIANT_VERSION_HEADER = "x-ms-qps-invariant-version";

    public HttpRequest buildPingRequest(Date currentDate, String address, String quickPulseId, String machineName, String roleName, String instanceName) {

        HttpRequest request = buildRequest(currentDate, address);
        request.setHeader(QPS_ROLE_NAME_HEADER, roleName);
        request.setHeader(QPS_MACHINE_NAME_HEADER, machineName);
        request.setHeader(QPS_STREAM_ID_HEADER, quickPulseId);
        request.setHeader(QPS_INSTANCE_NAME_HEADER, instanceName);
        request.setHeader(QPS_INVARIANT_VERSION_HEADER, Integer.toString(QuickPulse.QP_INVARIANT_VERSION));
        return request;
    }

    public HttpRequest buildRequest(Date currentDate, String address) {
        final long ticks = currentDate.getTime() * 10000 + TICKS_AT_EPOCH;

        HttpRequest request = new HttpRequest(HttpMethod.POST, address);
        request.setHeader(HEADER_TRANSMISSION_TIME, String.valueOf(ticks));
        return request;
    }

    public boolean isSuccess(HttpResponse response) {
        final int responseCode = response.getStatusCode();
        return responseCode == 200;
    }

    public QuickPulseHeaderInfo getQuickPulseHeaderInfo(HttpResponse response) {
        HttpHeaders headers = response.getHeaders();
        QuickPulseStatus status = QuickPulseStatus.ERROR;
        long servicePollingIntervalHint = -1;
        String serviceEndpointRedirect = null;
        final QuickPulseHeaderInfo quickPulseHeaderInfo;

        for (HttpHeader header: headers) {
            if (QPS_STATUS_HEADER.equalsIgnoreCase(header.getName())) {
                final String qpStatus = header.getValue();
                if ("true".equalsIgnoreCase(qpStatus)) {
                    status =  QuickPulseStatus.QP_IS_ON;
                } else {
                    status = QuickPulseStatus.QP_IS_OFF;
                }
            } else if (QPS_SERVICE_POLLING_INTERVAL_HINT.equalsIgnoreCase(header.getName())) {
                final String servicePollingIntervalHintHeaderValue = header.getValue();
                if (!LocalStringsUtils.isNullOrEmpty(servicePollingIntervalHintHeaderValue)) {
                    servicePollingIntervalHint = Long.parseLong(servicePollingIntervalHintHeaderValue);
                }
            } else if (QPS_SERVICE_ENDPOINT_REDIRECT.equalsIgnoreCase(header.getName())) {
                serviceEndpointRedirect = header.getValue();
            }
        }
        quickPulseHeaderInfo = new QuickPulseHeaderInfo(status, serviceEndpointRedirect, servicePollingIntervalHint);
        return quickPulseHeaderInfo;
    }
}
