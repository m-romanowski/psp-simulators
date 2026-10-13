package dev.marcinromanowski.pspsim.kernel.spi;

import dev.marcinromanowski.pspsim.kernel.ProviderId;
import dev.marcinromanowski.pspsim.kernel.credentials.ProviderAccounts;
import dev.marcinromanowski.pspsim.kernel.credentials.ProviderConfigurationPlan;
import dev.marcinromanowski.pspsim.kernel.credentials.StoryKeyWitness;
import dev.marcinromanowski.pspsim.kernel.routing.RouteAlias;
import dev.marcinromanowski.pspsim.kernel.routing.RouteAliasGrammar;
import dev.marcinromanowski.pspsim.kernel.routing.RouteAliasKey;
import dev.marcinromanowski.pspsim.kernel.routing.RouteAliasRefusedException;
import dev.marcinromanowski.pspsim.kernel.routing.RouteAliases;
import dev.marcinromanowski.pspsim.kernel.scenario.StoryKey;
import dev.marcinromanowski.pspsim.kernel.settings.DeclaredProviderSettings;
import dev.marcinromanowski.pspsim.kernel.settings.ProviderSettings;
import dev.marcinromanowski.pspsim.kernel.settings.SettingGrammar;
import dev.marcinromanowski.pspsim.kernel.settings.SettingKey;
import dev.marcinromanowski.pspsim.kernel.settings.TestRunSettings;
import dev.marcinromanowski.pspsim.kernel.testrun.TestRunId;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;

public final class ProviderConfigurations {

  private final ProviderAccounts accounts;
  private final RouteAliases routeAliases;
  private final DeclaredProviderSettings declaredSettings;
  private final TestRunSettings testRunSettings;

  public ProviderConfigurations(
      ProviderAccounts accounts,
      RouteAliases routeAliases,
      DeclaredProviderSettings declaredSettings,
      TestRunSettings testRunSettings) {
    this.accounts = accounts;
    this.routeAliases = routeAliases;
    this.declaredSettings = declaredSettings;
    this.testRunSettings = testRunSettings;
  }

  public record Storable(List<String> accountIds, ProviderConfigurationPlan plan) {

    public Storable {
      accountIds = List.copyOf(accountIds);
    }
  }

  public List<String> store(
      SimulatorProvider provider, ProviderConfiguration configuration, Storable storable) {
    accounts.merge(
        provider.id(),
        configuration.testRunId(),
        storable.accountIds(),
        configuration.webhookTargets(),
        storable.plan());
    return accounts.identitiesOf(provider.id(), configuration.testRunId());
  }

  public Storable theWriteItWouldMake(
      SimulatorProvider provider, ProviderConfiguration configuration) {
    ProviderConfigurationPlan plan = provider.parse(configuration);
    rejectAKeyTheProviderReadsNothingOf(provider, configuration, plan);
    rejectAStoryKeyTheSimulatorCouldNotStore(plan);
    List<RouteAlias> aliases = theAliasesThisConfigurationBinds(provider, configuration);
    Map<SettingKey, String> settings = theSettingsThisProviderDeclares(provider, configuration);
    List<String> accountIds = new ArrayList<>();
    for (ConfiguredAccount account : configuration.accounts()) {
      accountIds.add(account.accountId());
    }
    return new Storable(
        List.copyOf(accountIds),
        alsoBinding(plan, provider.id(), configuration.testRunId(), aliases, settings));
  }

  private Map<SettingKey, String> theSettingsThisProviderDeclares(
      SimulatorProvider provider, ProviderConfiguration configuration) {
    ProviderSettings declared = declaredSettings.of(provider.id());
    Map<SettingKey, String> chosen = new EnumMap<>(SettingKey.class);
    for (Map.Entry<String, String> setting : configuration.settings().entrySet()) {
      String property = ProviderConfigurationGrammar.settingProperty(setting.getKey());
      SettingKey key =
          SettingKey.named(setting.getKey())
              .filter(declared::declares)
              .orElseThrow(
                  () ->
                      new ProviderConfigurationRejectedException(
                          property
                              + " is declared by no setting of provider "
                              + provider.id().value()
                              + ", which declares "
                              + declared.keys()));
      try {
        SettingGrammar.rejectAValueTheSettingCannotCarry(key, property, setting.getValue());
      } catch (IllegalArgumentException refused) {
        throw new ProviderConfigurationRejectedException(String.valueOf(refused.getMessage()));
      }
      chosen.put(key, setting.getValue());
    }
    return chosen;
  }

