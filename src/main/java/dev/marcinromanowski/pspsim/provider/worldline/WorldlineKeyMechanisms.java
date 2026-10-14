package dev.marcinromanowski.pspsim.provider.worldline;

import dev.marcinromanowski.pspsim.kernel.credentials.Redaction;
import dev.marcinromanowski.pspsim.kernel.idempotency.KeyRole;
import dev.marcinromanowski.pspsim.kernel.spi.DeclaredKey;
import dev.marcinromanowski.pspsim.kernel.spi.KeyCarriage;
import dev.marcinromanowski.pspsim.kernel.spi.KeyClaim;
import dev.marcinromanowski.pspsim.kernel.spi.ProviderKeyMechanisms;
import java.util.List;
import java.util.Map;

final class WorldlineKeyMechanisms {

  static final String IDEMPOTENCE_KEY =
      WorldlineFields.IDEMPOTENCE_KEY_AS_THE_IDEMPOTENT_REQUESTS_PAGE_SPELLS_IT;

  static final String PAYMENT_ID = WorldlineOperations.PAYMENT_ID;

  static final String SCOPED_BY_THE_KEY_THE_CALLER_CHOSE =
      "the idempotence key the caller put on a mutating request, which provider worldline compares"
          + " against nothing else at all. Its repeat rule is published and it is not the one most"
          + " gateways have: \"For completed requests: Our server will respond with the same"
          + " outcome as the original request, even with different payloads. Updates related to"
          + " the operation, such as payment status, may still occur.\" A differing payload is"
          + " therefore not a conflict here, and this simulator never answers one — the"
          + " fingerprint of the first request is recorded so that a tester can see the two"
          + " payloads differed, and it decides nothing. The key \"Has a maximum length of 40"
          + " characters\", \"Should be ASCII compliant and not support UTF-8\", and \"Do not use"
          + " the same key twice within the idempotence period (at least 24 hours)\". The"
          + " operations that support it are exactly CreatePayment, CancelPayment, RefundPayment,"
          + " CapturePayment, CompletePayment, CreatePayout and SubsequentPayment, and of those"
          + " this slice serves three; on any other call the key is ignored rather than refused —"
          + " \"the RESTful Server API will ignore the key and treat the request as a first"
          + " request\". A replay carries an extra response header,"
          + " X-GCS-Idempotence-Request-Timestamp, which \"indicates the timestamp of the initial"
          + " request in milliseconds since January 1st, 1970 00:00:00 UTC\". "
          + WorldlineIdempotence.BOTH_SPELLINGS_ARE_READ;

  static final String SCOPED_BY_THE_PAYMENT =
      "the payment the capture or the cancel is about, which provider worldline names in the path"
          + " as {paymentId}: it addresses a payment that already exists, so a second call naming"
          + " it is answered from what that payment holds now and is neither replayed nor refused"
          + " as a duplicate. This provider is the first here whose path key is not stable —"
          + " every maintenance operation mints a new payment.id — so the identifier a second"
          + " call carries need not be the one the first call carried, and this simulator"
          + " resolves any identifier the payment has answered to back to the same payment";

  static final KeyClaim CLAIMED_BEFORE_THE_OPERATION_RUNS =
      new KeyClaim.ClaimedBeforeTheOperationRuns(
          "provider worldline claims the key before the operation runs and publishes what a"
              + " second request meeting it is answered: \"For requests in progress: A response"
              + " with an HTTP status code of 409 (Conflict) will indicate that the request is"
              + " currently being processed.\" That is the only one of the three published"
              + " meanings of 409 this simulator serves on an idempotence key. The second"
              + " meaning, a duplicate key, is published on the troubleshooting page as \"Either"
              + " you submitted a duplicate request or you are trying to create something with a"
              + " duplicate key\" and carried by the Java SDK's ReferenceException; this"
              + " simulator never answers it under this key, because the same contract's"
              + " idempotency page says a completed repeat replays rather than conflicts and no"
              + " key can do both. The third meaning belongs to the cancel route and is answered"
              + " there: \"Cancellation is not allowed because payment is closed\"");

  static final KeyClaim THE_PAYMENT_IS_NEVER_CLAIMED =
      new KeyClaim.NeverHeldByARequest(
          "provider worldline claims nothing when a call names a payment in its path:"
              + " {paymentId} addresses a payment that already exists, and worldline publishes no"
              + " answer anywhere on the capture or the cancel that refuses a call because"
              + " another request is still holding that payment. The one 409 those routes do"
              + " publish is about the payment being closed and not about a request in flight");

  static final String NO_DUPLICATE_DETECTION_KEY =
      "provider worldline publishes one 409 meaning \"you are trying to create something with a"
          + " duplicate key\" on its troubleshooting page, and its Java SDK carries a"
          + " ReferenceException for it, and nothing in this contract says which field that"
          + " duplicate key would be. order.references.merchantReference is not one: it is"
          + " published as a merchant's own reference with maxLength 40 and no uniqueness rule"
          + " anywhere, and this simulator refuses no repeat of it. The idempotence key is not"
          + " one either, because the same contract publishes a completed repeat of it as a"
          + " replay rather than a conflict. So nothing here refuses a repeat, and a client that"
          + " wants a repeat refused has no field on this contract to ask for it with";

  static final ProviderKeyMechanisms MECHANISMS =
      new ProviderKeyMechanisms(
          WorldlineApi.WORLDLINE,
          List.of(
              new DeclaredKey(
                  IDEMPOTENCE_KEY,
                  KeyRole.TRUE_IDEMPOTENCY_KEY,
                  KeyCarriage.HEADER,
                  SCOPED_BY_THE_KEY_THE_CALLER_CHOSE,
                  CLAIMED_BEFORE_THE_OPERATION_RUNS),
              new DeclaredKey(
                  PAYMENT_ID,
                  KeyRole.CORRELATION_KEY,
                  KeyCarriage.PATH_SEGMENT,
                  SCOPED_BY_THE_PAYMENT,
                  THE_PAYMENT_IS_NEVER_CLAIMED)),
          Map.of(KeyRole.DUPLICATE_DETECTION_KEY, NO_DUPLICATE_DETECTION_KEY),
          answered -> Redaction.redact(answered, WorldlineFields.SENSITIVE_RESPONSE_FIELDS));

  private WorldlineKeyMechanisms() {}
}
