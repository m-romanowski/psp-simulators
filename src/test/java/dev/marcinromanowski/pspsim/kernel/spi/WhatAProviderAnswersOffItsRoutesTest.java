package dev.marcinromanowski.pspsim.kernel.spi;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class WhatAProviderAnswersOffItsRoutesTest {

  private static final int NOT_FOUND = 404;

  private static final int METHOD_NOT_ALLOWED = 405;

  private static final byte[] AN_ENVELOPE_OF_ITS_OWN =
      "{\"err\":{\"id\":1}}".getBytes(StandardCharsets.UTF_8);

  private final ProviderDouble answersForItself =
      new ProviderDouble("ownwords")
          .answering(
              List.of("/api/light"),
              new ProviderWireAnswer.InThisProvidersOwnWords(
                  200, "text/html", AN_ENVELOPE_OF_ITS_OWN));

  private final ProviderDouble publishesNothing =
      new ProviderDouble("silent")
          .answering(
              List.of("/cgi-bin", "/token"),
              new ProviderWireAnswer.NothingThisContractPublishes(
                  "a signed body on the two routes it serves and nothing else"));

  private final WhatAProviderAnswersOffItsRoutes answers =
      new WhatAProviderAnswersOffItsRoutes(
          new ProviderRegistry(List.of(answersForItself, publishesNothing)));

  @Test
  void an_address_no_provider_of_this_build_owns_is_left_to_whatever_answered_it_before() {
    assertThat(answers.to(WireFault.AN_ADDRESS_THIS_PROVIDER_DOES_NOT_SERVE, "/ui/objects", 404))
        .as(
            "a screen's failure is drawn on this simulator's own page inside its shell, and that"
                + " treatment is not a provider's to take over")
        .isEmpty();
    assertThat(answers.to(WireFault.AN_ADDRESS_THIS_PROVIDER_DOES_NOT_SERVE, "/zzz", 404))
        .as("no namespace of either provider lies under /zzz, so neither could have been meant")
        .isEmpty();
    assertThat(answersForItself.theFaultsItWasAskedAbout()).isEmpty();
    assertThat(publishesNothing.theFaultsItWasAskedAbout()).isEmpty();
  }

  @Test
  void the_provider_whose_namespace_holds_the_address_is_the_one_asked_and_the_only_one() {
    answers.to(WireFault.A_METHOD_THIS_ADDRESS_DOES_NOT_ANSWER, "/cgi-bin/cgi_link", 405);

    assertThat(publishesNothing.theFaultsItWasAskedAbout())
        .containsExactly(WireFault.A_METHOD_THIS_ADDRESS_DOES_NOT_ANSWER);
    assertThat(answersForItself.theFaultsItWasAskedAbout()).isEmpty();
  }

  @Test
  void a_provider_that_answers_in_its_own_words_decides_the_status_the_type_and_the_bytes() {
    WireAnswer answered =
        answers
            .to(WireFault.AN_ADDRESS_THIS_PROVIDER_DOES_NOT_SERVE, "/api/light/nothingHere", 404)
            .orElseThrow();

    assertThat(answered.httpStatus())
        .as(
            "one provider of this build answers a request it will not process at HTTP 200, so the"
                + " status the container chose is the provider's to overrule")
        .isEqualTo(200);
    assertThat(answered.contentType()).isEqualTo("text/html");
    assertThat(answered.body()).isEqualTo(AN_ENVELOPE_OF_ITS_OWN);
  }

  @Test
  void
      a_provider_that_publishes_nothing_is_answered_in_this_simulators_own_voice_and_not_its_own() {
    WireAnswer answered =
        answers
            .to(WireFault.AN_ADDRESS_THIS_PROVIDER_DOES_NOT_SERVE, "/cgi-bin/nothingHere", 404)
            .orElseThrow();

    assertThat(answered.httpStatus()).isEqualTo(NOT_FOUND);
    assertThat(answered.contentType())
        .isEqualTo(WhatAProviderAnswersOffItsRoutes.DIAGNOSTIC_CONTENT_TYPE);
    String said = new String(answered.body(), StandardCharsets.UTF_8);
    assertThat(said).contains("silent").contains("the two routes it serves");
  }

  @Test
  void a_provider_that_publishes_nothing_keeps_the_status_nothing_else_chose_for_every_fault() {
    for (WireFault fault : WireFault.values()) {
      Optional<WireAnswer> answered = answers.to(fault, "/token/cgi_link", METHOD_NOT_ALLOWED);

      assertThat(answered.orElseThrow().httpStatus())
          .as(fault.name())
          .isEqualTo(METHOD_NOT_ALLOWED);
    }
  }

  @Test
  void each_fault_a_provider_publishes_prose_for_is_said_in_its_own_words_and_in_text_plain() {
    for (WireFault fault :
        List.of(
            WireFault.AN_ADDRESS_THIS_PROVIDER_DOES_NOT_SERVE,
            WireFault.A_METHOD_THIS_ADDRESS_DOES_NOT_ANSWER)) {
      WireAnswer answered = answers.to(fault, "/cgi-bin/cgi_link", NOT_FOUND).orElseThrow();

      assertThat(answered.contentType())
          .as(fault.name())
          .isEqualTo(WhatAProviderAnswersOffItsRoutes.DIAGNOSTIC_CONTENT_TYPE);
      assertThat(new String(answered.body(), StandardCharsets.UTF_8))
          .as(fault.name())
          .contains("publishes no answer for")
          .contains("silent");
    }
    assertThat(
            List.of(
                theGapNamedFor(WireFault.AN_ADDRESS_THIS_PROVIDER_DOES_NOT_SERVE),
                theGapNamedFor(WireFault.A_METHOD_THIS_ADDRESS_DOES_NOT_ANSWER)))
        .as("a tester reads off the body which of the faults was hit")
        .doesNotHaveDuplicates();
  }

  @Test
  void a_failure_a_provider_publishes_nothing_for_is_answered_with_no_body_and_no_media_type() {
    WireAnswer answered =
        answers
            .to(WireFault.A_FAILURE_NOTHING_ELSE_ANSWERED, "/cgi-bin/cgi_link", 503)
            .orElseThrow();

    assertThat(answered.body())
        .as(
            "this simulator's english prose in text/plain is a content type the real platform"
                + " never sends, so a generated client dispatching on content type or one with a"
                + " strict Accept behaves differently here than against the vendor; a bodyless"
                + " 5xx emits nothing the vendor never sends and can be read as no envelope")
        .isEmpty();
    assertThat(answered.contentType()).isEmpty();
    assertThat(answered.httpStatus()).isEqualTo(503);
  }

  private String theGapNamedFor(WireFault fault) {
    return new String(
        answers.to(fault, "/cgi-bin/cgi_link", NOT_FOUND).orElseThrow().body(),
        StandardCharsets.UTF_8);
  }

  private final ProviderDouble autraShaped =
      new ProviderDouble("autrashaped")
          .answering(
              List.of("/v1/oauth", "/v1/acquiring", "/tokenize/sessions"),
              new ProviderWireAnswer.NothingThisContractPublishes(
                  "two error shapes for the operations it serves and nothing for an address"));

  private final ProviderDouble stripeShaped =
      new ProviderDouble("stripeshaped")
          .answering(
              List.of("/v1/payment_intents", "/v1/refunds"),
              new ProviderWireAnswer.InThisProvidersOwnWords(
                  404, "application/json", "{\"error\":{\"type\":\"x\"}}".getBytes(UTF_8)));

  private final WhatAProviderAnswersOffItsRoutes twoProvidersUnderOnePath =
      new WhatAProviderAnswersOffItsRoutes(
          new ProviderRegistry(List.of(autraShaped, stripeShaped)));

  @Test
  void a_path_exactly_one_provider_publishes_under_is_that_providers_and_is_not_a_guess() {
    WireAnswer answered =
        answers
            .to(WireFault.AN_ADDRESS_THIS_PROVIDER_DOES_NOT_SERVE, "/api/lightly", NOT_FOUND)
            .orElseThrow();

    assertThat(answersForItself.theFaultsItWasAskedAbout())
        .as(
            "every address of this provider is reached through /api and no other provider"
                + " publishes anything under it, so a client that mistyped the segment under /api"
                + " could have meant no one else, and attributing it guesses nothing")
        .containsExactly(WireFault.AN_ADDRESS_THIS_PROVIDER_DOES_NOT_SERVE);
    assertThat(publishesNothing.theFaultsItWasAskedAbout()).isEmpty();
    assertThat(answered.contentType()).isEqualTo("text/html");
    assertThat(answered.body()).isEqualTo(AN_ENVELOPE_OF_ITS_OWN);
  }

  @Test
  void a_path_more_than_one_provider_publishes_under_is_answered_by_neither_of_them() {
    WireAnswer answered =
        twoProvidersUnderOnePath
            .to(WireFault.AN_ADDRESS_THIS_PROVIDER_DOES_NOT_SERVE, "/v1/paymentintents", NOT_FOUND)
            .orElseThrow();

    assertThat(autraShaped.theFaultsItWasAskedAbout())
        .as(
            "answering in one vendor's field names for an address the other vendor could as"
                + " easily have been meant by is a lie a client logging it cannot tell from the"
                + " real thing, so neither provider is asked at all")
        .isEmpty();
    assertThat(stripeShaped.theFaultsItWasAskedAbout()).isEmpty();
    assertThat(answered.httpStatus()).isEqualTo(NOT_FOUND);
    assertThat(answered.contentType())
        .isEqualTo(WhatAProviderAnswersOffItsRoutes.DIAGNOSTIC_CONTENT_TYPE);
  }

  @Test
  void the_answer_for_a_contested_path_names_every_claimant_and_only_what_it_claims_under_it() {
    String said =
        new String(
            twoProvidersUnderOnePath
                .to(WireFault.AN_ADDRESS_THIS_PROVIDER_DOES_NOT_SERVE, "/v1/paymentintents", 404)
                .orElseThrow()
                .body(),
            UTF_8);

    assertThat(said)
        .contains("/v1/paymentintents")
        .contains("/v1")
        .contains("autrashaped")
        .contains("stripeshaped")
        .contains("/v1/oauth")
        .contains("/v1/acquiring")
        .contains("/v1/payment_intents")
        .contains("/v1/refunds");
    assertThat(said)
        .as(
            "the addresses a claimant publishes somewhere else entirely are no help to a client"
                + " that mistyped a segment under the contested path, and naming them would say"
                + " this provider answers under it when it does not")
        .doesNotContain("/tokenize/sessions");
  }

  @Test
  void the_deepest_path_a_namespace_lies_under_decides_and_not_the_shallowest() {
    ProviderDouble deeper =
        new ProviderDouble("deeper")
            .answering(
                List.of("/shared/branch/leaf"),
                new ProviderWireAnswer.NothingThisContractPublishes("nothing of its own"));
    ProviderDouble shallower =
        new ProviderDouble("shallower")
            .answering(
                List.of("/shared/other"),
                new ProviderWireAnswer.NothingThisContractPublishes("nothing of its own"));
    WhatAProviderAnswersOffItsRoutes both =
        new WhatAProviderAnswersOffItsRoutes(new ProviderRegistry(List.of(deeper, shallower)));

    both.to(WireFault.AN_ADDRESS_THIS_PROVIDER_DOES_NOT_SERVE, "/shared/branch/leafy", 404);

    assertThat(deeper.theFaultsItWasAskedAbout())
        .as(
            "/shared is published under by both, and /shared/branch by one of them alone, so"
                + " reading the address from the shallowest path up would hand a client the"
                + " simulator's own answer where exactly one provider could have been meant")
        .containsExactly(WireFault.AN_ADDRESS_THIS_PROVIDER_DOES_NOT_SERVE);
    assertThat(shallower.theFaultsItWasAskedAbout()).isEmpty();
  }

  @Test
  void a_contested_path_still_leaves_every_address_outside_every_namespace_alone() {
    for (String address :
        List.of("/ui", "/ui/objects", "//ui/objects", "/", "//", "", "/v2", "/v1x", "/tokenizes")) {
      assertThat(
              twoProvidersUnderOnePath.to(
                  WireFault.AN_ADDRESS_THIS_PROVIDER_DOES_NOT_SERVE, address, 404))
          .as(
              "%s is an address no namespace of this build lies under, and a screen's failure is"
                  + " drawn on this simulator's own page inside its shell",
              address)
          .isEmpty();
    }
  }

  @Test
  void a_contested_path_keeps_the_status_nothing_else_chose_for_every_fault() {
    for (WireFault fault : WireFault.values()) {
      assertThat(
              twoProvidersUnderOnePath
                  .to(fault, "/v1/paymentintents", METHOD_NOT_ALLOWED)
                  .orElseThrow()
                  .httpStatus())
          .as(fault.name())
          .isEqualTo(METHOD_NOT_ALLOWED);
    }
  }

  @Test
  void each_fault_off_every_namespace_that_is_said_at_all_is_said_in_its_own_words_too() {
    List<String> said = new ArrayList<>();
    for (WireFault fault :
        List.of(
            WireFault.AN_ADDRESS_THIS_PROVIDER_DOES_NOT_SERVE,
            WireFault.A_METHOD_THIS_ADDRESS_DOES_NOT_ANSWER)) {
      WireAnswer answered =
          twoProvidersUnderOnePath.to(fault, "/v1/paymentintents", NOT_FOUND).orElseThrow();

      assertThat(answered.contentType())
          .as(fault.name())
          .isEqualTo(WhatAProviderAnswersOffItsRoutes.DIAGNOSTIC_CONTENT_TYPE);
      said.add(new String(answered.body(), UTF_8));
    }

    assertThat(said).hasSize(2).doesNotHaveDuplicates();
    assertThat(said.get(0)).contains("no provider of this build claims the address");
  }

  @Test
  void a_failure_on_a_contested_path_is_answered_with_no_body_either() {
    WireAnswer answered =
        twoProvidersUnderOnePath
            .to(WireFault.A_FAILURE_NOTHING_ELSE_ANSWERED, "/v1/paymentintents", 500)
            .orElseThrow();

    assertThat(answered.body())
        .as(
            "an address neither provider claims is answered for by this simulator, and for a"
                + " failure of the platform that answer is the one shape no vendor's client can"
                + " misread: none")
        .isEmpty();
    assertThat(answered.contentType()).isEmpty();
    assertThat(answered.httpStatus()).isEqualTo(500);
  }

  @Test
  void the_paths_an_address_is_read_through_run_from_its_deepest_segment_to_its_first() {
    assertThat(WhatAProviderAnswersOffItsRoutes.fromTheDeepestSegmentUp("/v1/oauth/token"))
        .containsExactly("/v1/oauth/token", "/v1/oauth", "/v1");
    assertThat(WhatAProviderAnswersOffItsRoutes.fromTheDeepestSegmentUp("/v1/"))
        .containsExactly("/v1");
    assertThat(WhatAProviderAnswersOffItsRoutes.fromTheDeepestSegmentUp("/"))
        .as(
            "a namespace of / is refused because it would claim every address of this"
                + " application, the screens included, and so is reading an address through it")
        .isEmpty();
    assertThat(WhatAProviderAnswersOffItsRoutes.fromTheDeepestSegmentUp("")).isEmpty();
    assertThat(WhatAProviderAnswersOffItsRoutes.fromTheDeepestSegmentUp("//ui/objects"))
        .as(
            "a doubled separator must not read an address through the root, which every"
                + " namespace of every provider lies under and which would take the screens")
        .containsExactly("//ui/objects", "//ui");
    assertThat(WhatAProviderAnswersOffItsRoutes.fromTheDeepestSegmentUp("/a"))
        .containsExactly("/a");
    assertThat(WhatAProviderAnswersOffItsRoutes.fromTheDeepestSegmentUp("/v1x"))
        .as(
            "the shortest walk is one path, the address itself, and it is the one that finds a"
                + " namespace lying under the address rather than above it")
        .containsExactly("/v1x");
    assertThat(WhatAProviderAnswersOffItsRoutes.fromTheDeepestSegmentUp("/v1/x"))
        .containsExactly("/v1/x", "/v1");
    assertThat(WhatAProviderAnswersOffItsRoutes.fromTheDeepestSegmentUp("/a/b/c/d/e"))
        .as("every segment of the address is a path it is read through, and none is skipped")
        .containsExactly("/a/b/c/d/e", "/a/b/c/d", "/a/b/c", "/a/b", "/a");
  }

  @Test
  void the_deepest_path_read_is_the_address_itself_so_a_namespace_under_it_is_found() {
    ProviderDouble endsInASeparator =
        new ProviderDouble("endsinaseparator")
            .answering(
                List.of("/hostedtokenization/tokenization/form/"),
                new ProviderWireAnswer.NothingThisContractPublishes("nothing of its own"));
    WhatAProviderAnswersOffItsRoutes answering =
        new WhatAProviderAnswersOffItsRoutes(
            new ProviderRegistry(List.of(endsInASeparator, publishesNothing)));

    answering.to(
        WireFault.AN_ADDRESS_THIS_PROVIDER_DOES_NOT_SERVE,
        "/hostedtokenization/tokenization/form",
        NOT_FOUND);

    assertThat(endsInASeparator.theFaultsItWasAskedAbout())
        .as(
            "this provider's published path ends in a separator, so the address a client reaches"
                + " it through without one is covered by nothing and is found only because the"
                + " address itself is the deepest path it is read through")
        .containsExactly(WireFault.AN_ADDRESS_THIS_PROVIDER_DOES_NOT_SERVE);
    assertThat(publishesNothing.theFaultsItWasAskedAbout()).isEmpty();
  }

  @Test
  void an_empty_address_is_not_read_as_the_root_that_every_namespace_lies_under() {
    assertThat(WhatAProviderAnswersOffItsRoutes.fromTheDeepestSegmentUp(""))
        .as(
            "every namespace of every provider lies under the empty path as it does under /,"
                + " because a namespace begins with a separator, so reading an address through it"
                + " would make every provider a claimant and could take the screens")
        .isEmpty();
    assertThat(
            twoProvidersUnderOnePath.to(WireFault.AN_ADDRESS_THIS_PROVIDER_DOES_NOT_SERVE, "", 404))
        .isEmpty();
    assertThat(autraShaped.theFaultsItWasAskedAbout()).isEmpty();
    assertThat(stripeShaped.theFaultsItWasAskedAbout()).isEmpty();
  }
}
