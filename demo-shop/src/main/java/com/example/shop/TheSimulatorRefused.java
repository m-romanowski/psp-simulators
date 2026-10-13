package com.example.shop;

public final class TheSimulatorRefused extends RuntimeException {

  private static final long serialVersionUID = 1L;

  TheSimulatorRefused(String what, int status, String answer) {
    super("the simulator's control API would not " + what + ": HTTP " + status + " " + answer);
  }
}
