package dev.marcinromanowski.pspsim.provider;

import static org.assertj.core.api.Assertions.assertThat;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaField;
import com.tngtech.archunit.core.domain.JavaModifier;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import dev.marcinromanowski.pspsim.kernel.spi.ProviderKeyMechanisms;
import java.lang.reflect.Field;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import org.junit.jupiter.api.Test;

class EveryKeyedExchangeIsWithheldTest {

  private static final String PROVIDERS = "dev.marcinromanowski.pspsim.provider";

  private static final String THE_DECLARATION = "SENSITIVE_RESPONSE_FIELDS";

  private static final String A_VALUE_NO_EXCHANGE_MAY_KEEP = "a-value-no-exchange-may-keep";

  @Test
  void every_provider_keeps_an_exchange_with_every_field_its_trail_withholds_redacted()
      throws ReflectiveOperationException {
    Map<String, Set<?>> declared = new TreeMap<>();
    Map<String, ProviderKeyMechanisms> mechanisms = new TreeMap<>();
    for (JavaClass type :
        new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages(PROVIDERS)) {
      for (JavaField field : type.getFields()) {
        if (!field.getModifiers().contains(JavaModifier.STATIC)) {
          continue;
        }
        if (field.getName().equals(THE_DECLARATION)) {
          declared.put(type.getPackageName(), valueOf(field, Set.class));
        }
        if (field.getRawType().isEquivalentTo(ProviderKeyMechanisms.class)) {
          mechanisms.put(type.getPackageName(), valueOf(field, ProviderKeyMechanisms.class));
        }
      }
    }

    assertThat(declared).as("the provider modules this test reads").isNotEmpty();
    assertThat(mechanisms.keySet())
        .as("every provider that declares what its trail withholds declares its keys once")
        .containsExactlyElementsOf(declared.keySet());
    declared.forEach(
        (module, withheld) -> {
          for (Object name : withheld) {
            String field = String.valueOf(name);
            assertThat(
                    mechanisms
                        .get(module)
                        .withholding()
                        .withheld(Map.of(field, List.of(A_VALUE_NO_EXCHANGE_MAY_KEEP)))
                        .values())
                .as("provider %s keeps %s in a keyed exchange as its trail does", module, field)
                .noneMatch(values -> values.contains(A_VALUE_NO_EXCHANGE_MAY_KEEP));
          }
        });
  }

  private static <T> T valueOf(JavaField declared, Class<T> type)
      throws ReflectiveOperationException {
    Field field = Class.forName(declared.getOwner().getName()).getDeclaredField(declared.getName());
    field.setAccessible(true);
    return type.cast(field.get(null));
  }
}
