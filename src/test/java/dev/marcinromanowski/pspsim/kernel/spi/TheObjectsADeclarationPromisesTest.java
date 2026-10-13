package dev.marcinromanowski.pspsim.kernel.spi;

import static org.assertj.core.api.Assertions.assertThat;

import dev.marcinromanowski.pspsim.kernel.testrun.TestRunId;
import java.util.List;
import org.junit.jupiter.api.Test;

class TheObjectsADeclarationPromisesTest {

  private static final TestRunId RUN = TestRunId.of("a-declaration-and-its-objects");

  private static final String A_CARD_SESSION = "session-1";

  private static final String A_PIX = "pix-1";

  private static final String A_PAYMENT = "payment-1";

  private static final String A_LATER_IDENTIFIER_OF_THE_PIX =
      "the-identifier-a-refund-of-it-minted";

  private static final HostedPageContract TWO_OBJECTS_ONE_OF_THEM_A_CARD =
      new HostedPageContract.ObjectsAPayerFinishes(
          List.of(
              new HostedPageContract.AnObjectItKeepsWaiting("a card session", true),
              new HostedPageContract.AnObjectItKeepsWaiting("a Pix", false)));

  private static final HostedPageContract ONE_OBJECT_A_PAYER_NAMES_A_CARD_ON =
      new HostedPageContract.ObjectsAPayerFinishes(
          List.of(new HostedPageContract.AnObjectItKeepsWaiting("a payment process", true)));

  @Test
  void a_module_that_keeps_two_objects_and_publishes_the_facts_of_both_agrees_with_itself() {
    SimulatorProvider module =
        new ProviderDouble()
            .declaring(TWO_OBJECTS_ONE_OF_THEM_A_CARD)
            .keepingWaiting(A_CARD_SESSION, true)
            .keepingWaiting(A_PIX, false);

    assertThat(TheObjectsADeclarationPromises.of(module, RUN, List.of(A_CARD_SESSION, A_PIX)))
        .isInstanceOf(
            TheObjectsADeclarationPromises.Agreement.TheDeclarationAndTheObjectsAgree.class);
  }

  @Test
  void
      a_module_that_declares_two_objects_and_publishes_the_facts_of_one_is_reported_as_disagreeing() {
    SimulatorProvider module =
        new ProviderDouble()
            .declaring(TWO_OBJECTS_ONE_OF_THEM_A_CARD)
            .keepingWaiting(A_CARD_SESSION, true)
            .holding(A_PIX);

    assertThat(TheObjectsADeclarationPromises.of(module, RUN, List.of(A_CARD_SESSION, A_PIX)))
        .isInstanceOfSatisfying(
            TheObjectsADeclarationPromises.Agreement.TheyDisagree.class,
            apart ->
                assertThat(apart.why())
                    .contains("declares it keeps 2 object(s) waiting for a payer")
                    .contains("[a card session, a Pix]")
                    .contains("1 of the objects read here publish the facts of one"));
  }

  @Test
  void
      a_module_that_declares_one_object_and_publishes_the_facts_of_two_is_reported_as_disagreeing() {
    SimulatorProvider module =
        new ProviderDouble()
            .declaring(ONE_OBJECT_A_PAYER_NAMES_A_CARD_ON)
            .keepingWaiting(A_CARD_SESSION, true)
            .keepingWaiting(A_PIX, true);

    assertThat(TheObjectsADeclarationPromises.of(module, RUN, List.of(A_CARD_SESSION, A_PIX)))
        .isInstanceOfSatisfying(
            TheObjectsADeclarationPromises.Agreement.TheyDisagree.class,
            apart ->
                assertThat(apart.why())
                    .contains("declares it keeps 1 object(s) waiting for a payer")
                    .contains("2 of the objects read here publish the facts of one"));
  }

  @Test
  void a_module_that_declares_a_card_on_one_object_whose_payer_step_offers_two_is_reported_apart() {
    SimulatorProvider module =
        new ProviderDouble()
            .declaring(TWO_OBJECTS_ONE_OF_THEM_A_CARD)
            .keepingWaiting(A_CARD_SESSION, true)
            .keepingWaiting(A_PIX, true);

    assertThat(TheObjectsADeclarationPromises.of(module, RUN, List.of(A_CARD_SESSION, A_PIX)))
        .isInstanceOfSatisfying(
            TheObjectsADeclarationPromises.Agreement.TheyDisagree.class,
            apart ->
                assertThat(apart.why())
                    .contains("declares that a payer names a card on 1 of the 2 object(s)")
                    .contains("2 of the 2 read here offer a payer a card to finish with"));
  }

  @Test
  void
      a_module_that_declares_a_card_on_both_objects_whose_payer_step_offers_one_is_reported_apart() {
    SimulatorProvider module =
        new ProviderDouble()
            .declaring(
                new HostedPageContract.ObjectsAPayerFinishes(
                    List.of(
                        new HostedPageContract.AnObjectItKeepsWaiting("a card session", true),
                        new HostedPageContract.AnObjectItKeepsWaiting("a second card page", true))))
            .keepingWaiting(A_CARD_SESSION, true)
            .keepingWaiting(A_PIX, false);

    assertThat(TheObjectsADeclarationPromises.of(module, RUN, List.of(A_CARD_SESSION, A_PIX)))
        .isInstanceOfSatisfying(
            TheObjectsADeclarationPromises.Agreement.TheyDisagree.class,
            apart ->
                assertThat(apart.why())
                    .contains("declares that a payer names a card on 2 of the 2 object(s)")
                    .contains("1 of the 2 read here offer a payer a card to finish with"));
  }

