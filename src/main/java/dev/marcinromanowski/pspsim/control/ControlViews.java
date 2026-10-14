package dev.marcinromanowski.pspsim.control;

import dev.marcinromanowski.pspsim.control.ControlPayloads.AbsentMechanismView;
import dev.marcinromanowski.pspsim.control.ControlPayloads.AcceptedValueView;
import dev.marcinromanowski.pspsim.control.ControlPayloads.AccountFieldView;
import dev.marcinromanowski.pspsim.control.ControlPayloads.AccountFieldsResponse;
import dev.marcinromanowski.pspsim.control.ControlPayloads.AddressResponse;
import dev.marcinromanowski.pspsim.control.ControlPayloads.AnsweredFactView;
import dev.marcinromanowski.pspsim.control.ControlPayloads.AnsweredRowView;
import dev.marcinromanowski.pspsim.control.ControlPayloads.AnsweredValueView;
import dev.marcinromanowski.pspsim.control.ControlPayloads.AuditRecordView;
import dev.marcinromanowski.pspsim.control.ControlPayloads.ContractOperationView;
import dev.marcinromanowski.pspsim.control.ControlPayloads.DeclaredFieldView;
import dev.marcinromanowski.pspsim.control.ControlPayloads.DeclaredKeyView;
import dev.marcinromanowski.pspsim.control.ControlPayloads.DeclaredReadView;
import dev.marcinromanowski.pspsim.control.ControlPayloads.DeliveredResponseView;
import dev.marcinromanowski.pspsim.control.ControlPayloads.DerivedBalancesView;
import dev.marcinromanowski.pspsim.control.ControlPayloads.FilledInFieldView;
import dev.marcinromanowski.pspsim.control.ControlPayloads.FixtureCardView;
import dev.marcinromanowski.pspsim.control.ControlPayloads.FixtureResponse;
import dev.marcinromanowski.pspsim.control.ControlPayloads.HostedSessionView;
import dev.marcinromanowski.pspsim.control.ControlPayloads.KeyInspectorResponse;
import dev.marcinromanowski.pspsim.control.ControlPayloads.KeyedExchangeView;
import dev.marcinromanowski.pspsim.control.ControlPayloads.KeyedRequestView;
import dev.marcinromanowski.pspsim.control.ControlPayloads.OfferedValueView;
import dev.marcinromanowski.pspsim.control.ControlPayloads.OperationView;
import dev.marcinromanowski.pspsim.control.ControlPayloads.OperationsResponse;
import dev.marcinromanowski.pspsim.control.ControlPayloads.OriginationResponse;
import dev.marcinromanowski.pspsim.control.ControlPayloads.OriginationsResponse;
import dev.marcinromanowski.pspsim.control.ControlPayloads.PersonaCatalogueResponse;
import dev.marcinromanowski.pspsim.control.ControlPayloads.PersonaInstrumentView;
import dev.marcinromanowski.pspsim.control.ControlPayloads.PersonaView;
import dev.marcinromanowski.pspsim.control.ControlPayloads.ReadAnswerResponse;
import dev.marcinromanowski.pspsim.control.ControlPayloads.ReadsResponse;
import dev.marcinromanowski.pspsim.control.ControlPayloads.RefusalView;
import dev.marcinromanowski.pspsim.control.ControlPayloads.ScenarioResponse;
import dev.marcinromanowski.pspsim.control.ControlPayloads.StateResponse;
import dev.marcinromanowski.pspsim.control.ControlPayloads.UncoveredIntentView;
import dev.marcinromanowski.pspsim.control.ControlPayloads.WayToStartAPaymentView;
import dev.marcinromanowski.pspsim.control.ControlPayloads.WebhookAttemptView;
import dev.marcinromanowski.pspsim.control.ControlPayloads.WebhookDeliveryView;
import dev.marcinromanowski.pspsim.control.ControlPayloads.WebhookJobView;
import dev.marcinromanowski.pspsim.kernel.RefusalSubject;
import dev.marcinromanowski.pspsim.kernel.audit.AuditRecord;
import dev.marcinromanowski.pspsim.kernel.audit.DeliveredResponse;
import dev.marcinromanowski.pspsim.kernel.audit.ProviderOutcome;
import dev.marcinromanowski.pspsim.kernel.fault.AppliedFault;
import dev.marcinromanowski.pspsim.kernel.fixture.FixtureCard;
import dev.marcinromanowski.pspsim.kernel.fixture.PaymentMethodFixture;
import dev.marcinromanowski.pspsim.kernel.idempotency.KeyRole;
import dev.marcinromanowski.pspsim.kernel.idempotency.KeyedExchange;
import dev.marcinromanowski.pspsim.kernel.idempotency.KeyedExchangePage;
import dev.marcinromanowski.pspsim.kernel.idempotency.KeyedRequest;
import dev.marcinromanowski.pspsim.kernel.money.DerivedBalances;
import dev.marcinromanowski.pspsim.kernel.money.MonetaryOperation;
import dev.marcinromanowski.pspsim.kernel.scenario.ScenarioRule;
import dev.marcinromanowski.pspsim.kernel.scenario.ScenarioStory;
import dev.marcinromanowski.pspsim.kernel.spi.AFieldATesterFillsIn;
import dev.marcinromanowski.pspsim.kernel.spi.AValueAFieldOffers;
import dev.marcinromanowski.pspsim.kernel.spi.AcceptedValue;
import dev.marcinromanowski.pspsim.kernel.spi.AddressedProviderObject;
import dev.marcinromanowski.pspsim.kernel.spi.AnsweredRow;
import dev.marcinromanowski.pspsim.kernel.spi.AnsweredRow.AnsweredValue;
import dev.marcinromanowski.pspsim.kernel.spi.AnsweredRow.Fact;
import dev.marcinromanowski.pspsim.kernel.spi.ConfigurableField;
import dev.marcinromanowski.pspsim.kernel.spi.DeclaredKey;
import dev.marcinromanowski.pspsim.kernel.spi.DeclaredOrigination;
import dev.marcinromanowski.pspsim.kernel.spi.DeclaredPersona;
import dev.marcinromanowski.pspsim.kernel.spi.DeclaredRead;
import dev.marcinromanowski.pspsim.kernel.spi.DeclaredRead.EmptyAnswer;
import dev.marcinromanowski.pspsim.kernel.spi.HostedSessionFacts;
import dev.marcinromanowski.pspsim.kernel.spi.OriginationOutcome;
import dev.marcinromanowski.pspsim.kernel.spi.OriginationRequest;
import dev.marcinromanowski.pspsim.kernel.spi.PersonaFixture;
import dev.marcinromanowski.pspsim.kernel.spi.PersonaIntent;
import dev.marcinromanowski.pspsim.kernel.spi.ProviderAccountFields;
import dev.marcinromanowski.pspsim.kernel.spi.ProviderKeyMechanisms;
import dev.marcinromanowski.pspsim.kernel.spi.ProviderObjectState;
import dev.marcinromanowski.pspsim.kernel.spi.ProviderOperation;
import dev.marcinromanowski.pspsim.kernel.spi.ProviderOriginationFields;
import dev.marcinromanowski.pspsim.kernel.spi.ProviderPairing;
import dev.marcinromanowski.pspsim.kernel.spi.ProviderPersonas;
import dev.marcinromanowski.pspsim.kernel.spi.ProviderReads;
import dev.marcinromanowski.pspsim.kernel.spi.ProviderVocabulary;
import dev.marcinromanowski.pspsim.kernel.spi.ReadOutcome;
import dev.marcinromanowski.pspsim.kernel.spi.ReadRequest;
import dev.marcinromanowski.pspsim.kernel.spi.ThePayerStep;
import dev.marcinromanowski.pspsim.kernel.testrun.TestRunHeader;
import dev.marcinromanowski.pspsim.kernel.testrun.TestRunId;
import dev.marcinromanowski.pspsim.kernel.testrun.TestRunResolutionRule;
import dev.marcinromanowski.pspsim.kernel.webhook.WebhookAttempt;
import dev.marcinromanowski.pspsim.kernel.webhook.WebhookDelivery;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.jspecify.annotations.Nullable;

