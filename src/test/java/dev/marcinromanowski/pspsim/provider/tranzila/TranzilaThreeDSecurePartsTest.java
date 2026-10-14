package dev.marcinromanowski.pspsim.provider.tranzila;

import static org.assertj.core.api.Assertions.assertThat;

import dev.marcinromanowski.pspsim.kernel.ObjectOrigin;
import dev.marcinromanowski.pspsim.kernel.spi.ProviderWireAnswer;
import dev.marcinromanowski.pspsim.kernel.spi.WireFault;
import dev.marcinromanowski.pspsim.kernel.testrun.TestRunId;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.SequencedMap;
import org.junit.jupiter.api.Test;

class TranzilaThreeDSecurePartsTest {

  private static final Instant AT = Instant.parse("2026-10-14T09:00:00Z");

  @Test
  void the_hosted_fields_echo_is_kept_as_a_form_and_read_back_whole() {
    SequencedMap<String, String> formData = new LinkedHashMap<>();
    formData.put("contact", "Dana & Levi");
    TranzilaHostedFieldsEcho echo =
        new TranzilaHostedFieldsEcho(
            "wthtk",
            "10.5",
            TranzilaPageCurrency.EURO,
            TranzilaTranmode.N,
            true,
            "https://shop.example/notify",
            formData);

    assertThat(echo.stored())
        .containsExactly(
            Map.entry("thtk", "wthtk"),
            Map.entry("amount", "10.5"),
            Map.entry("currency_code", "978"),
            Map.entry("tran_mode", "N"),
            Map.entry("tokenize", "true"),
            Map.entry("notify_url", "https://shop.example/notify"));
    assertThat(TranzilaHostedFieldsEcho.ofTheStored(echo.stored(), formData)).isEqualTo(echo);
    TranzilaHostedFieldsEcho untokenized =
        new TranzilaHostedFieldsEcho(
            "wthtk", "1", TranzilaPageCurrency.NIS, TranzilaTranmode.A, false, null, formData);
    assertThat(untokenized.stored())
        .containsEntry("tokenize", "false")
        .doesNotContainKey("notify_url");
    assertThat(TranzilaHostedFieldsEcho.ofTheStored(untokenized.stored(), formData))
        .isEqualTo(untokenized);
  }

  @Test
  void the_store_writes_a_verdict_and_the_hosted_fields_as_text_and_reads_them_back() {
    TranzilaHostedFieldsEcho echo =
        new TranzilaHostedFieldsEcho(
            "wthtk",
            "1",
            TranzilaPageCurrency.NIS,
            TranzilaTranmode.A,
            false,
            null,
            new LinkedHashMap<>(Map.of("a", "b")));

    assertThat(TranzilaStoredForm.verdictNameOf(TranzilaChallenge.Verdict.PASSED))
        .isEqualTo("PASSED");
    assertThat(TranzilaStoredForm.verdictNameOf(null)).isNull();
    assertThat(TranzilaStoredForm.verdictOf("FAILED")).isEqualTo(TranzilaChallenge.Verdict.FAILED);
    assertThat(TranzilaStoredForm.verdictOf(null)).isNull();
    assertThat(TranzilaStoredForm.theHostedFieldsOf(echo))
        .isEqualTo("thtk=wthtk&amount=1&currency_code=1&tran_mode=A&tokenize=false");
    assertThat(TranzilaStoredForm.theFormDataOf(echo)).isEqualTo("a=b");
    assertThat(TranzilaStoredForm.theHostedFieldsOf(null)).isNull();
    assertThat(TranzilaStoredForm.theFormDataOf(null)).isNull();
    assertThat(
            TranzilaStoredForm.hostedFieldsOf(
                TranzilaStoredForm.theHostedFieldsOf(echo), TranzilaStoredForm.theFormDataOf(echo)))
        .isEqualTo(echo);
    assertThat(TranzilaStoredForm.hostedFieldsOf(null, "a=b")).isNull();
    assertThat(TranzilaStoredForm.hostedFieldsOf("thtk=w", null)).isNull();
  }

  @Test
  void a_challenge_is_updated_when_answered_and_again_when_completed() {
    TranzilaChallenge made = aChallenge();
    TranzilaChallenge answered = made.answered(TranzilaChallenge.Verdict.PASSED, AT.plusSeconds(5));
    TranzilaChallenge completed = answered.completedAs(7L, AT.plusSeconds(9));

    assertThat(TranzilaChallengeObject.updatedAtOf(made)).isEqualTo(AT);
    assertThat(TranzilaChallengeObject.updatedAtOf(answered)).isEqualTo(AT.plusSeconds(5));
    assertThat(TranzilaChallengeObject.updatedAtOf(completed)).isEqualTo(AT.plusSeconds(9));
    assertThat(completed.transactionId()).isEqualTo(7L);
    assertThat(made.isAnswered()).isFalse();
    assertThat(answered.isAnswered()).isTrue();
    assertThat(answered.passed()).isTrue();
    assertThat(made.answered(TranzilaChallenge.Verdict.FAILED, AT).passed()).isFalse();
    assertThat(answered.isCompleted()).isFalse();
    assertThat(completed.isCompleted()).isTrue();
    assertThat(made.isOnTheHostedFields()).isFalse();
    assertThat(TranzilaChallengeObject.stateOf(answered, null, AT).updatedAt())
        .isEqualTo(AT.plusSeconds(5));
  }

