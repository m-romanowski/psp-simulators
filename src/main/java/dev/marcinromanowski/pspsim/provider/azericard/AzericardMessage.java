package dev.marcinromanowski.pspsim.provider.azericard;

import dev.marcinromanowski.pspsim.kernel.idempotency.FingerprintedRequest;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.jspecify.annotations.Nullable;

record AzericardMessage(AzericardRoute route, AzericardForm form) {

  static AzericardMessage of(AzericardRoute route, Map<String, List<String>> values) {
    return new AzericardMessage(route, AzericardForm.of(values));
  }

  @Nullable AzericardRequiredField unusableField() {
    return unusableFieldOtherThan(null);
  }

  @Nullable AzericardRequiredField unusableFieldOfARequestThisSimulatorPlacedItself() {
    return unusableFieldOtherThan(AzericardFields.REQUIRED_P_SIGN);
  }

  private @Nullable AzericardRequiredField unusableFieldOtherThan(
      @Nullable AzericardRequiredField carriedByNoWire) {
    for (AzericardRequiredField required : AzericardFields.REQUIRED_BEFORE_THE_OPERATION_IS_KNOWN) {
      if (form.first(required.name()) == null) {
        return required;
      }
    }
    Optional<AzericardTransactionType> exposed =
        AzericardTransactionType.of(form.required(AzericardFields.TRTYPE));
    if (exposed.isEmpty() || !route.answers(exposed.get())) {
      return AzericardFields.REQUIRED_TRTYPE;
    }
    for (AzericardRequiredField required : exposed.get().shape().required()) {
      if (required.equals(carriedByNoWire)) {
        continue;
      }
      if (form.first(required.name()) == null) {
        return required;
      }
    }
    AzericardRequiredField unusableToken = theTokenFieldsItCannotUse();
    if (unusableToken != null) {
      return unusableToken;
    }
    AzericardRequiredField unusableCard = theCardFieldsItCannotUse();
    if (unusableCard != null) {
      return unusableCard;
    }
    if (!AzericardOrderTrace.isWellFormed(form.required(AzericardFields.ORDER))) {
      return AzericardFields.REQUIRED_ORDER;
    }
    if (AzericardTimestamp.read(form.required(AzericardFields.TIMESTAMP)).isEmpty()) {
      return AzericardFields.REQUIRED_TIMESTAMP;
    }
    return null;
  }

  private @Nullable AzericardRequiredField theTokenFieldsItCannotUse() {
    if (route != AzericardRoute.TOKEN_CGI_LINK) {
      return null;
    }
    String action = form.first(AzericardFields.TOKEN_ACTION);
    String initiation = form.first(AzericardFields.MERCH_TRAN_STATE);
    if (action != null) {
      if (!AzericardTokenFlow.REGISTER.equals(action)) {
        return AzericardFields.REQUIRED_TOKEN_ACTION;
      }
      if (!AzericardTokenFlow.STORING_A_CARD.equals(initiation)) {
        return AzericardFields.REQUIRED_MERCH_TRAN_STATE;
      }
      return theRecurringFieldsItCannotUse();
    }
    if (form.first(AzericardFields.TOKEN) == null) {
      return AzericardFields.REQUIRED_TOKEN;
    }
    if (initiation == null) {
      return null;
    }
    if (!AzericardTokenFlow.MERCHANT_INITIATED.equals(initiation)) {
      return AzericardFields.REQUIRED_MERCH_TRAN_STATE;
    }
    if (transactionType() != AzericardTransactionType.AUTHORIZATION) {
      return AzericardFields.REQUIRED_TRTYPE;
    }
    return form.first(AzericardFields.MERCH_RN_ID) == null
        ? AzericardFields.REQUIRED_MERCH_RN_ID
        : null;
  }

  private @Nullable AzericardRequiredField theCardFieldsItCannotUse() {
    if (form.first(AzericardFields.CARD) == null) {
      return null;
    }
    if (form.first(AzericardFields.EXP) == null) {
      return AzericardFields.REQUIRED_EXP;
    }
    if (form.first(AzericardFields.EXP_YEAR) == null) {
      return AzericardFields.REQUIRED_EXP_YEAR;
    }
    String reason = form.first(AzericardFields.CVC2_RC);
    if (reason != null && !AzericardFields.CVC2_REASON_CODES.contains(reason)) {
      return AzericardFields.REQUIRED_CVC2_RC;
    }
    return carriesTheCvc2() && form.first(AzericardFields.CVC2) == null
        ? AzericardFields.REQUIRED_CVC2
        : null;
  }

  boolean carriesTheCvc2() {
    String reason = form.first(AzericardFields.CVC2_RC);
    return reason == null || AzericardFields.CVC2_PRESENT.equals(reason);
  }

  private @Nullable AzericardRequiredField theRecurringFieldsItCannotUse() {
    String agreement = form.first(AzericardFields.MIT_AGREEMENT);
    if (agreement == null) {
      return null;
    }
    if (!AzericardTokenFlow.AGREEMENTS.contains(agreement)) {
      return AzericardFields.REQUIRED_MIT_AGREEMENT;
    }
    return form.first(AzericardFields.MERCH_RN_ID) == null
        ? AzericardFields.REQUIRED_MERCH_RN_ID
        : null;
  }

