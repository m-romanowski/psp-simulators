package dev.marcinromanowski.pspsim.provider.tranzila;

import dev.marcinromanowski.pspsim.kernel.spi.KeyedAnswer;
import java.util.List;
import java.util.Map;
import org.jspecify.annotations.Nullable;

record TranzilaBody(int httpStatus, @Nullable TranzilaJson json) {

  static TranzilaBody of(int httpStatus, TranzilaJson json) {
    return new TranzilaBody(httpStatus, json);
  }

  static TranzilaBody nothingRendered() {
    return new TranzilaBody(0, null);
  }

  @Nullable String contentType() {
    return json == null ? null : TranzilaFields.JSON;
  }

  byte[] bytes() {
    TranzilaJson rendered = json;
    return rendered == null ? new byte[0] : TranzilaJsonWriter.toBytes(rendered);
  }

  String text() {
    TranzilaJson rendered = json;
    return rendered == null ? "" : TranzilaJsonWriter.toText(rendered);
  }

  @Nullable KeyedAnswer asAKeyedAnswer() {
    TranzilaJson rendered = json;
    return rendered == null ? null : new KeyedAnswer(httpStatus, rendered.flatten());
  }

  Map<String, List<String>> fields() {
    TranzilaJson rendered = json;
    return rendered == null ? Map.of() : rendered.flatten();
  }
}
