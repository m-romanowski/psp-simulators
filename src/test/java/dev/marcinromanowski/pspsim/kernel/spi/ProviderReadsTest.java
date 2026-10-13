package dev.marcinromanowski.pspsim.kernel.spi;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.marcinromanowski.pspsim.kernel.ObjectOrigin;
import dev.marcinromanowski.pspsim.kernel.ProviderId;
import dev.marcinromanowski.pspsim.kernel.testrun.TestRunId;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ProviderReadsTest {

  private static final ProviderId APROVIDER = ProviderId.of("aprovider");

  private static final String IT_PUBLISHES_NONE =
      "this provider publishes every read on its own object addresses and takes no identifier of a"
          + " merchant's own anywhere";

  private static final DeclaredRead A_RETRACE =
      new DeclaredRead(
          "aRetrace",
          "Find the charge",
          "which charge it holds under a merchant's own identifiers.",
          DeclaredRead.EmptyAnswer.whatItMeans(
              "it holds no charge of yours under those identifiers."),
          List.of(
              AFieldATesterFillsIn.typed(
                  "myOwnIdentifier", "your own identifier", true, "what it finds it by.", "1")));

  @Test
  void the_reads_a_provider_publishes_are_offered_by_name_and_by_label() {
    ProviderReads declared = ProviderReads.theOnesItPublishes(APROVIDER, List.of(A_RETRACE));

    assertThat(declared.publishesAny()).isTrue();
    assertThat(declared.names()).containsExactly("aRetrace");
    assertThat(declared.readNamed("aRetrace")).contains(A_RETRACE);
    assertThat(declared.readNamed("somethingElse")).isEmpty();
    assertThat(declared.theReadsItOffers()).isEqualTo("Find the charge (aRetrace)");
  }

  @Test
  void a_provider_that_publishes_none_says_in_its_own_words_what_it_has_instead() {
    ProviderReads declared = ProviderReads.noneOfThem(APROVIDER, IT_PUBLISHES_NONE);

    assertThat(declared.publishesAny()).isFalse();
    assertThat(declared.whatItPublishesInstead()).isEqualTo(IT_PUBLISHES_NONE);
    assertThat(declared.theOutcomeOfAModuleThatPerformsNoRead("anything"))
        .isEqualTo(new ReadOutcome.NotOffered(IT_PUBLISHES_NONE));
  }

  @Test
  void a_question_asked_of_a_provider_that_publishes_none_is_refused_with_what_it_has_instead() {
    ProviderReads declared = ProviderReads.noneOfThem(APROVIDER, IT_PUBLISHES_NONE);

    assertThat(declared.theRefusalOfAQuestionThisProviderDoesNotAnswer("aRetrace"))
        .as(
            "a refusal built from the questions a provider offers degenerates to a colon and"
                + " nothing after it when the provider offers none, and a tester reading it"
                + " would be told to pick one of an empty list")
        .isEqualTo(
            "provider aprovider answers no question a tester asks with identifiers of their own,"
                + " so there is no aRetrace to ask of it and no other question of it either: "
                + IT_PUBLISHES_NONE);
  }

  @Test
  void a_question_asked_of_a_provider_that_publishes_one_is_refused_with_the_one_it_answers() {
    ProviderReads declared = ProviderReads.theOnesItPublishes(APROVIDER, List.of(A_RETRACE));

    assertThat(declared.theRefusalOfAQuestionThisProviderDoesNotAnswer("somethingElse"))
        .isEqualTo(
            "provider aprovider answers one of these questions and no other: Find the charge"
                + " (aRetrace). Go back a step and pick one of them.");
  }

  @Test
  void a_provider_that_publishes_none_and_says_nothing_instead_is_refused() {
    assertThatThrownBy(() -> ProviderReads.noneOfThem(APROVIDER, " "))
        .as(
            "a screen reporting that absence in this simulator's words would report a decision"
                + " this simulator did not make")
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("publishes no read a tester asks with values of their own");
  }

  @Test
  void a_provider_that_publishes_a_read_and_an_instead_is_refused() {
    assertThatThrownBy(() -> new ProviderReads(APROVIDER, List.of(A_RETRACE), IT_PUBLISHES_NONE))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("at the same time says what it has in place of one");
  }

  @Test
  void one_read_declared_twice_is_refused() {
    assertThatThrownBy(
            () -> ProviderReads.theOnesItPublishes(APROVIDER, List.of(A_RETRACE, A_RETRACE)))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("twice as a read");
  }

  @Test
  void a_module_that_declares_nothing_publishes_no_read_and_answers_one_with_what_it_has_instead() {
    SimulatorProvider module = new ProviderDouble(APROVIDER.value());

    assertThat(module.reads().publishesAny())
        .as("a module says nothing about reads until it declares one, and that is an absence")
        .isFalse();
    assertThat(
            module.read(
                ReadRequest.of(
                    TestRunId.of("provider-reads"),
                    "merchant-1",
                    "aRetrace",
                    Map.of(),
                    ObjectOrigin.UI)))
        .as(
            "a screen that asked it anyway is told what this provider has in place of a read, in"
                + " this simulator's words only because the module declared none of its own")
        .isEqualTo(
            new ReadOutcome.NotOffered(
                SimulatorProvider.THIS_MODULE_PUBLISHES_NO_READ_A_TESTER_ASKS));
  }

  @Test
  void a_module_that_declares_a_read_and_performs_none_is_a_defect_and_not_an_absence() {
    ProviderReads declared = ProviderReads.theOnesItPublishes(APROVIDER, List.of(A_RETRACE));

    assertThatThrownBy(() -> declared.theOutcomeOfAModuleThatPerformsNoRead("aRetrace"))
        .as(
            "a tester who asked the question this provider declares would otherwise be told the"
                + " provider publishes no read at all")
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("publishes the read aRetrace and its module performs none");
  }
}
