package dev.marcinromanowski.pspsim.kernel.spi;

import static org.assertj.core.api.Assertions.assertThat;

import dev.marcinromanowski.pspsim.kernel.ProviderId;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Set;
import org.assertj.core.api.InstanceOfAssertFactories;
import org.junit.jupiter.api.Test;

class ThePagesADeclarationPromisesTest {

  private static final ProviderId APROVIDER = ProviderId.of("aprovider");

  private static final String A_PAGE = "/aprovider/payer/{session}";

  private static final String A_SECOND_PAGE = "/aprovider/tokenize/{session}";

  private static final AFieldATesterFillsIn AMOUNT =
      AFieldATesterFillsIn.typed("amount", "amount", true, "what goes on the card.", "10.00");

  @Test
  void a_declaration_whose_every_payer_page_this_build_answers_on_agrees_with_the_routes() {
    ProviderOriginationFields declared =
        theWays(
            aWay("a hold", new ThePayerStep.APageThisSimulatorServes(A_PAGE)),
            aWay("a card", new ThePayerStep.APageThisSimulatorServes(A_SECOND_PAGE)));

    assertThat(ThePagesADeclarationPromises.of(declared, Set.of(A_PAGE, A_SECOND_PAGE)))
        .isInstanceOf(ThePagesADeclarationPromises.Agreement.TheDeclarationAndTheRoutesAgree.class);
  }

  @Test
  void a_way_that_promises_a_page_this_build_answers_on_nowhere_disagrees_and_names_both_sides() {
    ProviderOriginationFields declared =
        theWays(
            aWay("a hold", new ThePayerStep.APageThisSimulatorServes(A_PAGE)),
            aWay("a card", new ThePayerStep.NoPageThisSimulatorServes("nothing waits.")));

    ThePagesADeclarationPromises.Agreement read =
        ThePagesADeclarationPromises.of(declared, Set.of(A_SECOND_PAGE));

    assertThat(read)
        .asInstanceOf(
            InstanceOfAssertFactories.type(
                ThePagesADeclarationPromises.Agreement.TheyDisagree.class))
        .extracting(ThePagesADeclarationPromises.Agreement.TheyDisagree::why)
        .asString()
        .contains("provider aprovider")
        .contains("a hold at " + A_PAGE)
        .contains(A_SECOND_PAGE)
        .contains("a tester is sent to a page that is not there");
  }

  @Test
  void every_way_is_read_and_not_only_the_first_of_them() {
    ProviderOriginationFields declared =
        theWays(
            aWay("a hold", new ThePayerStep.APageThisSimulatorServes(A_PAGE)),
            aWay("a card", new ThePayerStep.APageThisSimulatorServes(A_SECOND_PAGE)));

    ThePagesADeclarationPromises.Agreement read =
        ThePagesADeclarationPromises.of(declared, Set.of(A_PAGE));

    assertThat(read)
        .asInstanceOf(
            InstanceOfAssertFactories.type(
                ThePagesADeclarationPromises.Agreement.TheyDisagree.class))
        .extracting(ThePagesADeclarationPromises.Agreement.TheyDisagree::why)
        .asString()
        .contains("a card at " + A_SECOND_PAGE)
        .doesNotContain("a hold at " + A_PAGE);
  }

  @Test
  void a_declaration_that_promises_no_payer_page_at_all_agrees_with_a_build_that_answers_on_none() {
    ProviderOriginationFields declared =
        theWays(
            aWay("a hold", new ThePayerStep.NoPageThisSimulatorServes("nothing waits.")),
            aWay("a card", new ThePayerStep.NoPageThisSimulatorServes("nothing waits here too.")));

    assertThat(ThePagesADeclarationPromises.of(declared, Set.of()))
        .isInstanceOf(ThePagesADeclarationPromises.Agreement.TheDeclarationAndTheRoutesAgree.class);
  }

  private static ProviderOriginationFields theWays(DeclaredOrigination... ways) {
    return new ProviderOriginationFields(APROVIDER, List.of(ways), new LinkedHashMap<>());
  }

  private static DeclaredOrigination aWay(String name, ThePayerStep step) {
    return new DeclaredOrigination(
        name, "Start it this way", "what it leaves behind", step, List.of(AMOUNT));
  }
}
