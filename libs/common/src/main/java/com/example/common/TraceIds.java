package com.example.common;

import java.util.UUID;

public final class TraceIds {
  private TraceIds() {}

  public static String newTraceId() {
    return UUID.randomUUID().toString();
  }
}
