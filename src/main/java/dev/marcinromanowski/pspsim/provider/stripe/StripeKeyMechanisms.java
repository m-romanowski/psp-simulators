package dev.marcinromanowski.pspsim.provider.stripe;

import dev.marcinromanowski.pspsim.kernel.idempotency.KeyRole;
import dev.marcinromanowski.pspsim.kernel.spi.DeclaredKey;
import dev.marcinromanowski.pspsim.kernel.spi.KeyCarriage;
import dev.marcinromanowski.pspsim.kernel.spi.KeyClaim;
import dev.marcinromanowski.pspsim.kernel.spi.ProviderKeyMechanisms;
import java.util.List;
import java.util.Map;

final class StripeKeyMechanisms {

  static final String IDEMPOTENCY_KEY = StripeFields.IDEMPOTENCY_KEY_HEADER;

  static final String PAYMENT_INTENT_ID = "id";

  private static final String SCOPED_BY_ACCOUNT =
      "the API key's own account, for twenty-four hours: provider stripe stores the status code and"
          + " the body of the first request made under the key, whether it succeeded or failed, and"
          + " answers every later request that sends the same key and the same endpoint and"
          + " parameters with that stored answer and the Idempotent-Replayed header";

  private static final String SCOPED_BY_THE_INTENT =
      "the PaymentIntent the call is about: provider stripe draws the id when the intent is created"
          + " and every confirm, capture, cancel and refund of that payment names it in the path";

  private static final KeyClaim CLAIMED_UNTIL_THE_ENDPOINT_ANSWERS =
      new KeyClaim.ClaimedBeforeTheOperationRuns(
          "provider stripe claims the key before the endpoint executes and holds it until that"
              + " request has stored its answer, so a second request sent while the first is still"
              + " executing is answered 409 with the error code idempotency_key_in_use, typed"
              + " idempotency_error: \"The idempotency key provided is currently being used in"
              + " another request. This occurs if your integration is making duplicate requests"
              + " simultaneously.\" That refusal is retryable where a parameter mismatch is not");

  private static final KeyClaim THE_INTENT_IS_NEVER_CLAIMED =
      new KeyClaim.NeverHeldByARequest(
          "provider stripe claims nothing when a call names a PaymentIntent in its path: the id"
              + " addresses an intent that already exists, so a second call naming it while the"
              + " first is still running is answered from the intent's own state and never refused"
              + " for a claim another request is holding");

  private static final String NO_DUPLICATE_CHECK =
      "provider stripe refuses no request for repeating a merchant's own reference: a merchant"
          + " reference travels in metadata or description, which provider stripe never compares"
          + " against anything, so two charges of the same order are two charges";

  static final ProviderKeyMechanisms MECHANISMS =
      new ProviderKeyMechanisms(
          StripeApi.STRIPE,
          List.of(
              new DeclaredKey(
                  IDEMPOTENCY_KEY,
                  KeyRole.TRUE_IDEMPOTENCY_KEY,
                  KeyCarriage.HEADER,
                  SCOPED_BY_ACCOUNT,
                  CLAIMED_UNTIL_THE_ENDPOINT_ANSWERS),
              new DeclaredKey(
                  PAYMENT_INTENT_ID,
                  KeyRole.CORRELATION_KEY,
                  KeyCarriage.PATH_SEGMENT,
                  SCOPED_BY_THE_INTENT,
                  THE_INTENT_IS_NEVER_CLAIMED)),
          Map.of(KeyRole.DUPLICATE_DETECTION_KEY, NO_DUPLICATE_CHECK));

  private StripeKeyMechanisms() {}
}
