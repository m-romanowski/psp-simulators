package dev.marcinromanowski.pspsim.kernel.transaction;

import java.util.Set;

public record ClaimConstraints(Set<String> names) {

  public ClaimConstraints {
    names = Set.copyOf(names);
  }
}
