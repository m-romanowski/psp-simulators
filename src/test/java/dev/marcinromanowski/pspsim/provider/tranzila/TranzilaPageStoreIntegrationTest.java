package dev.marcinromanowski.pspsim.provider.tranzila;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.marcinromanowski.pspsim.PostgresBackedTest;
import dev.marcinromanowski.pspsim.kernel.ObjectOrigin;
import dev.marcinromanowski.pspsim.kernel.spi.ArrivalOf;
import dev.marcinromanowski.pspsim.kernel.spi.PayerOutcome;
import dev.marcinromanowski.pspsim.kernel.testrun.TestRunId;
import dev.marcinromanowski.pspsim.kernel.transaction.UnitOfWork;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.SequencedMap;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;

class TranzilaPageStoreIntegrationTest extends PostgresBackedTest {

  private static final TestRunId RUN = TestRunId.of("tranzila-page-store-it");

  private static final TestRunId ANOTHER = TestRunId.of("tranzila-page-store-other");

  private static final Instant AT = Instant.parse("2026-10-14T09:00:00Z");

  @Autowired private TranzilaPages pages;
  @Autowired private TranzilaHandshakes handshakes;
  @Autowired private UnitOfWork unitOfWork;

  @AfterEach
  void forgetEverything() {
    pages.clear(RUN);
    pages.clear(ANOTHER);
    handshakes.clear(RUN);
    handshakes.clear(ANOTHER);
  }

  @Test
  void a_page_reads_back_every_field_it_was_opened_with_and_settles_once() {
    SequencedMap<String, String> echoed = new LinkedHashMap<>();
    echoed.put("contact", "Dana & Levi=1");
    echoed.put("pdesc", "seats");
    TranzilaPage opened = aPage(RUN, "1", echoed, TranzilaTranmode.VK);

    assertThat(pages.open(opened)).isEqualTo(1);
    assertThat(pages.open(opened)).isZero();
    assertThat(pages.find(opened.pageId())).contains(opened);
    assertThat(pages.find(ANOTHER, opened.pageId())).isEmpty();
    TranzilaPage settled =
        opened.settledAs(
            PayerOutcome.PAID, Long.valueOf(41044), null, null, null, AT.plusSeconds(5));
    unitOfWork.keepingOnly(
        held -> true,
        () -> {
          assertThat(pages.lockedWhileOpen(RUN, opened.pageId())).contains(opened);
          pages.settle(settled);
          return pages.lockedWhileOpen(RUN, opened.pageId());
        });
    assertThat(pages.find(RUN, opened.pageId())).contains(settled);
    assertThat(pages.ofTheTransaction(RUN, 41044)).contains(settled);
    assertThat(pages.ofTheTransaction(RUN, 41045)).isEmpty();
    assertThat(
            unitOfWork.keepingOnly(
                held -> true, () -> pages.lockedWhileOpen(RUN, opened.pageId()).isPresent()))
        .isFalse();
  }

  @Test
  void a_token_only_page_keeps_its_response_token_and_card() {
    TranzilaPage opened = aPage(RUN, "2", new LinkedHashMap<>(), TranzilaTranmode.K);
    pages.open(opened);
    TranzilaPage saved =
        opened.settledAs(
            PayerOutcome.PAID,
            null,
            "777",
            "t5d55d2922ca4029022",
            new TranzilaCard("xxxxxxxxxxxx9022", TranzilaCard.VISA, 12, 30),
            AT);

    pages.settle(saved);

    assertThat(pages.find(RUN, opened.pageId())).contains(saved);
  }

