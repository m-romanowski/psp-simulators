package dev.marcinromanowski.pspsim.provider.autra;

import dev.marcinromanowski.pspsim.kernel.idempotency.KeyRole;
import dev.marcinromanowski.pspsim.kernel.spi.DeclaredKey;
import dev.marcinromanowski.pspsim.kernel.spi.KeyCarriage;
import dev.marcinromanowski.pspsim.kernel.spi.KeyClaim;
import dev.marcinromanowski.pspsim.kernel.spi.ProviderKeyMechanisms;
import java.util.List;
import java.util.Map;

final class AutraKeyMechanisms {

  static final String ORDER_ID = AutraFields.ORDER_ID;

  static final String PAYMENT_ID = AutraFields.PAYMENT_ID_LOWERCASE_D;

  static final String THE_SALE_CARRIES_THE_SAME_FIELD_AND_A_THIRD_POLICY =
      " Provider autra reads an orderId on POST /v1/acquiring/payments as well and gives it a"
          + " policy that is neither of the other two: it claims nothing in advance and refuses a"
          + " repeat only once a payment under that orderId has been approved — \"Se um pagamento"
          + " com o mesmo orderId ja estiver ACCEPTED ou CAPTURED, retorna 409 sem criar um novo"
          + " debito\" — answering 409 ORDER_ALREADY_APPROVED, a different code from the"
          + " pre-authorization's. A declined sale leaves the orderId free and a second attempt"
          + " under it is admitted; two sales racing under one orderId are both charged, because"
          + " autra publishes the claim-before-the-acquirer rule on the pre-authorization alone.";

  static final String PIX_CARRIES_THE_SAME_FIELD_AND_NO_KEY =
      " This role is the pre-authorization's alone."
          + THE_SALE_CARRIES_THE_SAME_FIELD_AND_A_THIRD_POLICY
          + " Provider autra reads an orderId on"
          + " POST /v1/acquiring/pix/checkout as well and publishes it under a heading that says"
          + " idempotency and then denies it: \"Nao e tecnicamente uma chave de idempotencia (a"
          + " API nao bloqueia 2 Pix com mesmo orderId hoje), mas e a referencia cruzada que voce"
          + " vai querer ter\", with the consequence \"se o front demorar e o usuario clicar 2x no"
          + " botao 'Pagar', voce pode acabar gerando 2 QRs\". Two Pix checkouts under one orderId"
          + " therefore produce two pixIds and two QR codes here, nothing is claimed and nothing is"
          + " refused, and no Pix checkout is recorded as a keyed exchange at all";

  private static final String SCOPED_BY_THE_MERCHANT_WITHIN_THE_TENANT =
      "the orderId a pre-authorization carries, \"com escopo por merchant (documentId) dentro do"
          + " seu tenant\". Sent absent or empty, provider autra turns idempotency off for that"
          + " call: \"Sem orderId (ausente ou vazio) a idempotencia fica desligada: cada POST cria"
          + " uma nova pre-autorizacao\". A repeat under an orderId that still holds a"
          + " pre-authorization is refused 409 ORDER_ALREADY_PREAUTHORIZED, and a repeat under one"
          + " whose attempt failed is admitted, because \"Tentativas que terminaram em falha"
          + " (FAILED/ERROR/EXPIRED) liberam o orderId para reuso\"."
          + PIX_CARRIES_THE_SAME_FIELD_AND_NO_KEY;

  private static final String SCOPED_BY_THE_PAYMENT =
      "the payment the capture or the void is about, which provider autra names in the path as"
          + " {paymentId}: it addresses a pre-authorization that already exists, so a second call"
          + " naming it is answered from what that payment holds now and is neither replayed nor"
          + " refused as a duplicate. A Pix has no such route at all: provider autra publishes"
          + " \"Sem endpoint publico de status\" and \"Sem cancelamento\" for Pix Checkout, so"
          + " nothing this contract serves ever names a pixId in a path";

  private static final KeyClaim CLAIMED_BEFORE_THE_ACQUIRER_IS_CALLED =
      new KeyClaim.ClaimedBeforeTheOperationRuns(
          "provider autra claims the order before it calls the acquirer — \"A ordem e reivindicada"
              + " antes da chamada ao adquirente — retries (timeout, queda de rede,"
              + " reprocessamento) com o mesmo orderId nunca geram uma segunda reserva no"
              + " cartao\" — so a second pre-authorization arriving while the first is still"
              + " running finds the order claimed. Autra publishes one conflict answer for this"
              + " endpoint and no separate answer for that race, so this simulator answers it the"
              + " same way: 409 with errors[] carrying ORDER_ALREADY_PREAUTHORIZED");

  private static final KeyClaim THE_PAYMENT_IS_NEVER_CLAIMED =
      new KeyClaim.NeverHeldByARequest(
          "provider autra claims nothing when a call names a payment in its path: {paymentId}"
              + " addresses a pre-authorization that already exists, and Autra publishes no answer"
              + " anywhere on the capture or the void that refuses a call for a claim another"
              + " request is still holding");

  private static final String NO_IDEMPOTENCY_KEY =
      "provider autra publishes no idempotency header and nothing anywhere in this contract that"
          + " replays a stored answer. orderId is a duplicate check whose repeat is refused: a"
          + " client that retries a pre-authorization after a lost response is answered 409"
          + " ORDER_ALREADY_PREAUTHORIZED, and that body \"carries no payment identity\", so it"
          + " tells the caller that a hold exists and never which one";

  static final ProviderKeyMechanisms MECHANISMS =
      new ProviderKeyMechanisms(
          AutraApi.AUTRA,
          List.of(
              new DeclaredKey(
                  ORDER_ID,
                  KeyRole.DUPLICATE_DETECTION_KEY,
                  KeyCarriage.REQUEST_FIELD,
                  SCOPED_BY_THE_MERCHANT_WITHIN_THE_TENANT,
                  CLAIMED_BEFORE_THE_ACQUIRER_IS_CALLED),
              new DeclaredKey(
                  PAYMENT_ID,
                  KeyRole.CORRELATION_KEY,
                  KeyCarriage.PATH_SEGMENT,
                  SCOPED_BY_THE_PAYMENT,
                  THE_PAYMENT_IS_NEVER_CLAIMED)),
          Map.of(KeyRole.TRUE_IDEMPOTENCY_KEY, NO_IDEMPOTENCY_KEY));

  private AutraKeyMechanisms() {}
}
