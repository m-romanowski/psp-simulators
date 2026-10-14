package dev.marcinromanowski.pspsim.control;

import dev.marcinromanowski.pspsim.kernel.audit.ActionFacts;
import dev.marcinromanowski.pspsim.kernel.scenario.ScenarioDocument;
import dev.marcinromanowski.pspsim.kernel.spi.WhereAFieldsValuesComeFrom;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.jspecify.annotations.Nullable;

final class ControlPayloads {

  private ControlPayloads() {}

  record ResetResponse(
      @Nullable String testRunId,
      String provider,
      String scope,
      int clearedObjects,
      int clearedAuditRecords) {}

  record ConfigurationResponse(
      String testRunId,
      String provider,
      List<String> configuredAccounts,
      int configuredWebhookTargets,
      int configuredRouteAliases,
      Instant updatedAt) {}

  record ClockResponse(
      String testRunId, String provider, String advancedBy, Instant movedFrom, Instant now) {}

  record AccountRemovedResponse(
      String testRunId, String provider, String removedAccount, List<String> configuredAccounts) {}

  record RouteAliasReleasedResponse(
      String testRunId,
      String provider,
      String releasedRouteAlias,
      List<String> configuredRouteAliases) {}

  record WebhookResponse(
      String webhookJobId,
      String eventId,
      String status,
      int attemptsMade,
      @Nullable Instant scheduledAt,
      List<WebhookJobView> deliveries) {}

  record WebhookJobView(
      String webhookJobId,
      String eventId,
      String status,
      int attemptsMade,
      @Nullable Instant scheduledAt) {}

  record WebhookDeliveryView(
      String webhookJobId,
      String eventId,
      @Nullable String eventType,
      @Nullable String providerObjectId,
      String merchantId,
      @Nullable String targetUrl,
      String status,
      @Nullable String shapedBy,
      @Nullable String notSentBecause,
      int attemptsMade,
      int attemptCap,
      @Nullable Instant nextAttemptAt,
      Instant createdAt,
      String contentType,
      Map<String, List<String>> headers,
      Map<String, List<String>> payload,
      List<WebhookAttemptView> attempts) {}

  record WebhookAttemptView(
      int number,
      Instant attemptedAt,
      String targetUrl,
      @Nullable Map<String, List<String>> sentHeaders,
      String verdict,
      @Nullable Integer httpStatus,
      @Nullable String transportFailure,
      @Nullable String responsePreview) {}

  record StateResponse(
      String testRunId,
      String provider,
      String providerObjectId,
      String addressedBy,
      List<String> answersTo,
      String nativeStatus,
      String normalizedStatus,
      boolean capturesAccepted,
      boolean refundsAccepted,
      @Nullable String amount,
      @Nullable String currency,
      List<OperationView> operations,
      @Nullable DerivedBalancesView derivedBalances,
      Map<String, String> relatedObjects,
      String origin,
      Instant updatedAt,
      @Nullable HostedSessionView hostedSession) {}

  record HostedSessionView(
      String account,
      String party,
      boolean aPartyIsNamed,
      String operation,
      Instant createdAt,
      String expires,
      boolean itExpiresAtAKnownTime,
      boolean itsDeadlineHasPassed) {}

  record OperationView(
      int sequence,
      String kind,
      String amount,
      @Nullable Boolean closesAuthorization,
      @Nullable String createdObjectId,
      String nativeStatus,
      String normalizedStatus,
      Instant createdAt) {}

  record DerivedBalancesView(
      String capturedAmount,
      String refundedAmount,
      String capturableRemainder,
      String refundableRemainder) {}

  record FixtureResponse(
      String fixtureId,
      String provider,
      String testRunId,
      String merchantId,
      String ownerId,
      String type,
      String alias,
      @Nullable FixtureCardView card,
      Map<String, String> providerObjects,
      @Nullable String defaultScenario,
      Instant createdAt) {}

  record FixtureCardView(String brand, String last4, int expMonth, int expYear, String funding) {}

  record HostedCompletionResponse(
      String testRunId,
      String provider,
      String providerObjectId,
      String outcome,
      @Nullable String createdObjectId,
      List<String> webhookJobIds) {}

