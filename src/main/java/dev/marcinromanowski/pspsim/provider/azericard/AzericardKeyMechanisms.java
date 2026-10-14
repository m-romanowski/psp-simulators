package dev.marcinromanowski.pspsim.provider.azericard;

import dev.marcinromanowski.pspsim.kernel.credentials.Redaction;
import dev.marcinromanowski.pspsim.kernel.idempotency.KeyRole;
import dev.marcinromanowski.pspsim.kernel.spi.DeclaredKey;
import dev.marcinromanowski.pspsim.kernel.spi.KeyCarriage;
import dev.marcinromanowski.pspsim.kernel.spi.KeyClaim;
import dev.marcinromanowski.pspsim.kernel.spi.ProviderKeyMechanisms;
import java.util.List;
import java.util.Map;

final class AzericardKeyMechanisms {

  static final String ORDER = AzericardFields.ORDER;

  static final String RRN = AzericardFields.RRN;

  static final String INT_REF = AzericardFields.INT_REF;

  private static final String SCOPED_BY_TERMINAL_AND_DAY =
      "the last "
          + AzericardOrderTrace.TRACE_LENGTH
          + " digits of ORDER, which are the system trace audit number and must be unique within a"
          + " day for the terminal the message is sent to. A message whose trace collides is"
          + " answered ACTION=1, duplicate transaction detected, and never with the answer the"
          + " first message got. A value is recorded here as the UTC day of the message followed"
          + " by those digits, so a trace reused on another day is a first request again";

  private static final String SCOPED_BY_THE_AUTHORIZATION =
      "the payment the authorization created: a completion, a reversal and a refund carry the RRN"
          + " the authorization answered with, and addressing it neither replays a stored answer"
          + " nor is refused as a duplicate";

  private static final String SCOPED_BY_THE_GATEWAY_REFERENCE =
      "the gateway's own internal reference for the payment, sent back beside RRN on every"
          + " completion and reversal of it";

  private static final KeyClaim AZERICARD_CLAIMS_NOTHING_IN_ADVANCE =
      new KeyClaim.NeverHeldByARequest(
          "provider azericard publishes no claim taken before a message is processed: the trace"
              + " audit number is compared against the messages already accepted for the terminal"
              + " that day, and RRN and INT_REF address a payment that already exists, so no"
              + " request under any of these keys can find the value held by a message that has"
              + " not answered");

  private static final String NO_IDEMPOTENCY_KEY =
      "provider azericard publishes no idempotency header and no key that replays an answer"
          + " anywhere in this contract. ORDER is a duplicate check whose repeat is refused, so a"
          + " client that retries an authorization after a lost response is answered ACTION=1"
          + " rather than the original authorization, and has to send TRTYPE=90 to find out what"
          + " happened";

  static final ProviderKeyMechanisms MECHANISMS =
      new ProviderKeyMechanisms(
          AzericardGateway.AZERICARD,
          List.of(
              new DeclaredKey(
                  ORDER,
                  KeyRole.DUPLICATE_DETECTION_KEY,
                  KeyCarriage.REQUEST_FIELD,
                  SCOPED_BY_TERMINAL_AND_DAY,
                  AZERICARD_CLAIMS_NOTHING_IN_ADVANCE),
              new DeclaredKey(
                  RRN,
                  KeyRole.CORRELATION_KEY,
                  KeyCarriage.REQUEST_FIELD,
                  SCOPED_BY_THE_AUTHORIZATION,
                  AZERICARD_CLAIMS_NOTHING_IN_ADVANCE),
              new DeclaredKey(
                  INT_REF,
                  KeyRole.CORRELATION_KEY,
                  KeyCarriage.REQUEST_FIELD,
                  SCOPED_BY_THE_GATEWAY_REFERENCE,
                  AZERICARD_CLAIMS_NOTHING_IN_ADVANCE)),
          Map.of(KeyRole.TRUE_IDEMPOTENCY_KEY, NO_IDEMPOTENCY_KEY),
          answered -> Redaction.redact(answered, AzericardFields.SENSITIVE_RESPONSE_FIELDS));

  private AzericardKeyMechanisms() {}
}
