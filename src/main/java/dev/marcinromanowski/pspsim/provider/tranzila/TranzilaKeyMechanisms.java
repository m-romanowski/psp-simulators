package dev.marcinromanowski.pspsim.provider.tranzila;

import dev.marcinromanowski.pspsim.kernel.idempotency.KeyRole;
import dev.marcinromanowski.pspsim.kernel.spi.DeclaredKey;
import dev.marcinromanowski.pspsim.kernel.spi.KeyCarriage;
import dev.marcinromanowski.pspsim.kernel.spi.KeyClaim;
import dev.marcinromanowski.pspsim.kernel.spi.ProviderKeyMechanisms;
import java.util.List;
import java.util.Map;

final class TranzilaKeyMechanisms {

  static final String SCOPED_BY_THE_TERMINAL_AND_A_DAY =
      "the DCdisable value a create carries in user_defined_fields, which provider tranzila"
          + " compares \"up to 24 hours back\" and \"at the parent terminal level\". A repeat"
          + " within that window of an approved original runs nothing and is answered error_code"
          + " 0 with \"This payment has already been made, please contact the business\" and the"
          + " original's transaction_result, so a client that retried after a lost answer reads"
          + " the transaction it already made; \"If the original transaction failed, DCdisable"
          + " will not prevent a retry\", and such a repeat is let through. The value is at most"
          + " 254 characters, and a longer one is refused 22103";

  static final String SCOPED_BY_THE_ORIGINAL =
      "the original transaction a force, a reversal, a credit or a cancel names in"
          + " reference_txn_id: it addresses a transaction that already exists, so a second call"
          + " naming it is answered from what that transaction holds now and is neither replayed"
          + " nor refused as a duplicate";

  static final KeyClaim NOTHING_IS_CLAIMED_IN_ADVANCE =
      new KeyClaim.NeverHeldByARequest(
          "provider tranzila publishes no claim taken before a transaction is processed: the"
              + " DCdisable check compares against transactions already approved, and"
              + " reference_txn_id addresses one that already exists, so no request under either"
              + " key can find it held by a request that has not answered");

  static final String NO_IDEMPOTENCY_KEY =
      "provider tranzila publishes no idempotency key or header. Its one duplicate protection is"
          + " DCdisable, whose repeat is answered with a duplicate message rather than with the"
          + " original answer replayed unchanged";

  static final ProviderKeyMechanisms MECHANISMS =
      new ProviderKeyMechanisms(
          TranzilaApi.TRANZILA,
          List.of(
              new DeclaredKey(
                  TranzilaFields.DC_DISABLE,
                  KeyRole.DUPLICATE_DETECTION_KEY,
                  KeyCarriage.REQUEST_FIELD,
                  SCOPED_BY_THE_TERMINAL_AND_A_DAY,
                  NOTHING_IS_CLAIMED_IN_ADVANCE),
              new DeclaredKey(
                  TranzilaFields.REFERENCE_TXN_ID,
                  KeyRole.CORRELATION_KEY,
                  KeyCarriage.REQUEST_FIELD,
                  SCOPED_BY_THE_ORIGINAL,
                  NOTHING_IS_CLAIMED_IN_ADVANCE)),
          Map.of(KeyRole.TRUE_IDEMPOTENCY_KEY, NO_IDEMPOTENCY_KEY));

  private TranzilaKeyMechanisms() {}
}
