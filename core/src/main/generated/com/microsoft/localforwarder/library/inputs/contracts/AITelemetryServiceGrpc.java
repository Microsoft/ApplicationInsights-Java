package com.microsoft.localforwarder.library.inputs.contracts;

import static io.grpc.MethodDescriptor.generateFullMethodName;
import static io.grpc.stub.ClientCalls.asyncBidiStreamingCall;
import static io.grpc.stub.ClientCalls.asyncClientStreamingCall;
import static io.grpc.stub.ClientCalls.asyncServerStreamingCall;
import static io.grpc.stub.ClientCalls.asyncUnaryCall;
import static io.grpc.stub.ClientCalls.blockingServerStreamingCall;
import static io.grpc.stub.ClientCalls.blockingUnaryCall;
import static io.grpc.stub.ClientCalls.futureUnaryCall;
import static io.grpc.stub.ServerCalls.asyncBidiStreamingCall;
import static io.grpc.stub.ServerCalls.asyncClientStreamingCall;
import static io.grpc.stub.ServerCalls.asyncServerStreamingCall;
import static io.grpc.stub.ServerCalls.asyncUnaryCall;
import static io.grpc.stub.ServerCalls.asyncUnimplementedStreamingCall;
import static io.grpc.stub.ServerCalls.asyncUnimplementedUnaryCall;

/**
 * <pre>
 * gRPC service to transmit telemetry
 * </pre>
 */
@javax.annotation.Generated(
    value = "by gRPC proto compiler (version 1.16.1)",
    comments = "Source: TelemetryBatch.proto")
public final class AITelemetryServiceGrpc {

  private AITelemetryServiceGrpc() {}

  public static final String SERVICE_NAME = "contracts.AITelemetryService";

  // Static method descriptors that strictly reflect the proto.
  private static volatile io.grpc.MethodDescriptor<com.microsoft.localforwarder.library.inputs.contracts.TelemetryBatch,
      com.microsoft.localforwarder.library.inputs.contracts.AiResponse> getSendTelemetryBatchMethod;

