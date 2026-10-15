package dev.marcinromanowski.pspsim.provider.tranzila;

import dev.marcinromanowski.pspsim.kernel.spi.PayerOutcome;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.SequencedMap;
import org.jspecify.annotations.Nullable;

final class TranzilaStoredForm {

  private TranzilaStoredForm() {}

  static @Nullable OffsetDateTime timestampOf(@Nullable Instant at) {
    return at == null ? null : at.atOffset(ZoneOffset.UTC);
  }

  static @Nullable Instant instantOf(@Nullable OffsetDateTime stored) {
    return stored == null ? null : stored.toInstant();
  }

  static TranzilaTxnType typeOf(String stored) {
    return TranzilaTxnType.of(stored)
        .orElseThrow(
            () ->
                new IllegalStateException(
                    "the store holds " + stored + " as a txn_type, which no type names"));
  }

  static @Nullable String nameOf(@Nullable PayerOutcome outcome) {
    return outcome == null ? null : outcome.name();
  }

  static @Nullable PayerOutcome outcomeOf(@Nullable String stored) {
    return stored == null ? null : PayerOutcome.valueOf(stored);
  }

  static @Nullable String verdictNameOf(TranzilaChallenge.@Nullable Verdict verdict) {
    return verdict == null ? null : verdict.name();
  }

  static TranzilaChallenge.@Nullable Verdict verdictOf(@Nullable String stored) {
    return stored == null ? null : TranzilaChallenge.Verdict.valueOf(stored);
  }

  static @Nullable String theHostedFieldsOf(@Nullable TranzilaHostedFieldsEcho hosted) {
    return hosted == null ? null : encoded(hosted.stored());
  }

  static @Nullable String theFormDataOf(@Nullable TranzilaHostedFieldsEcho hosted) {
    return hosted == null ? null : encoded(hosted.userFormData());
  }

  static @Nullable TranzilaHostedFieldsEcho hostedFieldsOf(
      @Nullable String kept, @Nullable String formData) {
    if (kept == null || formData == null) {
      return null;
    }
    return TranzilaHostedFieldsEcho.ofTheStored(decoded(kept), decoded(formData));
  }

  static @Nullable String brokerNameOf(@Nullable TranzilaWallet wallet) {
    return wallet == null ? null : wallet.broker();
  }

  static @Nullable TranzilaWallet brokerOf(@Nullable String stored) {
    if (stored == null) {
      return null;
    }
    return TranzilaWallet.ofTheBroker(stored)
        .orElseThrow(
            () ->
                new IllegalStateException(
                    "the store holds " + stored + " as a broker, which no wallet names"));
  }

  static TranzilaTranmode tranmodeOf(String stored) {
    return TranzilaTranmode.named(stored)
        .orElseThrow(
            () ->
                new IllegalStateException(
                    "the store holds " + stored + " as a tranmode, which no tranmode names"));
  }

  static TranzilaPageCurrency currencyOf(String stored) {
    return TranzilaPageCurrency.numbered(stored)
        .orElseThrow(
            () ->
                new IllegalStateException(
                    "the store holds "
                        + stored
                        + " as a page currency, which no currency numbers"));
  }

  static String encoded(Map<String, String> fields) {
    List<String> pairs = new ArrayList<>();
    fields.forEach(
        (name, value) ->
            pairs.add(
                URLEncoder.encode(name, StandardCharsets.UTF_8)
                    + "="
                    + URLEncoder.encode(value, StandardCharsets.UTF_8)));
    return String.join("&", pairs);
  }

  static SequencedMap<String, String> decoded(String stored) {
    SequencedMap<String, String> fields = new LinkedHashMap<>();
    for (String pair : stored.split("&", -1)) {
      if (pair.isEmpty()) {
        continue;
      }
      int separator = pair.indexOf('=');
      fields.put(
          URLDecoder.decode(pair.substring(0, separator), StandardCharsets.UTF_8),
          URLDecoder.decode(pair.substring(separator + 1), StandardCharsets.UTF_8));
    }
    return fields;
  }
}