  record FollowUpResponse(
      String testRunId,
      String provider,
      String followUp,
      StateResponse state,
      List<String> webhookJobIds) {}

  record FixtureRemovedResponse(String testRunId, String provider, String removedFixture) {}

  record DeliverDueResponse(String testRunId, String provider, int attempted) {}

  record ScenarioResponse(
      String scenarioId,
      String testRunId,
      String provider,
      int priority,
      @Nullable String operation,
      @Nullable String story,
      @Nullable String keyedOn,
      @Nullable String whenState,
      @Nullable String thenState,
      Instant createdAt) {}

  record AuditRecordView(
      String auditId,
      String testRunId,
      @Nullable String resolutionRule,
      String provider,
      @Nullable String accountId,
      String origin,
      String direction,
      @Nullable String outcome,
      String method,
      @Nullable String path,
      @Nullable Integer status,
      @Nullable String providerObjectId,
      @Nullable RefusalView refusal,
      @Nullable String scenarioId,
      int attempt,
      Instant createdAt,
      Map<String, List<String>> sanitizedHeaders,
      Map<String, List<String>> sanitizedBody,
      @Nullable DeliveredResponseView delivered) {}

  record RefusalView(
      String subject,
      @Nullable String providerObjectId,
      @Nullable String field,
      @Nullable Integer howManyAnswered,
      @Nullable String inItsOwnWords,
      @Nullable String whatItRefusedInstead) {}

  record DeliveredResponseView(
      @Nullable String transportFault,
      @Nullable String faultPlacement,
      long declaredContentLength,
      long writtenBodyBytes,
      long heldForMillis) {}

  record KeyInspectorResponse(
      String testRunId,
      String provider,
      List<DeclaredKeyView> keys,
      List<AbsentMechanismView> withoutAMechanism,
      List<KeyedExchangeView> exchanges,
      @Nullable String nextCursor) {}

  record DeclaredKeyView(
      String name,
      String role,
      String roleMeans,
      String carriedIn,
      String scopedBy,
      boolean canBeHeld,
      String whileItIsHeld) {}

  record AbsentMechanismView(String role, String roleMeans, String instead) {}

  record KeyedExchangeView(
      String cursor,
      String accountId,
      String keyName,
      String role,
      String roleMeans,
      String keyValue,
      String operation,
      String requestFingerprint,
      Instant firstRequestAt,
      List<Instant> replayTimestamps,
      boolean stateChanged,
      @Nullable String notRecorded,
      List<KeyedRequestView> requests) {}

  record KeyedRequestView(
      int sequence,
      String verdict,
      String verdictMeans,
      String operation,
      String requestFingerprint,
      @Nullable Integer status,
      @Nullable String contentType,
      @Nullable String body,
      @Nullable String providerObjectId,
      boolean stateChanged,
      @Nullable String timeout,
      @Nullable String conflictReason,
      @Nullable String noAnswerBecause,
      Instant at) {}

  record PersonaInstrumentView(
      String alias,
      String ownerId,
      String type,
      @Nullable FixtureCardView card,
      List<String> providerNativeIds,
      String howThoseAreMinted,
      @Nullable Integer triggersTheRuleAt) {}

  record PersonaView(
      String name,
      String intent,
      String whatATesterGets,
      String whatItGives,
      List<String> presets,
      List<PersonaInstrumentView> instruments) {}

  record UncoveredIntentView(String intent, String whatATesterGets, String instead) {}

  record PersonaCatalogueResponse(
      String provider, List<PersonaView> personas, List<UncoveredIntentView> uncovered) {}

  record PersonaPlanResponse(
      String provider,
      String testRunId,
      String persona,
      String intent,
      @Nullable ScenarioDocument rules,
      List<PersonaInstrumentView> instruments,
      boolean theRunAlreadyHoldsIt,
      @Nullable String theRefusalThisPlanCannotForesee) {}

  record PersonaSeededResponse(
      String provider,
      String testRunId,
      String persona,
      String intent,
      List<ScenarioResponse> rules,
      List<FixtureResponse> fixtures) {}

