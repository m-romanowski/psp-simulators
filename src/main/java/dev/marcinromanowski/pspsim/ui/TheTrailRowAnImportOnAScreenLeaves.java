package dev.marcinromanowski.pspsim.ui;

import dev.marcinromanowski.pspsim.kernel.ObjectOrigin;
import dev.marcinromanowski.pspsim.kernel.ProviderId;
import dev.marcinromanowski.pspsim.kernel.audit.ActionFacts;
import dev.marcinromanowski.pspsim.kernel.audit.ActionVerdict;
import dev.marcinromanowski.pspsim.kernel.audit.AuditedAction;
import dev.marcinromanowski.pspsim.kernel.testrun.TestRunId;
import java.util.List;
import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpStatus;

public record TheTrailRowAnImportOnAScreenLeaves(
    ProviderId provider,
    TestRunId theRunItBelongsUnder,
    String theRunItWritesInto,
    boolean itWritesNothing,
    String path) {

  private static final String SUBMITTED = "POST";

  public static TheTrailRowAnImportOnAScreenLeaves of(
      Selection.Chosen taking, @Nullable String into, boolean itWritesNothing, String path) {
    String target = theRunAnImportOnAScreenWritesInto(into, taking.testRunId());
    return new TheTrailRowAnImportOnAScreenLeaves(
        taking.module().id(),
        TestRunId.theRunARowAboutThisRequestBelongsTo(target, taking.testRunId()),
        target,
        itWritesNothing,
        path);
  }

  public static String theRunAnImportOnAScreenWritesInto(
      @Nullable String into, TestRunId theRunTheScreenWasUsedOn) {
    return into == null || into.isBlank() ? theRunTheScreenWasUsedOn.value() : into.strip();
  }

  public ActionFacts theFactsOfWhatWasAsked(List<String> theSecretsItWasGiven) {
    return ActionFacts.none()
        .and(ActionFacts.AN_IMPORT_WRITES_NOTHING, String.valueOf(itWritesNothing))
        .and(ActionFacts.THE_RUN_AN_IMPORT_WRITES_INTO, theRunItWritesInto)
        .andEach(ActionFacts.THE_SECRETS_AN_IMPORT_WAS_GIVEN, theSecretsItWasGiven);
  }

  public AuditedAction turnedAway(
      ActionVerdict.Refused verdict, ActionFacts facts, String inItsOwnWords) {
    return AuditedAction.refusing(
        provider,
        theRunItBelongsUnder,
        ObjectOrigin.UI,
        SUBMITTED,
        path,
        verdict,
        facts.and(ActionFacts.REFUSED_BECAUSE, inItsOwnWords).asRecorded());
  }

  public AuditedAction applied(ActionFacts facts) {
    return AuditedAction.applied(
        provider,
        theRunItBelongsUnder,
        ObjectOrigin.UI,
        SUBMITTED,
        path,
        ActionVerdict.applied(HttpStatus.OK.value()),
        facts.asRecorded());
  }
}
