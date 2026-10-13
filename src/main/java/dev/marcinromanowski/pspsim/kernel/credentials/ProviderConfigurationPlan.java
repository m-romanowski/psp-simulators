package dev.marcinromanowski.pspsim.kernel.credentials;

import java.util.List;
import java.util.Set;

public record ProviderConfigurationPlan(
    Set<String> configurationKeysItReads,
    List<StoryKeyWitness> longestStoryKeysItCouldSpell,
    ProviderRows rows) {

  @FunctionalInterface
  public interface ProviderRows {

    void storeTheAccountsTheConfigurationNamed();
  }

  public void store() {
    rows.storeTheAccountsTheConfigurationNamed();
  }
}