  @Test
  void
      a_module_that_keeps_no_such_object_and_publishes_no_such_facts_is_reported_as_keeping_none() {
    SimulatorProvider module = new ProviderDouble().holding(A_PAYMENT);

    assertThat(TheObjectsADeclarationPromises.of(module, RUN, List.of(A_PAYMENT)))
        .isInstanceOf(
            TheObjectsADeclarationPromises.Agreement.TheDeclarationAndTheObjectsAgree.class);
  }

  @Test
  void a_module_that_declares_no_page_and_publishes_the_facts_of_one_is_reported_as_disagreeing() {
    SimulatorProvider module = new ProviderDouble().keepingWaiting(A_PIX, false);

    assertThat(TheObjectsADeclarationPromises.of(module, RUN, List.of(A_PIX)))
        .isInstanceOfSatisfying(
            TheObjectsADeclarationPromises.Agreement.TheyDisagree.class,
            apart ->
                assertThat(apart.why())
                    .contains("declares that its contract keeps no object waiting for a payer")
                    .contains("[" + A_PIX + "]"));
  }

  @Test
  void an_object_the_run_does_not_hold_is_reported_rather_than_counted_as_publishing_nothing() {
    SimulatorProvider module =
        new ProviderDouble()
            .declaring(ONE_OBJECT_A_PAYER_NAMES_A_CARD_ON)
            .keepingWaiting(A_CARD_SESSION, true);

    assertThat(TheObjectsADeclarationPromises.of(module, RUN, List.of(A_CARD_SESSION, A_PIX)))
        .isInstanceOfSatisfying(
            TheObjectsADeclarationPromises.Agreement.TheyDisagree.class,
            apart ->
                assertThat(apart.why())
                    .contains("holds none of that name in test run " + RUN.value())
                    .contains(A_PIX));
  }

  @Test
  void a_module_declaring_no_page_is_read_against_every_object_of_the_run_and_not_the_ones_named() {
    SimulatorProvider module =
        new ProviderDouble().holding(A_PAYMENT).keepingWaiting(A_PIX, false).listingWhatItHolds();

    assertThat(TheObjectsADeclarationPromises.of(module, RUN, List.of(A_PAYMENT)))
        .isInstanceOfSatisfying(
            TheObjectsADeclarationPromises.Agreement.TheyDisagree.class,
            apart ->
                assertThat(apart.why())
                    .contains("declares that its contract keeps no object waiting for a payer")
                    .contains("[" + A_PIX + "]"));
  }

  @Test
  void
      an_object_the_reading_named_by_a_later_identifier_is_not_reported_a_second_time_by_its_own() {
    SimulatorProvider module =
        new ProviderDouble()
            .keepingWaiting(A_PIX, false)
            .mintingAFurtherIdentifierOn(A_PIX, A_LATER_IDENTIFIER_OF_THE_PIX)
            .listingWhatItHolds();

    assertThat(
            TheObjectsADeclarationPromises.of(module, RUN, List.of(A_LATER_IDENTIFIER_OF_THE_PIX)))
        .isInstanceOfSatisfying(
            TheObjectsADeclarationPromises.Agreement.TheyDisagree.class,
            apart ->
                assertThat(apart.why())
                    .contains("[" + A_LATER_IDENTIFIER_OF_THE_PIX + "]")
                    .doesNotContain(A_PIX));
  }

  @Test
  void an_object_the_listing_carries_beside_the_one_the_reading_named_is_reported_with_it() {
    SimulatorProvider module =
        new ProviderDouble()
            .keepingWaiting(A_CARD_SESSION, true)
            .keepingWaiting(A_PIX, false)
            .listingWhatItHolds();

    assertThat(TheObjectsADeclarationPromises.of(module, RUN, List.of(A_CARD_SESSION)))
        .isInstanceOfSatisfying(
            TheObjectsADeclarationPromises.Agreement.TheyDisagree.class,
            apart -> assertThat(apart.why()).contains("[" + A_CARD_SESSION + ", " + A_PIX + "]"));
  }

  @Test
  void a_module_declaring_no_page_and_listing_nothing_that_waits_is_reported_as_keeping_none() {
    SimulatorProvider module = new ProviderDouble().holding(A_PAYMENT).listingWhatItHolds();

    assertThat(TheObjectsADeclarationPromises.of(module, RUN, List.of(A_PAYMENT)))
        .isInstanceOf(
            TheObjectsADeclarationPromises.Agreement.TheDeclarationAndTheObjectsAgree.class);
  }

  @Test
  void a_provider_refusing_the_first_page_of_its_own_run_as_a_cursor_is_reported_as_disagreeing() {
    SimulatorProvider module =
        new ProviderDouble().holding(A_PAYMENT).refusingEveryListingAsAnUnknownCursor();

    assertThat(TheObjectsADeclarationPromises.of(module, RUN, List.of(A_PAYMENT)))
        .isInstanceOfSatisfying(
            TheObjectsADeclarationPromises.Agreement.TheyDisagree.class,
            apart ->
                assertThat(apart.why())
                    .contains(
                        TheObjectsADeclarationPromises.THE_LISTING_REFUSED_A_CURSOR_IT_NEVER_GOT));
  }

  @Test
  void an_object_of_the_same_provider_that_waits_for_nobody_is_not_counted_among_what_waits() {
    SimulatorProvider module =
        new ProviderDouble()
            .declaring(ONE_OBJECT_A_PAYER_NAMES_A_CARD_ON)
            .keepingWaiting(A_CARD_SESSION, true)
            .holding(A_PAYMENT);

    assertThat(TheObjectsADeclarationPromises.of(module, RUN, List.of(A_CARD_SESSION, A_PAYMENT)))
        .isInstanceOf(
            TheObjectsADeclarationPromises.Agreement.TheDeclarationAndTheObjectsAgree.class);
  }
}
