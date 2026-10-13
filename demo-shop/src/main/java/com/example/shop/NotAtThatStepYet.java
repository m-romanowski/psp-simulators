package com.example.shop;

public final class NotAtThatStepYet extends RuntimeException {

  private static final long serialVersionUID = 1L;

  NotAtThatStepYet(String testRunId, String step) {
    super("the story of " + testRunId + " has not reached the point where " + step);
  }
}
