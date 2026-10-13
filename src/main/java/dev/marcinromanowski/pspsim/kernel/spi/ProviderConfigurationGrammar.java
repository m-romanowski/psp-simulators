package dev.marcinromanowski.pspsim.kernel.spi;

import dev.marcinromanowski.pspsim.kernel.credentials.WebhookTarget;
import dev.marcinromanowski.pspsim.kernel.testrun.TestRunId;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.jspecify.annotations.Nullable;

public final class ProviderConfigurationGrammar {

  private ProviderConfigurationGrammar() {}

  public static ProviderConfiguration configurationOf(
      TestRunId testRunId, DeclaredConfiguration declared) {
    List<ConfiguredRouteAlias> aliases = routeAliasesOf(declared.routeAliases());
    return new ProviderConfiguration(
        testRunId,
        accountsOf(declared.accounts()),
        webhookTargetsOf(declared.webhooks()),
        aliases,
        settingsOf(declared.settings()));
  }

  public static String routeAliasProperty(int index) {
    return "routeAliases[" + index + "]";
  }

  public static String settingProperty(String key) {
    return "settings." + key;
  }

  private static Map<String, String> settingsOf(@Nullable Map<String, String> settings) {
    if (settings == null) {
      return Map.of();
    }
    Map<String, String> named = new LinkedHashMap<>();
    for (Map.Entry<String, String> entry : settings.entrySet()) {
      String key = entry.getKey().strip();
      if (key.isEmpty()) {
        throw new IllegalArgumentException("settings names a setting with no key");
      }
      String value = entry.getValue();
      if (value == null) {
        throw new IllegalArgumentException(
            settingProperty(key) + " must name the value the setting is configured with");
      }
      if (named.put(key, value) != null) {
        throw new IllegalArgumentException(
            settingProperty(key) + " is already configured by an earlier key");
      }
    }
    return Collections.unmodifiableMap(named);
  }

  private static List<ConfiguredRouteAlias> routeAliasesOf(
      @Nullable List<DeclaredRouteAlias> aliases) {
    if (aliases == null) {
      return List.of();
    }
    List<ConfiguredRouteAlias> named = new ArrayList<>();
    for (int i = 0; i < aliases.size(); i++) {
      DeclaredRouteAlias alias = aliases.get(i);
      if (alias == null) {
        throw new IllegalArgumentException(routeAliasProperty(i) + " must name a route alias");
      }
      String from = alias.from();
      String to = alias.to();
      if (from == null || from.isBlank()) {
        throw new IllegalArgumentException(routeAliasProperty(i) + ".from is required");
      }
      if (to == null || to.isBlank()) {
        throw new IllegalArgumentException(routeAliasProperty(i) + ".to is required");
      }
      named.add(new ConfiguredRouteAlias(from.strip(), to.strip()));
    }
    return List.copyOf(named);
  }

  private static List<ConfiguredAccount> accountsOf(@Nullable List<DeclaredAccount> accounts) {
    if (accounts == null) {
      return List.of();
    }
    List<ConfiguredAccount> converted = new ArrayList<>();
    Set<String> accountIds = new LinkedHashSet<>();
    for (int i = 0; i < accounts.size(); i++) {
      DeclaredAccount account = accounts.get(i);
      if (account == null) {
        throw new IllegalArgumentException("accounts[" + i + "] must name an account");
      }
      String identity = identityOf(AccountProperty.identityOf(i), account.accountId());
      if (!accountIds.add(identity)) {
        throw new IllegalArgumentException(
            AccountProperty.identityOf(i) + " is already configured by an earlier account");
      }
      converted.add(new ConfiguredAccount(identity, configurationOf(i, account.configuration())));
    }
    return List.copyOf(converted);
  }

  private static Map<String, String> configurationOf(
      int index, @Nullable Map<String, String> configuration) {
    if (configuration == null) {
      return Map.of();
    }
    Map<String, String> named = new LinkedHashMap<>();
    for (Map.Entry<String, String> entry : configuration.entrySet()) {
      String value = entry.getValue();
      if (value == null) {
        throw new IllegalArgumentException(
            AccountProperty.prefixOf(index)
                + entry.getKey()
                + " must name the value the account is configured with");
      }
      named.put(entry.getKey(), value);
    }
    return Map.copyOf(named);
  }

  private static String identityOf(String property, @Nullable String declared) {
    if (declared == null || declared.isBlank()) {
      throw new IllegalArgumentException(property + " is required");
    }
    return declared.strip();
  }

  private static List<WebhookTarget> webhookTargetsOf(
      @Nullable List<DeclaredWebhookTarget> webhooks) {
    if (webhooks == null) {
      return List.of();
    }
    List<WebhookTarget> converted = new ArrayList<>();
    for (int i = 0; i < webhooks.size(); i++) {
      DeclaredWebhookTarget webhook = webhooks.get(i);
      if (webhook == null) {
        throw new IllegalArgumentException("webhooks[" + i + "] must name a webhook target");
      }
      String identity = identityOf("webhooks[" + i + "].accountId", webhook.accountId());
      String url = webhook.url();
      if (url == null || url.isBlank()) {
        throw new IllegalArgumentException("webhooks[" + i + "].url is required");
      }
      converted.add(new WebhookTarget(identity, url));
    }
    return List.copyOf(converted);
  }
}
