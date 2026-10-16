package dev.marcinromanowski.pspsim.provider.tranzila;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.marcinromanowski.pspsim.PostgresBackedTest;
import dev.marcinromanowski.pspsim.kernel.ObjectOrigin;
import dev.marcinromanowski.pspsim.kernel.spi.ArrivalOf;
import dev.marcinromanowski.pspsim.kernel.testrun.TestRunId;
import dev.marcinromanowski.pspsim.kernel.transaction.UnitOfWork;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.SequencedMap;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;

class TranzilaChallengeStoreIntegrationTest extends PostgresBackedTest {

  private static final TestRunId RUN = TestRunId.of("tranzila-challenge-store-it");

  private static final TestRunId ANOTHER = TestRunId.of("tranzila-challenge-store-other");

  private static final Instant AT = Instant.parse("2026-10-14T09:00:00Z");

  @Autowired private TranzilaChallenges challenges;
  @Autowired private UnitOfWork unitOfWork;

  @AfterEach
  void forgetEverything() {
    challenges.clear(RUN);
    challenges.clear(ANOTHER);
  }

  @Test
  void a_challenge_reads_back_every_field_it_was_opened_with_and_is_answered_and_completed_once() {
    SequencedMap<String, String> params = new LinkedHashMap<>();
    params.put("order", "o & 1=2");
    params.put("lang", "he");
    TranzilaChallenge opened = aChallenge(RUN, "1", params, null);

    assertThat(challenges.open(opened)).isEqualTo(1);
    assertThat(challenges.open(opened)).isZero();
    assertThat(challenges.find(opened.trackId())).contains(opened);
    assertThat(challenges.find(ANOTHER, opened.trackId())).isEmpty();
    TranzilaChallenge answered =
        opened.answered(TranzilaChallenge.Verdict.PASSED, AT.plusSeconds(3));
    assertThat(challenges.answer(answered)).isEqualTo(1);
    assertThat(challenges.answer(opened.answered(TranzilaChallenge.Verdict.FAILED, AT))).isZero();
    TranzilaChallenge completed = answered.completedAs(41044L, AT.plusSeconds(9));
    assertThat(
            unitOfWork.keepingOnly(
                held -> true,
                () -> {
                  assertThat(challenges.lockedUntilCompleted(RUN, "account-1", opened.trackId()))
                      .contains(answered);
                  assertThat(challenges.lockedUntilCompleted(RUN, "account-2", opened.trackId()))
                      .isEmpty();
                  return challenges.complete(completed);
                }))
        .isEqualTo(1);
    assertThat(challenges.complete(completed)).isZero();
    assertThat(challenges.find(RUN, opened.trackId())).contains(completed);
    assertThat(
            unitOfWork.keepingOnly(
                held -> true,
                () -> challenges.lockedUntilCompleted(RUN, "account-1", opened.trackId())))
        .isEmpty();
  }

  @Test
  void a_challenge_of_the_hosted_fields_keeps_what_their_answer_and_notify_echo() {
    SequencedMap<String, String> formData = new LinkedHashMap<>();
    formData.put("contact", "Dana & Levi");
    formData.put("identity_number", "123456789");
    TranzilaHostedFieldsEcho echo =
        new TranzilaHostedFieldsEcho(
            "w" + "1".repeat(41),
            "10.5",
            TranzilaPageCurrency.US_DOLLAR,
            TranzilaTranmode.V,
            true,
            "https://shop.example/notify",
            formData);
    TranzilaHostedFieldsEcho withoutANotify =
        new TranzilaHostedFieldsEcho(
            "w" + "2".repeat(41),
            "1",
            TranzilaPageCurrency.NIS,
            TranzilaTranmode.A,
            false,
            null,
            new LinkedHashMap<>());
    TranzilaChallenge hosted = aChallenge(RUN, "2", new LinkedHashMap<>(), echo);
    TranzilaChallenge plain = aChallenge(RUN, "3", new LinkedHashMap<>(), withoutANotify);

    challenges.open(hosted);
    challenges.open(plain);

    assertThat(challenges.find(hosted.trackId()).orElseThrow().hostedFields()).isEqualTo(echo);
    assertThat(challenges.find(plain.trackId()).orElseThrow().hostedFields())
        .isEqualTo(withoutANotify);
  }

