package dev.marcinromanowski.pspsim.control;

import dev.marcinromanowski.pspsim.control.ControlPayloads.ControlError;
import dev.marcinromanowski.pspsim.control.ControlPayloads.ControlRefusal;
import dev.marcinromanowski.pspsim.control.ControlPayloads.TheWordsARefusalWasAnsweredIn;
import dev.marcinromanowski.pspsim.kernel.RefusalSubject;
import dev.marcinromanowski.pspsim.kernel.audit.ActionFacts;
import dev.marcinromanowski.pspsim.kernel.audit.ActionVerdict;
import dev.marcinromanowski.pspsim.kernel.testrun.TestRunId;
import java.util.List;
import java.util.Map;
import org.jspecify.annotations.Nullable;
import org.springframework.core.DefaultParameterNameDiscoverer;
import org.springframework.core.MethodParameter;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.method.HandlerMethod;
import tools.jackson.core.JacksonException;

final class ABodyTheControlApiCouldNotRead {

  static final String THE_RUN_A_REQUEST_NAMES_IN_ITS_QUERY = "testRunId";

  static final String THE_BODY_IT_ASKED_THIS_PROVIDER_TO_READ =
      "the body this request asked this provider to read";

  static final String WHAT_A_CALLER_IS_TOLD =
      "the body of this request is not one this control API reads, so nothing was attempted and"
          + " nothing changed. The properties beside this message are the fields the reading"
          + " stopped at, and there are none beside it where it stopped before the first one."
          + " What the body said is not repeated here, because a diagnostic of this simulator"
          + " names the field and never the value, and a body nothing parsed is where a credential"
          + " a caller mistyped would still be sitting.";

  private final List<String> theFieldsTheReadingStoppedAt;

  private final boolean theRouteItWasHeadingToReadsItsRunFromItsQuery;

  private ABodyTheControlApiCouldNotRead(
      List<String> theFieldsTheReadingStoppedAt,
      boolean theRouteItWasHeadingToReadsItsRunFromItsQuery) {
    this.theFieldsTheReadingStoppedAt = List.copyOf(theFieldsTheReadingStoppedAt);
    this.theRouteItWasHeadingToReadsItsRunFromItsQuery =
        theRouteItWasHeadingToReadsItsRunFromItsQuery;
  }

  static ABodyTheControlApiCouldNotRead of(
      HttpMessageNotReadableException unreadable, @Nullable HandlerMethod theRouteItWasHeadingTo) {
    return new ABodyTheControlApiCouldNotRead(
        theFieldsNamedBy(unreadable.getCause()),
        theRunIsReadFromTheQueryBy(theRouteItWasHeadingTo));
  }

  TestRunId theRunARowAboutItBelongsTo(@Nullable String theRunTheQueryNamed) {
    if (!theRouteItWasHeadingToReadsItsRunFromItsQuery) {
      return TestRunId.UNKNOWN;
    }
    return TestRunId.theRunARowAboutThisRequestBelongsTo(theRunTheQueryNamed, TestRunId.UNKNOWN);
  }

  ActionVerdict.Refused theRefusalItReports() {
    return ActionVerdict.rejected(
        RefusalSubject.noObjectWasNamed(THE_BODY_IT_ASKED_THIS_PROVIDER_TO_READ));
  }

  Map<String, List<String>> theFactsOfTheRowItLeaves() {
    return ActionFacts.none()
        .andEach(ActionFacts.REFUSED_PROPERTIES, theFieldsTheReadingStoppedAt)
        .asRecorded();
  }

  TheWordsARefusalWasAnsweredIn theWordsItIsAnsweredIn() {
    if (theFieldsTheReadingStoppedAt.isEmpty()) {
      return new ControlError(WHAT_A_CALLER_IS_TOLD);
    }
    return new ControlRefusal(WHAT_A_CALLER_IS_TOLD, theFieldsTheReadingStoppedAt);
  }

  private static boolean theRunIsReadFromTheQueryBy(@Nullable HandlerMethod route) {
    if (route == null) {
      return false;
    }
    for (MethodParameter taken : route.getMethodParameters()) {
      if (THE_RUN_A_REQUEST_NAMES_IN_ITS_QUERY.equals(theQueryParameterARouteBindsFrom(taken))) {
        return true;
      }
    }
    return false;
  }

  private static @Nullable String theQueryParameterARouteBindsFrom(MethodParameter taken) {
    RequestParam declared = taken.getParameterAnnotation(RequestParam.class);
    if (declared == null) {
      return null;
    }
    if (!declared.name().isEmpty()) {
      return declared.name();
    }
    taken.initParameterNameDiscovery(new DefaultParameterNameDiscoverer());
    return taken.getParameterName();
  }

  private static List<String> theFieldsNamedBy(@Nullable Throwable whatTheReaderRaised) {
    return whatTheReaderRaised instanceof JacksonException named
        ? thePathItStoppedAt(named)
        : List.of();
  }

  private static List<String> thePathItStoppedAt(JacksonException named) {
    StringBuilder path = new StringBuilder();
    for (JacksonException.Reference reference : named.getPath()) {
      path.append(theFieldOrTheIndexOf(reference, path.length()));
    }
    return path.isEmpty() ? List.of() : List.of(path.toString());
  }

  private static String theFieldOrTheIndexOf(
      JacksonException.Reference reference, int whatIsNamedAlready) {
    String property = reference.getPropertyName();
    if (property == null) {
      return "[" + reference.getIndex() + "]";
    }
    return whatIsNamedAlready == 0 ? property : "." + property;
  }
}
