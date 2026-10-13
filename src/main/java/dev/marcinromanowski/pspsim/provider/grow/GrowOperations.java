package dev.marcinromanowski.pspsim.provider.grow;

import dev.marcinromanowski.pspsim.kernel.scenario.StoryKeyLifetime;
import dev.marcinromanowski.pspsim.kernel.spi.FailureCodes;
import dev.marcinromanowski.pspsim.kernel.spi.MatchFact;
import dev.marcinromanowski.pspsim.kernel.spi.ProcessingStates;
import dev.marcinromanowski.pspsim.kernel.spi.ProviderOperation;
import dev.marcinromanowski.pspsim.kernel.spi.ProviderVocabulary;
import java.util.List;
import java.util.Map;
import java.util.Set;

final class GrowOperations {

  private GrowOperations() {}

  static final String CREATE_PAYMENT_PROCESS = "createPaymentProcess";

  static final String CREATE_TRANSACTION_WITH_TOKEN = "createTransactionWithToken";

  static final String APPROVE_TRANSACTION = "approveTransaction";

  static final String SETTLE_SUSPENDED_TRANSACTION = "settleSuspendedTransaction";

  static final String REFUND_TRANSACTION = "refundTransaction";

  static final String GET_TRANSACTION_INFO = "getTransactionInfo";

  static final String GET_PAYMENT_PROCESS_INFO = "getPaymentProcessInfo";

  static final String GET_TOKEN_TRANSACTIONS_BY_EXTERNAL_IDENTIFIERS =
      "getTokenTransactionsByExternalIdentifiers";

  static final String PAYMENT_PROCESS =
      "the payment process the call belongs to, or the transaction itself where a stored-card"
          + " charge left none";

  private static final String NO_CUSTOMER =
      "provider grow publishes no customer or owner id on any endpoint of this contract";

  private static final String NO_CURRENCY =
      "provider grow publishes no currency parameter and no currency error";

  private static final String NO_ORDER =
      "provider grow publishes no order identifier on any endpoint of this contract; the nearest"
          + " facts it publishes are transactionUniqueIdentifier and transactionGroupIdentifier,"
          + " which grow.md defines as duplicate-detection keys and not as an order";

  private static final String ERROR_ID =
      "provider grow answers a failure with a numeric err.id, which is "
          + GrowErrors.ERROR_ID_SHAPE
          + ". A scenario may name that number, or one of the decline reasons provider grow"
          + " publishes for a stored-card charge, each of which carries its own err.id and its own"
          + " published finality: "
          + GrowDecline.theReasonsDescribed();

  static final String NO_PROCESSING_OUTCOME =
      "provider grow publishes no processing status and no pending status for a transaction or"
          + " a process:"
          + " the native status codes it publishes are 0, 2, 3, 4, 6, 9 and 11, and every one of"
          + " them reports an outcome that has already happened. A grow transaction is answered,"
          + " held or declined; it is never in flight, so there is no native status for a rule to"
          + " put one in and none for a later lookup to report differently";

  static final ProviderVocabulary VOCABULARY =
      new ProviderVocabulary(
          GrowLightApi.GROW,
          List.of(
              operation(CREATE_PAYMENT_PROCESS, Set.of(MatchFact.MERCHANT_ID, MatchFact.AMOUNT)),
              operation(
                  CREATE_TRANSACTION_WITH_TOKEN,
                  Set.of(MatchFact.MERCHANT_ID, MatchFact.PAYMENT_METHOD_ID, MatchFact.AMOUNT)),
              operation(
                  APPROVE_TRANSACTION, Set.of(MatchFact.MERCHANT_ID, MatchFact.PROVIDER_OBJECT_ID)),
              operation(
                  SETTLE_SUSPENDED_TRANSACTION,
                  Set.of(MatchFact.MERCHANT_ID, MatchFact.PROVIDER_OBJECT_ID, MatchFact.AMOUNT)),
              operation(
                  REFUND_TRANSACTION,
                  Set.of(MatchFact.MERCHANT_ID, MatchFact.PROVIDER_OBJECT_ID, MatchFact.AMOUNT)),
              operation(
                  GET_TRANSACTION_INFO,
                  Set.of(MatchFact.MERCHANT_ID, MatchFact.PROVIDER_OBJECT_ID)),
              operation(
                  GET_PAYMENT_PROCESS_INFO,
                  Set.of(MatchFact.MERCHANT_ID, MatchFact.PROVIDER_OBJECT_ID)),
              operation(
                  GET_TOKEN_TRANSACTIONS_BY_EXTERNAL_IDENTIFIERS,
                  Set.of(
                      MatchFact.MERCHANT_ID,
                      MatchFact.PROVIDER_OBJECT_ID,
                      MatchFact.PAYMENT_METHOD_ID))),
          Map.of(
              MatchFact.CUSTOMER_ID,
              NO_CUSTOMER,
              MatchFact.CURRENCY,
              NO_CURRENCY,
              MatchFact.ORDER_ID,
              NO_ORDER),
          new FailureCodes(
              GrowErrors.ERROR_ID_PATTERN + "|" + GrowDecline.theReasonsAScenarioMayName(),
              ERROR_ID),
          ProcessingStates.noneOfThem(NO_PROCESSING_OUTCOME));

  private static ProviderOperation operation(String name, Set<MatchFact> carries) {
    return new ProviderOperation(
        name, carries, PAYMENT_PROCESS, StoryKeyLifetime.AN_IDENTIFIER_THE_OBJECT_KEEPS_FOR_LIFE);
  }
}
