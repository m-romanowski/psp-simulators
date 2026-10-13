package dev.marcinromanowski.pspsim.kernel.audit.internal;

import dev.marcinromanowski.pspsim.kernel.RefusalSubject;
import java.util.List;
import org.jspecify.annotations.Nullable;

final class StoredRefusalSubject {

  private static final String THE_OBJECT = "TheObject";

  private static final String NO_OBJECT_ANSWERED_TO = "NoObjectAnsweredTo";

  private static final String SEVERAL_OBJECTS_ANSWERED_TO = "SeveralObjectsAnsweredTo";

  private static final String AN_OBJECT_IT_WILL_NOT_NAME = "AnObjectItWillNotName";

  private static final String NO_OBJECT_WAS_NAMED = "NoObjectWasNamed";

  private StoredRefusalSubject() {}

  static List<String> of(RefusalSubject refusal) {
    return switch (refusal) {
      case RefusalSubject.TheObject named -> List.of(THE_OBJECT, named.providerObjectId());
      case RefusalSubject.NoObjectAnsweredTo none ->
          List.of(NO_OBJECT_ANSWERED_TO, none.theFieldThatNamedIt());
      case RefusalSubject.SeveralObjectsAnsweredTo several ->
          List.of(
              SEVERAL_OBJECTS_ANSWERED_TO,
              several.theFieldThatNamedThem(),
              String.valueOf(several.howManyAnswered()));
      case RefusalSubject.AnObjectItWillNotName withheld ->
          List.of(AN_OBJECT_IT_WILL_NOT_NAME, withheld.whyItsContractNamesNone());
      case RefusalSubject.NoObjectWasNamed nothing ->
          List.of(NO_OBJECT_WAS_NAMED, nothing.whatItRefusedInstead());
    };
  }

  static @Nullable RefusalSubject from(@Nullable List<String> stored) {
    if (stored == null || stored.isEmpty()) {
      return null;
    }
    return switch (stored.get(0)) {
      case THE_OBJECT -> new RefusalSubject.TheObject(stored.get(1));
      case NO_OBJECT_ANSWERED_TO -> new RefusalSubject.NoObjectAnsweredTo(stored.get(1));
      case SEVERAL_OBJECTS_ANSWERED_TO ->
          new RefusalSubject.SeveralObjectsAnsweredTo(
              stored.get(1), Integer.parseInt(stored.get(2)));
      case AN_OBJECT_IT_WILL_NOT_NAME -> new RefusalSubject.AnObjectItWillNotName(stored.get(1));
      case NO_OBJECT_WAS_NAMED -> new RefusalSubject.NoObjectWasNamed(stored.get(1));
      default ->
          throw new IllegalStateException(
              "the audit trail holds a refusal subject this build does not know how to read back,"
                  + " so a timeline drawn from it would silently drop the refusals stored under it");
    };
  }
}
