package dev.marcinromanowski.pspsim.provider.tranzila;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;

final class TranzilaHostedFieldsScript {

  static final String WHERE_IT_IS_KEPT = "/tranzila/hf/thostedf.js";

  private TranzilaHostedFieldsScript() {}

  static byte[] read() {
    try (InputStream kept =
        TranzilaHostedFieldsScript.class.getResourceAsStream(WHERE_IT_IS_KEPT)) {
      if (kept == null) {
        throw new IllegalStateException(
            WHERE_IT_IS_KEPT
                + " is not on the classpath, so this build has no TzlaHostedFields script to serve");
      }
      return kept.readAllBytes();
    } catch (IOException unreadable) {
      throw new UncheckedIOException(unreadable);
    }
  }
}
