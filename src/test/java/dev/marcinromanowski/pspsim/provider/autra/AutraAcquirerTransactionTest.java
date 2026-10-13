package dev.marcinromanowski.pspsim.provider.autra;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.marcinromanowski.pspsim.kernel.ObjectOrigin;
import dev.marcinromanowski.pspsim.kernel.ids.IdGenerator;
import dev.marcinromanowski.pspsim.kernel.money.Amount;
import dev.marcinromanowski.pspsim.kernel.money.Ledger;
import dev.marcinromanowski.pspsim.kernel.spi.AcceptedValue;
import dev.marcinromanowski.pspsim.kernel.testrun.TestRunId;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

class AutraAcquirerTransactionTest {

  private static final TestRunId RUN = TestRunId.of("autra-one-transaction");

  private static final Instant NOW = Instant.parse("2026-09-22T10:00:00Z");

  private static final String ACCOUNT = "merchant-1";

  private static final String DOCUMENT = "25055662000190";

  private static final String REPORTED_BY_THE_ACQUIRER = "400111222333";

  @Test
  void the_identifier_the_acquirer_names_is_asked_of_the_generator_as_twelve_digits() {
    LengthsAsked ids = new LengthsAsked("400111222333");

    String reported =
        AutraAcquirerTransaction.asTheAcquirerReportsIt(
            merchantThat(AutraAcquirerReport.REPORTED), ids);

    assertThat(ids.lengths).containsExactly(12);
    assertThat(reported).isEqualTo("400111222333");
  }

  @Test
  void an_acquirer_that_names_no_transaction_has_nothing_asked_of_the_generator_at_all() {
    LengthsAsked ids = new LengthsAsked("400111222333");

    String reported =
        AutraAcquirerTransaction.asTheAcquirerReportsIt(
            merchantThat(AutraAcquirerReport.NOT_REPORTED), ids);

    assertThat(reported).isNull();
    assertThat(ids.lengths).isEmpty();
  }

  @Test
  void both_published_names_carry_one_value_and_the_primary_one_comes_first() {
    Map<String, AutraJson> answer = new LinkedHashMap<>();

    AutraAcquirerTransaction.underBothPublishedNames(
        answer, paymentReporting(REPORTED_BY_THE_ACQUIRER));

    assertThat(answer.keySet()).containsExactly("trackingNumber", "nsu");
    assertThat(answer.get("trackingNumber"))
        .isEqualTo(new AutraJson.JsonText(REPORTED_BY_THE_ACQUIRER));
    assertThat(answer.get("nsu")).isEqualTo(answer.get("trackingNumber"));
  }

  @Test
  void neither_published_name_is_written_when_the_acquirer_named_no_transaction() {
    Map<String, AutraJson> answer = new LinkedHashMap<>();

    AutraAcquirerTransaction.underBothPublishedNames(answer, paymentReporting(null));

    assertThat(answer).isEmpty();
  }

  @Test
  void the_endpoint_that_publishes_the_primary_name_alone_writes_no_compatibility_name() {
    Map<String, AutraJson> answer = new LinkedHashMap<>();

    AutraAcquirerTransaction.underThePrimaryNameAlone(
        answer, paymentReporting(REPORTED_BY_THE_ACQUIRER));

    assertThat(answer.keySet()).containsExactly("trackingNumber");
    assertThat(answer.get("trackingNumber"))
        .isEqualTo(new AutraJson.JsonText(REPORTED_BY_THE_ACQUIRER));
  }

  @Test
  void the_primary_name_alone_is_absent_too_when_the_acquirer_named_no_transaction() {
    Map<String, AutraJson> answer = new LinkedHashMap<>();

    AutraAcquirerTransaction.underThePrimaryNameAlone(answer, paymentReporting(null));

    assertThat(answer).isEmpty();
  }

  @Test
  void the_inspector_names_the_one_value_once_and_names_nothing_when_there_is_none() {
    Map<String, String> named = new LinkedHashMap<>();
    AutraAcquirerTransaction.asTheInspectorNamesIt(
        named, paymentReporting(REPORTED_BY_THE_ACQUIRER));
    assertThat(named).containsExactly(Map.entry("trackingNumber", REPORTED_BY_THE_ACQUIRER));

    Map<String, String> withholding = new LinkedHashMap<>();
    AutraAcquirerTransaction.asTheInspectorNamesIt(withholding, paymentReporting(null));
    assertThat(withholding).isEmpty();
  }

  @Test
  void an_account_that_declares_nothing_has_an_acquirer_that_names_the_transaction() {
    assertThat(AutraAcquirerReport.of(AutraAcquirerReport.SETTING, null))
        .isEqualTo(AutraAcquirerReport.REPORTED);
    assertThat(AutraAcquirerReport.of(AutraAcquirerReport.SETTING, "  "))
        .isEqualTo(AutraAcquirerReport.REPORTED);
    assertThat(AutraAcquirerReport.REPORTED.isReported()).isTrue();
    assertThat(AutraAcquirerReport.NOT_REPORTED.isReported()).isFalse();
  }

  @Test
  void each_value_the_setting_offers_is_one_it_takes_back_and_each_carries_a_sentence() {
    for (AcceptedValue offered : AutraAcquirerReport.acceptedValues()) {
      assertThat(AutraAcquirerReport.of(AutraAcquirerReport.SETTING, offered.value()).name())
          .isEqualTo(offered.value());
      assertThat(offered.whatItMeans()).contains("acquirer");
    }
    assertThat(AutraAcquirerReport.acceptedValues())
        .extracting(AcceptedValue::value)
        .containsExactly("REPORTED", "NOT_REPORTED");
  }

  @Test
  void a_value_this_provider_does_not_publish_is_refused_and_the_refusal_names_the_property() {
    assertThatThrownBy(
            () ->
                AutraAcquirerReport.of(
                    "accounts[0].configuration." + AutraAcquirerReport.SETTING, "SOMETIMES"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("accounts[0].configuration." + AutraAcquirerReport.SETTING)
        .hasMessageContaining("REPORTED")
        .hasMessageContaining("NOT_REPORTED");
  }

  private static AutraMerchant merchantThat(AutraAcquirerReport report) {
    return AutraDoubles.merchant(
        RUN, ACCOUNT, DOCUMENT, Map.of(AutraAcquirerReport.SETTING, report.name()));
  }

  private static AutraPayment paymentReporting(@Nullable String trackingNumber) {
    return new AutraPayment(
        RUN,
        "550e8400-e29b-41d4-a716-446655440000",
        ACCOUNT,
        DOCUMENT,
        null,
        AutraTimeline.SALE,
        AutraFields.CREDIT,
        Amount.of("500.00"),
        "BRL",
        1,
        AutraStatus.ACCEPTED,
        "A1B2C3",
        "112676199769",
        trackingNumber,
        null,
        null,
        Ledger.empty(),
        null,
        ObjectOrigin.PROVIDER_API,
        NOW,
        NOW);
  }

  private static final class LengthsAsked implements IdGenerator {

    private final List<Integer> lengths = new ArrayList<>();

    private final String answers;

    private LengthsAsked(String answers) {
      this.answers = answers;
    }

    @Override
    public String hex(int length) {
      lengths.add(length);
      return answers;
    }

    @Override
    public String digits(int length) {
      lengths.add(length);
      return answers;
    }
  }
}
