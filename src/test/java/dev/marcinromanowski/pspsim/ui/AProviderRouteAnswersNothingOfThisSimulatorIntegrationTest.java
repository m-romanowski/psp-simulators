package dev.marcinromanowski.pspsim.ui;

import static org.assertj.core.api.Assertions.assertThat;

import dev.marcinromanowski.pspsim.PostgresBackedTest;
import dev.marcinromanowski.pspsim.kernel.spi.ProviderRegistry;
import dev.marcinromanowski.pspsim.kernel.spi.ProviderWireRoutes;
import dev.marcinromanowski.pspsim.kernel.spi.SimulatorProvider;
import dev.marcinromanowski.pspsim.kernel.spi.WhatAProviderAnswersOffItsRoutes;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestClient;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

class AProviderRouteAnswersNothingOfThisSimulatorIntegrationTest extends PostgresBackedTest {

  private static final String A_ROUTE_GROW_ANSWERS_ONLY_A_POST_ON =
      "/api/light/server/1.0/createPaymentProcess";

  private static final String A_ROUTE_NO_PROVIDER_OF_THIS_BUILD_MAPS =
      "/api/light/server/1.0/createPaymentProcessTypedWrong";

  @LocalServerPort private int port;

  private RestClient http;

  @BeforeEach
  void anApplicationWrittenAgainstTheRealProvider() {
    http = RestClient.create("http://localhost:" + port);
  }

  private static final String A_PAYER_PAGE_OF_AZERICARD_THAT_TAKES_ONLY_A_POST = "/azericard/payer";

  private static final String A_NEAR_MISS_OF_A_HOSTED_PAGE_ADDRESS =
      "/hostedtokenization/tokenization/form/";

  private static final String WHAT_A_BROWSER_ASKS_FOR =
      "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8";

  @Test
  void
      a_browser_that_lands_on_a_provider_or_a_payer_address_is_handed_no_screen_of_this_simulator() {
    for (String address :
        List.of(
            A_ROUTE_GROW_ANSWERS_ONLY_A_POST_ON,
            A_ROUTE_NO_PROVIDER_OF_THIS_BUILD_MAPS,
            A_PAYER_PAGE_OF_AZERICARD_THAT_TAKES_ONLY_A_POST,
            A_NEAR_MISS_OF_A_HOSTED_PAGE_ADDRESS)) {
      ResponseEntity<String> answered = whatABrowserIsAnswered(address);

      assertThat(answered.getStatusCode().is4xxClientError()).as(address).isTrue();
      assertThat(answered.getBody())
          .as(
              address
                  + " is not a screen of this simulator, and there is no login on the screens: a"
                  + " payer who mistypes a hosted page address was handed eight kilobytes of the"
                  + " operator's navigation, the provider switcher over every provider and"
                  + " links into /ui/objects and /ui/audit. A real provider's hosted page answers"
                  + " a near miss with nothing of the kind")
          .doesNotContain("Skip the navigation")
          .doesNotContain("PSP simulator")
          .doesNotContain("/ui/objects")
          .doesNotContain("This request was not served");
    }
  }

