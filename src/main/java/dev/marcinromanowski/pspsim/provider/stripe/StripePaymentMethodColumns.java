package dev.marcinromanowski.pspsim.provider.stripe;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

final class StripePaymentMethodColumns {

  static final String NAMES =
      "test_run_id, payment_method_id, account_id, customer_id, method_type,"
          + " card_brand, card_last4, card_exp_month, card_exp_year, card_funding,"
          + " card_country, card_display_brand, card_networks, card_regulated_status,"
          + " card_three_d_secure_supported,"
          + " billing_address_city, billing_address_country, billing_address_line1,"
          + " billing_address_line2, billing_address_postal_code, billing_address_state,"
          + " billing_email, billing_name, billing_phone, billing_tax_id,"
          + " metadata, allow_redisplay, copied_from,"
          + " detached_at, origin, created_at, updated_at";

  static final String PLACEHOLDERS =
      "?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?::json, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?,"
          + " ?::json, ?, ?, ?, ?, ?, ?";

  record StoredCard(
      @Nullable String brand,
      @Nullable String last4,
      int expMonth,
      int expYear,
      @Nullable String funding,
      @Nullable String country,
      @Nullable String displayBrand,
      @Nullable String networks,
      @Nullable String regulatedStatus,
      @Nullable Boolean threeDSecureSupported) {}

  private StripePaymentMethodColumns() {}

  static List<@Nullable Object> theValuesOf(StripePaymentMethod method) {
    StripeCard card = method.card();
    StripeBillingDetails billing = method.billingDetails();
    List<@Nullable Object> values = new ArrayList<>();
    values.add(method.testRunId().value());
    values.add(method.paymentMethodId());
    values.add(method.accountId());
    values.add(method.customerId());
    values.add(method.type().name());
    values.addAll(
        card == null
            ? Arrays.asList(new Object[10])
            : Arrays.asList(
                card.brand(),
                card.last4(),
                card.expMonth(),
                card.expYear(),
                card.funding(),
                card.country(),
                card.displayBrand(),
                networksAsWritten(card.networks()),
                card.regulatedStatus(),
                card.threeDSecureSupported()));
    values.add(billing.city());
    values.add(billing.country());
    values.add(billing.line1());
    values.add(billing.line2());
    values.add(billing.postalCode());
    values.add(billing.state());
    values.add(billing.email());
    values.add(billing.name());
    values.add(billing.phone());
    values.add(billing.taxId());
    values.add(metadataAsWritten(method.metadata()));
    values.add(method.allowRedisplay());
    values.add(method.copiedFrom());
    values.add(atUtc(method.detachedAt()));
    values.add(method.origin().name());
    values.add(atUtc(method.createdAt()));
    values.add(atUtc(method.updatedAt()));
    return values;
  }

  static @Nullable StripeCard theCardOf(StripePaymentMethodType type, StoredCard stored) {
    if (!type.describesACard()) {
      return null;
    }
    return new StripeCard(
        Objects.requireNonNull(stored.brand(), "card_brand"),
        Objects.requireNonNull(stored.last4(), "card_last4"),
        stored.expMonth(),
        stored.expYear(),
        Objects.requireNonNull(stored.funding(), "card_funding"),
        stored.country(),
        stored.displayBrand(),
        networksOf(stored.networks()),
        stored.regulatedStatus(),
        stored.threeDSecureSupported());
  }

  static @Nullable String networksAsWritten(@Nullable List<String> networks) {
    if (networks == null) {
      return null;
    }
    List<StripeJson> written = new ArrayList<>();
    networks.forEach(network -> written.add(StripeJson.text(network)));
    return StripeJsonWriter.toText(StripeJson.array(written));
  }

  static @Nullable List<String> networksOf(@Nullable String stored) {
    if (stored == null) {
      return null;
    }
    List<String> networks = new ArrayList<>();
    if (StripeJsonReader.read(stored) instanceof StripeJson.JsonArray array) {
      for (StripeJson element : array.elements()) {
        if (element instanceof StripeJson.JsonText text) {
          networks.add(text.value());
        }
      }
    }
    return List.copyOf(networks);
  }

  static String metadataAsWritten(Map<String, String> metadata) {
    Map<String, StripeJson> members = new LinkedHashMap<>();
    metadata.forEach((key, value) -> members.put(key, StripeJson.text(value)));
    return StripeJsonWriter.toText(new StripeJson.JsonObject(members));
  }

  static Map<String, String> metadataOf(String stored) {
    Map<String, String> metadata = new LinkedHashMap<>();
    if (StripeJsonReader.read(stored) instanceof StripeJson.JsonObject object) {
      object
          .members()
          .forEach(
              (key, value) -> {
                if (value instanceof StripeJson.JsonText text) {
                  metadata.put(key, text.value());
                }
              });
    }
    return metadata;
  }

  static @Nullable OffsetDateTime atUtc(@Nullable Instant instant) {
    return instant == null ? null : instant.atOffset(ZoneOffset.UTC);
  }

  static @Nullable Instant instantOf(@Nullable OffsetDateTime stored) {
    return stored == null ? null : stored.toInstant();
  }
}
