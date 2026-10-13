package dev.marcinromanowski.pspsim.architecture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.marcinromanowski.pspsim.architecture.ExclusionBranchBudget.Branching;
import dev.marcinromanowski.pspsim.architecture.ExclusionBranchBudget.Family;
import dev.marcinromanowski.pspsim.architecture.ExclusionBranchBudget.Reading;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;
import org.assertj.core.api.InstanceOfAssertFactories;
import org.junit.jupiter.api.Test;

class ExclusionBranchBudgetTest {

  private static final Path COMPILED_TESTS = Path.of("build", "classes", "java", "test");

  private static final Path COMPILED_MAIN = Path.of("build", "classes", "java", "main");

  private static final String FIXTURES =
      "dev.marcinromanowski.pspsim.architecture.ExclusionBranchBudgetTest$";

  private static final String DECIDING = FIXTURES + "Deciding";

  private static final String LOOPING = FIXTURES + "Looping";

  static final class Deciding {

    static int answer(int left, int right) {
      if (left > 0) {
        return 1;
      }
      if (right > 0) {
        return 2;
      }
      if (left == right) {
        return 3;
      }
      return 4;
    }
  }

  static final class Looping {

    static int upTo(int limit) {
      int found = 0;
      while (true) {
        found++;
        if (found >= limit) {
          return found;
        }
      }
    }
  }

  @Test
  void a_conditional_jump_is_counted_once_and_an_unconditional_jump_is_not_counted_at_all() {
    Reading reading = ExclusionBranchBudget.read(COMPILED_TESTS, family(LOOPING, 99, 99));

    assertThat(reading.branches()).isEqualTo(1);
    assertThat(reading.worstMethod().map(Branching::branches)).contains(1);
  }

  @Test
  void every_method_of_an_excluded_class_counts_towards_its_family() {
    Reading reading = ExclusionBranchBudget.read(COMPILED_TESTS, family(DECIDING, 99, 99));

    assertThat(reading.branches()).isEqualTo(3);
    assertThat(reading.worstMethod().orElseThrow().owner()).isEqualTo(DECIDING + ".answer(II)I");
  }

  @Test
  void a_family_holding_exactly_the_number_it_was_measured_at_is_allowed() {
    assertThat(ExclusionBranchBudget.overruns(COMPILED_TESTS, List.of(family(DECIDING, 3, 3))))
        .isEmpty();
  }

  @Test
  void a_family_holding_one_conditional_jump_more_than_it_was_measured_at_is_refused() {
    assertThat(ExclusionBranchBudget.overruns(COMPILED_TESTS, List.of(family(DECIDING, 2, 99))))
        .singleElement(InstanceOfAssertFactories.STRING)
        .contains("they now hold 3 conditional jumps")
        .contains("The ratchet stands at 2");
  }

  @Test
  void a_method_denser_than_the_densest_the_family_held_is_refused_even_within_the_family_total() {
    assertThat(ExclusionBranchBudget.overruns(COMPILED_TESTS, List.of(family(DECIDING, 99, 2))))
        .singleElement(InstanceOfAssertFactories.STRING)
        .contains("answer(II)I")
        .contains("holds 3 conditional jumps in one method")
        .contains("The ratchet stands at 2");
  }

  @Test
  void the_controllers_the_exclusion_hides_today_sit_inside_a_ratchet_set_at_what_they_measure() {
    List<String> controllers = compiledNamesEndingIn("Controller");
    assertThat(controllers).hasSizeGreaterThan(20);
    int measured =
        ExclusionBranchBudget.read(
                COMPILED_MAIN,
                new Family("dev.marcinromanowski.pspsim.**.*Controller", 0, 0, controllers))
            .branches();

    assertThat(
            ExclusionBranchBudget.overruns(
                COMPILED_MAIN,
                List.of(
                    new Family(
                        "dev.marcinromanowski.pspsim.**.*Controller", measured, 99, controllers))))
        .isEmpty();
    assertThat(
            ExclusionBranchBudget.overruns(
                COMPILED_MAIN,
                List.of(
                    new Family(
                        "dev.marcinromanowski.pspsim.**.*Controller",
                        measured - 1,
                        99,
                        controllers))))
        .isNotEmpty();
  }

  @Test
  void a_family_specification_survives_the_trip_from_the_build_script() {
    Family parsed =
        Family.parse(
            "dev.marcinromanowski.pspsim.**.Jdbc*|251|9|dev.marcinromanowski.pspsim.a.JdbcOne,dev.marcinromanowski.pspsim.b.JdbcTwo");

    assertThat(parsed.glob()).isEqualTo("dev.marcinromanowski.pspsim.**.Jdbc*");
    assertThat(parsed.branchBudget()).isEqualTo(251);
    assertThat(parsed.methodBudget()).isEqualTo(9);
    assertThat(parsed.classNames())
        .containsExactly(
            "dev.marcinromanowski.pspsim.a.JdbcOne", "dev.marcinromanowski.pspsim.b.JdbcTwo");
    assertThat(Family.parse("dev.marcinromanowski.pspsim.**.*Module|0|0|").classNames()).isEmpty();
  }

  @Test
  void a_glob_whose_classes_are_not_compiled_is_answered_rather_than_scored_as_empty() {
    assertThatThrownBy(
            () ->
                ExclusionBranchBudget.read(
                    COMPILED_TESTS,
                    family("dev.marcinromanowski.pspsim.architecture.NothingCompiledThis", 0, 0)))
        .hasMessageContaining("no compiled class for");
  }

  private static Family family(String className, int branchBudget, int methodBudget) {
    return new Family("glob for " + className, branchBudget, methodBudget, List.of(className));
  }

  private static List<String> compiledNamesEndingIn(String suffix) {
    try (Stream<Path> walk = Files.walk(COMPILED_MAIN)) {
      return walk.filter(Files::isRegularFile)
          .map(COMPILED_MAIN::relativize)
          .map(Path::toString)
          .filter(name -> name.endsWith(".class"))
          .map(name -> name.substring(0, name.length() - ".class".length()).replace('/', '.'))
          .filter(name -> name.endsWith(suffix))
          .sorted()
          .toList();
    } catch (IOException unreadable) {
      throw new UncheckedIOException(unreadable);
    }
  }
}