final class ControlViews {

  static final String THE_OBJECT = "THE_OBJECT";

  static final String NO_OBJECT_ANSWERED_TO = "NO_OBJECT_ANSWERED_TO";

  static final String SEVERAL_OBJECTS_ANSWERED_TO = "SEVERAL_OBJECTS_ANSWERED_TO";

  static final String AN_OBJECT_IT_WILL_NOT_NAME = "AN_OBJECT_IT_WILL_NOT_NAME";

  static final String NO_OBJECT_WAS_NAMED = "NO_OBJECT_WAS_NAMED";

  private ControlViews() {}

  static AddressResponse addressOf(
      TestRunId testRunId, String provider, String publicBaseUrl, ProviderPairing pairing) {
    return new AddressResponse(
        testRunId.value(),
        provider,
        publicBaseUrl,
        pairing.providerBaseUrlWithin(publicBaseUrl),
        pairing.apiBasePath(),
        TestRunHeader.NAME,
        pairing.howARequestNamesItsAccount());
  }

  static AccountFieldsResponse accountFieldsOf(String provider, ProviderAccountFields declared) {
    List<AccountFieldView> views = new ArrayList<>();
    for (ConfigurableField field : declared.fields()) {
      views.add(
          new AccountFieldView(
              field.key(),
              field.label(),
              field.whatItIsFor(),
              field.required(),
              field.secret(),
              theValuesItAccepts(field)));
    }
    return new AccountFieldsResponse(provider, List.copyOf(views));
  }

