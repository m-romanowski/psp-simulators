package dev.marcinromanowski.pspsim.kernel.spi;

import dev.marcinromanowski.pspsim.kernel.ProviderId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;

public record ProviderPaymentMethodFields(
    ProviderId provider,
    List<ConfigurableField> fields,
    Map<String, String> keysItNeverReads,
    Set<String> keysThatNameAnOwner) {

  public static final String CUSTOMER_ID = "customerId";

  public static final String PAYMENT_METHOD_ID = "paymentMethodId";

  public static final String TOKEN = "token";

  public static final List<String> THE_PUBLISHED_KEYS =
      List.of(CUSTOMER_ID, PAYMENT_METHOD_ID, TOKEN);

  public static final String A_BOX_HINTS_A_VALUE_AND_NEVER_A_KEY =
      " is drawn by the payment-methods form as a box a tester types a value into, and declares no"
          + " example for that box to hint. The only other thing such a box could hint is the key"
          + " this provider reads the value under, which is a developer's name for the field and"
          + " not a value anybody could type: write an example this provider would accept";

  public ProviderPaymentMethodFields {
    Map<String, ConfigurableField> byKey = new LinkedHashMap<>();
    for (ConfigurableField field : fields) {
      if (byKey.put(field.key(), field) != null) {
        throw new IllegalArgumentException(
            "provider-native payment-method id "
                + field.key()
                + " is declared twice by one provider, so a form would draw two inputs writing one"
                + " value");
      }
      if (field.example() == null) {
        throw new IllegalArgumentException(
            "provider-native payment-method id "
                + field.key()
                + A_BOX_HINTS_A_VALUE_AND_NEVER_A_KEY);
      }
      if (keysItNeverReads.containsKey(field.key())) {
        throw new IllegalArgumentException(
            "provider-native payment-method id "
                + field.key()
                + " is declared as one provider "
                + provider.value()
                + " reads and as one it never reads, and a seed cannot be both taken and refused");
      }
    }
    for (Map.Entry<String, String> absent : keysItNeverReads.entrySet()) {
      if (absent.getValue().isBlank()) {
        throw new IllegalArgumentException(
            "a provider that reads no "
                + absent.getKey()
                + " must say, in its own words, what its contract has instead of it");
      }
    }
    for (String published : THE_PUBLISHED_KEYS) {
      if (!byKey.containsKey(published) && !keysItNeverReads.containsKey(published)) {
        throw new IllegalArgumentException(
            "provider "
                + provider.value()
                + " accounts for neither reading nor refusing providerIds."
                + published
                + ", so a seed naming it would be answered by whichever branch happened to run"
                + " first");
      }
    }
    theFloorUnderASharedKey(provider, byKey, keysThatNameAnOwner);
    fields = List.copyOf(fields);
    keysItNeverReads = Map.copyOf(keysItNeverReads);
    keysThatNameAnOwner = Set.copyOf(keysThatNameAnOwner);
  }

  public ProviderPaymentMethodFields(
      ProviderId provider, List<ConfigurableField> fields, Map<String, String> keysItNeverReads) {
    this(provider, fields, keysItNeverReads, Set.of());
  }

  private static void theFloorUnderASharedKey(
      ProviderId provider, Map<String, ConfigurableField> byKey, Set<String> keysThatNameAnOwner) {
    if (keysThatNameAnOwner.isEmpty()) {
      return;
    }
    for (String owner : keysThatNameAnOwner) {
      if (!byKey.containsKey(owner)) {
        throw new IllegalArgumentException(
            "provider "
                + provider.value()
                + " declares providerIds."
                + owner
                + " as a key that names an owner and reads no such key, so the relaxation would"
                + " apply to nothing this provider is ever seeded with");
      }
    }
    for (ConfigurableField field : byKey.values()) {
      if (field.required() && !keysThatNameAnOwner.contains(field.key())) {
        return;
      }
    }
    throw new IllegalArgumentException(
        "provider "
            + provider.value()
            + " declares "
            + new TreeSet<>(keysThatNameAnOwner)
            + " as keys that name an owner, and many fixtures of a test run may name one value"
            + " under such a key, so it must also require a key that names the instrument itself;"
            + " without one, every id a fixture carries could be shared and no lookup could ever"
            + " resolve one fixture rather than a list");
  }

  public boolean namesAnOwner(String key) {
    return keysThatNameAnOwner.contains(key);
  }

  public List<String> keys() {
    List<String> keys = new ArrayList<>();
    for (ConfigurableField field : fields) {
      keys.add(field.key());
    }
    return List.copyOf(keys);
  }

  public Optional<ConfigurableField> field(String key) {
    for (ConfigurableField declared : fields) {
      if (declared.key().equals(key)) {
        return Optional.of(declared);
      }
    }
    return Optional.empty();
  }

  public String theRefusalOf(String key) {
    String instead = keysItNeverReads.get(key);
    if (instead != null) {
      return instead;
    }
    return "providerIds."
        + key
        + " is no key of the published payment-method fixture schema, which names "
        + new TreeSet<>(THE_PUBLISHED_KEYS);
  }
}
