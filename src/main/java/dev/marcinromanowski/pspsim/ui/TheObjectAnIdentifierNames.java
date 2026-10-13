package dev.marcinromanowski.pspsim.ui;

import dev.marcinromanowski.pspsim.kernel.spi.ProviderObjectState;
import java.util.List;
import java.util.Optional;

final class TheObjectAnIdentifierNames {

  static final String THE_ASKED_IDENTIFIER_IS_THE_KEY =
      "a batched read answers objects and never a key, so the caller holding the identifier is the"
          + " one that matches it, and it matches against every identifier the object answers to."
          + " A provider that mints a further identifier per maintenance operation reads one"
          + " object back under any of them, so matching only the canonical member would print an"
          + " object this run holds as one it holds nothing under";

  private TheObjectAnIdentifierNames() {}

  static Optional<ProviderObjectState> among(List<ProviderObjectState> held, String identifier) {
    for (ProviderObjectState state : held) {
      if (state.identity().answersTo(identifier)) {
        return Optional.of(state);
      }
    }
    return Optional.empty();
  }
}
