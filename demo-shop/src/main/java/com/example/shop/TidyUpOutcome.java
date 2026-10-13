package com.example.shop;

import java.util.List;

public record TidyUpOutcome(List<String> tidied, List<RunLeftBehind> leftBehind) {

  public record RunLeftBehind(String testRunId, String why) {}

  public TidyUpOutcome {
    tidied = List.copyOf(tidied);
    leftBehind = List.copyOf(leftBehind);
  }
}