  boolean isMerchantInitiated() {
    return paysWithAStoredCard()
        && AzericardTokenFlow.MERCHANT_INITIATED.equals(
            form.first(AzericardFields.MERCH_TRAN_STATE));
  }

  boolean storesACardForMerchantInitiatedPayments() {
    return storesACard() && form.first(AzericardFields.MIT_AGREEMENT) != null;
  }

  @Nullable String extNetRef() {
    return form.first(AzericardFields.EXT_NET_REF);
  }

  AzericardTokenFlow tokenFlow() {
    return form.first(AzericardFields.TOKEN_ACTION) == null
        ? AzericardTokenFlow.STORED_CARD_PAYMENT
        : AzericardTokenFlow.CARD_STORAGE;
  }

  boolean storesACard() {
    return route == AzericardRoute.TOKEN_CGI_LINK && tokenFlow() == AzericardTokenFlow.CARD_STORAGE;
  }

  boolean paysWithAStoredCard() {
    return route == AzericardRoute.TOKEN_CGI_LINK
        && tokenFlow() == AzericardTokenFlow.STORED_CARD_PAYMENT;
  }

  boolean collectsTheCardFromThePayer() {
    return transactionType().createsThePayment()
        && !paysWithAStoredCard()
        && form.first(AzericardFields.CARD) == null;
  }

  AzericardPresentedCard presentedCard(Instant now) {
    if (form.first(AzericardFields.CARD) == null) {
      return new AzericardPresentedCard.NoCardCarried();
    }
    return AzericardCardCheck.ofTheWire(this, now);
  }

  FingerprintedRequest fingerprinted() {
    return new FingerprintedRequest(
        AzericardGateway.WIRE_METHOD, operationName(), withoutWhatIsNeverKept());
  }

  Map<String, List<String>> withoutWhatIsNeverKept() {
    Map<String, List<String>> kept = new LinkedHashMap<>(form.values());
    kept.keySet().removeAll(AzericardFields.SENSITIVE_REQUEST_FIELDS);
    return kept;
  }

  String operationName() {
    AzericardTransactionType type = transactionType();
    return route == AzericardRoute.TOKEN_CGI_LINK
        ? type.operationName() + " " + tokenFlow().namedInAnOperation()
        : type.operationName();
  }

  @Nullable String terminalAsSent() {
    return form.first(AzericardFields.TERMINAL);
  }

  String terminal() {
    return form.required(AzericardFields.TERMINAL);
  }

  String order() {
    return form.required(AzericardFields.ORDER);
  }

  String amount() {
    return form.required(AzericardFields.AMOUNT);
  }

  String currency() {
    return form.required(AzericardFields.CURRENCY);
  }

  String nonce() {
    return form.required(AzericardFields.NONCE);
  }

  String pSign() {
    return form.required(AzericardFields.P_SIGN);
  }

  String trtype() {
    return form.required(AzericardFields.TRTYPE);
  }

  String rrn() {
    return form.required(AzericardFields.RRN);
  }

  String intRef() {
    return form.required(AzericardFields.INT_REF);
  }

  String token() {
    return form.required(AzericardFields.TOKEN);
  }

  @Nullable String backref() {
    return form.first(AzericardFields.BACKREF);
  }

  @Nullable String merchantUrl() {
    return form.first(AzericardFields.MERCH_URL);
  }

  @Nullable String merchantName() {
    return form.first(AzericardFields.MERCH_NAME);
  }

  @Nullable String description() {
    return form.first(AzericardFields.DESC);
  }

  @Nullable String cardholderName() {
    return form.first(AzericardFields.NAME);
  }

  @Nullable String email() {
    return form.first(AzericardFields.EMAIL);
  }

  String rrnAsSentOrNothing() {
    return orNothing(AzericardFields.RRN);
  }

  String intRefAsSentOrNothing() {
    return orNothing(AzericardFields.INT_REF);
  }

  String originalTransactionTypeAsSentOrNothing() {
    return orNothing(AzericardFields.TRAN_TRTYPE);
  }

  private String orNothing(String field) {
    String value = form.first(field);
    return value == null ? AzericardOutcomes.ABSENT : value;
  }

  AzericardTransactionType transactionType() {
    return AzericardTransactionType.of(trtype()).orElseThrow();
  }

  Instant sentAt() {
    return AzericardTimestamp.read(form.required(AzericardFields.TIMESTAMP)).orElseThrow();
  }

  byte[] macSource() {
    return transactionType().shape().macSourceOf(form);
  }

  byte[] macSourceAsTheManualPublishesIt() {
    if (transactionType().shape() != AzericardRequestShape.SETTLEMENT) {
      return macSource();
    }
    return AzericardSettlementMac.theSourceTheManualPublishesFor(form);
  }
}