  private static List<AcceptedValueView> theValuesItAccepts(ConfigurableField field) {
    List<AcceptedValueView> accepted = new ArrayList<>();
    for (AcceptedValue value : field.acceptedValues()) {
      accepted.add(new AcceptedValueView(value.value(), value.whatItMeans()));
    }
    return List.copyOf(accepted);
  }

  static OperationsResponse operationsOf(
      TestRunId testRunId, String provider, ProviderVocabulary vocabulary) {
    List<ContractOperationView> views = new ArrayList<>();
    for (ProviderOperation exposed : vocabulary.operations()) {
      views.add(
          new ContractOperationView(
              exposed.name(),
              exposed.theFactsItCarries(),
              exposed.storiesKeyedOn(),
              exposed.itsFlowKeyMayBeReplaced(),
              vocabulary.processingStates().reachedBy(exposed.name())));
    }
    return new OperationsResponse(testRunId.value(), provider, List.copyOf(views));
  }

  static ReadsResponse readsOf(
      TestRunId testRunId,
      String provider,
      @Nullable String accountId,
      ProviderReads declared,
      Map<String, List<AValueAFieldOffers>> theValuesThisRunHasAlreadyProduced) {
    List<DeclaredReadView> reads = new ArrayList<>();
    for (DeclaredRead read : declared.reads()) {
      reads.add(viewOf(read, theValuesThisRunHasAlreadyProduced));
    }
    return new ReadsResponse(
        testRunId.value(),
        provider,
        accountId,
        List.copyOf(reads),
        declared.publishesAny() ? null : declared.whatItPublishesInstead());
  }

  static OriginationsResponse originationsOf(
      TestRunId testRunId,
      String provider,
      @Nullable String accountId,
      List<String> theAccountsThisRunConfigures,
      ProviderOriginationFields declared,
      Map<String, List<AValueAFieldOffers>> theValuesEachFieldAlsoOffers) {
    List<WayToStartAPaymentView> ways = new ArrayList<>();
    for (DeclaredOrigination way : declared.waysToStartAPayment()) {
      ways.add(viewOf(way, theValuesEachFieldAlsoOffers));
    }
    List<FilledInFieldView> filledIn = new ArrayList<>();
    for (Map.Entry<String, String> supplied : declared.fieldsItFillsInItself().entrySet()) {
      filledIn.add(new FilledInFieldView(supplied.getKey(), supplied.getValue()));
    }
    return new OriginationsResponse(
        testRunId.value(),
        provider,
        accountId,
        theAccountsThisRunConfigures,
        List.copyOf(ways),
        List.copyOf(filledIn));
  }

