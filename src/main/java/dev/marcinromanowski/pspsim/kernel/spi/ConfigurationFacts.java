package dev.marcinromanowski.pspsim.kernel.spi;

import dev.marcinromanowski.pspsim.kernel.clock.DeclaredClock;
import dev.marcinromanowski.pspsim.kernel.credentials.Redaction;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.jspecify.annotations.Nullable;

public final class ConfigurationFacts {

  private ConfigurationFacts() {}

  public static Map<String, List<String>> of(
      ProviderAccountFields fields, DeclaredConfiguration declared) {
    LinkedHashMap<String, List<String>> facts = new LinkedHashMap<>();
    clockOf(declared.clock(), facts);
    accountsOf(fields, declared.accounts(), facts);
    webhooksOf(declared.webhooks(), facts);
    routeAliasesOf(declared.routeAliases(), facts);
    settingsOf(declared.settings(), facts);
    return Collections.unmodifiableSequencedMap(facts);
  }

  private static void settingsOf(
      @Nullable Map<String, String> settings, Map<String, List<String>> facts) {
    if (settings == null) {
      return;
    }
    for (Map.Entry<String, String> setting : settings.entrySet()) {
      putIfNamed(
          facts,
          ProviderConfigurationGrammar.settingProperty(setting.getKey()),
          setting.getValue());
    }
  }

  private static void clockOf(@Nullable DeclaredClock clock, Map<String, List<String>> facts) {
    if (clock == null) {
      return;
    }
    String fixedNow = clock.fixedNow();
    facts.put("clock.fixedNow", List.of(fixedNow == null ? "released" : fixedNow));
  }

  private static void accountsOf(
      ProviderAccountFields fields,
      @Nullable List<DeclaredAccount> accounts,
      Map<String, List<String>> facts) {
    if (accounts == null) {
      return;
    }
    for (int i = 0; i < accounts.size(); i++) {
      DeclaredAccount account = accounts.get(i);
      if (account == null) {
        continue;
      }
      String prefix = "accounts[" + i + "]";
      putIfNamed(facts, prefix + ".accountId", account.accountId());
      Map<String, String> configuration = account.configuration();
      if (configuration == null) {
        continue;
      }
      Map<String, List<String>> disclosed =
          Redaction.redact(
              multiValued(configuration),
              everythingButTheDeclaredPlainFields(fields, configuration.keySet()));
      for (Map.Entry<String, List<String>> field : disclosed.entrySet()) {
        facts.put(prefix + ".configuration." + field.getKey(), List.copyOf(field.getValue()));
      }
    }
  }

  private static void webhooksOf(
      @Nullable List<DeclaredWebhookTarget> webhooks, Map<String, List<String>> facts) {
    if (webhooks == null) {
      return;
    }
    for (int i = 0; i < webhooks.size(); i++) {
      DeclaredWebhookTarget target = webhooks.get(i);
      if (target == null) {
        continue;
      }
      putIfNamed(facts, "webhooks[" + i + "].accountId", target.accountId());
      putIfNamed(facts, "webhooks[" + i + "].url", target.url());
    }
  }

  private static void routeAliasesOf(
      @Nullable List<DeclaredRouteAlias> aliases, Map<String, List<String>> facts) {
    if (aliases == null) {
      return;
    }
    for (int i = 0; i < aliases.size(); i++) {
      DeclaredRouteAlias alias = aliases.get(i);
      if (alias == null) {
        continue;
      }
      putIfNamed(facts, "routeAliases[" + i + "].from", alias.from());
      putIfNamed(facts, "routeAliases[" + i + "].to", alias.to());
    }
  }

  private static void putIfNamed(
      Map<String, List<String>> facts, String name, @Nullable String value) {
    if (value != null) {
      facts.put(name, List.of(value));
    }
  }

  public static Set<String> everythingButTheDeclaredPlainFields(
      ProviderAccountFields fields, Set<String> submitted) {
    Set<String> withheld = new LinkedHashSet<>();
    for (String key : submitted) {
      if (!fields.isShownInPlain(key)) {
        withheld.add(key);
      }
    }
    return Set.copyOf(withheld);
  }

  private static Map<String, List<String>> multiValued(Map<String, String> values) {
    Map<String, List<String>> multi = new LinkedHashMap<>();
    for (Map.Entry<String, String> value : values.entrySet()) {
      if (value.getValue() != null) {
        multi.put(value.getKey(), List.of(value.getValue()));
      }
    }
    return multi;
  }
}