  @Test
  void pages_are_placed_newest_first_and_read_together_by_id_within_their_run() {
    TranzilaPage first = aPage(RUN, "3", new LinkedHashMap<>(), TranzilaTranmode.A);
    TranzilaPage second = aPage(RUN, "4", new LinkedHashMap<>(), TranzilaTranmode.A);
    TranzilaPage elsewhere = aPage(ANOTHER, "5", new LinkedHashMap<>(), TranzilaTranmode.A);
    pages.open(first);
    pages.open(second);
    pages.open(elsewhere);

    List<ArrivalOf<TranzilaPage>> newest = pages.newestFirst(RUN, null, 10);
    List<ArrivalOf<TranzilaPage>> afterTheNewest =
        pages.newestFirst(RUN, pages.placeOf(RUN, second.pageId()).orElseThrow(), 10);

    assertThat(newest).extracting(ArrivalOf::row).containsExactly(second, first);
    assertThat(afterTheNewest).extracting(ArrivalOf::row).containsExactly(first);
    assertThat(pages.newestFirst(RUN, null, 1)).hasSize(1);
    assertThat(pages.placeOf(RUN, elsewhere.pageId())).isEmpty();
    assertThat(pages.allById(RUN, List.of(first.pageId(), elsewhere.pageId())))
        .containsExactly(first);
    assertThat(pages.clear(RUN)).isEqualTo(2);
    assertThat(pages.find(ANOTHER, elsewhere.pageId())).isPresent();
  }

  @Test
  void the_page_store_refuses_a_value_no_page_can_hold() {
    assertThatThrownBy(
            () ->
                pages.open(
                    withId(aPage(RUN, "6", new LinkedHashMap<>(), TranzilaTranmode.A), "page-1")))
        .isInstanceOf(DataIntegrityViolationException.class);
    TranzilaPage token = aPage(RUN, "7", new LinkedHashMap<>(), TranzilaTranmode.K);
    pages.open(token);
    assertThatThrownBy(
            () ->
                pages.settle(
                    token.settledAs(PayerOutcome.PAID, Long.valueOf(1), "777", null, null, AT)))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  @Test
  void a_handshake_reads_back_what_it_locked_and_is_retained_until_a_later_moment() {
    TranzilaHandshake made =
        new TranzilaHandshake(
            "w" + "1".repeat(41),
            RUN,
            "account-1",
            "myterminal",
            "150",
            "{\"order_id\":\"ORD-1\"}",
            AT,
            AT.plusSeconds(1200));

    assertThat(handshakes.insert(made)).isEqualTo(1);
    assertThat(handshakes.insert(made)).isZero();
    handshakes.retainUntil(RUN, made.thtk(), AT.plusSeconds(2400));

    assertThat(handshakes.find(RUN, made.thtk()))
        .contains(
            new TranzilaHandshake(
                made.thtk(),
                RUN,
                "account-1",
                "myterminal",
                "150",
                "{\"order_id\":\"ORD-1\"}",
                AT,
                AT.plusSeconds(2400)));
    assertThat(handshakes.find(ANOTHER, made.thtk())).isEmpty();
    assertThatThrownBy(
            () ->
                handshakes.insert(
                    new TranzilaHandshake(
                        "not-a-thtk", RUN, "a", "myterminal", "1", null, AT, AT.plusSeconds(1))))
        .isInstanceOf(DataIntegrityViolationException.class);
    assertThat(handshakes.clear(RUN)).isEqualTo(1);
  }

  private static TranzilaPage aPage(
      TestRunId testRunId,
      String suffix,
      SequencedMap<String, String> echoed,
      TranzilaTranmode tranmode) {
    return new TranzilaPage(
        "directng_" + "0".repeat(23) + suffix,
        testRunId,
        "account-1",
        "myterminal",
        "10.53",
        TranzilaPageCurrency.EURO,
        tranmode,
        "https://shop.example/ok",
        "https://shop.example/fail",
        null,
        "order-1",
        null,
        "{\"order_id\":\"ORD-1\"}",
        echoed,
        ObjectOrigin.PROVIDER_API,
        AT,
        null,
        null,
        null,
        null,
        null,
        null,
        null);
  }

  private static TranzilaPage withId(TranzilaPage page, String pageId) {
    return new TranzilaPage(
        pageId,
        page.testRunId(),
        page.accountId(),
        page.terminalName(),
        page.sum(),
        page.currency(),
        page.tranmode(),
        page.successUrl(),
        page.failUrl(),
        page.notifyUrl(),
        page.dcDisable(),
        page.thtk(),
        page.requestParams(),
        page.echoed(),
        page.origin(),
        page.createdAt(),
        page.outcome(),
        page.transactionId(),
        page.response(),
        page.token(),
        page.cardMask(),
        page.cardType(),
        page.settledAt());
  }
}
