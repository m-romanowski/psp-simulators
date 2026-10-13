package dev.marcinromanowski.pspsim.ui;

import java.util.function.Supplier;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Component
public final class TheScreenAFormIsAnsweredOn {

  public static final String THE_REFUSAL = "refusal";

  static final String THE_PROVIDER = "provider";

  static final String THE_TEST_RUN = "testRunId";

  private static final String IT_SENDS_THE_TESTER_ELSEWHERE = "redirect:";

  private final TheRail rail;

  TheScreenAFormIsAnsweredOn(TheRail rail) {
    this.rail = rail;
  }

  public String aRefusalIsDrawnOn(
      @Nullable String returnTo,
      String theScreenTheFormPostedTo,
      String refusal,
      Selection.Chosen chosen,
      WhatWasTyped typed,
      RedirectAttributes attributes,
      Supplier<String> theScreenThatPostReached) {
    String where = rail.theScreenToReturnTo(returnTo, theScreenTheFormPostedTo);
    if (where.equals(theScreenTheFormPostedTo)) {
      return theScreenThatPostReached.get();
    }
    attributes.addFlashAttribute(THE_REFUSAL, refusal);
    attributes.addFlashAttribute(WhatWasTyped.ATTRIBUTE, typed);
    attributes.addAttribute(THE_PROVIDER, chosen.provider());
    attributes.addAttribute(THE_TEST_RUN, chosen.testRunId().value());
    return IT_SENDS_THE_TESTER_ELSEWHERE + where;
  }

  public String aStartedPaymentIsAnnouncedOn(
      @Nullable String returnTo,
      String theWizard,
      String provider,
      String providerObjectId,
      RedirectAttributes attributes) {
    String where = rail.theScreenToReturnTo(returnTo, theWizard);
    if (!where.equals(theWizard)) {
      attributes.addFlashAttribute(
          Done.ATTRIBUTE, Done.thePaymentExistsNow(provider, providerObjectId));
    }
    return IT_SENDS_THE_TESTER_ELSEWHERE + where;
  }
}
