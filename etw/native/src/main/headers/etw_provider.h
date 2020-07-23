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

#pragma once

#pragma comment(lib, "advapi32")

#include <windows.h>
#include <TraceLoggingProvider.h>
#include <string>
#include "com_microsoft_applicationinsights_agent_bootstrap_diagnostics_etw_EtwProvider.h"

TRACELOGGING_DECLARE_PROVIDER(provider_EtwHandle);

#define VERBOSE_EVENT_NAME "Verbose"
#define INFO_EVENT_NAME "Trace"
#define WARN_EVENT_NAME "Warn"
#define ERROR_EVENT_NAME "Error"
#define CRITICAL_EVENT_NAME "Critical"

#define WRITE_VERBOSE_EVENT(...) TraceLoggingWrite(provider_EtwHandle, VERBOSE_EVENT_NAME, TraceLoggingLevel(WINEVENT_LEVEL_VERBOSE), __VA_ARGS__)
#define WRITE_INFO_EVENT(...) TraceLoggingWrite(provider_EtwHandle, INFO_EVENT_NAME, TraceLoggingLevel(WINEVENT_LEVEL_INFO), __VA_ARGS__)
#define WRITE_WARN_EVENT(...) TraceLoggingWrite(provider_EtwHandle, WARN_EVENT_NAME, TraceLoggingLevel(WINEVENT_LEVEL_WARNING), __VA_ARGS__)
#define WRITE_ERROR_EVENT(...) TraceLoggingWrite(provider_EtwHandle, ERROR_EVENT_NAME, TraceLoggingLevel(WINEVENT_LEVEL_ERROR), __VA_ARGS__)
#define WRITE_CRITICAL_EVENT(...) TraceLoggingWrite(provider_EtwHandle, CRITICAL_EVENT_NAME, TraceLoggingLevel(WINEVENT_LEVEL_CRITICAL), __VA_ARGS__)

typedef int aijnierr_t;

#define AIJNIERR_SUCCESS            0x00

#define AIJNIERR_NULL_GETSTR        0x01
#define AIJNIERR_STRCPY             0x02
#define AIJNIERR_METHOD_NAME        0x03
#define AIJNIERR_EXCEPTION_RAISED   0x04
#define AIJNIERR_UNKONWN_EVENTID    0x05
#define AIJNIERR_CLASS_NOT_FOUND    0x06

#define JSTRID_MESSAGE              0x0100
#define JSTRID_EXTENSION_VERSION    0x0400
#define JSTRID_SUBSCRIPTION_ID      0x0500
#define JSTRID_APP_NAME             0x0600

#define STR_MAX_BUFF_SIZE   1024000

#define EVENTID_VERBOSE     5
#define EVENTID_INFO        4
#define EVENTID_WARN        3
#define EVENTID_ERROR       2
#define EVENTID_CRITICAL    1

char * jstring2cstr(JNIEnv * env, jstring &jstr_input, char * cstr_output, aijnierr_t field_id) throw(aijnierr_t);

char * stringGetter2cstr(JNIEnv * env, jobject &jobj_target, const char * method_name, char * rval, aijnierr_t field_id) throw(aijnierr_t);

void handleJstrException(JNIEnv * env, aijnierr_t jnierr) noexcept;

void handleGenericException(JNIEnv * env) noexcept;

std::string jstrid2name(int jnierr) noexcept;

int getEventId(JNIEnv * env, jobject &jobj_event) throw(aijnierr_t);

jthrowable newJniException(JNIEnv * env, const char * message) noexcept;

jthrowable newJniException(JNIEnv * env, const char * message, jthrowable cause) noexcept;

void javaThrowUnknownError(JNIEnv * env, std::string message) noexcept;

void javaThrowJniException(JNIEnv * env, std::string message) noexcept;

void writeEvent_IpaEtwEvent(JNIEnv * env, jobject &jobj_event, int event_id) noexcept;

