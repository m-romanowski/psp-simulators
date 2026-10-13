package dev.marcinromanowski.pspsim.kernel.fixture;

import dev.marcinromanowski.pspsim.kernel.ObjectOrigin;
import dev.marcinromanowski.pspsim.kernel.ProviderId;
import dev.marcinromanowski.pspsim.kernel.testrun.TestRunId;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.SequencedMap;
import org.jspecify.annotations.Nullable;

public record NewPaymentMethodFixture(
    ProviderId provider,
    TestRunId testRunId,
    String merchantId,
    String ownerId,
    PaymentMethodKind type,
    String fixtureAlias,
    @Nullable FixtureCard card,
    Map<String, String> providerIds,
    @Nullable String defaultScenario,
    ObjectOrigin askedBy) {

  public static final String A_FIXTURE_IS_SEEDED_BY_A_CONTROL_SURFACE =
      "a payment-method fixture is seeded by the control API or by a tester on the screen, and an"
          + " object a provider stores while answering a client is stored by that exchange and"
          + " never by a seed, so askedBy names the surface that asked for this one";

  public static final String A_CARD_IS_DESCRIBED_BY_THE_TYPE_THAT_CARRIES_ONE =
      "card is required by type "
          + "card"
          + " and is carried by no other type, because a wallet or a PSP token describes no card"
          + " this simulator was ever told about";

  public NewPaymentMethodFixture {
    if (askedBy != ObjectOrigin.CONTROL_API && askedBy != ObjectOrigin.UI) {
      throw new IllegalArgumentException(A_FIXTURE_IS_SEEDED_BY_A_CONTROL_SURFACE);
    }
    providerIds = theIdsInTheOrderTheSeedDeclaredThem(providerIds);
    if (type.describesACard() == (card == null)) {
      throw new IllegalArgumentException(A_CARD_IS_DESCRIBED_BY_THE_TYPE_THAT_CARRIES_ONE);
    }
  }

  private static SequencedMap<String, String> theIdsInTheOrderTheSeedDeclaredThem(
      Map<String, String> declared) {
    LinkedHashMap<String, String> inTheOrderTheSeedDeclaredThem = new LinkedHashMap<>(declared);
    return Collections.unmodifiableSequencedMap(inTheOrderTheSeedDeclaredThem);
  }
}
