package dev.marcinromanowski.pspsim.kernel.audit;

import static org.assertj.core.api.Assertions.assertThat;

import dev.marcinromanowski.pspsim.kernel.ObjectOrigin;
import dev.marcinromanowski.pspsim.kernel.ProviderId;
import dev.marcinromanowski.pspsim.kernel.testrun.TestRunId;
import java.lang.reflect.Constructor;
import java.lang.reflect.Executable;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class AuditedActionTest {

  @Test
  void no_way_of_building_an_action_takes_a_bare_status_so_every_surface_states_its_verdict() {
    List<String> takingABareStatus = new ArrayList<>();
    for (Executable door : theDoorsIntoAnAuditedAction()) {
      for (Class<?> parameter : door.getParameterTypes()) {
        if (parameter == int.class || parameter == Integer.class) {
          takingABareStatus.add(door.toString());
        }
      }
    }

    assertThat(takingABareStatus).isEmpty();
  }

  @Test
  void every_way_of_building_an_action_asks_for_the_verdict_it_reached() {
    for (Executable door : theDoorsIntoAnAuditedAction()) {
      assertThat(door.getParameterTypes())
          .as("%s", door)
          .anySatisfy(parameter -> assertThat(ActionVerdict.class).isAssignableFrom(parameter));
    }
  }

  @Test
  void an_action_a_tester_performed_as_an_account_carries_that_account_onto_its_row() {
    AuditedAction action = anAction().theAccountItActedFor("  merchant-1 ");

    assertThat(action.accountId()).isEqualTo("merchant-1");
    assertThat(action.decidedBy("scn_1").accountId()).isEqualTo("merchant-1");
  }

  @Test
  void an_action_that_named_no_account_carries_none_rather_than_a_blank_no_filter_could_match() {
    assertThat(anAction().accountId()).isNull();
    assertThat(anAction().theAccountItActedFor("   ").accountId()).isNull();
    assertThat(anAction().theAccountItActedFor(null).accountId()).isNull();
  }

  private static AuditedAction anAction() {
    return AuditedAction.applied(
        ProviderId.of("grow"),
        TestRunId.of("audited-action"),
        ObjectOrigin.UI,
        "POST",
        "/ui/payments/start",
        ActionVerdict.applied(302),
        Map.of());
  }

  private static List<Executable> theDoorsIntoAnAuditedAction() {
    List<Executable> doors = new ArrayList<>();
    for (Constructor<?> constructor : AuditedAction.class.getDeclaredConstructors()) {
      if (Modifier.isPublic(constructor.getModifiers())) {
        doors.add(constructor);
      }
    }
    for (Method method : AuditedAction.class.getDeclaredMethods()) {
      if (Modifier.isPublic(method.getModifiers())
          && Modifier.isStatic(method.getModifiers())
          && method.getReturnType() == AuditedAction.class) {
        doors.add(method);
      }
    }
    assertThat(doors).isNotEmpty();
    return doors;
  }
}