  @io.grpc.stub.annotations.RpcMethod(
      fullMethodName = SERVICE_NAME + '/' + "SendTelemetryBatch",
      requestType = com.microsoft.localforwarder.library.inputs.contracts.TelemetryBatch.class,
      responseType = com.microsoft.localforwarder.library.inputs.contracts.AiResponse.class,
      methodType = io.grpc.MethodDescriptor.MethodType.BIDI_STREAMING)
  public static io.grpc.MethodDescriptor<com.microsoft.localforwarder.library.inputs.contracts.TelemetryBatch,
      com.microsoft.localforwarder.library.inputs.contracts.AiResponse> getSendTelemetryBatchMethod() {
    io.grpc.MethodDescriptor<com.microsoft.localforwarder.library.inputs.contracts.TelemetryBatch, com.microsoft.localforwarder.library.inputs.contracts.AiResponse> getSendTelemetryBatchMethod;
    if ((getSendTelemetryBatchMethod = AITelemetryServiceGrpc.getSendTelemetryBatchMethod) == null) {
      synchronized (AITelemetryServiceGrpc.class) {
        if ((getSendTelemetryBatchMethod = AITelemetryServiceGrpc.getSendTelemetryBatchMethod) == null) {
          AITelemetryServiceGrpc.getSendTelemetryBatchMethod = getSendTelemetryBatchMethod = 
              io.grpc.MethodDescriptor.<com.microsoft.localforwarder.library.inputs.contracts.TelemetryBatch, com.microsoft.localforwarder.library.inputs.contracts.AiResponse>newBuilder()
              .setType(io.grpc.MethodDescriptor.MethodType.BIDI_STREAMING)
              .setFullMethodName(generateFullMethodName(
                  "contracts.AITelemetryService", "SendTelemetryBatch"))
              .setSampledToLocalTracing(true)
              .setRequestMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  com.microsoft.localforwarder.library.inputs.contracts.TelemetryBatch.getDefaultInstance()))
              .setResponseMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  com.microsoft.localforwarder.library.inputs.contracts.AiResponse.getDefaultInstance()))
                  .setSchemaDescriptor(new AITelemetryServiceMethodDescriptorSupplier("SendTelemetryBatch"))
                  .build();
          }
        }
     }
     return getSendTelemetryBatchMethod;
  }

  /**
   * Creates a new async stub that supports all call types for the service
   */
  public static AITelemetryServiceStub newStub(io.grpc.Channel channel) {
    return new AITelemetryServiceStub(channel);
  }

  /**
   * Creates a new blocking-style stub that supports unary and streaming output calls on the service
   */
  public static AITelemetryServiceBlockingStub newBlockingStub(
      io.grpc.Channel channel) {
    return new AITelemetryServiceBlockingStub(channel);
  }

  /**
   * Creates a new ListenableFuture-style stub that supports unary calls on the service
   */
  public static AITelemetryServiceFutureStub newFutureStub(
      io.grpc.Channel channel) {
    return new AITelemetryServiceFutureStub(channel);
  }

  /**
   * <pre>
   * gRPC service to transmit telemetry
   * </pre>
   */
  public static abstract class AITelemetryServiceImplBase implements io.grpc.BindableService {

    /**
     */
    public io.grpc.stub.StreamObserver<com.microsoft.localforwarder.library.inputs.contracts.TelemetryBatch> sendTelemetryBatch(
        io.grpc.stub.StreamObserver<com.microsoft.localforwarder.library.inputs.contracts.AiResponse> responseObserver) {
      return asyncUnimplementedStreamingCall(getSendTelemetryBatchMethod(), responseObserver);
    }

    @java.lang.Override public final io.grpc.ServerServiceDefinition bindService() {
      return io.grpc.ServerServiceDefinition.builder(getServiceDescriptor())
          .addMethod(
            getSendTelemetryBatchMethod(),
            asyncBidiStreamingCall(
              new MethodHandlers<
                com.microsoft.localforwarder.library.inputs.contracts.TelemetryBatch,
                com.microsoft.localforwarder.library.inputs.contracts.AiResponse>(
                  this, METHODID_SEND_TELEMETRY_BATCH)))
          .build();
    }
  }

  /**
   * <pre>
   * gRPC service to transmit telemetry
   * </pre>
   */
  public static final class AITelemetryServiceStub extends io.grpc.stub.AbstractStub<AITelemetryServiceStub> {
    private AITelemetryServiceStub(io.grpc.Channel channel) {
      super(channel);
    }

    private AITelemetryServiceStub(io.grpc.Channel channel,
        io.grpc.CallOptions callOptions) {
      super(channel, callOptions);
    }

    @java.lang.Override
    protected AITelemetryServiceStub build(io.grpc.Channel channel,
        io.grpc.CallOptions callOptions) {
      return new AITelemetryServiceStub(channel, callOptions);
    }

    /**
     */
    public io.grpc.stub.StreamObserver<com.microsoft.localforwarder.library.inputs.contracts.TelemetryBatch> sendTelemetryBatch(
        io.grpc.stub.StreamObserver<com.microsoft.localforwarder.library.inputs.contracts.AiResponse> responseObserver) {
      return asyncBidiStreamingCall(
          getChannel().newCall(getSendTelemetryBatchMethod(), getCallOptions()), responseObserver);
    }
  }

  /**
   * <pre>
   * gRPC service to transmit telemetry
   * </pre>
   */
  public static final class AITelemetryServiceBlockingStub extends io.grpc.stub.AbstractStub<AITelemetryServiceBlockingStub> {
    private AITelemetryServiceBlockingStub(io.grpc.Channel channel) {
      super(channel);
    }

    private AITelemetryServiceBlockingStub(io.grpc.Channel channel,
        io.grpc.CallOptions callOptions) {
      super(channel, callOptions);
    }

    @java.lang.Override
    protected AITelemetryServiceBlockingStub build(io.grpc.Channel channel,
        io.grpc.CallOptions callOptions) {
      return new AITelemetryServiceBlockingStub(channel, callOptions);
    }
  }

  /**
   * <pre>
   * gRPC service to transmit telemetry
   * </pre>
   */
  public static final class AITelemetryServiceFutureStub extends io.grpc.stub.AbstractStub<AITelemetryServiceFutureStub> {
    private AITelemetryServiceFutureStub(io.grpc.Channel channel) {
      super(channel);
    }

    private AITelemetryServiceFutureStub(io.grpc.Channel channel,
        io.grpc.CallOptions callOptions) {
      super(channel, callOptions);
    }

    @java.lang.Override
    protected AITelemetryServiceFutureStub build(io.grpc.Channel channel,
        io.grpc.CallOptions callOptions) {
      return new AITelemetryServiceFutureStub(channel, callOptions);
    }
  }

  private static final int METHODID_SEND_TELEMETRY_BATCH = 0;

  private static final class MethodHandlers<Req, Resp> implements
      io.grpc.stub.ServerCalls.UnaryMethod<Req, Resp>,
      io.grpc.stub.ServerCalls.ServerStreamingMethod<Req, Resp>,
      io.grpc.stub.ServerCalls.ClientStreamingMethod<Req, Resp>,
      io.grpc.stub.ServerCalls.BidiStreamingMethod<Req, Resp> {
    private final AITelemetryServiceImplBase serviceImpl;
    private final int methodId;

    MethodHandlers(AITelemetryServiceImplBase serviceImpl, int methodId) {
      this.serviceImpl = serviceImpl;
      this.methodId = methodId;
    }

    @java.lang.Override
    @java.lang.SuppressWarnings("unchecked")
    public void invoke(Req request, io.grpc.stub.StreamObserver<Resp> responseObserver) {
      switch (methodId) {
        default:
          throw new AssertionError();
      }
    }

    @java.lang.Override
    @java.lang.SuppressWarnings("unchecked")
    public io.grpc.stub.StreamObserver<Req> invoke(
        io.grpc.stub.StreamObserver<Resp> responseObserver) {
      switch (methodId) {
        case METHODID_SEND_TELEMETRY_BATCH:
          return (io.grpc.stub.StreamObserver<Req>) serviceImpl.sendTelemetryBatch(
              (io.grpc.stub.StreamObserver<com.microsoft.localforwarder.library.inputs.contracts.AiResponse>) responseObserver);
        default:
          throw new AssertionError();
      }
    }
  }

  private static abstract class AITelemetryServiceBaseDescriptorSupplier
      implements io.grpc.protobuf.ProtoFileDescriptorSupplier, io.grpc.protobuf.ProtoServiceDescriptorSupplier {
    AITelemetryServiceBaseDescriptorSupplier() {}

    @java.lang.Override
    public com.google.protobuf.Descriptors.FileDescriptor getFileDescriptor() {
      return com.microsoft.localforwarder.library.inputs.contracts.TelemetryBatchOuterClass.getDescriptor();
    }

    @java.lang.Override
    public com.google.protobuf.Descriptors.ServiceDescriptor getServiceDescriptor() {
      return getFileDescriptor().findServiceByName("AITelemetryService");
    }
  }

  private static final class AITelemetryServiceFileDescriptorSupplier
      extends AITelemetryServiceBaseDescriptorSupplier {
    AITelemetryServiceFileDescriptorSupplier() {}
  }

  private static final class AITelemetryServiceMethodDescriptorSupplier
      extends AITelemetryServiceBaseDescriptorSupplier
      implements io.grpc.protobuf.ProtoMethodDescriptorSupplier {
    private final String methodName;

    AITelemetryServiceMethodDescriptorSupplier(String methodName) {
      this.methodName = methodName;
    }

    @java.lang.Override
    public com.google.protobuf.Descriptors.MethodDescriptor getMethodDescriptor() {
      return getServiceDescriptor().findMethodByName(methodName);
    }
  }

  private static volatile io.grpc.ServiceDescriptor serviceDescriptor;

  public static io.grpc.ServiceDescriptor getServiceDescriptor() {
    io.grpc.ServiceDescriptor result = serviceDescriptor;
    if (result == null) {
      synchronized (AITelemetryServiceGrpc.class) {
        result = serviceDescriptor;
        if (result == null) {
          serviceDescriptor = result = io.grpc.ServiceDescriptor.newBuilder(SERVICE_NAME)
              .setSchemaDescriptor(new AITelemetryServiceFileDescriptorSupplier())
              .addMethod(getSendTelemetryBatchMethod())
              .build();
        }
      }
    }
    return result;
  }
}
