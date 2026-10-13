package dev.marcinromanowski.pspsim.provider.azericard;

import dev.marcinromanowski.pspsim.kernel.ObjectOrigin;
import dev.marcinromanowski.pspsim.kernel.ids.IdGenerator;
import dev.marcinromanowski.pspsim.kernel.testrun.TestRunId;
import java.time.Instant;
import java.util.Locale;
import org.jspecify.annotations.Nullable;

record AzericardStoredCard(
    TestRunId testRunId,
    String token,
    String terminalId,
    String merchantId,
    @Nullable String storedByIntRef,
    @Nullable String order,
    @Nullable String fixtureId,
    ObjectOrigin origin,
    Instant createdAt,
    @Nullable String extNetRef) {

  static final int TOKEN_LENGTH = 28;

  static final int EXT_NET_REF_LENGTH = 20;

  static String anExtNetRef(IdGenerator ids) {
    return ids.hex(EXT_NET_REF_LENGTH).toUpperCase(Locale.ROOT);
  }

  static String aToken(IdGenerator ids) {
    return ids.hex(TOKEN_LENGTH).toUpperCase(Locale.ROOT);
  }

  static final String A_CARD_IS_STORED_BY_A_PAYMENT_OR_SEEDED_BY_A_FIXTURE =
      "an Azericard stored card is either the card a payment stored, which names the INT_REF and"
          + " the ORDER that stored it, or the card a payment-method fixture seeded, which names"
          + " the fixture and no payment; a row that is both would report a payment that never"
          + " ran";

  static final String A_SEEDED_CARD_NAMES_THE_SURFACE_THAT_SEEDED_IT =
      "a card a payment stored was stored by that exchange, and a card a seed placed was placed by"
          + " the control API or by a tester on the screen, so the two sets of surfaces do not"
          + " overlap and an origin outside them would report a surface that stored nothing";

  AzericardStoredCard {
    boolean storedByAPayment = storedByIntRef != null && order != null && fixtureId == null;
    boolean seeded = storedByIntRef == null && order == null && fixtureId != null;
    if (storedByAPayment == seeded) {
      throw new IllegalArgumentException(A_CARD_IS_STORED_BY_A_PAYMENT_OR_SEEDED_BY_A_FIXTURE);
    }
    boolean placedByASurface = origin == ObjectOrigin.CONTROL_API || origin == ObjectOrigin.UI;
    if (seeded != placedByASurface) {
      throw new IllegalArgumentException(A_SEEDED_CARD_NAMES_THE_SURFACE_THAT_SEEDED_IT);
    }
  }

  static AzericardStoredCard storedBy(
      TestRunId testRunId,
      String token,
      String terminalId,
      String merchantId,
      String intRef,
      String order,
      Instant createdAt) {
    return storedBy(testRunId, token, terminalId, merchantId, intRef, order, createdAt, null);
  }

  static AzericardStoredCard storedBy(
      TestRunId testRunId,
      String token,
      String terminalId,
      String merchantId,
      String intRef,
      String order,
      Instant createdAt,
      @Nullable String extNetRef) {
    return new AzericardStoredCard(
        testRunId,
        token,
        terminalId,
        merchantId,
        intRef,
        order,
        null,
        ObjectOrigin.PROVIDER_API,
        createdAt,
        extNetRef);
  }

  static AzericardStoredCard seededBy(
      TestRunId testRunId,
      String token,
      String terminalId,
      String merchantId,
      String fixtureId,
      ObjectOrigin seededBy,
      Instant createdAt) {
    return new AzericardStoredCard(
        testRunId, token, terminalId, merchantId, null, null, fixtureId, seededBy, createdAt, null);
  }
}