  @Test
  void a_request_rebuilt_from_a_challenge_carries_what_its_transaction_records() {
    TranzilaCreateRequest entered = TranzilaCreateRequest.ofTheChallenge(aChallenge());
    TranzilaChallenge nothingEntered = withTheEntries(aChallenge(), "0", "0");
    TranzilaCreateRequest none = TranzilaCreateRequest.ofTheChallenge(nothingEntered);

    assertThat(entered.cvv()).isEqualTo("000");
    assertThat(entered.cardHolderId()).isEqualTo("000000000");
    assertThat(entered.cardNumber()).isEqualTo("t5d55d2922ca4025005");
    assertThat(entered.namesAToken()).isTrue();
    assertThat(entered.amount()).isEqualTo("10.53");
    assertThat(entered.type()).isEqualTo(TranzilaTxnType.VERIFY);
    assertThat(entered.verifyMode()).isEqualTo(5);
    assertThat(entered.expireMonth()).isEqualTo(7);
    assertThat(entered.expireYear()).isEqualTo(30);
    assertThat(entered.dcDisable()).isEqualTo("order-1");
    assertThat(entered.currencyCode()).isEqualTo("EUR");
    assertThat(entered.threeDSecure()).isNull();
    assertThat(none.cvv()).isNull();
    assertThat(none.cardHolderId()).isNull();
  }

  @Test
  void an_address_off_the_challenge_routes_is_answered_by_a_page_of_this_simulators_words() {
    ProviderWireAnswer.InThisProvidersOwnWords notFound =
        (ProviderWireAnswer.InThisProvidersOwnWords)
            TranzilaWireFaults.theAnswerTo(
                WireFault.AN_ADDRESS_THIS_PROVIDER_DOES_NOT_SERVE, "/tranzila/3ds/x");
    ProviderWireAnswer.InThisProvidersOwnWords wrongMethod =
        (ProviderWireAnswer.InThisProvidersOwnWords)
            TranzilaWireFaults.theAnswerTo(
                WireFault.A_METHOD_THIS_ADDRESS_DOES_NOT_ANSWER, "/tranzila/3ds/challenge/3ds_1");

    assertThat(notFound.httpStatus()).isEqualTo(404);
    assertThat(notFound.contentType()).isEqualTo("text/html; charset=utf-8");
    assertThat(new String(notFound.body(), StandardCharsets.UTF_8))
        .contains(TranzilaChallengePage.escaped(TranzilaChallengePage.NO_SUCH_ADDRESS));
    assertThat(wrongMethod.httpStatus()).isEqualTo(405);
    assertThat(new String(wrongMethod.body(), StandardCharsets.UTF_8))
        .contains(TranzilaChallengePage.escaped(TranzilaChallengePage.NO_SUCH_METHOD));
    assertThat(
            TranzilaWireFaults.theAnswerTo(
                WireFault.A_FAILURE_NOTHING_ELSE_ANSWERED, "/tranzila/3ds/challenge/3ds_1"))
        .isEqualTo(
            new ProviderWireAnswer.NothingThisContractPublishes(
                TranzilaWireFaults.WHAT_THE_CHALLENGE_PUBLISHES_FOR_A_FAILURE_INSIDE_IT));
  }

  @Test
  void the_challenge_page_is_addressed_on_the_public_base_and_answers_a_verdict_in_words() {
    assertThat(TranzilaChallengePage.addressOf("https://sim.test", "3ds_1"))
        .isEqualTo("https://sim.test/tranzila/3ds/challenge/3ds_1");
    assertThat(TranzilaChallengePage.reporting(true)).contains(TranzilaChallengePage.PASSED);
    assertThat(TranzilaChallengePage.reporting(false)).contains(TranzilaChallengePage.FAILED);
    assertThat(TranzilaChallengeAnsweredPage.telling("3ds_<1>", false))
        .contains("data-track-id=\"3ds_&lt;1&gt;\"")
        .contains(TranzilaChallengePage.FAILED);
  }

  @Test
  void the_track_id_is_declared_a_duplicate_detection_key_that_a_complete_claims() {
    assertThat(TranzilaKeyMechanisms.MECHANISMS.keys())
        .anySatisfy(
            key -> {
              assertThat(key.name()).isEqualTo("track_id");
              assertThat(key.role())
                  .isEqualTo(
                      dev.marcinromanowski.pspsim.kernel.idempotency.KeyRole
                          .DUPLICATE_DETECTION_KEY);
            });
  }

  static TranzilaChallenge aChallenge() {
    return new TranzilaChallenge(
        "3ds_" + "0".repeat(39) + "1",
        TestRunId.of("parts"),
        "account-1",
        "myterminal",
        41043L,
        TranzilaTxnType.VERIFY,
        5,
        "10.53",
        "EUR",
        "t5d55d2922ca4025005",
        new TranzilaCard("491891xxxxxx5005", TranzilaCard.VISA, 7, 30),
        "1",
        "1",
        "order-1",
        false,
        "{}",
        null,
        new LinkedHashMap<>(),
        null,
        ObjectOrigin.PROVIDER_API,
        AT,
        null,
        null,
        null);
  }

  private static TranzilaChallenge withTheEntries(
      TranzilaChallenge made, String responseCvv, String responseId) {
    return new TranzilaChallenge(
        made.trackId(),
        made.testRunId(),
        made.accountId(),
        made.terminalName(),
        made.transactionId(),
        made.type(),
        made.verifyMode(),
        made.amount(),
        made.currencyCode(),
        made.token(),
        made.card(),
        responseCvv,
        responseId,
        made.dcDisable(),
        made.forceTxnOnFail(),
        made.originalRequest(),
        made.redirectUrl(),
        made.redirectParams(),
        made.hostedFields(),
        made.origin(),
        made.createdAt(),
        made.payerVerdict(),
        made.answeredAt(),
        made.completedAt());
  }
}
