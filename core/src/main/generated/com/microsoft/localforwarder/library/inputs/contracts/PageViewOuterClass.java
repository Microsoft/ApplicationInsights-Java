// Generated by the protocol buffer compiler.  DO NOT EDIT!
// source: PageView.proto

package com.microsoft.localforwarder.library.inputs.contracts;

public final class PageViewOuterClass {
  private PageViewOuterClass() {}
  public static void registerAllExtensions(
      com.google.protobuf.ExtensionRegistryLite registry) {
  }

  public static void registerAllExtensions(
      com.google.protobuf.ExtensionRegistry registry) {
    registerAllExtensions(
        (com.google.protobuf.ExtensionRegistryLite) registry);
  }
  static final com.google.protobuf.Descriptors.Descriptor
    internal_static_contracts_PageView_descriptor;
  static final 
    com.google.protobuf.GeneratedMessageV3.FieldAccessorTable
      internal_static_contracts_PageView_fieldAccessorTable;

  public static com.google.protobuf.Descriptors.FileDescriptor
      getDescriptor() {
    return descriptor;
  }
  private static  com.google.protobuf.Descriptors.FileDescriptor
      descriptor;
  static {
    java.lang.String[] descriptorData = {
      "\n\016PageView.proto\022\tcontracts\032\036google/prot" +
      "obuf/duration.proto\032\013Event.proto\"\206\001\n\010Pag" +
      "eView\022\037\n\005event\030\001 \001(\0132\020.contracts.Event\022\013" +
      "\n\003url\030\002 \001(\t\022+\n\010duration\030\003 \001(\0132\031.google.p" +
      "rotobuf.Duration\022\n\n\002id\030\004 \001(\t\022\023\n\013referrer" +
      "Uri\030\005 \001(\tBm\n5com.microsoft.localforwarde" +
      "r.library.inputs.contractsP\001\252\0021Microsoft" +
      ".LocalForwarder.Library.Inputs.Contracts" +
      "b\006proto3"
    };
    com.google.protobuf.Descriptors.FileDescriptor.InternalDescriptorAssigner assigner =
        new com.google.protobuf.Descriptors.FileDescriptor.    InternalDescriptorAssigner() {
          public com.google.protobuf.ExtensionRegistry assignDescriptors(
              com.google.protobuf.Descriptors.FileDescriptor root) {
            descriptor = root;
            return null;
          }
        };
    com.google.protobuf.Descriptors.FileDescriptor
      .internalBuildGeneratedFileFrom(descriptorData,
        new com.google.protobuf.Descriptors.FileDescriptor[] {
          com.google.protobuf.DurationProto.getDescriptor(),
          com.microsoft.localforwarder.library.inputs.contracts.EventOuterClass.getDescriptor(),
        }, assigner);
    internal_static_contracts_PageView_descriptor =
      getDescriptor().getMessageTypes().get(0);
    internal_static_contracts_PageView_fieldAccessorTable = new
      com.google.protobuf.GeneratedMessageV3.FieldAccessorTable(
        internal_static_contracts_PageView_descriptor,
        new java.lang.String[] { "Event", "Url", "Duration", "Id", "ReferrerUri", });
    com.google.protobuf.DurationProto.getDescriptor();
    com.microsoft.localforwarder.library.inputs.contracts.EventOuterClass.getDescriptor();
  }

  // @@protoc_insertion_point(outer_class_scope)
}
