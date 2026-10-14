package dev.marcinromanowski.pspsim.provider.grow;

import dev.marcinromanowski.pspsim.kernel.credentials.Redaction;
import dev.marcinromanowski.pspsim.kernel.idempotency.KeyRole;
import dev.marcinromanowski.pspsim.kernel.spi.DeclaredKey;
import dev.marcinromanowski.pspsim.kernel.spi.KeyCarriage;
import dev.marcinromanowski.pspsim.kernel.spi.KeyClaim;
import dev.marcinromanowski.pspsim.kernel.spi.ProviderKeyMechanisms;
import java.util.List;
import java.util.Map;

final class GrowKeyMechanisms {

  static final String UNIQUE_IDENTIFIER = GrowFields.TRANSACTION_UNIQUE_IDENTIFIER;

  static final String GROUP_IDENTIFIER = GrowFields.TRANSACTION_GROUP_IDENTIFIER;

  static final String TRANSACTION_ID = GrowFields.TRANSACTION_ID;

  private static final String SCOPED_BY_EVERY_SUBMISSION =
      "every stored-card charge this business has submitted, successful or failed: provider grow"
          + " rejects a repeat outright regardless of the outcome of the first request, so a repeat"
          + " is answered with a duplicate error and never with the answer the first request got";

  private static final String SCOPED_BY_A_SUCCESSFUL_PAYMENT =
      "only the successful payments of this business: provider grow rejects the request when a"
          + " successful payment already carries the value, so a repeat after a failed payment is"
          + " admitted where a repeat of "
          + UNIQUE_IDENTIFIER
          + " is not. It is a second duplicate check scoped differently, and not correlation data";

  private static final String SCOPED_BY_THE_TRANSACTION =
      "the transaction it names, looked up together with the transactionToken provider grow drew"
          + " for it: a lookup under it neither replays a stored answer nor is refused as a"
          + " duplicate, it answers the state the transaction is in now";

  private static final KeyClaim GROW_CLAIMS_NOTHING_IN_ADVANCE =
      new KeyClaim.NeverHeldByARequest(
          "provider grow publishes no claim taken before an operation runs: both of its duplicate"
              + " checks and its transaction lookup are compared against the charges already"
              + " stored, so a repeat is refused for what an earlier request finished rather than"
              + " for what one is still doing, and no request under any of these keys can find the"
              + " value held by a request that has not answered");

  private static final String NO_IDEMPOTENCY_KEY =
      "provider grow publishes no idempotency header and no key that replays an answer anywhere in"
          + " this contract. Both keys it compares repeats against are duplicate checks whose"
          + " repeat is refused, so a client that retries a charge after a lost response gets a"
          + " duplicate error and must look the transaction up rather than expect the original"
          + " response back";

  static final ProviderKeyMechanisms MECHANISMS =
      new ProviderKeyMechanisms(
          GrowLightApi.GROW,
          List.of(
              new DeclaredKey(
                  UNIQUE_IDENTIFIER,
                  KeyRole.DUPLICATE_DETECTION_KEY,
                  KeyCarriage.REQUEST_FIELD,
                  SCOPED_BY_EVERY_SUBMISSION,
                  GROW_CLAIMS_NOTHING_IN_ADVANCE),
              new DeclaredKey(
                  GROUP_IDENTIFIER,
                  KeyRole.DUPLICATE_DETECTION_KEY,
                  KeyCarriage.REQUEST_FIELD,
                  SCOPED_BY_A_SUCCESSFUL_PAYMENT,
                  GROW_CLAIMS_NOTHING_IN_ADVANCE),
              new DeclaredKey(
                  TRANSACTION_ID,
                  KeyRole.CORRELATION_KEY,
                  KeyCarriage.REQUEST_FIELD,
                  SCOPED_BY_THE_TRANSACTION,
                  GROW_CLAIMS_NOTHING_IN_ADVANCE)),
          Map.of(KeyRole.TRUE_IDEMPOTENCY_KEY, NO_IDEMPOTENCY_KEY),
          answered -> Redaction.redact(answered, GrowFields.SENSITIVE_RESPONSE_FIELDS));

  private GrowKeyMechanisms() {}
}
