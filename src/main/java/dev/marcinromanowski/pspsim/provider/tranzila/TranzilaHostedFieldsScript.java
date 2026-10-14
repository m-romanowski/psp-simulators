package dev.marcinromanowski.pspsim.provider.tranzila;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;

final class TranzilaHostedFieldsScript {

  static final String WHERE_IT_IS_KEPT = "/tranzila/hf/thostedf.js";

  private TranzilaHostedFieldsScript() {}

  static byte[] read() {
    return read(WHERE_IT_IS_KEPT);
  }

  static byte[] read(String where) {
    try (InputStream kept = TranzilaHostedFieldsScript.class.getResourceAsStream(where)) {
      if (kept == null) {
        throw new IllegalStateException(
            where
                + " is not on the classpath, so this build has no TzlaHostedFields script to"
                + " serve");
      }
      return kept.readAllBytes();
    } catch (IOException unreadable) {
      throw new UncheckedIOException(unreadable);
    }
  }
}
