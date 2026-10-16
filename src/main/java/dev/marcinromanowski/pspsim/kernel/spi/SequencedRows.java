package dev.marcinromanowski.pspsim.kernel.spi;

import java.util.function.IntSupplier;

public final class SequencedRows {

  public static final int ATTEMPTS = 100;

  public void recorded(String what, IntSupplier insertedRows) {
    for (int attempt = 1; attempt <= ATTEMPTS; attempt++) {
      if (insertedRows.getAsInt() == 1) {
        return;
      }
    }
    throw new IllegalStateException(
        what
            + " could not be recorded in "
            + ATTEMPTS
            + " attempts, each of which found the sequence number it read already taken by"
            + " another row");
  }
}