  @Test
  void challenges_are_placed_newest_first_and_read_together_by_id_within_their_run() {
    TranzilaChallenge first = aChallenge(RUN, "4", new LinkedHashMap<>(), null);
    TranzilaChallenge second = aChallenge(RUN, "5", new LinkedHashMap<>(), null);
    TranzilaChallenge elsewhere = aChallenge(ANOTHER, "6", new LinkedHashMap<>(), null);
    challenges.open(first);
    challenges.open(second);
    challenges.open(elsewhere);

    List<ArrivalOf<TranzilaChallenge>> newest = challenges.newestFirst(RUN, null, 10);
    List<ArrivalOf<TranzilaChallenge>> afterTheNewest =
        challenges.newestFirst(RUN, challenges.placeOf(RUN, second.trackId()).orElseThrow(), 10);

    assertThat(newest).extracting(ArrivalOf::row).containsExactly(second, first);
    assertThat(afterTheNewest).extracting(ArrivalOf::row).containsExactly(first);
    assertThat(challenges.newestFirst(RUN, null, 1)).hasSize(1);
    assertThat(challenges.placeOf(RUN, elsewhere.trackId())).isEmpty();
    assertThat(challenges.allById(RUN, List.of(first.trackId(), elsewhere.trackId())))
        .containsExactly(first);
    assertThat(challenges.clear(RUN)).isEqualTo(2);
    assertThat(challenges.find(ANOTHER, elsewhere.trackId())).isPresent();
  }

  @Test
  void the_challenge_store_refuses_a_value_no_challenge_can_hold() {
    TranzilaChallenge valid = aChallenge(RUN, "7", new LinkedHashMap<>(), null);

    assertThatThrownBy(() -> challenges.open(withTrack(valid, "3ds_not-hex")))
        .isInstanceOf(DataIntegrityViolationException.class);
    assertThatThrownBy(() -> challenges.open(asA(valid, TranzilaTxnType.FORCE, null)))
        .isInstanceOf(DataIntegrityViolationException.class);
    assertThatThrownBy(() -> challenges.open(asA(valid, TranzilaTxnType.VERIFY, 6)))
        .isInstanceOf(DataIntegrityViolationException.class);
    assertThatThrownBy(() -> challenges.open(asA(valid, TranzilaTxnType.DEBIT, 5)))
        .isInstanceOf(DataIntegrityViolationException.class);
    challenges.open(valid);
    assertThatThrownBy(() -> challenges.complete(valid.completedAs(1L, AT)))
        .as("a challenge is completed only after its payer answered it")
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  private static TranzilaChallenge aChallenge(
      TestRunId testRunId,
      String suffix,
      SequencedMap<String, String> params,
      @Nullable TranzilaHostedFieldsEcho hosted) {
    return new TranzilaChallenge(
        "3ds_" + "0".repeat(39) + suffix,
        testRunId,
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
        "0",
        "order-1",
        true,
        "{\"terminal_name\":\"myterminal\",\"cvv\":\"***\"}",
        params.isEmpty() ? null : "https://shop.example/back",
        params,
        hosted,
        ObjectOrigin.PROVIDER_API,
        AT,
        null,
        null,
        null);
  }

  private static TranzilaChallenge withTrack(TranzilaChallenge made, String trackId) {
    return new TranzilaChallenge(
        trackId,
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
        made.responseCvv(),
        made.responseId(),
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

  private static TranzilaChallenge asA(
      TranzilaChallenge made, TranzilaTxnType type, @Nullable Integer verifyMode) {
    return new TranzilaChallenge(
        made.trackId(),
        made.testRunId(),
        made.accountId(),
        made.terminalName(),
        made.transactionId(),
        type,
        verifyMode,
        made.amount(),
        made.currencyCode(),
        made.token(),
        made.card(),
        made.responseCvv(),
        made.responseId(),
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
