package dev.marcinromanowski.pspsim.provider.tranzila;

import dev.marcinromanowski.pspsim.kernel.scenario.StoryKeyLifetime;
import dev.marcinromanowski.pspsim.kernel.spi.FailureCodes;
import dev.marcinromanowski.pspsim.kernel.spi.MatchFact;
import dev.marcinromanowski.pspsim.kernel.spi.ProcessingStates;
import dev.marcinromanowski.pspsim.kernel.spi.ProviderOperation;
import dev.marcinromanowski.pspsim.kernel.spi.ProviderVocabulary;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

final class TranzilaOperations {

  private TranzilaOperations() {}

  static final String DEBIT = TranzilaTxnType.DEBIT.wire();

  static final String VERIFY = TranzilaTxnType.VERIFY.wire();

  static final String FORCE = TranzilaTxnType.FORCE.wire();

  static final String REVERSAL = TranzilaTxnType.REVERSAL.wire();

  static final String CREDIT = TranzilaTxnType.CREDIT.wire();

  static final String CANCEL = TranzilaTxnType.CANCEL.wire();

  static final String THREE_D_SECURE = "3ds";

  static final String THE_ORIGINAL_TRANSACTION =
      "the original transaction, a debit or a verify, which provider tranzila names by the"
          + " integer transaction_id it answers. A force, a reversal, a credit and a cancel are"
          + " transactions of their own with their own transaction_id, and a story about any of"
          + " them stands on the original they reach through reference_txn_id, so one story"
          + " follows a J5, its force and the credits of that force. The 3ds step of a debit or a"
          + " verify sent with activate_3ds Y stands on the transaction it authenticates, which"
          + " a challenge makes only once it is completed";

  static final String NO_ORDER_ID =
      "provider tranzila publishes no order reference on the create request: the fields a"
          + " merchant may put its own values in are user_defined_fields and remarks, and neither"
          + " is an order identifier this contract compares or answers";

  static final String NO_CUSTOMER_ID =
      "provider tranzila publishes no customer on the create request; card_holder_id is the"
          + " card holder's national ID, checked by the issuer and answered as Responseid, and not"
          + " a merchant's customer identifier";

  static final String NO_EVENT_TYPE =
      "provider tranzila publishes no event type: the create answers synchronously, and the only"
          + " notification it publishes is the payment page's notify, which names no event";

  static final String NO_PROCESSING_STATE =
      "provider tranzila answers every transaction of this route synchronously, approved or"
          + " declined. The code table's shva, \"Pending SHVA response.\", is never answered here,"
          + " because no published read resolves a pending answer";

  private static final Set<MatchFact> OF_AN_ORIGINAL =
      Set.of(
          MatchFact.MERCHANT_ID, MatchFact.AMOUNT, MatchFact.CURRENCY, MatchFact.PAYMENT_METHOD_ID);

  private static final Set<MatchFact> OF_A_FOLLOW_ON =
      Set.of(
          MatchFact.MERCHANT_ID,
          MatchFact.PROVIDER_OBJECT_ID,
          MatchFact.AMOUNT,
          MatchFact.CURRENCY,
          MatchFact.PAYMENT_METHOD_ID);

  static final ProviderVocabulary VOCABULARY =
      new ProviderVocabulary(
          TranzilaApi.TRANZILA,
          List.of(
              operation(THREE_D_SECURE, OF_AN_ORIGINAL),
              operation(DEBIT, OF_AN_ORIGINAL),
              operation(VERIFY, OF_AN_ORIGINAL),
              operation(FORCE, OF_A_FOLLOW_ON),
              operation(REVERSAL, OF_A_FOLLOW_ON),
              operation(CREDIT, OF_A_FOLLOW_ON),
              operation(CANCEL, OF_A_FOLLOW_ON)),
          Map.of(
              MatchFact.ORDER_ID, NO_ORDER_ID,
              MatchFact.CUSTOMER_ID, NO_CUSTOMER_ID,
              MatchFact.EVENT_TYPE, NO_EVENT_TYPE),
          new FailureCodes(
              TranzilaResponseCodes.THE_DECLINE_SHAPE, TranzilaResponseCodes.THE_DECLINE_CODES),
          ProcessingStates.noneOfThem(NO_PROCESSING_STATE),
          theCodesEachOperationAnswers());

  private static Map<String, FailureCodes> theCodesEachOperationAnswers() {
    FailureCodes declines =
        new FailureCodes(
            TranzilaResponseCodes.A_PROCESSOR_DECLINE,
            TranzilaResponseCodes.THE_PROCESSOR_DECLINES);
    Map<String, FailureCodes> answered = new LinkedHashMap<>();
    answered.put(
        THREE_D_SECURE,
        new FailureCodes(
            TranzilaResponseCodes.A_3DS_FAILURE, TranzilaResponseCodes.THE_3DS_FAILURE_CODES));
    for (TranzilaTxnType type : TranzilaTxnType.values()) {
      answered.put(type.wire(), declines);
    }
    return answered;
  }

  private static ProviderOperation operation(String name, Set<MatchFact> carries) {
    return new ProviderOperation(
        name,
        carries,
        THE_ORIGINAL_TRANSACTION,
        StoryKeyLifetime.AN_IDENTIFIER_THE_OBJECT_KEEPS_FOR_LIFE);
  }
}
