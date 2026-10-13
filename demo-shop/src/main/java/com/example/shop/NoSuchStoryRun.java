package com.example.shop;

public final class NoSuchStoryRun extends RuntimeException {

  private static final long serialVersionUID = 1L;

  NoSuchStoryRun(String testRunId) {
    super("this shop keeps no story run called " + testRunId);
  }
}
