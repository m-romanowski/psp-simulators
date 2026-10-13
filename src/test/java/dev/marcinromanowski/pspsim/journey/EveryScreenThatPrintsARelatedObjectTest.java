package dev.marcinromanowski.pspsim.journey;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.lang.classfile.ClassFile;
import java.lang.classfile.ClassModel;
import java.lang.classfile.CodeElement;
import java.lang.classfile.MethodModel;
import java.lang.classfile.instruction.InvokeInstruction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

class EveryScreenThatPrintsARelatedObjectTest {

  private static final Path COMPILED_MAIN = Path.of("build", "classes", "java", "main");

  private static final Path TEMPLATES = Path.of("src", "main", "resources", "templates");

  private static final String THE_SCREENS = "dev.marcinromanowski.pspsim.ui.";

  private static final String THE_STATE =
      "dev/marcinromanowski/pspsim/kernel/spi/ProviderObjectState";

  private static final String THE_MAP_A_SCREEN_PRINTS = "relatedObjects";

  private static final Map<String, String> WHAT_EACH_ONE_DOES_WITH_THE_VALUE =
      new TreeMap<>(
          Map.of(
              "dev.marcinromanowski.pspsim.ui.ObjectScreen",
              "prints the field and the value of every entry on the object's own screen",
              "dev.marcinromanowski.pspsim.ui.TimelineScreen",
              "hands the map to timeline.html, which prints every value as the text of a link"
                  + " and again inside that link's own query",
              "dev.marcinromanowski.pspsim.ui.CorrelationScreen",
              "prints every value as a fact reading an object it names by that field",
              "dev.marcinromanowski.pspsim.ui.InspectorScreen",
              "prints the field and the value of every entry of the object a held key"
                  + " produced"));

  private static final List<String> THE_TEMPLATES_THAT_NAME_THE_MAP =
      List.of("extensions/autra/pix.html", "extensions/autra/read-back.html", "timeline.html");

  private static final String WHY_THIS_IS_PINNED =
      "every one of these prints the same map of the same object, so a journey that opens that"
          + " object's own screen is read for all of them, and a read of the timeline, the"
          + " correlation or the inspector adds no address the object's screen does not already"
          + " print. Measured: with grow's hosted page address published among its related"
          + " objects, /ui/objects for that object fails, the objects listing of the run does not"
          + " print a value at all, and /ui/timeline fails only once a key selects that same"
          + " object as its subject. A fifth reader is a question this pin forces: whether the"
          + " object it prints is one a journey opens, because the sweep in"
          + " NoWorkingPayerAddressOnAScreen reads the page a journey was served and nothing else.";

  @Test
  void the_screens_that_read_an_objects_related_map_are_the_ones_recorded_here() {
    List<String> reading = theScreensThatReadTheMap();

    assertThat(reading)
        .as(WHY_THIS_IS_PINNED)
        .containsExactlyElementsOf(WHAT_EACH_ONE_DOES_WITH_THE_VALUE.keySet());
  }

  @Test
  void the_templates_that_name_an_objects_related_map_are_the_ones_recorded_here() {
    List<String> naming = new ArrayList<>();
    try (Stream<Path> walked = Files.walk(TEMPLATES)) {
      walked
          .filter(Files::isRegularFile)
          .sorted()
          .forEach(
              template -> {
                if (theTextOf(template).contains(THE_MAP_A_SCREEN_PRINTS)) {
                  naming.add(TEMPLATES.relativize(template).toString().replace('\\', '/'));
                }
              });
    } catch (IOException unreadable) {
      throw new UncheckedIOException(unreadable);
    }

    assertThat(naming)
        .as(WHY_THIS_IS_PINNED)
        .containsExactlyElementsOf(THE_TEMPLATES_THAT_NAME_THE_MAP);
  }

  private static List<String> theScreensThatReadTheMap() {
    Path screens = COMPILED_MAIN.resolve(THE_SCREENS.replace('.', '/'));
    if (!Files.isDirectory(screens)) {
      throw new IllegalStateException(
          screens
              + " is not a directory of compiled classes, so this sweep would read no screen and"
              + " report that none prints an object's related map. A sweep that found none and a"
              + " sweep that read nothing must not pass the same way");
    }
    List<String> reading = new ArrayList<>();
    try (Stream<Path> walked = Files.walk(screens)) {
      walked
          .filter(Files::isRegularFile)
          .filter(compiled -> compiled.getFileName().toString().endsWith(".class"))
          .sorted()
          .forEach(
              compiled -> {
                ClassModel model = parse(compiled);
                if (readsTheMap(model)) {
                  reading.add(model.thisClass().asInternalName().replace('/', '.'));
                }
              });
    } catch (IOException unreadable) {
      throw new UncheckedIOException(unreadable);
    }
    return List.copyOf(reading);
  }

  private static boolean readsTheMap(ClassModel model) {
    for (MethodModel method : model.methods()) {
      if (method.code().isEmpty()) {
        continue;
      }
      for (CodeElement element : method.code().get().elementList()) {
        if (element instanceof InvokeInstruction invoked
            && invoked.owner().asInternalName().equals(THE_STATE)
            && invoked.name().stringValue().equals(THE_MAP_A_SCREEN_PRINTS)) {
          return true;
        }
      }
    }
    return false;
  }

  private static ClassModel parse(Path compiled) {
    try {
      return ClassFile.of().parse(compiled);
    } catch (IOException unreadable) {
      throw new UncheckedIOException(unreadable);
    }
  }

  private static String theTextOf(Path file) {
    try {
      return Files.readString(file, StandardCharsets.UTF_8);
    } catch (IOException unreadable) {
      throw new UncheckedIOException(unreadable);
    }
  }
}
