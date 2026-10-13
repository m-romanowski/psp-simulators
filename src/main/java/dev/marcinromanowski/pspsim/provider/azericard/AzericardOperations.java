package dev.marcinromanowski.pspsim.provider.azericard;

import dev.marcinromanowski.pspsim.kernel.scenario.StoryKeyLifetime;
import dev.marcinromanowski.pspsim.kernel.spi.FailureCodes;
import dev.marcinromanowski.pspsim.kernel.spi.MatchFact;
import dev.marcinromanowski.pspsim.kernel.spi.ProcessingStates;
import dev.marcinromanowski.pspsim.kernel.spi.ProviderOperation;
import dev.marcinromanowski.pspsim.kernel.spi.ProviderVocabulary;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

final class AzericardOperations {

  private AzericardOperations() {}

  static final String PAYMENT =
      "the payment the call is about, which provider azericard names by the TERMINAL it was sent"
          + " to and the ORDER the merchant chose for it";

  static final String DECLINE_CODE_PATTERN = "(?!00)[0-9A-Za-z]{2}";

  private static final String NO_CUSTOMER =
      "provider azericard publishes no customer or owner id on any endpoint of this contract; the"
          + " nearest field it publishes is NAME, the cardholder name as it appears on the card,"
          + " which names nobody a later call can address";

  private static final String RESPONSE_CODE =
      "provider azericard answers a decline as ACTION=2 carrying RC, the issuer's ISO-8583"
          + " field-39 code, which its response field table sizes at exactly two characters; the"
          + " vendor publishes no response-code table for this gateway at all, so every"
          + " two-character code is admitted except the published approval 00, which no decline"
          + " can carry";

  private static final String NO_EVENT_NAME =
      "provider azericard names no callback event anywhere in its contract: BACKREF carries the"
          + " authorization result and nothing else, so every callback this gateway posts reports"
          + " the same one thing and a rule narrowed on an event type could never fire";

  static final String NO_PROCESSING_OUTCOME =
      "provider azericard publishes no pending or processing outcome for this gateway at all: the"
          + " only disposition values are ACTION 0, 1, 2, 3, 6, 7 and 8, and none of them is"
          + " in-flight. The TRTYPE=90 lookup this simulator answers therefore reports the"
          + " ACTION and RC the payment already reached, and no rule can put a payment of this"
          + " gateway in flight for a later one to report differently. No slice of this simulator"
          + " will change that, because it is the contract and not the build";

  static final ProviderVocabulary VOCABULARY =
      new ProviderVocabulary(
          AzericardGateway.AZERICARD,
          operations(),
          Map.of(MatchFact.CUSTOMER_ID, NO_CUSTOMER, MatchFact.EVENT_TYPE, NO_EVENT_NAME),
          new FailureCodes(DECLINE_CODE_PATTERN, RESPONSE_CODE),
          ProcessingStates.noneOfThem(NO_PROCESSING_OUTCOME));

  private static List<ProviderOperation> operations() {
    List<ProviderOperation> exposed = new ArrayList<>();
    for (AzericardTransactionType type : AzericardTransactionType.values()) {
      exposed.add(
          new ProviderOperation(
              type.operationName(),
              factsCarriedBy(type),
              PAYMENT,
              StoryKeyLifetime.AN_IDENTIFIER_THE_OBJECT_KEEPS_FOR_LIFE));
      if (type.createsThePayment()) {
        for (AzericardTokenFlow flow : AzericardTokenFlow.values()) {
          exposed.add(
              new ProviderOperation(
                  type.operationName() + " " + flow.namedInAnOperation(),
                  factsCarriedBy(type, flow),
                  PAYMENT,
                  StoryKeyLifetime.AN_IDENTIFIER_THE_OBJECT_KEEPS_FOR_LIFE));
        }
      }
    }
    return List.copyOf(exposed);
  }

  private static Set<MatchFact> factsCarriedBy(AzericardTransactionType type) {
    if (type.looksUpThePayment()) {
      return Set.of(MatchFact.MERCHANT_ID, MatchFact.ORDER_ID);
    }
    if (type.createsThePayment()) {
      return Set.of(
          MatchFact.MERCHANT_ID, MatchFact.ORDER_ID, MatchFact.AMOUNT, MatchFact.CURRENCY);
    }
    return Set.of(
        MatchFact.MERCHANT_ID,
        MatchFact.ORDER_ID,
        MatchFact.PROVIDER_OBJECT_ID,
        MatchFact.AMOUNT,
        MatchFact.CURRENCY);
  }

  private static Set<MatchFact> factsCarriedBy(
      AzericardTransactionType type, AzericardTokenFlow flow) {
    Set<MatchFact> carried = new LinkedHashSet<>(factsCarriedBy(type));
    if (flow == AzericardTokenFlow.STORED_CARD_PAYMENT) {
      carried.add(MatchFact.PAYMENT_METHOD_ID);
    }
    return Set.copyOf(carried);
  }
}
