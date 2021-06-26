package com.microsoft.applicationinsights.agent.internal.wasbootstrap;

import com.microsoft.applicationinsights.agent.internal.exporter.Exporter;
import com.microsoft.applicationinsights.agent.internal.wascore.common.Strings;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.tracer.ServerSpan;
import io.opentelemetry.sdk.trace.ReadWriteSpan;
import io.opentelemetry.sdk.trace.ReadableSpan;
import io.opentelemetry.sdk.trace.SpanProcessor;
import io.opentelemetry.semconv.trace.attributes.SemanticAttributes;

public class AiOperationNameSpanProcessor implements SpanProcessor {

  @Override
  public void onStart(Context parentContext, ReadWriteSpan span) {
    Span serverSpan = ServerSpan.fromContextOrNull(parentContext);
    if (!(serverSpan instanceof ReadableSpan)) {
      return;
    }
    span.setAttribute(Exporter.AI_OPERATION_NAME_KEY, getOperationName((ReadableSpan) serverSpan));
  }

  @Override
  public boolean isStartRequired() {
    return true;
  }

  @Override
  public void onEnd(ReadableSpan span) {}

  @Override
  public boolean isEndRequired() {
    return false;
  }

  private static String getOperationName(ReadableSpan serverSpan) {
    String spanName = serverSpan.getName();
    // calling toSpanData() is expensive, probably better to hack via reflection
    String httpMethod = serverSpan.toSpanData().getAttributes().get(SemanticAttributes.HTTP_METHOD);
    if (Strings.isNullOrEmpty(httpMethod)) {
      return spanName;
    }
    return httpMethod + " " + spanName;
  }
}
