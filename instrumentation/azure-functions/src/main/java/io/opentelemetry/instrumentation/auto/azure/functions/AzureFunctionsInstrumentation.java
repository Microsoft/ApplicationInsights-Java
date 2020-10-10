/*
 * Copyright The OpenTelemetry Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.opentelemetry.instrumentation.auto.azure.functions;

import static io.opentelemetry.instrumentation.auto.azure.functions.InvocationRequestExtractAdapter.GETTER;
import static io.opentelemetry.instrumentation.auto.azure.functions.InvocationRequestExtractAdapter.TRACER;
import static java.util.Collections.singletonMap;
import static net.bytebuddy.matcher.ElementMatchers.isMethod;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;

import com.google.auto.service.AutoService;
import com.google.common.base.Strings;
import io.grpc.Context;
import io.opentelemetry.OpenTelemetry;
import io.opentelemetry.context.Scope;
import io.opentelemetry.instrumentation.api.aiconnectionstring.AiConnectionString;
import io.opentelemetry.javaagent.tooling.Instrumenter;
import io.opentelemetry.trace.DefaultSpan;
import io.opentelemetry.trace.SpanContext;
import io.opentelemetry.trace.TracingContextUtils;
import java.util.Map;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

@AutoService(Instrumenter.class)
public class AzureFunctionsInstrumentation extends Instrumenter.Default {

  public AzureFunctionsInstrumentation() {
    super("azure-functions");
  }

  @Override
  public ElementMatcher<? super TypeDescription> typeMatcher() {
    return named("com.microsoft.azure.functions.worker.handler.InvocationRequestHandler");
  }

  @Override
  public Map<? extends ElementMatcher<? super MethodDescription>, String> transformers() {
    System.out.println("######### transformers starts ########");
    return singletonMap(
        isMethod()
            .and(named("execute"))
            .and(
                takesArgument(
                    0, named("com.microsoft.azure.functions.rpc.messages.InvocationRequest"))),
        AzureFunctionsInstrumentation.class.getName() + "$InvocationRequestAdvice");
  }

  @Override
  public String[] helperClassNames() {
    return new String[] {packageName + ".InvocationRequestExtractAdapter"};
  }

  public static class InvocationRequestAdvice {
    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static Scope methodEnter(@Advice.Argument(0) final Object request)
        throws ReflectiveOperationException {
      System.out.println("######### start intercepting AzureFunction specialization request");
      // race condition (two initial requests happening at the same time) is not a worry here
      // because at worst they both enter the condition below and update the connection string
      System.out.println("######### start lazilySetConnectionString");
      if (!AiConnectionString.hasConnectionString()) {
        String connectionString = System.getenv("APPLICATIONINSIGHTS_CONNECTION_STRING");
        if (!Strings.isNullOrEmpty(connectionString)) {
          AiConnectionString.setConnectionString(connectionString);
          // TODO to be deleted later once the testing is completed
          System.out.println("######### Lazily set the connection string for Azure Function Linux Consumption Plan" + connectionString);
        } else {
          // if the instrumentation key is neither null nor empty , we will create a default connection string based on the instrumentation key.
          System.out.println("######### Connection string is null or empty for Azure Function Linux Consumption Plan.");
          String instrumentationKey = System.getenv("APPINSIGHTS_INSTRUMENTATIONKEY");
          if (!Strings.isNullOrEmpty(instrumentationKey)) {
            AiConnectionString.setConnectionString("InstrumentationKey=" + instrumentationKey);
          }
        }
      } else {
        // TODO to be deleted later once the testing is completed
        System.out.println("######### Connection string has already been set.");
      }

      System.out.println("######### end lazilySetConnectionString");
      final Object traceContext =
          InvocationRequestExtractAdapter.getTraceContextMethod.invoke(request);

      final Context extractedContext =
          OpenTelemetry.getPropagators()
              .getTextMapPropagator()
              .extract(Context.ROOT, traceContext, GETTER);
      final SpanContext spanContext = TracingContextUtils.getSpan(extractedContext).getContext();

      return TRACER.withSpan(DefaultSpan.create(spanContext));
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
    public static void methodExit(@Advice.Enter final Scope scope) {
      scope.close();
    }

  }
}