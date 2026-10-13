package dev.marcinromanowski.pspsim.provider.stripe;

import dev.marcinromanowski.pspsim.kernel.audit.ProviderOutcome;
import dev.marcinromanowski.pspsim.kernel.spi.ObjectActionOutcome;
import dev.marcinromanowski.pspsim.kernel.spi.ObjectActionRequest;
import dev.marcinromanowski.pspsim.kernel.spi.ObjectActions;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class StripeObjectActions {

  static final ObjectActions DECLARED = new ObjectActions(StripeApi.STRIPE, Map.of());

  static final String A_CONTROL_SURFACE_CAPTURE_IS_THE_FINAL_ONE = StripeFlag.TRUE;

  private static final String ERROR_CODE = "error.code";

  private static final String ERROR_DECLINE_CODE = "error.decline_code";

  private static final String ERROR_MESSAGE = "error.message";

  private StripeObjectActions() {}

  static StripeForm formOf(ObjectActionRequest request, StripePaymentIntent intent) {
    Map<String, List<String>> fields = new LinkedHashMap<>();
    switch (request.action()) {
      case CAPTURE -> {
        fields.put(StripeFields.AMOUNT_TO_CAPTURE, List.of(request.theAmountAsked().value()));
        fields.put(StripeFields.FINAL_CAPTURE, List.of(A_CONTROL_SURFACE_CAPTURE_IS_THE_FINAL_ONE));
      }
      case REFUND -> {
        fields.put(StripeFields.PAYMENT_INTENT, List.of(intent.intentId()));
        fields.put(StripeFields.AMOUNT, List.of(request.theAmountAsked().value()));
      }
      case CANCEL -> {}
    }
    return StripeForm.of(fields);
  }

  static ObjectActionOutcome outcomeOf(StripeOperationResult result, StripePaymentIntent acted) {
    if (result.outcome() == ProviderOutcome.UNKNOWN) {
      return new ObjectActionOutcome.AnsweredByAScenarioInstead(
          String.valueOf(result.scenarioId()));
    }
    if (result.outcome() != ProviderOutcome.SUCCESS) {
      return new ObjectActionOutcome.RefusedByTheProvider(theWordsOf(result), result.scenarioId());
    }
    String answered = result.providerObjectId();
    return new ObjectActionOutcome.Applied(
        acted.intentId(), acted.intentId().equals(answered) ? null : answered, result.scenarioId());
  }

  static ObjectActionOutcome theRunConfiguresNoAccountFor(StripePaymentIntent intent) {
    return new ObjectActionOutcome.RefusedByTheProvider(
        theWordsOf(
                StripeOperationResult.refusedBeforeExecuting(
                    StripeRefusals.theKeyReachesNoAccount(), StripeErrors.noValidApiKey()))
            + " This test run configures no stripe account under the id "
            + intent.accountId()
            + ", which is the account this PaymentIntent was created on, so no secret key this run"
            + " holds reaches it.");
  }

  static String theWordsOf(StripeOperationResult result) {
    Map<String, List<String>> answered = result.body().flatten();
    return wordedAs(
        firstOf(answered, ERROR_CODE),
        firstOf(answered, ERROR_DECLINE_CODE),
        firstOf(answered, ERROR_MESSAGE));
  }

  private static String firstOf(Map<String, List<String>> answered, String name) {
    List<String> values = answered.get(name);
    return values == null || values.isEmpty() ? "" : values.get(0);
  }

  private static String wordedAs(String... said) {
    List<String> words = new ArrayList<>();
    for (String word : said) {
      if (!word.isBlank()) {
        words.add(word);
      }
    }
    return String.join(" ", words);
  }
}
