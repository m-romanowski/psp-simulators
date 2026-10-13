package dev.marcinromanowski.pspsim.control;

import static org.assertj.core.api.Assertions.assertThat;

import dev.marcinromanowski.pspsim.control.ControlPayloads.ControlError;
import dev.marcinromanowski.pspsim.control.ControlPayloads.ControlRefusal;
import dev.marcinromanowski.pspsim.kernel.RefusalSubject;
import dev.marcinromanowski.pspsim.kernel.audit.ActionFacts;
import dev.marcinromanowski.pspsim.kernel.audit.RefusedActionStatus;
import dev.marcinromanowski.pspsim.kernel.testrun.TestRunId;
import java.lang.reflect.Method;
import java.util.List;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.mock.http.MockHttpInputMessage;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.method.HandlerMethod;
import tools.jackson.core.JacksonException;
import tools.jackson.core.JsonParser;
import tools.jackson.databind.exc.MismatchedInputException;

class ABodyTheControlApiCouldNotReadTest {

  private static final String A_SECRET_A_HARNESS_WOULD_RECOGNISE = "sk_test_9e2b71a4c6d8";

  @Test
  void a_body_nothing_parsed_is_refused_over_no_object_and_says_what_it_refused_instead() {
    ABodyTheControlApiCouldNotRead unreadable =
        ABodyTheControlApiCouldNotRead.of(
            theFailureOfAReadThatStoppedAtTheFirstBrace(), theRouteThatNamesItsRunInItsBody());

    assertThat(unreadable.theRefusalItReports().status())
        .as(
            "a body this api could not read is a request it turned away, and the status of a"
                + " request turned away for the shape it arrived in is the rejection this build"
                + " answers every unreadable request with")
        .isEqualTo(RefusedActionStatus.REJECTED);
    assertThat(unreadable.theRefusalItReports().subject())
        .as(
            "nothing read the body, so no object of this run was ever named, and a subject that"
                + " claimed one would send a tester looking for an object this request never"
                + " reached")
        .isEqualTo(
            RefusalSubject.noObjectWasNamed(
                ABodyTheControlApiCouldNotRead.THE_BODY_IT_ASKED_THIS_PROVIDER_TO_READ));
  }

  @Test
  void a_reading_that_stopped_before_the_first_field_names_none_of_them() {
    ABodyTheControlApiCouldNotRead unreadable =
        ABodyTheControlApiCouldNotRead.of(
            theFailureOfAReadThatStoppedAtTheFirstBrace(), theRouteThatNamesItsRunInItsBody());

    assertThat(unreadable.theFactsOfTheRowItLeaves())
        .as(
            "a row names the properties a refusal named, and this reading named none, so the fact"
                + " is absent rather than present and empty")
        .doesNotContainKey(ActionFacts.REFUSED_PROPERTIES);
    assertThat(unreadable.theWordsItIsAnsweredIn())
        .as(
            "the answer names no properties either, in the one-field shape every refusal of this"
                + " api that names no property is answered in, rather than an empty array a"
                + " tester would read as a field this api named and lost")
        .isInstanceOf(ControlError.class);
    assertThat(unreadable.theWordsItIsAnsweredIn().message())
        .isEqualTo(ABodyTheControlApiCouldNotRead.WHAT_A_CALLER_IS_TOLD);
  }

  @Test
  void a_reading_that_stopped_inside_the_body_names_the_field_it_stopped_at_and_no_value() {
    ABodyTheControlApiCouldNotRead unreadable =
        ABodyTheControlApiCouldNotRead.of(
            theFailureOfAReadThatStoppedAt(A_SECRET_A_HARNESS_WOULD_RECOGNISE),
            theRouteThatNamesItsRunInItsBody());

    assertThat(unreadable.theFactsOfTheRowItLeaves())
        .as(
            "the field a reading stopped at is the one thing a suite needs to fix its body, and"
                + " the path this build writes it in is the one every other refusal of this api"
                + " names a property by")
        .containsEntry(ActionFacts.REFUSED_PROPERTIES, List.of("accounts[0].configuration"));
    assertThat(unreadable.theWordsItIsAnsweredIn()).isInstanceOf(ControlRefusal.class);
    assertThat(((ControlRefusal) unreadable.theWordsItIsAnsweredIn()).properties())
        .containsExactly("accounts[0].configuration");
    assertThat(String.valueOf(unreadable.theFactsOfTheRowItLeaves()))
        .as(
            "the body of a configuration request carries credentials, and a diagnostic of this"
                + " build names the field and never the value, so what the failure itself said"
                + " travels nowhere")
        .doesNotContain(A_SECRET_A_HARNESS_WOULD_RECOGNISE);
    assertThat(unreadable.theWordsItIsAnsweredIn().message())
        .doesNotContain(A_SECRET_A_HARNESS_WOULD_RECOGNISE);
  }

