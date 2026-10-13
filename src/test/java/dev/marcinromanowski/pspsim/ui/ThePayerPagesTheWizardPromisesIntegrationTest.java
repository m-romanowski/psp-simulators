package dev.marcinromanowski.pspsim.ui;

import static org.assertj.core.api.Assertions.assertThat;

import dev.marcinromanowski.pspsim.PostgresBackedTest;
import dev.marcinromanowski.pspsim.journey.NoWorkingPayerAddressOnAScreen;
import dev.marcinromanowski.pspsim.kernel.spi.DeclaredOrigination;
import dev.marcinromanowski.pspsim.kernel.spi.ProviderRegistry;
import dev.marcinromanowski.pspsim.kernel.spi.SimulatorProvider;
import dev.marcinromanowski.pspsim.kernel.spi.ThePagesADeclarationPromises;
import dev.marcinromanowski.pspsim.kernel.spi.ThePayerStep;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

class ThePayerPagesTheWizardPromisesIntegrationTest extends PostgresBackedTest {

  private static final List<String> THE_PAYER_PAGES_THIS_BUILD_SERVES =
      NoWorkingPayerAddressOnAScreen.THE_PAGES_EACH_PROVIDER_SERVES.values().stream()
          .flatMap(List::stream)
          .sorted()
          .toList();

  private static final String A_WIZARD_WOULD_SEND_A_TESTER_TO_A_PAGE_THAT_IS_NOT_THERE =
      "the wizard tells a tester, for every kind of payment it offers, whether this simulator"
          + " serves the payer's page itself and at which route. That claim used to be a sentence"
          + " a provider wrote by hand, and two of them said this simulator does not serve a page"
          + " it does serve. It is data now, and this is what compares it against the routes this"
          + " build actually answers on";

  private static final String A_SCREEN_SWEPT_FOR_ANOTHER_MODULE_S_PAGE =
      "a journey sweeps the screen of an object for the payer pages that object's own provider"
          + " declares, so a route attributed to the wrong module would read one provider's"
          + " screens for a page it does not serve and that provider's screens for none at all,"
          + " and both of those pass. This is what holds the attribution to the declarations";

  @Autowired
  @Qualifier("requestMappingHandlerMapping")
  private RequestMappingHandlerMapping mappings;

  @Autowired private ProviderRegistry providers;

  @Test
  void every_payer_page_a_way_of_starting_a_payment_promises_is_a_route_this_build_answers_on() {
    Set<String> answeredOn = theRoutesThisBuildAnswersOn();

    for (SimulatorProvider module : providers.modules()) {
      assertThat(ThePagesADeclarationPromises.of(module.originationFields(), answeredOn))
          .as(A_WIZARD_WOULD_SEND_A_TESTER_TO_A_PAGE_THAT_IS_NOT_THERE)
          .isInstanceOf(
              ThePagesADeclarationPromises.Agreement.TheDeclarationAndTheRoutesAgree.class);
    }
  }

  @Test
  void the_pages_the_declarations_promise_are_the_payer_pages_of_this_build_and_no_others() {
    assertThat(thePagesTheDeclarationsPromise())
        .as(A_WIZARD_WOULD_SEND_A_TESTER_TO_A_PAGE_THAT_IS_NOT_THERE)
        .containsExactlyElementsOf(THE_PAYER_PAGES_THIS_BUILD_SERVES);
  }

  @Test
  void this_build_answers_on_each_of_those_pages_for_a_payer_arriving_in_a_browser() {
    Set<String> answeredOn = theRoutesThisBuildAnswersOn();

    assertThat(answeredOn)
        .as(A_WIZARD_WOULD_SEND_A_TESTER_TO_A_PAGE_THAT_IS_NOT_THERE)
        .containsAll(THE_PAYER_PAGES_THIS_BUILD_SERVES);
  }

  @Test
  void the_pages_a_module_declares_are_the_pages_its_own_screens_are_swept_for() {
    for (SimulatorProvider module : providers.modules()) {
      String provider = module.id().value();
      List<String> declared = thePagesDeclaredBy(module);
      if (declared.isEmpty()) {
        assertThat(NoWorkingPayerAddressOnAScreen.THE_PAGES_EACH_PROVIDER_SERVES)
            .as(A_SCREEN_SWEPT_FOR_ANOTHER_MODULE_S_PAGE)
            .doesNotContainKey(provider);
        assertThat(NoWorkingPayerAddressOnAScreen.THE_PROVIDERS_THAT_SERVE_NO_PAYER_PAGE)
            .as(A_SCREEN_SWEPT_FOR_ANOTHER_MODULE_S_PAGE)
            .contains(provider);
        continue;
      }
      assertThat(NoWorkingPayerAddressOnAScreen.THE_PROVIDERS_THAT_SERVE_NO_PAYER_PAGE)
          .as(A_SCREEN_SWEPT_FOR_ANOTHER_MODULE_S_PAGE)
          .doesNotContain(provider);
      assertThat(NoWorkingPayerAddressOnAScreen.THE_PAGES_EACH_PROVIDER_SERVES.get(provider))
          .as(A_SCREEN_SWEPT_FOR_ANOTHER_MODULE_S_PAGE)
          .containsExactlyElementsOf(declared);
    }
  }

  private List<String> thePagesTheDeclarationsPromise() {
    Set<String> promised = new LinkedHashSet<>();
    for (SimulatorProvider module : providers.modules()) {
      promised.addAll(thePagesDeclaredBy(module));
    }
    List<String> sorted = new ArrayList<>(promised);
    sorted.sort(String::compareTo);
    return List.copyOf(sorted);
  }

  private List<String> thePagesDeclaredBy(SimulatorProvider module) {
    Set<String> promised = new LinkedHashSet<>();
    for (DeclaredOrigination way : module.originationFields().waysToStartAPayment()) {
      if (way.thePayerStep() instanceof ThePayerStep.APageThisSimulatorServes page) {
        promised.add(page.route());
      }
    }
    return List.copyOf(promised);
  }

  private Set<String> theRoutesThisBuildAnswersOn() {
    Set<String> answeredOn = new LinkedHashSet<>();
    for (RequestMappingInfo mapping : mappings.getHandlerMethods().keySet()) {
      answeredOn.addAll(mapping.getPatternValues());
    }
    return Set.copyOf(answeredOn);
  }
}
