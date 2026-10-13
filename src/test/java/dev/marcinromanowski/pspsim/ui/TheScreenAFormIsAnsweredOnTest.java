package dev.marcinromanowski.pspsim.ui;

import static org.assertj.core.api.Assertions.assertThat;

import dev.marcinromanowski.pspsim.kernel.spi.ProviderRegistry;
import dev.marcinromanowski.pspsim.kernel.testrun.TestRunId;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.web.servlet.mvc.support.RedirectAttributesModelMap;

class TheScreenAFormIsAnsweredOnTest {

  private static final String THE_SCREEN_THE_POST_REACHED = "/ui/objects";

  private static final String THE_PIX_SCREEN = "/ui/x/autra/pix";

  private static final String THE_WIZARD = "/ui/objects/new";

  private static final String WHY = "tick confirm before moving money on a payment";

  private static final String THE_SCREEN_THE_CONTROLLER_WOULD_DRAW = "objects";

  private static final Map<String, String> THE_PIX_FORM_AS_IT_WAS_FILLED =
      Map.of("amount", "abc", "currency", "BRL", "description", "Pedido", "orderId", "pedido-1");

  @Test
  void a_refusal_of_a_form_on_a_contributed_screen_is_drawn_on_that_screen_and_not_a_core_one() {
    RedirectAttributesModelMap attributes = new RedirectAttributesModelMap();

    String where =
        theScreenAFormIsAnsweredOn()
            .aRefusalIsDrawnOn(
                THE_PIX_SCREEN,
                THE_SCREEN_THE_POST_REACHED,
                WHY,
                chosen(),
                WhatWasTyped.theFieldsOf(THE_PIX_FORM_AS_IT_WAS_FILLED),
                attributes,
                () -> THE_SCREEN_THE_CONTROLLER_WOULD_DRAW);

    assertThat(where).isEqualTo("redirect:" + THE_PIX_SCREEN);
    assertThat(attributes.getFlashAttributes().get(TheScreenAFormIsAnsweredOn.THE_REFUSAL))
        .as(
            "a refusal a tester never reads is a refusal nobody was given, so the message travels"
                + " with the redirect and is drawn beside the form that was filled")
        .isEqualTo(WHY);
    assertThat(attributes)
        .containsEntry("provider", "autra")
        .containsEntry(TheScreenAFormIsAnsweredOn.THE_TEST_RUN, "a-run");
    assertThat(attributes.getFlashAttributes().get(WhatWasTyped.ATTRIBUTE))
        .as(
            "the redirect carried the refusal and threw away the form: a tester read why the"
                + " payment would not start on a screen with every field blank again")
        .isEqualTo(WhatWasTyped.theFieldsOf(THE_PIX_FORM_AS_IT_WAS_FILLED));
  }

  @Test
  void a_refusal_of_a_form_that_named_no_screen_to_come_back_to_is_drawn_where_it_was_posted() {
    RedirectAttributesModelMap attributes = new RedirectAttributesModelMap();

    String where =
        theScreenAFormIsAnsweredOn()
            .aRefusalIsDrawnOn(
                null,
                THE_SCREEN_THE_POST_REACHED,
                WHY,
                chosen(),
                WhatWasTyped.theFieldsOf(THE_PIX_FORM_AS_IT_WAS_FILLED),
                attributes,
                () -> THE_SCREEN_THE_CONTROLLER_WOULD_DRAW);

    assertThat(where).isEqualTo(THE_SCREEN_THE_CONTROLLER_WOULD_DRAW);
    assertThat(attributes.getFlashAttributes())
        .as(
            "the screen this post reached is the screen the form was on, so it is drawn in place"
                + " with what the tester typed still in it and nothing is flashed to a redirect"
                + " that is not happening")
        .isEmpty();
    assertThat(attributes).isEmpty();
  }

  @Test
  void a_refusal_of_a_form_that_asked_to_come_back_to_the_screen_it_posted_to_is_drawn_in_place() {
    RedirectAttributesModelMap attributes = new RedirectAttributesModelMap();

    String where =
        theScreenAFormIsAnsweredOn()
            .aRefusalIsDrawnOn(
                THE_SCREEN_THE_POST_REACHED,
                THE_SCREEN_THE_POST_REACHED,
                WHY,
                chosen(),
                WhatWasTyped.theFieldsOf(THE_PIX_FORM_AS_IT_WAS_FILLED),
                attributes,
                () -> THE_SCREEN_THE_CONTROLLER_WOULD_DRAW);

    assertThat(where).isEqualTo(THE_SCREEN_THE_CONTROLLER_WOULD_DRAW);
    assertThat(attributes.getFlashAttributes()).isEmpty();
  }

  @Test
  void a_refusal_of_a_form_that_named_a_screen_this_simulator_does_not_carry_is_drawn_in_place() {
    RedirectAttributesModelMap attributes = new RedirectAttributesModelMap();

    String where =
        theScreenAFormIsAnsweredOn()
            .aRefusalIsDrawnOn(
                "https://elsewhere.example/steal",
                THE_SCREEN_THE_POST_REACHED,
                WHY,
                chosen(),
                WhatWasTyped.theFieldsOf(THE_PIX_FORM_AS_IT_WAS_FILLED),
                attributes,
                () -> THE_SCREEN_THE_CONTROLLER_WOULD_DRAW);

    assertThat(where)
        .as("a refusal that redirected wherever a query string told it to is an open redirect")
        .isEqualTo(THE_SCREEN_THE_CONTROLLER_WOULD_DRAW);
    assertThat(attributes.getFlashAttributes()).isEmpty();
  }

  @Test
  void a_payment_started_from_a_screen_of_its_own_says_so_on_that_screen() {
    RedirectAttributesModelMap attributes = new RedirectAttributesModelMap();

    String where =
        theScreenAFormIsAnsweredOn()
            .aStartedPaymentIsAnnouncedOn(
                THE_PIX_SCREEN, THE_WIZARD, "autra", "pix_0ec1", attributes);

    assertThat(where).isEqualTo("redirect:" + THE_PIX_SCREEN);
    assertThat(attributes.getFlashAttributes().get(Done.ATTRIBUTE))
        .as(
            "a screen that answered a start with nothing but a new row left a tester to work out"
                + " whether anything had happened")
        .isEqualTo(Done.thePaymentExistsNow("autra", "pix_0ec1"));
  }

  @Test
  void a_payment_started_in_the_wizard_is_left_to_the_panel_the_wizard_draws_for_one() {
    RedirectAttributesModelMap attributes = new RedirectAttributesModelMap();

    String where =
        theScreenAFormIsAnsweredOn()
            .aStartedPaymentIsAnnouncedOn(null, THE_WIZARD, "autra", "pix_0ec1", attributes);

    assertThat(where).isEqualTo("redirect:" + THE_WIZARD);
    assertThat(attributes.getFlashAttributes())
        .as(
            "the wizard answers a start with a panel of its own, and a banner saying the same"
                + " sentence above it is the screen saying one thing twice")
        .isEmpty();
  }

  private static TheScreenAFormIsAnsweredOn theScreenAFormIsAnsweredOn() {
    ProviderModuleDouble autra = new ProviderModuleDouble("autra");
    return new TheScreenAFormIsAnsweredOn(
        new TheRail(
            new ContributedScreens(
                List.of(new ContributedScreen("autra", "pix", "Pix", RailGroup.DAILY_WORK, 15))),
            new ProviderRegistry(List.of(autra))));
  }

  private static Selection.Chosen chosen() {
    return new Selection.Chosen(new ProviderModuleDouble("autra"), TestRunId.of("a-run"));
  }
}