  @Test
  void a_reading_that_failed_on_something_no_reader_of_json_raised_names_no_field() {
    ABodyTheControlApiCouldNotRead unreadable =
        ABodyTheControlApiCouldNotRead.of(
            new HttpMessageNotReadableException(
                "the stream carrying this body ended",
                new IllegalStateException("nothing to read"),
                theBytesThatArrived()),
            theRouteThatNamesItsRunInItsBody());

    assertThat(unreadable.theWordsItIsAnsweredIn()).isInstanceOf(ControlError.class);
  }

  @Test
  void a_request_that_carried_no_body_at_all_names_no_field_and_is_refused_the_same_way() {
    ABodyTheControlApiCouldNotRead unreadable =
        ABodyTheControlApiCouldNotRead.of(
            new HttpMessageNotReadableException(
                "a body is required on this route", theBytesThatArrived()),
            theRouteThatNamesItsRunInItsBody());

    assertThat(unreadable.theWordsItIsAnsweredIn())
        .as(
            "no reader was ever handed bytes, so no reader named a field, and this fault is the"
                + " same decision as a body that stopped a reader: nothing was read and nothing"
                + " was attempted")
        .isInstanceOf(ControlError.class);
    assertThat(unreadable.theRefusalItReports().subject())
        .isEqualTo(
            RefusalSubject.noObjectWasNamed(
                ABodyTheControlApiCouldNotRead.THE_BODY_IT_ASKED_THIS_PROVIDER_TO_READ));
  }

  @Test
  void the_run_a_row_about_it_belongs_to_is_the_one_its_query_named_where_the_route_reads_it() {
    ABodyTheControlApiCouldNotRead unreadable =
        ABodyTheControlApiCouldNotRead.of(
            theFailureOfAReadThatStoppedAtTheFirstBrace(), theRouteThatReadsItsRunFromItsQuery());

    assertThat(unreadable.theRunARowAboutItBelongsTo("bodies-it"))
        .as(
            "this route reads its run from the query and nothing of the body was needed to know"
                + " it, so the row belongs to the run the request named where that route reads it")
        .isEqualTo(TestRunId.of("bodies-it"));
  }

  @Test
  void a_route_that_spells_the_parameter_in_its_annotation_reads_its_run_from_the_query_too() {
    ABodyTheControlApiCouldNotRead unreadable =
        ABodyTheControlApiCouldNotRead.of(
            theFailureOfAReadThatStoppedAtTheFirstBrace(),
            theRouteCalled("namingTheRunItReadsInTheAnnotationItself"));

    assertThat(unreadable.theRunARowAboutItBelongsTo("bodies-it"))
        .as(
            "a request parameter names what it binds from either in the annotation or by the name"
                + " the route was compiled with, and a rule that reads one spelling of an"
                + " annotation measures a fraction of what spring does with it")
        .isEqualTo(TestRunId.of("bodies-it"));
  }

  @Test
  void a_route_that_names_its_run_in_its_body_leaves_its_row_under_the_reserved_run() {
    ABodyTheControlApiCouldNotRead unreadable =
        ABodyTheControlApiCouldNotRead.of(
            theFailureOfAReadThatStoppedAtTheFirstBrace(), theRouteThatNamesItsRunInItsBody());

    assertThat(unreadable.theRunARowAboutItBelongsTo("bodies-it"))
        .as(
            "this route never reads a run out of a query, so a run named there was named nowhere"
                + " this api reads, and a row under it would show a tester reading that run a"
                + " call of theirs it could as easily have named another run in its body")
        .isEqualTo(TestRunId.UNKNOWN);
  }

