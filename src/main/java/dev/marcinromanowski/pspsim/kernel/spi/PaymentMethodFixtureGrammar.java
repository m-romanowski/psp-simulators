package dev.marcinromanowski.pspsim.kernel.spi;

import dev.marcinromanowski.pspsim.kernel.ObjectOrigin;
import dev.marcinromanowski.pspsim.kernel.ProviderId;
import dev.marcinromanowski.pspsim.kernel.fixture.FixtureCard;
import dev.marcinromanowski.pspsim.kernel.fixture.NewPaymentMethodFixture;
import dev.marcinromanowski.pspsim.kernel.fixture.PaymentMethodKind;
import dev.marcinromanowski.pspsim.kernel.testrun.TestRunId;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.SequencedMap;
import org.jspecify.annotations.Nullable;

public final class PaymentMethodFixtureGrammar {

  public static final int LONGEST_IDENTITY = 128;

  public static final int LONGEST_PROVIDER_NATIVE_ID = 256;

  public static final String ALIAS = "alias";

  private PaymentMethodFixtureGrammar() {}

  public static NewPaymentMethodFixture fixtureOf(
      ProviderId provider,
      TestRunId testRunId,
      DeclaredPaymentMethodFixture declared,
      ObjectOrigin askedBy) {
    PaymentMethodKind type = typeOf(declared.type());
    return new NewPaymentMethodFixture(
        provider,
        testRunId,
        identityOf("merchantId", declared.merchantId()),
        identityOf("ownerId", declared.ownerId()),
        type,
        identityOf(ALIAS, declared.alias()),
        cardOf(type, declared.card()),
        providerIdsOf(declared.providerIds()),
        namedOrNothing(declared.defaultScenario()),
        askedBy);
  }

  private static PaymentMethodKind typeOf(@Nullable String declared) {
    if (declared == null || declared.isBlank()) {
      throw new IllegalArgumentException(
          "type is required, and must be one of " + PaymentMethodKind.thePublishedNames());
    }
    Optional<PaymentMethodKind> named = PaymentMethodKind.of(declared.strip());
    if (named.isEmpty()) {
      throw new IllegalArgumentException(
          "type must be one of " + PaymentMethodKind.thePublishedNames());
    }
    return named.get();
  }

  private static @Nullable FixtureCard cardOf(PaymentMethodKind type, @Nullable DeclaredCard card) {
    if (card == null) {
      if (type.describesACard()) {
        throw new IllegalArgumentException(
            NewPaymentMethodFixture.A_CARD_IS_DESCRIBED_BY_THE_TYPE_THAT_CARRIES_ONE);
      }
      return null;
    }
    if (!type.describesACard()) {
      throw new IllegalArgumentException(
          NewPaymentMethodFixture.A_CARD_IS_DESCRIBED_BY_THE_TYPE_THAT_CARRIES_ONE);
    }
    return new FixtureCard(
        required("card.brand", card.brand()),
        required("card.last4", card.last4()),
        requiredNumber("card.expMonth", card.expMonth()),
        requiredNumber("card.expYear", card.expYear()),
        required("card.funding", card.funding()));
  }

  private static SequencedMap<String, String> providerIdsOf(
      @Nullable Map<String, String> declared) {
    if (declared == null) {
      return Collections.unmodifiableSequencedMap(new LinkedHashMap<>());
    }
    LinkedHashMap<String, String> named = new LinkedHashMap<>();
    for (Map.Entry<String, String> entry : declared.entrySet()) {
      String value = entry.getValue();
      if (value == null || value.isBlank()) {
        throw new IllegalArgumentException(
            "providerIds."
                + entry.getKey()
                + " must name the provider-native id a test will drive this fixture with, or be"
                + " left out");
      }
      String id = value.strip();
      if (id.length() > LONGEST_PROVIDER_NATIVE_ID) {
        throw new IllegalArgumentException(
            "providerIds."
                + entry.getKey()
                + " must be at most "
                + LONGEST_PROVIDER_NATIVE_ID
                + " characters, because one fixture of a test run claims it and the claim is"
                + " kept in an index whose entries a longer value would not fit");
      }
      named.put(entry.getKey(), id);
    }
    return Collections.unmodifiableSequencedMap(named);
  }

  private static String identityOf(String property, @Nullable String declared) {
    String named = required(property, declared);
    if (named.length() > LONGEST_IDENTITY) {
      throw new IllegalArgumentException(
          property + " must be at most " + LONGEST_IDENTITY + " characters");
    }
    return named;
  }

  private static String required(String property, @Nullable String declared) {
    if (declared == null || declared.isBlank()) {
      throw new IllegalArgumentException(property + " is required");
    }
    return declared.strip();
  }

  private static int requiredNumber(String property, @Nullable Integer declared) {
    if (declared == null) {
      throw new IllegalArgumentException(property + " is required");
    }
    return declared;
  }

  private static @Nullable String namedOrNothing(@Nullable String declared) {
    if (declared == null || declared.isBlank()) {
      return null;
    }
    return declared.strip();
  }
}