  private static WayToStartAPaymentView viewOf(
      DeclaredOrigination way, Map<String, List<AValueAFieldOffers>> theValuesEachFieldAlsoOffers) {
    List<DeclaredFieldView> fields = new ArrayList<>();
    for (AFieldATesterFillsIn field : way.fields()) {
      fields.add(viewOf(field, theValuesEachFieldAlsoOffers.getOrDefault(field.key(), List.of())));
    }
    return new WayToStartAPaymentView(
        way.name(),
        way.label(),
        way.whatItLeavesBehind(),
        way.thePayerStep().asATesterReadsIt(),
        thePageThisSimulatorServesFor(way.thePayerStep()),
        List.copyOf(fields));
  }

  private static @Nullable String thePageThisSimulatorServesFor(ThePayerStep step) {
    return switch (step) {
      case ThePayerStep.APageThisSimulatorServes served -> served.route();
      case ThePayerStep.NoPageThisSimulatorServes _ -> null;
    };
  }

  static OriginationResponse startedOf(
      TestRunId testRunId,
      String provider,
      OriginationRequest request,
      OriginationOutcome.Originated started) {
    return new OriginationResponse(
        testRunId.value(),
        provider,
        request.accountId(),
        request.startedAs(),
        started.providerObjectId(),
        started.whatExistsNow(),
        started.scenarioId());
  }

  private static DeclaredReadView viewOf(
      DeclaredRead read, Map<String, List<AValueAFieldOffers>> theValuesThisRunHasAlreadyProduced) {
    List<DeclaredFieldView> fields = new ArrayList<>();
    for (AFieldATesterFillsIn field : read.fields()) {
      fields.add(
          viewOf(field, theValuesThisRunHasAlreadyProduced.getOrDefault(field.key(), List.of())));
    }
    return new DeclaredReadView(
        read.name(),
        read.label(),
        read.theQuestionItAnswers(),
        read.whatAnEmptyAnswerWouldMean().inTheProvidersOwnWords(),
        itNeverAnswersNothingAtAll(read.whatAnEmptyAnswerWouldMean()),
        List.copyOf(fields));
  }

  private static boolean itNeverAnswersNothingAtAll(EmptyAnswer whatAnEmptyAnswerWouldMean) {
    return switch (whatAnEmptyAnswerWouldMean) {
      case EmptyAnswer.ItMeansThis _ -> false;
      case EmptyAnswer.ItNeverAnswersNothing _ -> true;
    };
  }

  private static DeclaredFieldView viewOf(
      AFieldATesterFillsIn field, List<AValueAFieldOffers> theValuesThisRunHasAlreadyProduced) {
    List<OfferedValueView> offered = new ArrayList<>();
    for (AValueAFieldOffers choice : field.choices()) {
      offered.add(new OfferedValueView(choice.value(), choice.label()));
    }
    for (AValueAFieldOffers produced : theValuesThisRunHasAlreadyProduced) {
      offered.add(new OfferedValueView(produced.value(), produced.label()));
    }
    return new DeclaredFieldView(
        field.key(),
        field.label(),
        field.required(),
        field.whatItIsFor(),
        TheFieldParameterBothRoutesRead.theQueryParameterAValueIsSentIn(field.key()),
        field.example(),
        field.offering(),
        List.copyOf(offered));
  }

  static ReadAnswerResponse answerOf(
      TestRunId testRunId,
      String provider,
      ReadRequest request,
      DeclaredRead asked,
      ReadOutcome.Answered answered) {
    return new ReadAnswerResponse(
        testRunId.value(),
        provider,
        request.accountId(),
        request.readNamed(),
        rowsOf(answered.rows()),
        factsOf(answered.whatItAnsweredAboutTheQuery()),
        answered.itAnsweredNoRowAtAll() ? asked.whatItMeansThatThisOneAnsweredNothing() : null,
        answered.scenarioId());
  }

  private static List<AnsweredRowView> rowsOf(List<AnsweredRow> rows) {
    List<AnsweredRowView> views = new ArrayList<>();
    for (AnsweredRow row : rows) {
      views.add(new AnsweredRowView(row.providerObjectId(), factsOf(row.facts())));
    }
    return List.copyOf(views);
  }