  @Test
  void a_wire_route_answers_a_client_nothing_of_this_simulator_s_own_insides() {
    ResponseEntity<String> wrongMethod = whatItAnswers(A_ROUTE_GROW_ANSWERS_ONLY_A_POST_ON);
    ResponseEntity<String> noSuchRoute = whatItAnswers(A_ROUTE_NO_PROVIDER_OF_THIS_BUILD_MAPS);

    assertThat(wrongMethod.getStatusCode()).isEqualTo(HttpStatus.METHOD_NOT_ALLOWED);
    assertThat(noSuchRoute.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    for (ResponseEntity<String> answered : List.of(wrongMethod, noSuchRoute)) {
      assertThat(answered.getBody())
          .as(
              "an application written against the real provider's API is pointed at this simulator"
                  + " and must not notice the difference, and no payment provider answers a client"
                  + " with a Java stack trace, a Java exception message or the name of a Java"
                  + " class. The screens want all three, which is why they are added for a /ui"
                  + " address and for no other")
          .doesNotContain("trace")
          .doesNotContain("at dev.marcinromanowski.pspsim")
          .doesNotContain("Exception")
          .doesNotContain("dev.marcinromanowski.pspsim")
          .doesNotContain("org.springframework")
          .doesNotContain("message");
    }
  }

  private static final Map<String, String> A_WIRE_ROUTE_OF_EACH_PROVIDER =
      Map.of(
          "grow", "/api/light/server/1.0/createPaymentProcess",
          "autra", "/v1/acquiring/pix/checkout",
          "stripe", "/v1/payment_intents",
          "worldline", "/v2/M1/payments",
          "azericard", "/cgi-bin/cgi_link");

  private static final Map<String, String> AN_UNSERVED_ADDRESS_OF_EACH_PROVIDER =
      Map.of(
          "grow", "/api/light/server/1.0/nothingHere",
          "autra", "/v1/acquiring/nothingHere",
          "stripe", "/v1/payment_intents/pi_1/nothingHere",
          "worldline", "/v2/M1/nothingHere",
          "azericard", "/cgi-bin/nothingHere");

  @Autowired private ProviderRegistry providers;

  @Autowired
  @Qualifier("requestMappingHandlerMapping")
  private RequestMappingHandlerMapping mappings;

  @Test
  void no_two_provider_modules_claim_one_address_but_where_one_claims_a_whole_route_of_its_own() {
    Set<String> routes = new LinkedHashSet<>();
    for (RequestMappingInfo mapping : mappings.getHandlerMethods().keySet()) {
      routes.addAll(mapping.getPatternValues());
    }
    List<String> collisions = new ArrayList<>();
    List<String> nested = new ArrayList<>();
    for (SimulatorProvider claiming : providers.modules()) {
      for (SimulatorProvider other : providers.modules()) {
        if (claiming.id().equals(other.id())) {
          continue;
        }
        for (String namespace : other.wireRoutes().namespaces()) {
          if (!claiming.wireRoutes().covers(namespace)) {
            continue;
          }
          String overlap =
              namespace
                  + " belongs to "
                  + other.id().value()
                  + " and is also claimed by "
                  + claiming.id().value();
          boolean aWholeRouteAnsweredByItsOwner =
              routes.contains(namespace)
                  && ProviderWireRoutes.theDeepestClaimOn(providers.modules(), namespace)
                      .map(SimulatorProvider::id)
                      .filter(other.id()::equals)
                      .isPresent();
          (aWholeRouteAnsweredByItsOwner ? nested : collisions).add(overlap);
        }
      }
    }

    assertThat(collisions)
        .as(
            "the core finds the provider an address belongs to by walking the modules in the"
                + " order the container built them, so two modules claiming one address would"
                + " make the envelope a client receives depend on that order; the one exception"
                + " is a namespace that is exactly a route its own module serves, which the"
                + " deepest claim gives to that module whatever the order")
        .isEmpty();
    assertThat(nested)
        .containsExactly(
            "/v2/handshake/create belongs to tranzila and is also claimed by worldline");
  }

  @Test
  void a_wrong_method_on_every_providers_wire_route_is_answered_the_same_whatever_was_asked_for() {
    for (Map.Entry<String, String> route : A_WIRE_ROUTE_OF_EACH_PROVIDER.entrySet()) {
      ResponseEntity<String> asJson = whatItAnswers(route.getValue());
      ResponseEntity<String> asABrowser = whatABrowserIsAnswered(route.getValue());

      assertThat(withoutTheIdThatIsDrawnPerAnswer(asABrowser))
          .as(
              "%s answered a browser Accept with a page of eight kilobytes while the same route"
                  + " answered an API client correctly, which is how a fidelity claim proved over"
                  + " JSON alone stayed green",
              route.getKey())
          .isEqualTo(withoutTheIdThatIsDrawnPerAnswer(asJson));
      assertThat(asABrowser.getStatusCode()).as(route.getKey()).isEqualTo(asJson.getStatusCode());
      assertThat(asJson.getBody()).as(route.getKey()).isNotNull();
      assertThat(whatNoProviderSends(asJson)).as(route.getKey()).isEmpty();
      assertThat(whatNoProviderSends(asABrowser)).as(route.getKey()).isEmpty();
    }
  }

  @Test
  void an_address_inside_a_providers_namespace_that_it_does_not_serve_is_answered_the_same_way() {
    for (Map.Entry<String, String> route : AN_UNSERVED_ADDRESS_OF_EACH_PROVIDER.entrySet()) {
      ResponseEntity<String> asJson = whatItAnswers(route.getValue());
      ResponseEntity<String> asABrowser = whatABrowserIsAnswered(route.getValue());

      assertThat(withoutTheIdThatIsDrawnPerAnswer(asABrowser))
          .as(route.getKey())
          .isEqualTo(withoutTheIdThatIsDrawnPerAnswer(asJson));
      assertThat(whatNoProviderSends(asJson)).as(route.getKey()).isEmpty();
      assertThat(whatNoProviderSends(asABrowser)).as(route.getKey()).isEmpty();
    }
  }

  @Test
  void the_screens_keep_the_page_this_simulator_draws_for_them_and_the_providers_keep_their_own() {
    ResponseEntity<String> aScreen = whatABrowserIsAnswered("/ui/nothingHere");
    ResponseEntity<String> aWireRoute =
        whatABrowserIsAnswered(A_ROUTE_NO_PROVIDER_OF_THIS_BUILD_MAPS);

    assertThat(aScreen.getBody())
        .as(
            "a screen's failure is drawn on this simulator's own page inside its shell, and that"
                + " treatment must not follow a client onto a provider route")
        .contains("This request was not served");
    assertThat(aWireRoute.getBody()).doesNotContain("This request was not served");
  }

  private static String withoutTheIdThatIsDrawnPerAnswer(ResponseEntity<String> answered) {
    String body = answered.getBody() == null ? "" : answered.getBody();
    return body.replaceAll(
        "\"errorId\":\"[0-9a-f-]+\"", "\"errorId\":\"one drawn for this answer\"");
  }

  private static List<String> whatNoProviderSends(ResponseEntity<String> answered) {
    List<String> found = new ArrayList<>();
    String body = answered.getBody() == null ? "" : answered.getBody();
    for (String leaked :
        List.of(
            "Whitelabel",
            "\"timestamp\"",
            "\"error\":\"",
            "no explicit mapping for /error",
            "org.springframework",
            "dev.marcinromanowski.pspsim")) {
      if (body.contains(leaked)) {
        found.add(leaked);
      }
    }
    return found;
  }

  private ResponseEntity<String> whatABrowserIsAnswered(String route) {
    return http.get()
        .uri(route)
        .header("Accept", WHAT_A_BROWSER_ASKS_FOR)
        .retrieve()
        .onStatus(status -> true, (request, response) -> {})
        .toEntity(String.class);
  }

  private ResponseEntity<String> whatItAnswers(String route) {
    return http.get()
        .uri(route)
        .accept(MediaType.APPLICATION_JSON)
        .retrieve()
        .onStatus(status -> true, (request, response) -> {})
        .toEntity(String.class);
  }

  private static final List<String> UNDER_THE_PATH_TWO_PROVIDERS_PUBLISH_UNDER =
      List.of("/v1", "/v1/nothingHere", "/v1/paymentintents", "/v1/oauth2/token");

  private static final Map<String, String> UNDER_A_PATH_ONE_PROVIDER_ALONE_PUBLISHES_UNDER =
      Map.of(
          "/api", "grow",
          "/api/lightly", "grow",
          "/tokenize", "autra",
          "/tokenize/sessionz", "autra",
          "/hostedtokenization", "worldline",
          "/hostedtokenization/tokenization/form", "worldline");

  @Test
  void an_address_under_a_path_two_providers_publish_under_is_answered_by_neither_of_them() {
    for (String address : UNDER_THE_PATH_TWO_PROVIDERS_PUBLISH_UNDER) {
      ResponseEntity<String> asJson = whatItAnswers(address);
      ResponseEntity<String> asABrowser = whatABrowserIsAnswered(address);

      assertThat(asJson.getStatusCode()).as(address).isEqualTo(HttpStatus.NOT_FOUND);
      assertThat(asABrowser.getBody())
          .as(
              "%s answered a browser Accept with this application's own Whitelabel page while an"
                  + " API client was handed a different body at the same address, and a fidelity"
                  + " claim proved over one representation proves a fraction of itself",
              address)
          .isEqualTo(asJson.getBody());
      for (ResponseEntity<String> answered : List.of(asJson, asABrowser)) {
        assertThat(whatNoProviderSends(answered)).as(address).isEmpty();
        assertThat(answered.getHeaders().getContentType())
            .as(address)
            .isEqualTo(
                MediaType.parseMediaType(WhatAProviderAnswersOffItsRoutes.DIAGNOSTIC_CONTENT_TYPE));
        assertThat(answered.getBody())
            .as(
                "autra and stripe both publish under /v1, so %s is attributable to neither, and"
                    + " an answer in the field names of one of them is indistinguishable from"
                    + " that vendor's to a client that logs it",
                address)
            .contains("no provider of this build claims the address " + address)
            .contains("autra")
            .contains("stripe")
            .contains("/v1/acquiring")
            .contains("/v1/payment_intents")
            .doesNotContain("This request was not served");
      }
    }
  }

  @Test
  void an_address_under_a_path_one_provider_alone_publishes_under_is_that_providers() {
    for (Map.Entry<String, String> address :
        UNDER_A_PATH_ONE_PROVIDER_ALONE_PUBLISHES_UNDER.entrySet()) {
      ResponseEntity<String> asJson = whatItAnswers(address.getKey());
      ResponseEntity<String> asABrowser = whatABrowserIsAnswered(address.getKey());

      assertThat(withoutTheIdThatIsDrawnPerAnswer(asABrowser))
          .as(address.getKey())
          .isEqualTo(withoutTheIdThatIsDrawnPerAnswer(asJson));
      for (ResponseEntity<String> answered : List.of(asJson, asABrowser)) {
        assertThat(whatNoProviderSends(answered)).as(address.getKey()).isEmpty();
        assertThat(answered.getBody())
            .as(
                "every address of %s is reached through the path above %s and no other provider"
                    + " of this build publishes anything under it, so attributing it guesses"
                    + " nothing",
                address.getValue(), address.getKey())
            .contains("provider " + address.getValue())
            .doesNotContain("This request was not served");
        for (String notThisOne : providers.names()) {
          if (!notThisOne.equals(address.getValue())) {
            assertThat(answered.getBody())
                .as(
                    "%s is reachable through no namespace of %s, and naming it would offer a"
                        + " client the wrong vendor's contract to read the answer against",
                    address.getKey(), notThisOne)
                .doesNotContain(notThisOne);
          }
        }
      }
    }
  }

  @Test
  void an_address_no_namespace_of_this_build_lies_under_starts_belonging_to_nothing() {
    for (String address : List.of("/zzz", "/v2x", "/tokens", "/farm")) {
      for (ResponseEntity<String> answered :
          List.of(whatItAnswers(address), whatABrowserIsAnswered(address))) {
        assertThat(answered.getBody())
            .as(
                "%s is claimed by no provider and owned by no part of this simulator, and an"
                    + " answer naming a provider or a contested path would have it pretend to"
                    + " belong to something, while one carrying the framework's envelope would"
                    + " publish a shape nothing of this build answers in",
                address)
            .isNull();
      }
    }
  }

  @Test
  void an_address_the_control_api_does_not_serve_keeps_the_answer_the_surface_left_it() {
    for (ResponseEntity<String> answered :
        List.of(
            whatItAnswers("/__simulator/nothingHere"),
            whatABrowserIsAnswered("/__simulator/nothingHere"))) {
      assertThat(answered.getBody())
          .as(
              "the control api is a surface this simulator asks, and it publishes no answer for"
                  + " an address it does not serve: that answer is the one it had, and it names"
                  + " no provider and no contested path")
          .isNotNull()
          .doesNotContain("no provider of this build claims the address")
          .doesNotContain("publishes no answer for");
    }
  }
}
