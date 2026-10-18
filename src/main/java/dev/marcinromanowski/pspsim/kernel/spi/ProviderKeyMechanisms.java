package dev.marcinromanowski.pspsim.kernel.spi;

import dev.marcinromanowski.pspsim.kernel.ProviderId;
import dev.marcinromanowski.pspsim.kernel.idempotency.KeyRole;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public record ProviderKeyMechanisms(
    ProviderId provider,
    List<DeclaredKey> keys,
    Map<KeyRole, String> rolesItHasNoMechanismFor,
    AnswerWithholding withholding) {

  public ProviderKeyMechanisms {
    Map<String, DeclaredKey> byName = new LinkedHashMap<>();
    for (DeclaredKey key : keys) {
      if (byName.put(key.name(), key) != null) {
        throw new IllegalArgumentException(
            "key "
                + key.name()
                + " is declared twice by provider "
                + provider.value()
                + ", so one recorded request would be read under two mechanisms");
      }
      if (rolesItHasNoMechanismFor.containsKey(key.role())) {
        throw new IllegalArgumentException(
            "provider "
                + provider.value()
                + " declares "
                + key.name()
                + " as its "
                + key.role().publishedName()
                + " and at the same time declares that it has none, and a tester reading both"
                + " would not know which repeat behaviour to expect");
      }
    }
    for (Map.Entry<KeyRole, String> absent : rolesItHasNoMechanismFor.entrySet()) {
      if (absent.getValue().isBlank()) {
        throw new IllegalArgumentException(
            "a provider that has no "
                + absent.getKey().publishedName()
                + " must say, in its own words, what its contract has instead of one: a blank"
                + " field reads as unknown where the answer is that there is none");
      }
    }
    for (KeyRole role : KeyRole.values()) {
      if (!declares(keys, role) && !rolesItHasNoMechanismFor.containsKey(role)) {
        throw new IllegalArgumentException(
            "provider "
                + provider.value()
                + " accounts for neither having nor lacking a "
                + role.publishedName()
                + ", so the inspector would show that row blank, which reads as a fact this"
                + " simulator failed to record rather than as a mechanism this provider has not"
                + " got");
      }
    }
    keys = List.copyOf(keys);
    rolesItHasNoMechanismFor = Map.copyOf(rolesItHasNoMechanismFor);
  }

  private static boolean declares(List<DeclaredKey> keys, KeyRole role) {
    for (DeclaredKey key : keys) {
      if (key.role() == role) {
        return true;
      }
    }
    return false;
  }

  public List<DeclaredKey> inTheRoleOf(KeyRole role) {
    List<DeclaredKey> named = new ArrayList<>();
    for (DeclaredKey key : keys) {
      if (key.role() == role) {
        named.add(key);
      }
    }
    return List.copyOf(named);
  }

  public Optional<DeclaredKey> key(String name) {
    for (DeclaredKey declared : keys) {
      if (declared.name().equals(name)) {
        return Optional.of(declared);
      }
    }
    return Optional.empty();
  }

  public KeyRole roleOf(String name) {
    return key(name)
        .map(DeclaredKey::role)
        .orElseThrow(
            () ->
                new IllegalStateException(
                    "provider "
                        + provider.value()
                        + " recorded a request under key "
                        + name
                        + ", which it declares nowhere, so nothing says whether a repeat of it"
                        + " replays, is refused or addresses the object it names"));
  }

  public KeyClaim claimOf(String name) {
    return key(name)
        .map(DeclaredKey::whileItIsHeld)
        .orElseThrow(
            () ->
                new IllegalStateException(
                    "provider "
                        + provider.value()
                        + " recorded a request under key "
                        + name
                        + ", which it declares nowhere, so nothing says whether a request can ever"
                        + " find this key held by one that has not finished"));
  }

  public boolean has(KeyRole role) {
    return declares(keys, role);
  }

  public String theAbsenceOf(KeyRole role) {
    String instead = rolesItHasNoMechanismFor.get(role);
    if (instead != null) {
      return instead;
    }
    throw new IllegalStateException(
        "provider " + provider.value() + " declares a " + role.publishedName() + " of its own");
  }
}