  public List<String> identitiesOf(SimulatorProvider provider, TestRunId testRunId) {
    return accounts.identitiesOf(provider.id(), testRunId);
  }

  public List<RouteAlias> routeAliasesOf(SimulatorProvider provider, TestRunId testRunId) {
    return routeAliases.of(provider.id(), testRunId);
  }

  public boolean releaseRouteAlias(
      SimulatorProvider provider, TestRunId testRunId, RouteAliasKey key) {
    return routeAliases.release(provider.id(), testRunId, key);
  }

  public boolean remove(SimulatorProvider provider, TestRunId testRunId, String accountId) {
    return accounts.remove(
        provider.id(), testRunId, accountId, provider.forget(testRunId, accountId));
  }

  private ProviderConfigurationPlan alsoBinding(
      ProviderConfigurationPlan plan,
      ProviderId provider,
      TestRunId testRunId,
      List<RouteAlias> aliases,
      Map<SettingKey, String> settings) {
    return new ProviderConfigurationPlan(
        plan.configurationKeysItReads(),
        plan.longestStoryKeysItCouldSpell(),
        () -> {
          plan.store();
          routeAliases.merge(provider, testRunId, aliases);
          testRunSettings.merge(provider, testRunId, settings);
        });
  }

  private List<RouteAlias> theAliasesThisConfigurationBinds(
      SimulatorProvider provider, ProviderConfiguration configuration) {
    List<RouteAlias> bound = new ArrayList<>();
    Set<String> claimed = new LinkedHashSet<>();
    List<ConfiguredRouteAlias> declared = configuration.routeAliases();
    for (int index = 0; index < declared.size(); index++) {
      String property = ProviderConfigurationGrammar.routeAliasProperty(index);
      ConfiguredRouteAlias alias = declared.get(index);
      RouteAlias parsed = parsed(provider.id(), configuration.testRunId(), property, alias);
      String from = property + ".from";
      if (!claimed.add(parsed.key().value())) {
        throw new ProviderConfigurationRejectedException(
            from, from + " is already claimed by an earlier route alias of this configuration");
      }
      rejectAUrlAnotherTestRunHasBound(from, parsed);
      bound.add(parsed);
    }
    return List.copyOf(bound);
  }

  private static RouteAlias parsed(
      ProviderId provider, TestRunId testRunId, String property, ConfiguredRouteAlias alias) {
    try {
      return RouteAliasGrammar.aliasOf(provider, testRunId, property, alias.from(), alias.to());
    } catch (RouteAliasRefusedException refused) {
      throw new ProviderConfigurationRejectedException(
          refused.property(), String.valueOf(refused.getMessage()));
    }
  }

  private void rejectAUrlAnotherTestRunHasBound(String from, RouteAlias parsed) {
    RouteAliasKey key = parsed.key();
    Optional<RouteAlias> held = routeAliases.boundTo(key);
    if (held.isEmpty()) {
      return;
    }
    RouteAlias bound = held.get();
    if (bound.provider().equals(parsed.provider())
        && bound.testRunId().equals(parsed.testRunId())) {
      return;
    }
    throw new ProviderConfigurationRejectedException(
        from,
        from
            + " is already bound to test run "
            + bound.testRunId().value()
            + " of provider "
            + bound.provider().value());
  }

  private static void rejectAKeyTheProviderReadsNothingOf(
      SimulatorProvider provider,
      ProviderConfiguration configuration,
      ProviderConfigurationPlan plan) {
    Set<String> read = plan.configurationKeysItReads();
    List<ConfiguredAccount> configured = configuration.accounts();
    for (int index = 0; index < configured.size(); index++) {
      for (String key : configured.get(index).configuration().keySet()) {
        if (!read.contains(key)) {
          throw new ProviderConfigurationRejectedException(
              AccountProperty.prefixOf(index)
                  + key
                  + " is read by no account of provider "
                  + provider.id().value()
                  + ", which reads "
                  + new TreeSet<>(read));
        }
      }
    }
  }

  private static void rejectAStoryKeyTheSimulatorCouldNotStore(ProviderConfigurationPlan plan) {
    for (StoryKeyWitness witness : plan.longestStoryKeysItCouldSpell()) {
      try {
        StoryKey.rejectWhatNoStoryKeyCanCarry(witness.property(), witness.longestKeyItSpells());
      } catch (IllegalArgumentException refused) {
        throw new ProviderConfigurationRejectedException(String.valueOf(refused.getMessage()));
      }
    }
  }
}