  @Test
  void a_fault_raised_before_any_route_was_matched_leaves_its_row_under_the_reserved_run() {
    ABodyTheControlApiCouldNotRead unreadable =
        ABodyTheControlApiCouldNotRead.of(theFailureOfAReadThatStoppedAtTheFirstBrace(), null);

    assertThat(unreadable.theRunARowAboutItBelongsTo("bodies-it"))
        .as(
            "no route was in hand, so nothing says a run named in the query is one this api would"
                + " have read")
        .isEqualTo(TestRunId.UNKNOWN);
  }

  @Test
  void a_request_whose_query_named_no_run_leaves_its_row_under_the_reserved_run() {
    ABodyTheControlApiCouldNotRead unreadable =
        ABodyTheControlApiCouldNotRead.of(
            theFailureOfAReadThatStoppedAtTheFirstBrace(), theRouteThatReadsItsRunFromItsQuery());

    assertThat(unreadable.theRunARowAboutItBelongsTo(null))
        .as(
            "nothing parsed the body, so the run a request names there was never read, and the"
                + " default run would file this row under a run a tester is reading for their own"
                + " requests")
        .isEqualTo(TestRunId.UNKNOWN);
    assertThat(unreadable.theRunARowAboutItBelongsTo("")).isEqualTo(TestRunId.UNKNOWN);
    assertThat(unreadable.theRunARowAboutItBelongsTo("not a run any namespace can hold"))
        .isEqualTo(TestRunId.UNKNOWN);
  }

  @Test
  void a_route_reading_another_parameter_from_its_query_does_not_read_a_run_there() {
    ABodyTheControlApiCouldNotRead unreadable =
        ABodyTheControlApiCouldNotRead.of(
            theFailureOfAReadThatStoppedAtTheFirstBrace(),
            theRouteCalled("readingAnAccountFromItsQueryAndItsRunFromItsBody"));

    assertThat(unreadable.theRunARowAboutItBelongsTo("bodies-it")).isEqualTo(TestRunId.UNKNOWN);
  }

  private static HandlerMethod theRouteThatReadsItsRunFromItsQuery() {
    return theRouteCalled("readingTheRunItWasAskedForFromItsQuery");
  }

  private static HandlerMethod theRouteThatNamesItsRunInItsBody() {
    return theRouteCalled("readingTheRunItWasAskedForOutOfItsBody");
  }

  private static HandlerMethod theRouteCalled(String named) {
    for (Method declared : TheRoutesOfAnApiLikeThisOne.class.getDeclaredMethods()) {
      if (declared.getName().equals(named)) {
        return new HandlerMethod(new TheRoutesOfAnApiLikeThisOne(), declared);
      }
    }
    throw new AssertionError("no route of the fixture is called " + named);
  }

  private static MockHttpInputMessage theBytesThatArrived() {
    return new MockHttpInputMessage(new byte[] {'{'});
  }

  private static HttpMessageNotReadableException theFailureOfAReadThatStoppedAtTheFirstBrace() {
    return new HttpMessageNotReadableException(
        "a reader of this api stopped at the first byte",
        MismatchedInputException.from((JsonParser) null, Object.class, "no object began there"),
        theBytesThatArrived());
  }

  private static HttpMessageNotReadableException theFailureOfAReadThatStoppedAt(String value) {
    JacksonException named =
        MismatchedInputException.from(
                (JsonParser) null, Object.class, "cannot read " + value + " as an object")
            .prependPath(new JacksonException.Reference(null, "configuration"))
            .prependPath(new JacksonException.Reference(null, 0))
            .prependPath(new JacksonException.Reference(null, "accounts"));
    return new HttpMessageNotReadableException(
        "a reader of this api stopped inside", named, theBytesThatArrived());
  }

  static final class TheRoutesOfAnApiLikeThisOne {

    String readingTheRunItWasAskedForFromItsQuery(
        @RequestParam(required = false) @Nullable String testRunId, @RequestBody String body) {
      return testRunId + body;
    }

    String namingTheRunItReadsInTheAnnotationItself(
        @RequestParam("testRunId") String whateverTheRouteCallsIt, @RequestBody String body) {
      return whateverTheRouteCallsIt + body;
    }

    String readingTheRunItWasAskedForOutOfItsBody(@RequestBody String body) {
      return body;
    }

    String readingAnAccountFromItsQueryAndItsRunFromItsBody(
        @RequestParam(required = false) @Nullable String accountId, @RequestBody String body) {
      return accountId + body;
    }
  }
}
