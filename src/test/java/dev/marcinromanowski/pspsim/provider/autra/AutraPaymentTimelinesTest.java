package dev.marcinromanowski.pspsim.provider.autra;

import static org.assertj.core.api.Assertions.assertThat;

import dev.marcinromanowski.pspsim.kernel.ObjectOrigin;
import dev.marcinromanowski.pspsim.kernel.money.Amount;
import dev.marcinromanowski.pspsim.kernel.money.Ledger;
import dev.marcinromanowski.pspsim.kernel.spi.ArrivalOf;
import dev.marcinromanowski.pspsim.kernel.testrun.TestRunId;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class AutraPaymentTimelinesTest {

  private static final TestRunId RUN = TestRunId.of("autra-timelines");

  private static final Instant AT = Instant.parse("2026-09-22T10:00:00Z");

  @Test
  void one_answer_covering_several_payments_is_split_per_payment_in_the_order_it_arrived() {
    Map<String, List<AutraPaymentEvent>> keyed =
        AutraPaymentTimelines.keyedByPayment(
            List.of(
                anEvent("pay-1", AutraTimeline.PREAUTH),
                anEvent("pay-1", AutraTimeline.CAPTURE),
                anEvent("pay-2", AutraTimeline.SALE)));

    assertThat(keyed.keySet())
        .as(
            "a listing places each payment's events against that payment, and a key order the"
                + " runtime picks would make the same answer read back differently in another"
                + " JVM")
        .containsExactly("pay-1", "pay-2");
    assertThat(AutraPaymentTimelines.theTimelineOf(keyed, "pay-1"))
        .extracting(AutraPaymentEvent::type)
        .containsExactly(AutraTimeline.PREAUTH, AutraTimeline.CAPTURE);
    assertThat(AutraPaymentTimelines.theTimelineOf(keyed, "pay-2"))
        .extracting(AutraPaymentEvent::type)
        .containsExactly(AutraTimeline.SALE);
  }

  @Test
  void a_payment_the_one_answer_carried_no_event_for_has_an_empty_timeline_and_not_a_missing_one() {
    Map<String, List<AutraPaymentEvent>> keyed =
        AutraPaymentTimelines.keyedByPayment(List.of(anEvent("pay-1", AutraTimeline.SALE)));

    assertThat(AutraPaymentTimelines.theTimelineOf(keyed, "pay-2")).isEmpty();
  }

  @Test
  void no_event_at_all_is_no_timeline_at_all_rather_than_an_entry_without_events() {
    assertThat(AutraPaymentTimelines.keyedByPayment(List.of())).isEmpty();
  }

  @Test
  void the_identifiers_a_page_carries_are_read_off_the_rows_it_arrived_with() {
    assertThat(
            AutraPaymentTimelines.theIdentifiersArrivedAt(
                List.of(
                    new ArrivalOf<>(2L, aPayment("pay-2")),
                    new ArrivalOf<>(1L, aPayment("pay-1")))))
        .containsExactly("pay-2", "pay-1");
  }

  @Test
  void a_page_that_carried_no_payment_names_no_identifier_to_read_a_timeline_for() {
    assertThat(AutraPaymentTimelines.theIdentifiersArrivedAt(List.of())).isEmpty();
    assertThat(AutraPaymentTimelines.theIdentifiersOf(List.of())).isEmpty();
  }

  private static AutraPaymentEvent anEvent(String paymentId, String type) {
    return new AutraPaymentEvent(
        RUN, paymentId, type, AutraStatus.ACCEPTED, Amount.of("100.00"), null, null, AT);
  }

  private static AutraPayment aPayment(String paymentId) {
    return new AutraPayment(
        RUN,
        paymentId,
        "merchant-1",
        "25055662000190",
        null,
        AutraTimeline.PREAUTH,
        AutraFields.CREDIT,
        Amount.of("100.00"),
        "BRL",
        1,
        AutraStatus.ACCEPTED,
        "A1B2C3",
        "112676199769",
        "900123456789",
        null,
        null,
        Ledger.empty(),
        null,
        ObjectOrigin.PROVIDER_API,
        AT,
        AT);
  }
}
