package dev.marcinromanowski.pspsim.provider.autra;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class AutraStatusTest {

  @Test
  void every_one_of_the_seventeen_the_route_names_counts_as_published_and_nothing_else_does() {
    assertThat(AutraStatus.THE_VALUES_THE_LOOKUP_CALLS_USUAL)
        .as(
            "the seventeen autra names on the listing route, spelled out here rather than read"
                + " back from the list under test, which would pass for any content it held")
        .containsExactly(
            "PENDING",
            "ACCEPTED",
            "AUTHORIZED",
            "APPROVED",
            "CAPTURED",
            "VOIDED",
            "PARTIALLY_VOIDED",
            "REFUND_PENDING",
            "PARTIAL_REFUND_PENDING",
            "REFUNDED",
            "SETTLED",
            "PAID",
            "FAILED",
            "ERROR",
            "EXPIRED",
            "DECLINED",
            "NOT_FOUND");
    assertThat(AutraStatus.isOneTheVendorHasPublished("ACCEPTED")).isTrue();
    assertThat(AutraStatus.isOneTheVendorHasPublished("PARTIAL_REFUND_PENDING")).isTrue();
    assertThat(AutraStatus.isOneTheVendorHasPublished("NOT_FOUND")).isTrue();
    assertThat(AutraStatus.isOneTheVendorHasPublished("ACCEPTE")).isFalse();
    assertThat(AutraStatus.isOneTheVendorHasPublished("PARTIALY_VOIDED")).isFalse();
  }

  @Test
  void the_status_the_vendors_guide_alone_names_for_a_reservation_is_refused_as_autra_refuses_it() {
    assertThat(AutraStatus.isOneTheVendorHasPublished("PRE_AUTHORIZED"))
        .as(
            "autra publishes seventeen accepted values on the listing route and \"Valor"
                + " desconhecido -> 400 INVALID_STATUS\" beside them; PRE_AUTHORIZED is in"
                + " neither, so accepting it would answer 200 where autra answers 400")
        .isFalse();
  }

  @Test
  void a_status_the_vendor_has_not_published_is_not_counted_among_the_ones_it_has() {
    assertThat(AutraStatus.isOneTheVendorHasPublished("CHARGEBACK_OPENED")).isFalse();
  }

  @Test
  void a_status_the_vendor_has_not_published_is_described_with_the_vendors_own_open_set_warning() {
    assertThat(AutraStatus.describedAsTheVendorWouldHaveAReaderTreatIt("CHARGEBACK_OPENED"))
        .contains("CHARGEBACK_OPENED")
        .contains("a lista pode crescer");
  }

  @Test
  void a_published_status_is_described_without_the_open_set_warning() {
    assertThat(AutraStatus.describedAsTheVendorWouldHaveAReaderTreatIt(AutraStatus.CAPTURED))
        .isEqualTo("holds " + AutraStatus.CAPTURED);
  }

  @Test
  void the_three_failure_statuses_the_vendor_names_and_a_decline_release_the_order_for_reuse() {
    assertThat(AutraStatus.releasesTheOrderForReuse("FAILED")).isTrue();
    assertThat(AutraStatus.releasesTheOrderForReuse("ERROR")).isTrue();
    assertThat(AutraStatus.releasesTheOrderForReuse("EXPIRED")).isTrue();
    assertThat(AutraStatus.releasesTheOrderForReuse(AutraStatus.DECLINED)).isTrue();
  }

  @Test
  void a_reservation_that_stands_does_not_release_the_order_it_was_taken_under() {
    assertThat(AutraStatus.releasesTheOrderForReuse(AutraStatus.ACCEPTED)).isFalse();
    assertThat(AutraStatus.releasesTheOrderForReuse(AutraStatus.CAPTURED)).isFalse();
    assertThat(AutraStatus.releasesTheOrderForReuse(AutraStatus.VOIDED)).isFalse();
  }
}