  private static List<AnsweredFactView> factsOf(List<Fact> facts) {
    List<AnsweredFactView> views = new ArrayList<>();
    for (Fact fact : facts) {
      views.add(new AnsweredFactView(fact.asTheProviderNamesIt(), valueOf(fact.value())));
    }
    return List.copyOf(views);
  }

  private static AnsweredValueView valueOf(AnsweredValue answered) {
    return switch (answered) {
      case AnsweredValue.TheValueItAnswered one ->
          new AnsweredValueView(one.asTheProviderAnsweredIt(), false, null, null);
      case AnsweredValue.NoValueAtAll _ -> new AnsweredValueView(null, true, null, null);
      case AnsweredValue.TheMembersUnderIt members ->
          new AnsweredValueView(null, false, factsOf(members.members()), null);
      case AnsweredValue.TheElementsUnderIt elements ->
          new AnsweredValueView(null, false, null, elementsOf(elements.elements()));
    };
  }

  private static List<AnsweredValueView> elementsOf(List<AnsweredValue> elements) {
    List<AnsweredValueView> views = new ArrayList<>();
    for (AnsweredValue element : elements) {
      views.add(valueOf(element));
    }
    return List.copyOf(views);
  }

  static FixtureResponse viewOf(PaymentMethodFixture fixture) {
    FixtureCard card = fixture.card();
    return new FixtureResponse(
        fixture.fixtureId(),
        fixture.provider().value(),
        fixture.testRunId().value(),
        fixture.merchantId(),
        fixture.ownerId(),
        fixture.type().publishedName(),
        fixture.fixtureAlias(),
        card == null
            ? null
            : new FixtureCardView(
                card.brand(), card.last4(), card.expMonth(), card.expYear(), card.funding()),
        fixture.providerIds(),
        fixture.defaultScenario(),
        fixture.createdAt());
  }

  static PersonaCatalogueResponse viewOf(ProviderPersonas published) {
    List<PersonaView> personas = new ArrayList<>();
    for (DeclaredPersona persona : published.personas()) {
      personas.add(
          new PersonaView(
              persona.name(),
              persona.intent().publishedName(),
              persona.intent().whatATesterGets(),
              persona.whatItGives(),
              persona.thePresetNamesItWrites(),
              viewsOf(persona.fixtures())));
    }
    List<UncoveredIntentView> uncovered = new ArrayList<>();
    for (PersonaIntent intent : PersonaIntent.values()) {
      String instead = published.intentsItPublishesNoneFor().get(intent);
      if (instead != null) {
        uncovered.add(
            new UncoveredIntentView(intent.publishedName(), intent.whatATesterGets(), instead));
      }
    }
    return new PersonaCatalogueResponse(published.provider().value(), personas, uncovered);
  }

  static List<PersonaInstrumentView> viewsOf(List<PersonaFixture> instruments) {
    List<PersonaInstrumentView> views = new ArrayList<>();
    for (PersonaFixture instrument : instruments) {
      FixtureCard card = instrument.card();
      views.add(
          new PersonaInstrumentView(
              instrument.alias(),
              instrument.ownerId(),
              instrument.type().publishedName(),
              card == null
                  ? null
                  : new FixtureCardView(
                      card.brand(), card.last4(), card.expMonth(), card.expYear(), card.funding()),
              instrument.theProviderNativeKeysItCarries(),
              instrument.howThoseAreMinted(),
              instrument.triggersTheRuleAt()));
    }
    return views;
  }

  static AuditRecordView viewOf(AuditRecord record) {
    DeliveredResponse delivered = record.delivered();
    TestRunResolutionRule rule = record.resolutionRule();
    ProviderOutcome outcome = record.outcome();
    return new AuditRecordView(
        record.auditId(),
        record.testRunId().value(),
        rule == null ? null : rule.name(),
        record.provider().value(),
        record.accountId(),
        record.origin().name(),
        record.direction().name(),
        outcome == null ? null : outcome.name(),
        record.method(),
        record.path(),
        record.httpStatus(),
        record.providerObjectId(),
        viewOf(record.refusal()),
        record.scenarioId(),
        record.attempt(),
        record.createdAt(),
        record.sanitizedHeaders(),
        record.sanitizedBody(),
        delivered == null ? null : viewOf(delivered));
  }

