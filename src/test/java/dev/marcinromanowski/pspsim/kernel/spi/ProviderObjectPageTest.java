package dev.marcinromanowski.pspsim.kernel.spi;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.marcinromanowski.pspsim.kernel.NormalizedStatus;
import dev.marcinromanowski.pspsim.kernel.ObjectOrigin;
import dev.marcinromanowski.pspsim.kernel.money.Amount;
import dev.marcinromanowski.pspsim.kernel.money.Ledger;
import dev.marcinromanowski.pspsim.kernel.testrun.TestRunId;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ProviderObjectPageTest {

  private static final Instant AT = Instant.parse("2026-09-07T12:00:00Z");
  private static final TestRunId RUN = TestRunId.of("object-list");

  @Test
  void a_page_hands_a_reader_the_newest_object_first_however_its_stores_answered() {
    ProviderObjectPage.Listed page =
        ProviderObjectPages.newestFirst(
            List.of(placed(1, "old"), placed(3, "new"), placed(2, "mid")), 10);

    assertThat(idsOf(page)).containsExactly("new", "mid", "old");
  }

  @Test
  void a_page_hands_back_no_more_than_the_page_size_was_asked_for_and_drops_the_oldest() {
    ProviderObjectPage.Listed page =
        ProviderObjectPages.newestFirst(
            List.of(placed(1, "old"), placed(3, "new"), placed(2, "mid")), 2);

    assertThat(idsOf(page)).containsExactly("new", "mid");
  }

  @Test
  void a_page_that_filled_its_page_size_offers_the_object_the_next_page_carries_on_from() {
    ProviderObjectPage.Listed full =
        ProviderObjectPages.newestFirst(List.of(placed(1, "old"), placed(2, "new")), 2);

    assertThat(full.cursorToCarryOnFrom(2)).isEqualTo("old");
  }

  @Test
  void a_page_shorter_than_its_page_size_is_the_end_of_the_list_and_offers_no_next_page() {
    ProviderObjectPage.Listed shortPage =
        ProviderObjectPages.newestFirst(List.of(placed(1, "only")), 2);

    assertThat(shortPage.cursorToCarryOnFrom(2)).isNull();
    assertThat(ProviderObjectPages.newestFirst(List.of(), 2).cursorToCarryOnFrom(2)).isNull();
  }

  @Test
  void a_page_a_reader_could_not_walk_forward_through_is_refused_rather_than_handed_over() {
    List<PlacedProviderObject> oldestFirst = List.of(placed(1, "old"), placed(2, "new"));
    List<PlacedProviderObject> arrivedTogether = List.of(placed(2, "same"), placed(2, "same"));

    assertThatThrownBy(() -> new ProviderObjectPage.Listed(oldestFirst))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("newest first");
    assertThatThrownBy(() -> new ProviderObjectPage.Listed(arrivedTogether))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("newest first");
  }

  @Test
  void an_object_placed_in_the_order_under_an_id_it_does_not_carry_is_refused() {
    ProviderObjectCursor at = new ProviderObjectCursor(1, "one-id");
    ProviderObjectState carryingAnotherId = objectNamed("another-id");

    assertThatThrownBy(() -> new PlacedProviderObject(at, carryingAnotherId))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("placed under a different id");
    assertThatThrownBy(() -> new ProviderObjectCursor(1, " "))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void a_list_read_that_names_no_page_size_takes_the_published_default() {
    ProviderObjectQuery query =
        ProviderObjectQuery.of(new DeclaredObjectQuery(null, null, null), RUN);

    assertThat(query.limit()).isEqualTo(ProviderObjectQuery.DEFAULT_LIMIT);
    assertThat(query.testRunId()).isEqualTo(RUN);
    assertThat(query.after()).isNull();
  }

  @Test
  void a_list_read_refuses_a_page_size_outside_the_bounds_the_audit_read_already_publishes() {
    assertThatThrownBy(() -> queryWith("0")).isInstanceOf(IllegalArgumentException.class);
    String pastTheCeiling = String.valueOf(ProviderObjectQuery.LARGEST_LIMIT + 1);

    assertThatThrownBy(() -> queryWith(pastTheCeiling))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("limit must be between 1 and");
    assertThatThrownBy(() -> queryWith("all"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("limit")
        .hasMessageNotContaining("all");
    assertThat(queryWith(String.valueOf(ProviderObjectQuery.LARGEST_LIMIT)).limit())
        .isEqualTo(ProviderObjectQuery.LARGEST_LIMIT);
  }

  @Test
  void a_cursor_a_reader_sent_empty_is_a_cursor_that_names_no_object_and_not_a_first_page() {
    ProviderObjectQuery query =
        ProviderObjectQuery.of(new DeclaredObjectQuery(null, "", null), RUN);

    assertThat(query.after()).isEqualTo("");
  }

  @Test
  void a_test_run_outside_the_published_shape_is_refused_by_the_read_that_lists_its_objects() {
    DeclaredObjectQuery namingATestRunWithASpace = new DeclaredObjectQuery("a b", null, null);

    assertThatThrownBy(() -> ProviderObjectQuery.of(namingATestRunWithASpace, RUN))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("testRunId");
    assertThat(ProviderObjectQuery.of(new DeclaredObjectQuery("  ", null, null), RUN).testRunId())
        .isEqualTo(RUN);
    assertThat(
            ProviderObjectQuery.of(new DeclaredObjectQuery("another-run", null, null), RUN)
                .testRunId())
        .isEqualTo(TestRunId.of("another-run"));
  }

  @Test
  void refuses_a_page_on_which_two_objects_answer_to_one_identifier() {
    PlacedProviderObject theOneACaptureRenamed =
        new PlacedProviderObject(
            new ProviderObjectCursor(2, "pay_1"),
            objectWith(ProviderObjectIdentity.of("pay_1").answeringAlsoTo("pay_2")));
    PlacedProviderObject theOneThatTookTheSameName =
        new PlacedProviderObject(
            new ProviderObjectCursor(1, "pay_9"),
            objectWith(ProviderObjectIdentity.of("pay_9").answeringAlsoTo("pay_2")));

    List<PlacedProviderObject> bothAnsweringToPayTwo =
        List.of(theOneACaptureRenamed, theOneThatTookTheSameName);

    assertThatThrownBy(() -> new ProviderObjectPage.Listed(bothAnsweringToPayTwo))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("two objects answering to one identifier");
  }

  @Test
  void admits_a_page_on_which_one_object_answers_to_several_identifiers_of_its_own() {
    PlacedProviderObject renamed =
        new PlacedProviderObject(
            new ProviderObjectCursor(2, "pay_1"),
            objectWith(ProviderObjectIdentity.of("pay_1").answeringAlsoTo("pay_2")));

    ProviderObjectPage.Listed page =
        new ProviderObjectPage.Listed(List.of(renamed, placed(1, "pay_9")));

    assertThat(idsOf(page)).containsExactly("pay_1", "pay_9");
  }

  private static ProviderObjectQuery queryWith(String limit) {
    return ProviderObjectQuery.of(new DeclaredObjectQuery(null, null, limit), RUN);
  }

  private static List<String> idsOf(ProviderObjectPage.Listed page) {
    return page.objects().stream().map(ProviderObjectState::providerObjectId).toList();
  }

  private static PlacedProviderObject placed(long arrival, String id) {
    return new PlacedProviderObject(new ProviderObjectCursor(arrival, id), objectNamed(id));
  }

  private static ProviderObjectState objectNamed(String id) {
    return objectWith(ProviderObjectIdentity.of(id));
  }

  private static ProviderObjectState objectWith(ProviderObjectIdentity identity) {
    return new ProviderObjectState(
        identity,
        AKindOfObjectATestNames.ONE,
        "2",
        NormalizedStatus.SUCCEEDED,
        Amount.of("10.00"),
        null,
        Ledger.empty(),
        false,
        false,
        false,
        Map.of(),
        ObjectOrigin.PROVIDER_API,
        AT);
  }
}
