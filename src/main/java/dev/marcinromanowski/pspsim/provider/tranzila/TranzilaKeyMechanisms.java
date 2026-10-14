package dev.marcinromanowski.pspsim.provider.tranzila;

import dev.marcinromanowski.pspsim.kernel.credentials.Redaction;
import dev.marcinromanowski.pspsim.kernel.idempotency.KeyRole;
import dev.marcinromanowski.pspsim.kernel.spi.DeclaredKey;
import dev.marcinromanowski.pspsim.kernel.spi.KeyCarriage;
import dev.marcinromanowski.pspsim.kernel.spi.KeyClaim;
import dev.marcinromanowski.pspsim.kernel.spi.ProviderKeyMechanisms;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

final class TranzilaKeyMechanisms {

  static final String SCOPED_BY_THE_TERMINAL_AND_A_DAY =
      "the DCdisable value a create carries in user_defined_fields, kept as the client sent it"
          + " under a key name that carries the txn_type and verify_mode it is compared within,"
          + " which provider tranzila"
          + " compares \"up to 24 hours back\" and \"at the parent terminal level\", against"
          + " transactions of the same txn_type and verify_mode. A repeat within that window of an"
          + " approved original runs nothing and is answered error_code"
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

  static final KeyClaim ONE_REQUEST_AT_A_TIME_UNDER_A_DC_DISABLE =
      new KeyClaim.ClaimedBeforeTheOperationRuns(
          "this simulator holds a DCdisable of one terminal, transaction type and verify mode"
              + " while the request carrying it runs, so a second request carrying it waits rather"
              + " than charging the card twice, and is then answered as a duplicate of the first"
              + " if that one was approved, or let through if it was not. Tranzila publishes no"
              + " answer for a request meeting a DCdisable in flight, so none is refused for it");

  static final KeyClaim THE_ORIGINAL_IS_NEVER_CLAIMED =
      new KeyClaim.NeverHeldByARequest(
          "provider tranzila claims nothing when a call names its original in reference_txn_id:"
              + " that transaction already exists, and a second follow-on of it is refused on what"
              + " it holds now, not because another request is still holding it");

  static final String SCOPED_BY_THE_CHALLENGE =
      "the track_id a create with activate_3ds Y answers beside challengeUrl, which"
          + " 3ds/complete names to finish that challenge once the card holder answered it. A"
          + " challenge is completed once: a second complete naming it runs nothing and is answered"
          + " error_code 900, the code the complete route publishes for a failed 3-D Secure"
          + " validation";

  static final KeyClaim ONE_COMPLETE_AT_A_TIME_UNDER_A_TRACK_ID =
      new KeyClaim.ClaimedBeforeTheOperationRuns(
          "this simulator holds a challenge while the complete naming its track_id runs, so a"
              + " second complete naming it waits rather than charging the card twice, and is"
              + " then answered error_code 900 because the challenge is completed");

  static final String NO_IDEMPOTENCY_KEY =
      "provider tranzila publishes no idempotency key or header. Its one duplicate protection is"
          + " DCdisable, whose repeat is answered with a duplicate message rather than with the"
          + " original answer replayed unchanged";

  static final List<String> THE_DC_DISABLE_KEY_NAMES =
      List.of(
          TranzilaDcDisableKey.keyNameOf(TranzilaTxnType.DEBIT, null),
          TranzilaDcDisableKey.keyNameOf(TranzilaTxnType.VERIFY, TranzilaTxnType.J2),
          TranzilaDcDisableKey.keyNameOf(TranzilaTxnType.VERIFY, TranzilaTxnType.J5),
          TranzilaDcDisableKey.keyNameOf(TranzilaTxnType.FORCE, null),
          TranzilaDcDisableKey.keyNameOf(TranzilaTxnType.REVERSAL, null),
          TranzilaDcDisableKey.keyNameOf(TranzilaTxnType.CREDIT, null),
          TranzilaDcDisableKey.keyNameOf(TranzilaTxnType.CANCEL, null));

  static final ProviderKeyMechanisms MECHANISMS =
      new ProviderKeyMechanisms(
          TranzilaApi.TRANZILA,
          theKeys(),
          Map.of(KeyRole.TRUE_IDEMPOTENCY_KEY, NO_IDEMPOTENCY_KEY),
          answered -> Redaction.redact(answered, TranzilaFields.SENSITIVE_RESPONSE_FIELDS));

  private static List<DeclaredKey> theKeys() {
    List<DeclaredKey> keys = new ArrayList<>();
    THE_DC_DISABLE_KEY_NAMES.forEach(
        name ->
            keys.add(
                new DeclaredKey(
                    name,
                    KeyRole.DUPLICATE_DETECTION_KEY,
                    KeyCarriage.REQUEST_FIELD,
                    SCOPED_BY_THE_TERMINAL_AND_A_DAY,
                    ONE_REQUEST_AT_A_TIME_UNDER_A_DC_DISABLE)));
    keys.add(
        new DeclaredKey(
            TranzilaFields.REFERENCE_TXN_ID,
            KeyRole.CORRELATION_KEY,
            KeyCarriage.REQUEST_FIELD,
            SCOPED_BY_THE_ORIGINAL,
            THE_ORIGINAL_IS_NEVER_CLAIMED));
    keys.add(
        new DeclaredKey(
            TranzilaThreeDSecureAnswers.TRACK_ID,
            KeyRole.DUPLICATE_DETECTION_KEY,
            KeyCarriage.REQUEST_FIELD,
            SCOPED_BY_THE_CHALLENGE,
            ONE_COMPLETE_AT_A_TIME_UNDER_A_TRACK_ID));
    return List.copyOf(keys);
  }

  private TranzilaKeyMechanisms() {}
}
