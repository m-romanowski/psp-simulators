package com.example.shop;

public final class TheSimulatorDidNotAnswer extends RuntimeException {

  private static final long serialVersionUID = 1L;

  TheSimulatorDidNotAnswer(String what, Throwable cause) {
    super(
        "the simulator's control API could not be reached in time to "
            + what
            + ": "
            + cause.getMessage(),
        cause);
  }
}
