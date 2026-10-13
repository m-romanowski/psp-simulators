package dev.marcinromanowski.pspsim.provider.autra;

import dev.marcinromanowski.pspsim.kernel.scenario.StoryKeyLifetime;
import dev.marcinromanowski.pspsim.kernel.spi.FailureCodes;
import dev.marcinromanowski.pspsim.kernel.spi.MatchFact;
import dev.marcinromanowski.pspsim.kernel.spi.ProcessingStates;
import dev.marcinromanowski.pspsim.kernel.spi.ProviderOperation;
import dev.marcinromanowski.pspsim.kernel.spi.ProviderVocabulary;
import java.util.List;
import java.util.Map;
import java.util.Set;

final class AutraOperations {

  private AutraOperations() {}

  static final String INITIALIZE = "POST /v1/acquiring/payments/initialize";

  static final String CARD_SESSION = "POST /v1/acquiring/card-sessions";

  static final String SALE = "POST /v1/acquiring/payments";

  static final String PRE_AUTHORIZATION = "POST /v1/acquiring/payments/pre-authorization";

  static final String CAPTURE = "POST /v1/acquiring/payments/{paymentId}/capture";

  static final String VOID = "POST /v1/acquiring/payments/{paymentId}/void";

  static final String PIX_INITIALIZE = "POST /v1/acquiring/pix/initialize";

  static final String PIX_CHECKOUT = "POST /v1/acquiring/pix/checkout";

  static final String THE_PIX =
      "the Pix the call creates, which provider autra names by the pixId it draws while answering"
          + " the checkout and which its acquiring webhooks carry in operationData.pixId, the same"
          + " value its correlation table equates with the Pix txid";

  static final String THE_PIX_SESSION =
      "the Pix session this call opens, which provider autra names by the merchant's documentId,"
          + " because initialize carries that field and nothing else and the transactionalToken it"
          + " answers with does not exist until the call has been decided";

  static final String THE_CARD_SESSION =
      "the hosted card session the call opens, which provider autra names by the"
          + " hostedCardSessionId it draws while answering and puts inside the hostedCardUrl a"
          + " payer opens: \"Curta duracao e uso unico\"";

  static final String THE_PAYMENT =
      "the payment the call is about, which provider autra draws a UUID for when the sale is"
          + " charged or the pre-authorization is taken and which the capture and the void of a"
          + " hold name in the path";

  static final String THE_ACQUIRING_SESSION =
      "the acquiring session this call opens, which provider autra names by the merchant's"
          + " documentId, because initialize carries that field and nothing else and the"
          + " transactionalToken it answers with does not exist until the call has been decided";

  static final String NO_CUSTOMER =
      "provider autra publishes no customer, payer or owner object anywhere on its acquiring"
          + " surface: the only party field these endpoints carry is documentId, which this"
          + " contract carries as the merchant's own CPF or CNPJ, so nothing here names a payer a"
          + " later call could address";

  static final String NO_PROCESSING_OUTCOME =
      "provider autra publishes two statuses that mean accepted-but-unsettled, REFUND_PENDING and"
          + " PARTIAL_REFUND_PENDING, and publishes them on the void endpoint alone as \"aceito"
          + " para analise; o resultado definitivo chega depois pelo webhook"
          + " acquiring.financial_cycle, correlacionado por rrn\". It publishes that event's"
          + " payload nowhere, and it never says which status a resolved reversal lands on, so"
          + " there is no published outcome for a rule to settle a held one with. The"
          + " pre-authorization, the capture and the initialize of this contract publish no"
          + " in-flight status at all";

  static final ProviderVocabulary VOCABULARY =
      new ProviderVocabulary(
          AutraApi.AUTRA,
          List.of(
              new ProviderOperation(
                  CARD_SESSION,
                  Set.of(MatchFact.MERCHANT_ID),
                  THE_CARD_SESSION,
                  StoryKeyLifetime.AN_IDENTIFIER_THE_OBJECT_KEEPS_FOR_LIFE),
              new ProviderOperation(
                  INITIALIZE,
                  Set.of(MatchFact.MERCHANT_ID),
                  THE_ACQUIRING_SESSION,
                  StoryKeyLifetime.AN_IDENTIFIER_THE_OBJECT_KEEPS_FOR_LIFE),
              new ProviderOperation(
                  SALE,
                  Set.of(
                      MatchFact.MERCHANT_ID,
                      MatchFact.ORDER_ID,
                      MatchFact.AMOUNT,
                      MatchFact.CURRENCY,
                      MatchFact.PAYMENT_METHOD_ID),
                  THE_PAYMENT,
                  StoryKeyLifetime.AN_IDENTIFIER_THE_OBJECT_KEEPS_FOR_LIFE),
              new ProviderOperation(
                  PRE_AUTHORIZATION,
                  Set.of(
                      MatchFact.MERCHANT_ID,
                      MatchFact.ORDER_ID,
                      MatchFact.AMOUNT,
                      MatchFact.CURRENCY,
                      MatchFact.PAYMENT_METHOD_ID),
                  THE_PAYMENT,
                  StoryKeyLifetime.AN_IDENTIFIER_THE_OBJECT_KEEPS_FOR_LIFE),
              new ProviderOperation(
                  CAPTURE,
                  Set.of(MatchFact.MERCHANT_ID, MatchFact.PROVIDER_OBJECT_ID, MatchFact.AMOUNT),
                  THE_PAYMENT,
                  StoryKeyLifetime.AN_IDENTIFIER_THE_OBJECT_KEEPS_FOR_LIFE),
              new ProviderOperation(
                  VOID,
                  Set.of(
                      MatchFact.MERCHANT_ID,
                      MatchFact.PROVIDER_OBJECT_ID,
                      MatchFact.AMOUNT,
                      MatchFact.CURRENCY),
                  THE_PAYMENT,
                  StoryKeyLifetime.AN_IDENTIFIER_THE_OBJECT_KEEPS_FOR_LIFE),
              new ProviderOperation(
                  PIX_INITIALIZE,
                  Set.of(MatchFact.MERCHANT_ID),
                  THE_PIX_SESSION,
                  StoryKeyLifetime.AN_IDENTIFIER_THE_OBJECT_KEEPS_FOR_LIFE),
              new ProviderOperation(
                  PIX_CHECKOUT,
                  Set.of(
                      MatchFact.MERCHANT_ID,
                      MatchFact.ORDER_ID,
                      MatchFact.AMOUNT,
                      MatchFact.CURRENCY),
                  THE_PIX,
                  StoryKeyLifetime.AN_IDENTIFIER_THE_OBJECT_KEEPS_FOR_LIFE)),
          Map.of(MatchFact.CUSTOMER_ID, NO_CUSTOMER),
          new FailureCodes(AutraErrors.theCodesAScenarioMayName(), AutraErrors.DECLINE_CODES),
          ProcessingStates.noneOfThem(NO_PROCESSING_OUTCOME));
}
