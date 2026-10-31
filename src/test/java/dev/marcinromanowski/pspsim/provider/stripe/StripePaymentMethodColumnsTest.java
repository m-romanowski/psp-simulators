package dev.marcinromanowski.pspsim.provider.stripe;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.marcinromanowski.pspsim.kernel.ObjectOrigin;
import dev.marcinromanowski.pspsim.kernel.testrun.TestRunId;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class StripePaymentMethodColumnsTest {

  private static final Instant CREATED = Instant.parse("2026-10-31T09:00:00Z");
  private static final Instant UPDATED = Instant.parse("2026-10-31T10:00:00Z");
  private static final Instant DETACHED = Instant.parse("2026-10-31T11:00:00Z");

  @Test
  void a_card_is_bound_column_by_column_in_the_order_the_names_are_listed() {
    Map<String, String> metadata = new LinkedHashMap<>();
    metadata.put("zeta", "last");
    metadata.put("alpha", "first");
    StripePaymentMethod card =
        new StripePaymentMethod(
            TestRunId.of("run"),
            "pm_sim_1",
            "merchant-1",
            null,
            StripePaymentMethodType.CARD,
            new StripeCard(
                "visa",
                "4242",
                10,
                2028,
                "credit",
                "US",
                "visa",
                List.of("visa", "cartes_bancaires"),
                "unregulated",
                true),
            new StripeBillingDetails("c", "PL", "l1", "l2", "00-001", "s", "e", "n", "p", "t"),
            metadata,
            "always",
            "pm_card_visa",
            DETACHED,
            ObjectOrigin.PROVIDER_API,
            CREATED,
            UPDATED);

    assertThat(StripePaymentMethodColumns.theValuesOf(card))
        .containsExactly(
            "run",
            "pm_sim_1",
            "merchant-1",
            null,
            "CARD",
            "visa",
            "4242",
            10,
            2028,
            "credit",
            "US",
            "visa",
            "[\"visa\",\"cartes_bancaires\"]",
            "unregulated",
            true,
            "c",
            "PL",
            "l1",
            "l2",
            "00-001",
            "s",
            "e",
            "n",
            "p",
            "t",
            "{\"zeta\":\"last\",\"alpha\":\"first\"}",
            "always",
            "pm_card_visa",
            OffsetDateTime.parse("2026-10-31T11:00:00Z"),
            "PROVIDER_API",
            OffsetDateTime.parse("2026-10-31T09:00:00Z"),
            OffsetDateTime.parse("2026-10-31T10:00:00Z"));
    assertThat(StripePaymentMethodColumns.NAMES.split(",", -1)).hasSize(32);
    assertThat(StripePaymentMethodColumns.PLACEHOLDERS.split(",", -1)).hasSize(32);
  }

  @Test
  void a_wallet_binds_no_card_column_and_no_detachment() {
    StripePaymentMethod wallet =
        new StripePaymentMethod(
            TestRunId.of("run"),
            "pm_wallet",
            "merchant-1",
            "cus_1",
            StripePaymentMethodType.PAYPAL,
            null,
            null,
            ObjectOrigin.CONTROL_API,
            CREATED,
            UPDATED);

    List<Object> values = StripePaymentMethodColumns.theValuesOf(wallet);

    assertThat(values.subList(5, 15)).containsExactlyElementsOf(Arrays.asList(new Object[10]));
    assertThat(values.get(3)).isEqualTo("cus_1");
    assertThat(values.get(4)).isEqualTo("PAYPAL");
    assertThat(values.get(25)).isEqualTo("{}");
    assertThat(values.get(26)).isEqualTo("unspecified");
    assertThat(values.get(27)).isNull();
    assertThat(values.get(28)).isNull();
    assertThat(values.get(29)).isEqualTo("CONTROL_API");
  }

  @Test
  void a_stored_card_is_read_back_as_the_card_hash_and_a_wallet_as_no_card() {
    StripePaymentMethodColumns.StoredCard stored =
        new StripePaymentMethodColumns.StoredCard(
            "visa", "4242", 10, 2028, "credit", "US", "visa", "[\"visa\"]", "unregulated", true);

    assertThat(StripePaymentMethodColumns.theCardOf(StripePaymentMethodType.CARD, stored))
        .isEqualTo(
            new StripeCard(
                "visa",
                "4242",
                10,
                2028,
                "credit",
                "US",
                "visa",
                List.of("visa"),
                "unregulated",
                true));
    assertThat(StripePaymentMethodColumns.theCardOf(StripePaymentMethodType.PAYPAL, stored))
        .isNull();
  }

  @Test
  void a_stored_card_without_its_required_columns_is_a_broken_row() {
    StripePaymentMethodColumns.StoredCard broken =
        new StripePaymentMethodColumns.StoredCard(
            null, "4242", 10, 2028, "credit", null, null, null, null, null);

    assertThatThrownBy(
            () -> StripePaymentMethodColumns.theCardOf(StripePaymentMethodType.CARD, broken))
        .isInstanceOf(NullPointerException.class)
        .hasMessage("card_brand");
  }

  @Test
  void networks_are_written_as_a_json_list_and_read_back_and_no_networks_stay_none() {
    assertThat(StripePaymentMethodColumns.networksAsWritten(List.of("visa", "mastercard")))
        .isEqualTo("[\"visa\",\"mastercard\"]");
    assertThat(StripePaymentMethodColumns.networksAsWritten(null)).isNull();
    assertThat(StripePaymentMethodColumns.networksOf("[\"visa\",\"mastercard\"]"))
        .containsExactly("visa", "mastercard");
    assertThat(StripePaymentMethodColumns.networksOf("[\"visa\",1]")).containsExactly("visa");
    assertThat(StripePaymentMethodColumns.networksOf("{}")).isEmpty();
    assertThat(StripePaymentMethodColumns.networksOf(null)).isNull();
  }

  @Test
  void metadata_is_written_in_the_order_sent_and_only_its_texts_are_read_back() {
    assertThat(StripePaymentMethodColumns.metadataAsWritten(Map.of())).isEqualTo("{}");
    assertThat(StripePaymentMethodColumns.metadataOf("{\"b\":\"2\",\"a\":\"1\",\"n\":3}"))
        .containsExactly(Map.entry("b", "2"), Map.entry("a", "1"));
    assertThat(StripePaymentMethodColumns.metadataOf("[]")).isEmpty();
  }

  @Test
  void a_moment_is_bound_at_utc_and_read_back_and_no_moment_stays_none() {
    assertThat(StripePaymentMethodColumns.atUtc(CREATED))
        .isEqualTo(CREATED.atOffset(ZoneOffset.UTC));
    assertThat(StripePaymentMethodColumns.atUtc(null)).isNull();
    assertThat(
            StripePaymentMethodColumns.instantOf(OffsetDateTime.parse("2026-10-31T11:00:00+02:00")))
        .isEqualTo(Instant.parse("2026-10-31T09:00:00Z"));
    assertThat(StripePaymentMethodColumns.instantOf(null)).isNull();
  }
}