  static @Nullable RefusalView viewOf(@Nullable RefusalSubject refusal) {
    return switch (refusal) {
      case null -> null;
      case RefusalSubject.TheObject named ->
          new RefusalView(THE_OBJECT, named.providerObjectId(), null, null, null, null);
      case RefusalSubject.NoObjectAnsweredTo none ->
          new RefusalView(
              NO_OBJECT_ANSWERED_TO, null, none.theFieldThatNamedIt(), null, null, null);
      case RefusalSubject.SeveralObjectsAnsweredTo several ->
          new RefusalView(
              SEVERAL_OBJECTS_ANSWERED_TO,
              null,
              several.theFieldThatNamedThem(),
              several.howManyAnswered(),
              null,
              null);
      case RefusalSubject.AnObjectItWillNotName withheld ->
          new RefusalView(
              AN_OBJECT_IT_WILL_NOT_NAME,
              null,
              null,
              null,
              withheld.whyItsContractNamesNone(),
              null);
      case RefusalSubject.NoObjectWasNamed nothing ->
          new RefusalView(
              NO_OBJECT_WAS_NAMED, null, null, null, null, nothing.whatItRefusedInstead());
    };
  }

  private static DeliveredResponseView viewOf(DeliveredResponse delivered) {
    AppliedFault fault = delivered.transportFault();
    return new DeliveredResponseView(
        fault == null ? null : fault.kind().name(),
        fault == null ? null : fault.placement().name(),
        delivered.declaredContentLength(),
        delivered.writtenBodyBytes(),
        delivered.heldFor().toMillis());
  }

  static ScenarioResponse viewOf(String provider, ScenarioRule rule) {
    ScenarioStory story = rule.story();
    return new ScenarioResponse(
        rule.scenarioId(),
        rule.testRunId().value(),
        provider,
        rule.priority(),
        rule.match().operation(),
        story == null ? null : story.name(),
        story == null ? null : story.keyedOn().name(),
        story == null ? null : story.whenState(),
        story == null ? null : story.thenState(),
        rule.createdAt());
  }

  static StateResponse viewOf(
      TestRunId testRunId, String provider, AddressedProviderObject addressed, Instant now) {
    ProviderObjectState state = addressed.object();
    List<OperationView> operations = new ArrayList<>();
    for (MonetaryOperation operation : state.ledger().operations()) {
      operations.add(
          new OperationView(
              operation.sequence(),
              operation.kind().name(),
              operation.amount().value(),
              operation.closesAuthorization(),
              operation.createdObjectId(),
              operation.nativeStatus(),
              operation.normalizedStatus().name(),
              operation.at()));
    }
    return new StateResponse(
        testRunId.value(),
        provider,
        state.providerObjectId(),
        addressed.addressedBy(),
        addressed.answersTo(),
        state.nativeStatus(),
        state.normalizedStatus().name(),
        state.capturesAccepted(),
        state.refundsAccepted(),
        state.amount() == null ? null : state.amount().value(),
        state.currency(),
        operations,
        state.derivedBalances().map(ControlViews::viewOf).orElse(null),
        state.relatedObjects(),
        state.origin().name(),
        state.updatedAt(),
        viewOf(state.hostedSession(), now));
  }

  private static @Nullable HostedSessionView viewOf(HostedSessionFacts facts, Instant now) {
    return switch (facts) {
      case HostedSessionFacts.NotAnObjectAPayerFinishes _ -> null;
      case HostedSessionFacts.AnObjectAPayerFinishes finished ->
          new HostedSessionView(
              finished.theAccountItBelongsTo(),
              finished.thePartyAsItReads(),
              finished.aPartyIsNamed(),
              finished.theOperationItWasStartedFor(),
              finished.createdAt(),
              finished.theDeadlineAsItReads(),
              finished.itExpiresAtAKnownTime(),
              finished.itsDeadlineHasPassedBy(now));
    };
  }

