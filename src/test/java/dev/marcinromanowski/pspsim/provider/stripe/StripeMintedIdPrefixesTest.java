package dev.marcinromanowski.pspsim.provider.stripe;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.lang.classfile.Attributes;
import java.lang.classfile.ClassFile;
import java.lang.classfile.ClassModel;
import java.lang.classfile.FieldModel;
import java.lang.classfile.attribute.ConstantValueAttribute;
import java.lang.classfile.constantpool.StringEntry;
import java.lang.constant.ClassDesc;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

class StripeMintedIdPrefixesTest {

  private static final Path THE_COMPILED_MODULE =
      Path.of(
          "build",
          "classes",
          "java",
          "main",
          "dev",
          "marcinromanowski",
          "pspsim",
          "provider",
          "stripe");

  private static final String THE_NAME_EVERY_MINTING_CLASS_HOLDS_ITS_PREFIX_UNDER = "ID_PREFIX";

  private static final int THE_FEWEST_MINTING_CLASSES_A_WORKING_SWEEP_FINDS = 6;

  private static final String A_PREFIX_SPELLED_LIKE_ANOTHER_ONES_BEGINNING = "pi_sim_extra";

  @Test
  void no_id_this_module_mints_begins_with_the_prefix_another_object_of_it_is_minted_under() {
    Map<String, String> byTheClassThatMintsIt = thePrefixesThisModuleMintsUnder();

    assertThat(byTheClassThatMintsIt)
        .as(
            "the prefixes are read off the compiled module rather than listed here, so a sixth"
                + " minting class joins this claim by existing and cannot be forgotten the way two"
                + " hand-written enumerations forgot the ephemeral key's")
        .hasSizeGreaterThanOrEqualTo(THE_FEWEST_MINTING_CLASSES_A_WORKING_SWEEP_FINDS);
    assertThat(byTheClassThatMintsIt.values()).doesNotHaveDuplicates();

    byTheClassThatMintsIt.forEach(
        (minting, prefix) ->
            byTheClassThatMintsIt.forEach(
                (other, another) -> {
                  if (!minting.equals(other)) {
                    assertThat(prefix)
                        .as(
                            "%s mints under %s and %s under %s, and an id whose prefix begins with"
                                + " another object's prefix is an id this simulator's own stores"
                                + " and screens cannot tell apart by reading it",
                            minting, prefix, other, another)
                        .doesNotStartWith(another);
                  }
                }));
  }

  @Test
  void this_sweep_reads_a_prefix_off_a_class_file_and_would_see_one_that_begins_with_another() {
    Map<String, String> read = thePrefixesThisModuleMintsUnder();

    assertThat(read)
        .as("the sweep found the classes it is written to read, by the name each holds")
        .containsKeys("CreatePaymentIntent", "CreateEphemeralKey", "StripeChargeMinting");
    assertThat(read.get("CreatePaymentIntent"))
        .as("a prefix this sweep read is the value the class declares and not an empty reading")
        .isEqualTo(CreatePaymentIntent.ID_PREFIX);
    assertThat(A_PREFIX_SPELLED_LIKE_ANOTHER_ONES_BEGINNING)
        .as(
            "the specimen this sweep is read back against, so a passing claim above means the"
                + " comparison fires rather than that nothing was compared")
        .startsWith(read.get("CreatePaymentIntent"));
  }

  private static Map<String, String> thePrefixesThisModuleMintsUnder() {
    Map<String, String> byTheClassThatMintsIt = new LinkedHashMap<>();
    for (Path compiled : everyClassFileOfTheModule()) {
      ClassModel model = parsed(compiled);
      for (FieldModel field : model.fields()) {
        if (!field
            .fieldName()
            .stringValue()
            .equals(THE_NAME_EVERY_MINTING_CLASS_HOLDS_ITS_PREFIX_UNDER)) {
          continue;
        }
        Optional<ConstantValueAttribute> constant = field.findAttribute(Attributes.constantValue());
        if (constant.isPresent() && constant.get().constant() instanceof StringEntry text) {
          byTheClassThatMintsIt.put(simpleNameOf(model.thisClass().asSymbol()), text.stringValue());
        }
      }
    }
    return Map.copyOf(byTheClassThatMintsIt);
  }

  private static String simpleNameOf(ClassDesc described) {
    String binary = described.displayName();
    return binary.substring(binary.lastIndexOf('.') + 1);
  }

  private static List<Path> everyClassFileOfTheModule() {
    try (Stream<Path> walked = Files.walk(THE_COMPILED_MODULE)) {
      return walked
          .filter(Files::isRegularFile)
          .filter(each -> each.getFileName().toString().endsWith(".class"))
          .sorted()
          .toList();
    } catch (IOException noCompiledModuleToRead) {
      throw new UncheckedIOException(noCompiledModuleToRead);
    }
  }

  private static ClassModel parsed(Path compiled) {
    try {
      return ClassFile.of().parse(compiled);
    } catch (IOException unreadable) {
      throw new UncheckedIOException(unreadable);
    }
  }
}