  record AddressResponse(
      String testRunId,
      String provider,
      String publicBaseUrl,
      String providerBaseUrl,
      String apiBasePath,
      String testRunHeader,
      String howARequestNamesItsAccount) {}

  record OperationsResponse(
      String testRunId, String provider, List<ContractOperationView> operations) {}

  record ContractOperationView(
      String name,
      List<String> carries,
      String storiesKeyedOn,
      boolean itsFlowKeyMayBeReplaced,
      boolean reportsAProcessingState,
      String failsWith) {}

  record AccountFieldsResponse(String provider, List<AccountFieldView> accountFields) {}

  record ReadsResponse(
      String testRunId,
      String provider,
      @Nullable String accountId,
      List<DeclaredReadView> reads,
      @Nullable String whatThisProviderPublishesInstead) {}

  record DeclaredReadView(
      String readNamed,
      String label,
      String theQuestionItAnswers,
      String whatAnEmptyAnswerWouldMean,
      boolean itNeverAnswersNothingAtAll,
      List<DeclaredFieldView> fields) {}

  record DeclaredFieldView(
      String key,
      String label,
      boolean required,
      String whatItIsFor,
      String theQueryParameterItIsSentIn,
      @Nullable String theValueItSuggests,
      WhereAFieldsValuesComeFrom whereItsValuesComeFrom,
      List<OfferedValueView> theValuesItOffers) {}

  record OfferedValueView(String value, String whatItNames) {}

  record OriginationsResponse(
      String testRunId,
      String provider,
      @Nullable String accountId,
      List<String> theAccountsThisRunConfigures,
      List<WayToStartAPaymentView> waysToStartAPayment,
      List<FilledInFieldView> theFieldsThisSimulatorFillsInItself) {}

  record WayToStartAPaymentView(
      String startedAs,
      String label,
      String whatItLeavesBehind,
      String whatThePayerDoesNext,
      @Nullable String thePayerPageThisSimulatorServes,
      List<DeclaredFieldView> fields) {}

  record FilledInFieldView(String key, String whereTheValueComesFrom) {}

  record OriginationResponse(
      String testRunId,
      String provider,
      String accountId,
      String startedAs,
      String providerObjectId,
      String whatExistsNow,
      @Nullable String theRuleThatDecidedIt) {}

  record ReadAnswerResponse(
      String testRunId,
      String provider,
      String accountId,
      String readNamed,
      List<AnsweredRowView> rows,
      List<AnsweredFactView> whatItAnsweredAboutTheQuery,
      @Nullable String whatItMeansThatItAnsweredNothingAtAll,
      @Nullable String theRuleThatDecidedIt) {}

  record AnsweredRowView(@Nullable String providerObjectId, List<AnsweredFactView> facts) {}

  record AnsweredFactView(String asTheProviderNamesIt, AnsweredValueView value) {}

  record AnsweredValueView(
      @Nullable String theValueItAnswered,
      boolean itAnsweredNoValueAtAll,
      @Nullable List<AnsweredFactView> theMembersUnderIt,
      @Nullable List<AnsweredValueView> theElementsUnderIt) {}

  record AccountFieldView(
      String key,
      String label,
      String whatItIsFor,
      boolean required,
      boolean secret,
      List<AcceptedValueView> acceptedValues) {}

  record AcceptedValueView(String value, String whatItMeans) {}

  sealed interface TheWordsARefusalWasAnsweredIn permits ControlError, ControlRefusal {

    String message();

    static Map<String, List<String>> beside(
        Map<String, List<String>> facts, TheWordsARefusalWasAnsweredIn body) {
      LinkedHashMap<String, List<String>> recorded = new LinkedHashMap<>(facts);
      recorded.put(ActionFacts.REFUSED_BECAUSE, List.of(body.message()));
      return recorded;
    }
  }

  record ControlError(String message) implements TheWordsARefusalWasAnsweredIn {}

  record ControlRefusal(String message, List<String> properties)
      implements TheWordsARefusalWasAnsweredIn {}
}