  private static DerivedBalancesView viewOf(DerivedBalances balances) {
    return new DerivedBalancesView(
        balances.capturedAmount().value(),
        balances.refundedAmount().value(),
        balances.capturableRemainder().value(),
        balances.refundableRemainder().value());
  }

  static KeyInspectorResponse viewOf(
      TestRunId testRunId, ProviderKeyMechanisms mechanisms, KeyedExchangePage page, int pageSize) {
    List<DeclaredKeyView> declared = new ArrayList<>();
    for (DeclaredKey key : mechanisms.keys()) {
      declared.add(
          new DeclaredKeyView(
              key.name(),
              key.role().publishedName(),
              key.role().whatARepeatDoes(),
              key.carriedIn().describedAs(),
              key.scopedBy(),
              key.whileItIsHeld().canBeHeld(),
              key.whileItIsHeld().describedAs()));
    }
    List<AbsentMechanismView> absent = new ArrayList<>();
    for (KeyRole role : KeyRole.values()) {
      if (!mechanisms.has(role)) {
        absent.add(
            new AbsentMechanismView(
                role.publishedName(), role.whatARepeatDoes(), mechanisms.theAbsenceOf(role)));
      }
    }
    List<KeyedExchangeView> exchanges = new ArrayList<>();
    for (KeyedExchange exchange : page.exchanges()) {
      exchanges.add(viewOf(exchange));
    }
    return new KeyInspectorResponse(
        testRunId.value(),
        mechanisms.provider().value(),
        declared,
        absent,
        exchanges,
        page.cursorToCarryOnFrom(pageSize));
  }

  private static KeyedExchangeView viewOf(KeyedExchange exchange) {
    List<KeyedRequestView> requests = new ArrayList<>();
    for (KeyedRequest request : exchange.requests()) {
      requests.add(
          new KeyedRequestView(
              request.sequence(),
              request.verdict().name(),
              request.verdict().describedAs(),
              request.operation(),
              request.fingerprint(),
              request.httpStatus(),
              request.contentType(),
              request.body(),
              request.providerObjectId(),
              request.stateChanged(),
              request.whenTheFaultLanded(),
              exchange.conflictReasonOf(request),
              request.whyNoAnswerIsShown(),
              request.at()));
    }
    return new KeyedExchangeView(
        String.valueOf(exchange.arrival()),
        exchange.accountId(),
        exchange.keyName(),
        exchange.role().publishedName(),
        exchange.role().whatARepeatDoes(),
        exchange.keyValue(),
        exchange.operation(),
        exchange.fingerprint(),
        exchange.firstRequestAt(),
        exchange.replayTimestamps(),
        exchange.stateChanged(),
        exchange.whatThisLogDidNotRecord(),
        requests);
  }

  static WebhookJobView jobOf(WebhookDelivery delivery) {
    return new WebhookJobView(
        delivery.deliveryId(),
        delivery.eventId(),
        delivery.state().name(),
        delivery.attemptsMade(),
        delivery.nextAttemptAt());
  }

  static WebhookDeliveryView viewOf(WebhookDelivery delivery, List<WebhookAttempt> made) {
    List<WebhookAttemptView> attempts = new ArrayList<>();
    for (WebhookAttempt attempt : made) {
      attempts.add(
          new WebhookAttemptView(
              attempt.number(),
              attempt.attemptedAt(),
              attempt.targetUrl(),
              attempt.sentHeaders(),
              attempt.verdict().name(),
              attempt.httpStatus(),
              attempt.failure() == null ? null : attempt.failure().name(),
              attempt.responsePreview()));
    }
    return new WebhookDeliveryView(
        delivery.deliveryId(),
        delivery.eventId(),
        delivery.eventType(),
        delivery.providerObjectId(),
        delivery.merchantId(),
        delivery.targetUrl(),
        delivery.state().name(),
        delivery.shapedBy(),
        delivery.notSentBecause(),
        delivery.attemptsMade(),
        delivery.retryPlan().attemptCap(),
        delivery.nextAttemptAt(),
        delivery.createdAt(),
        delivery.payload().contentType(),
        delivery.payload().wireHeaders(),
        delivery.payload().sanitizedBody(),
        attempts);
  }
}
