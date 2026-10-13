package dev.marcinromanowski.pspsim.ui;

import java.util.Map;
import org.jspecify.annotations.Nullable;

public record WhatWasTyped(
    Map<String, String> fields,
    @Nullable String theObjectAStepWasChosenFor,
    @Nullable String theStepThePayerTook,
    @Nullable String theCardThePayerNamed) {

  public static final String ATTRIBUTE = "typed";

  public WhatWasTyped {
    fields = Map.copyOf(fields);
  }

  public static WhatWasTyped nothing() {
    return new WhatWasTyped(Map.of(), null, null, null);
  }

  public static WhatWasTyped theFieldsOf(Map<String, String> submitted) {
    return new WhatWasTyped(submitted, null, null, null);
  }

  public static WhatWasTyped theFieldTyped(String field, @Nullable String value) {
    return theFieldsOf(value == null ? Map.of() : Map.of(field, value));
  }

  public static WhatWasTyped thePayerStepChosenFor(
      @Nullable String providerObjectId, @Nullable String outcome, @Nullable String card) {
    return new WhatWasTyped(Map.of(), providerObjectId, outcome, card);
  }

  public String valueOf(String field) {
    String typed = fields.get(field);
    return typed == null ? "" : typed;
  }

  public @Nullable String theStepChosenFor(String providerObjectId) {
    return providerObjectId.equals(theObjectAStepWasChosenFor) ? theStepThePayerTook : null;
  }

  public @Nullable String theCardChosenFor(String providerObjectId) {
    return providerObjectId.equals(theObjectAStepWasChosenFor) ? theCardThePayerNamed : null;
  }
}
