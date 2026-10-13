package dev.marcinromanowski.pspsim.kernel.spi;

import dev.marcinromanowski.pspsim.kernel.fixture.FixtureCard;
import dev.marcinromanowski.pspsim.kernel.fixture.PaymentMethodKind;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.SequencedMap;
import java.util.TreeSet;
import org.jspecify.annotations.Nullable;

public record PersonaFixture(
    String alias,
    String ownerId,
    PaymentMethodKind type,
    @Nullable FixtureCard card,
    List<String> theProviderNativeKeysItCarries,
    String howThoseAreMinted,
    Minting minting,
    @Nullable Integer triggersTheRuleAt,
    Map<String, String> theIdsItPublishes) {

  @FunctionalInterface
  public interface Minting {

    Map<String, String> theIdsItMints();
  }

  public static final int LONGEST_ALIAS = 128;

  public static final String A_CARD_IS_DESCRIBED_BY_THE_TYPE_THAT_CARRIES_ONE =
      "a persona instrument of type "
          + PaymentMethodKind.CARD.publishedName()
          + " describes a card and an instrument of any other type describes none, because a"
          + " wallet or a PSP token describes no card this simulator was ever told about";

  public static final String AN_INSTRUMENT_SAYS_WHICH_MINTING_PRODUCED_ITS_IDS =
      "a persona instrument that carries a provider-native id must say, in the provider's own"
          + " words, which minting of that provider produces it, because the whole value of a"
          + " persona is that the id it hands over is one the provider's own endpoints answer to,"
          + " and a tester cannot check that claim against a sentence no one wrote";

  public static final String AN_INSTRUMENT_THAT_CARRIES_NO_ID_MINTS_NONE =
      "a persona instrument that declares no provider-native id must say so by declaring none,"
          + " and one that declares a key twice would draw one value under two names";

  public static final String A_PUBLISHED_VALUE_BELONGS_TO_A_KEY_IT_CARRIES =
      "a persona instrument declares a published value under a provider-native key it does not"
          + " carry, so the declaration names no id any seeding of it would ever compare";

  public static final String A_KEY_IS_CARRIED_WITH_A_VALUE_UNDER_IT_OR_NOT_AT_ALL =
      "a persona instrument names a provider-native key with nothing under it, and an id a"
          + " tester is handed as the provider's own must be a value the provider's endpoints"
          + " answer to rather than an absence a later reader would file as one: ";

  public static final String A_PUBLISHED_VALUE_IS_THE_VALUE_THE_MINTING_PRODUCES =
      "a persona instrument declares a published value a seeding of it does not produce, and a"
          + " plan that read the declaration would foresee a refusal over an id the creation never"
          + " uses";

  public PersonaFixture(
      String alias,
      String ownerId,
      PaymentMethodKind type,
      @Nullable FixtureCard card,
      List<String> theProviderNativeKeysItCarries,
      String howThoseAreMinted,
      Minting minting,
      @Nullable Integer triggersTheRuleAt) {
    this(
        alias,
        ownerId,
        type,
        card,
        theProviderNativeKeysItCarries,
        howThoseAreMinted,
        minting,
        triggersTheRuleAt,
        Map.of());
  }

  public PersonaFixture {
    alias = named("alias", alias);
    ownerId = named("ownerId", ownerId);
    if (type.describesACard() == (card == null)) {
      throw new IllegalArgumentException(A_CARD_IS_DESCRIBED_BY_THE_TYPE_THAT_CARRIES_ONE);
    }
    if (new TreeSet<>(theProviderNativeKeysItCarries).size()
        != theProviderNativeKeysItCarries.size()) {
      throw new IllegalArgumentException(AN_INSTRUMENT_THAT_CARRIES_NO_ID_MINTS_NONE);
    }
    if (!theProviderNativeKeysItCarries.isEmpty() && howThoseAreMinted.isBlank()) {
      throw new IllegalArgumentException(AN_INSTRUMENT_SAYS_WHICH_MINTING_PRODUCED_ITS_IDS);
    }
    if (triggersTheRuleAt != null && triggersTheRuleAt < 0) {
      throw new IllegalArgumentException(
          "triggersTheRuleAt names the place of a rule this persona writes and counts from zero,"
              + " so a negative place names no rule this persona could ever write");
    }
    if (!theProviderNativeKeysItCarries.containsAll(theIdsItPublishes.keySet())) {
      throw new IllegalArgumentException(A_PUBLISHED_VALUE_BELONGS_TO_A_KEY_IT_CARRIES);
    }
    theProviderNativeKeysItCarries = List.copyOf(theProviderNativeKeysItCarries);
    theIdsItPublishes =
        theIdsInTheOrderTheKeysAreCarried(theProviderNativeKeysItCarries, theIdsItPublishes);
  }

  private static String named(String property, String value) {
    String stripped = value.strip();
    if (stripped.isEmpty()) {
      throw new IllegalArgumentException(
          property + " of a persona instrument must name what the tester will see it filed under");
    }
    if (stripped.length() > LONGEST_ALIAS) {
      throw new IllegalArgumentException(
          property + " of a persona instrument must be at most " + LONGEST_ALIAS + " characters");
    }
    return stripped;
  }

  private static SequencedMap<String, String> theIdsInTheOrderTheKeysAreCarried(
      List<String> theKeysItCarries, Map<String, String> ids) {
    LinkedHashMap<String, String> inTheOrderTheKeysAreCarried = new LinkedHashMap<>();
    for (String key : theKeysItCarries) {
      if (!ids.containsKey(key)) {
        continue;
      }
      String id = ids.get(key);
      if (id == null) {
        throw new IllegalArgumentException(
            A_KEY_IS_CARRIED_WITH_A_VALUE_UNDER_IT_OR_NOT_AT_ALL + key);
      }
      inTheOrderTheKeysAreCarried.put(key, id);
    }
    return Collections.unmodifiableSequencedMap(inTheOrderTheKeysAreCarried);
  }

  public SequencedMap<String, String> mint() {
    SequencedMap<String, String> minted =
        theIdsInTheOrderTheKeysAreCarried(theProviderNativeKeysItCarries, minting.theIdsItMints());
    if (!new TreeSet<>(minted.keySet()).equals(new TreeSet<>(theProviderNativeKeysItCarries))) {
      throw new IllegalStateException(
          "persona instrument "
              + alias
              + " publishes the provider-native keys "
              + new TreeSet<>(theProviderNativeKeysItCarries)
              + " and minted "
              + new TreeSet<>(minted.keySet())
              + ", so the screen that showed a tester what it would create described something"
              + " other than what it created");
    }
    for (Map.Entry<String, String> published : theIdsItPublishes.entrySet()) {
      if (!published.getValue().equals(minted.get(published.getKey()))) {
        throw new IllegalStateException(
            "persona instrument "
                + alias
                + " declares the published "
                + published.getKey()
                + " "
                + published.getValue()
                + " and minted "
                + minted.get(published.getKey())
                + ". "
                + A_PUBLISHED_VALUE_IS_THE_VALUE_THE_MINTING_PRODUCES);
      }
    }
    return minted;
  }
}
