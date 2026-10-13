package dev.marcinromanowski.pspsim.kernel.spi;

import dev.marcinromanowski.pspsim.kernel.ProviderId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeSet;

public record ProviderPersonas(
    ProviderId provider,
    List<DeclaredPersona> personas,
    Map<PersonaIntent, String> intentsItPublishesNoneFor) {

  public static final String A_PROVIDER_ACCOUNTS_FOR_EVERY_INTENT =
      "accounts for neither publishing nor declining a persona for intent ";

  public static final String AN_INTENT_IS_PUBLISHED_OR_DECLINED_AND_NOT_BOTH =
      "publishes a persona for intent %s and also says it has none, and a screen would show the"
          + " same intent as offered and as uncovered";

  public static final String ONE_INTENT_ONE_PERSONA =
      "publishes two personas for intent %s, and a tester asking for that intent would be handed"
          + " whichever the module happened to list first";

  public static final String TWO_PERSONAS_UNDER_ONE_NAME =
      "publishes two personas named %s, and a seed naming it would be answered by whichever the"
          + " module happened to list first";

  public static final String A_DECLINED_INTENT_SAYS_WHY =
      "must say, in its own words, what its contract has instead of a persona for intent ";

  public ProviderPersonas {
    Map<PersonaIntent, DeclaredPersona> byIntent = new LinkedHashMap<>();
    Map<String, DeclaredPersona> byName = new LinkedHashMap<>();
    for (DeclaredPersona persona : personas) {
      if (byIntent.put(persona.intent(), persona) != null) {
        throw new IllegalArgumentException(
            refusing(provider, ONE_INTENT_ONE_PERSONA.formatted(persona.intent().publishedName())));
      }
      if (byName.put(persona.name(), persona) != null) {
        throw new IllegalArgumentException(
            refusing(provider, TWO_PERSONAS_UNDER_ONE_NAME.formatted(persona.name())));
      }
    }
    for (Map.Entry<PersonaIntent, String> declined : intentsItPublishesNoneFor.entrySet()) {
      if (byIntent.containsKey(declined.getKey())) {
        throw new IllegalArgumentException(
            refusing(
                provider,
                AN_INTENT_IS_PUBLISHED_OR_DECLINED_AND_NOT_BOTH.formatted(
                    declined.getKey().publishedName())));
      }
      if (declined.getValue().isBlank()) {
        throw new IllegalArgumentException(
            refusing(provider, A_DECLINED_INTENT_SAYS_WHY + declined.getKey().publishedName()));
      }
    }
    for (PersonaIntent intent : PersonaIntent.values()) {
      if (!byIntent.containsKey(intent) && !intentsItPublishesNoneFor.containsKey(intent)) {
        throw new IllegalArgumentException(
            refusing(provider, A_PROVIDER_ACCOUNTS_FOR_EVERY_INTENT + intent.publishedName()));
      }
    }
    personas = List.copyOf(personas);
    intentsItPublishesNoneFor = Map.copyOf(intentsItPublishesNoneFor);
  }

  private static String refusing(ProviderId provider, String what) {
    return "provider " + provider.value() + " " + what;
  }

  public static ProviderPersonas noneOfThem(ProviderId provider, String whyItPublishesNone) {
    Map<PersonaIntent, String> declined = new LinkedHashMap<>();
    for (PersonaIntent intent : PersonaIntent.values()) {
      declined.put(intent, whyItPublishesNone);
    }
    return new ProviderPersonas(provider, List.of(), declined);
  }

  public Optional<DeclaredPersona> named(String name) {
    for (DeclaredPersona persona : personas) {
      if (persona.name().equals(name)) {
        return Optional.of(persona);
      }
    }
    return Optional.empty();
  }

  public List<String> theNamesItPublishes() {
    List<String> names = new ArrayList<>();
    for (DeclaredPersona persona : personas) {
      names.add(persona.name());
    }
    return List.copyOf(names);
  }

  public String theRefusalOfAnUnknownPersona() {
    if (personas.isEmpty()) {
      return "provider "
          + provider.value()
          + " publishes no persona at all, so no name could be seeded for it";
    }
    return "persona must name one this provider publishes, which are "
        + new TreeSet<>(theNamesItPublishes());
  }
}
