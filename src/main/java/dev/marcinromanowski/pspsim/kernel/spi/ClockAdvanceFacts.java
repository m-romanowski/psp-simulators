package dev.marcinromanowski.pspsim.kernel.spi;

import dev.marcinromanowski.pspsim.kernel.audit.ActionFacts;
import java.util.List;
import java.util.Map;
import org.jspecify.annotations.Nullable;

public final class ClockAdvanceFacts {

  private static final String ADVANCE_BY = "clock.advanceBy";

  private static final String MOVED_FROM = "clock.movedFrom";

  private static final String NOW = "clock.now";

  private ClockAdvanceFacts() {}

  public static Map<String, List<String>> asked(@Nullable String advanceBy) {
    return ActionFacts.none().and(ADVANCE_BY, advanceBy).asRecorded();
  }

  public static Map<String, List<String>> of(ClockAdvanceAdmission.Verdict.Advanced advanced) {
    return ActionFacts.none()
        .and(ADVANCE_BY, advanced.by().toString())
        .and(MOVED_FROM, advanced.movedFrom().toString())
        .and(NOW, advanced.now().toString())
        .asRecorded();
  }
}
