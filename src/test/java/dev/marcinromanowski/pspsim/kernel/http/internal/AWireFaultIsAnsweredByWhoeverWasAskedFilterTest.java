package dev.marcinromanowski.pspsim.kernel.http.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.marcinromanowski.pspsim.RecordedActions;
import dev.marcinromanowski.pspsim.kernel.ProviderId;
import dev.marcinromanowski.pspsim.kernel.audit.ActionTrail;
import dev.marcinromanowski.pspsim.kernel.audit.AuditRecord;
import dev.marcinromanowski.pspsim.kernel.audit.AuditTrail;
import dev.marcinromanowski.pspsim.kernel.audit.TheCausesOfAFailure;
import dev.marcinromanowski.pspsim.kernel.http.WhoeverWasAsked;
import dev.marcinromanowski.pspsim.kernel.spi.ProviderDouble;
import dev.marcinromanowski.pspsim.kernel.spi.ProviderRegistry;
import dev.marcinromanowski.pspsim.kernel.spi.ProviderWireAnswer;
import dev.marcinromanowski.pspsim.kernel.spi.WhatAProviderAnswersOffItsRoutes;
import dev.marcinromanowski.pspsim.kernel.spi.WireFault;
import dev.marcinromanowski.pspsim.kernel.testrun.TestRunId;
import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.ServletException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class AWireFaultIsAnsweredByWhoeverWasAskedFilterTest {

  private static final String A_WIRE_ROUTE = "/cgi-bin/cgi_link";

  private static final String AN_ADDRESS_NO_PROVIDER_OWNS = "/ui/objects";

  private static final byte[] THE_PROVIDERS_OWN_BYTES =
      "<html>no ACTION, no RC</html>".getBytes(StandardCharsets.UTF_8);

  private static final int A_STATUS_THE_PROVIDER_CHOSE = 410;

  private final ProviderDouble gateway =
      new ProviderDouble("gateway")
          .answering(
              List.of("/cgi-bin"),
              new ProviderWireAnswer.InThisProvidersOwnWords(
                  A_STATUS_THE_PROVIDER_CHOSE, "text/html", THE_PROVIDERS_OWN_BYTES));

  private final RecordedActions trail = new RecordedActions();

  private final AWireFaultIsAnsweredByWhoeverWasAskedFilter filter =
      new AWireFaultIsAnsweredByWhoeverWasAskedFilter(
          new WhoeverWasAsked(
              new WhatAProviderAnswersOffItsRoutes(new ProviderRegistry(List.of(gateway))),
              new ProviderRegistry(List.of(gateway)),
              List.of(),
              trail.asTrail()));

  @Test
  void a_wire_route_the_container_stopped_is_answered_by_its_provider_and_the_chain_is_not_run()
      throws IOException, ServletException {
    MockFilterChain chain = new MockFilterChain();
    MockHttpServletResponse answered = new MockHttpServletResponse();

    filter.doFilter(stopped("GET", A_WIRE_ROUTE, 405), answered, chain);

    assertThat(answered.getStatus())
        .as(
            "the status a provider chose is the one a client reads, and asserting 200 against a"
                + " response double whose own default is 200 would pass with the write deleted")
        .isEqualTo(A_STATUS_THE_PROVIDER_CHOSE);
    assertThat(answered.getContentType()).isEqualTo("text/html");
    assertThat(answered.getContentAsByteArray()).isEqualTo(THE_PROVIDERS_OWN_BYTES);
    assertThat(answered.getContentLength())
        .as(
            "a client that reads Content-Length bytes off the socket and was handed none, or one"
                + " left over from the answer this write replaced, hangs or truncates")
        .isEqualTo(THE_PROVIDERS_OWN_BYTES.length);
    assertThat(answered.isCommitted())
        .as(
            "the chain is not run after this write, so nothing downstream will flush it and a"
                + " body left in the buffer never reaches the client")
        .isTrue();
    assertThat(chain.getRequest())
        .as(
            "the chain ends in this application's own error page, and letting it run after the"
                + " provider has written would hand a client both answers")
        .isNull();
  }

  @Test
  void whatever_the_container_had_already_put_in_the_buffer_is_dropped_rather_than_prefixed()
      throws IOException, ServletException {
    MockHttpServletResponse answered = new MockHttpServletResponse();
    answered.setStatus(405);
    answered.setContentType("application/json");
    answered
        .getOutputStream()
        .write(
            "{\"timestamp\":\"now\",\"error\":\"Method Not Allowed\"}"
                .getBytes(StandardCharsets.UTF_8));

    filter.doFilter(stopped("GET", A_WIRE_ROUTE, 405), answered, new MockFilterChain());

    assertThat(answered.getContentAsByteArray())
        .as(
            "the error dispatch had already written this application's own envelope into the"
                + " buffer, and a provider's bytes appended to it are two answers in one body")
        .isEqualTo(THE_PROVIDERS_OWN_BYTES);
    assertThat(answered.getStatus()).isEqualTo(A_STATUS_THE_PROVIDER_CHOSE);
    assertThat(answered.getContentType()).isEqualTo("text/html");
  }

  @Test
  void a_method_a_wire_route_does_not_answer_and_an_address_it_does_not_serve_are_told_apart()
      throws IOException, ServletException {
    filter.doFilter(
        stopped("GET", A_WIRE_ROUTE, 405), new MockHttpServletResponse(), new MockFilterChain());
    filter.doFilter(
        stopped("POST", "/cgi-bin/nothingHere", 404),
        new MockHttpServletResponse(),
        new MockFilterChain());
    filter.doFilter(
        stopped("POST", A_WIRE_ROUTE, 503), new MockHttpServletResponse(), new MockFilterChain());

    assertThat(gateway.theFaultsItWasAskedAbout())
        .containsExactly(
            WireFault.A_METHOD_THIS_ADDRESS_DOES_NOT_ANSWER,
            WireFault.AN_ADDRESS_THIS_PROVIDER_DOES_NOT_SERVE,
            WireFault.A_FAILURE_NOTHING_ELSE_ANSWERED);
  }

  @Test
  void the_failure_a_wire_route_was_stopped_on_is_left_on_the_trail_in_its_own_words()
      throws IOException, ServletException {
    MockHttpServletRequest stopped = stopped("POST", A_WIRE_ROUTE, 500);
    stopped.setAttribute(
        RequestDispatcher.ERROR_EXCEPTION,
        new IllegalStateException("a column of the trail rejected the value it was handed"));

    filter.doFilter(stopped, new MockHttpServletResponse(), new MockFilterChain());

    assertThat(trail.written())
        .as(
            "the wire answer carries none of this, so a tester who cannot read the reason off the"
                + " trail cannot tell a defect of this simulator from a refusal the provider"
                + " published")
        .hasSize(1);
    assertThat(trail.written().get(0).sanitizedBody())
        .containsEntry(
            TheCausesOfAFailure.THE_CAUSES,
            List.of(
                "IllegalStateException: a column of the trail rejected the value it was handed"));
    assertThat(trail.written().get(0).httpStatus()).isEqualTo(500);
    assertThat(trail.written().get(0).theCalledPath()).isEqualTo(A_WIRE_ROUTE);
  }

  @Test
  void a_wrong_method_and_an_unserved_address_are_answered_and_leave_the_trail_untouched()
      throws IOException, ServletException {
    filter.doFilter(
        stopped("GET", A_WIRE_ROUTE, 405), new MockHttpServletResponse(), new MockFilterChain());
    filter.doFilter(
        stopped("POST", "/cgi-bin/nothingHere", 404),
        new MockHttpServletResponse(),
        new MockFilterChain());

    assertThat(trail.written())
        .as(
            "a client asking at an address a provider does not serve is not a defect of this"
                + " simulator, and a row per mistyped address would bury the rows that are")
        .isEmpty();
  }

  @Test
  void an_address_under_the_screens_is_left_to_the_page_this_application_draws_for_it()
      throws IOException, ServletException {
    MockFilterChain chain = new MockFilterChain();

    filter.doFilter(
        stopped("GET", AN_ADDRESS_NO_PROVIDER_OWNS, 500), new MockHttpServletResponse(), chain);

    assertThat(chain.getRequest()).isNotNull();
    assertThat(gateway.theFaultsItWasAskedAbout()).isEmpty();
  }

  @Test
  void a_status_no_fault_of_this_contract_names_is_left_alone()
      throws IOException, ServletException {
    for (int status : new int[] {400, 401, 403, 409, 422, 499}) {
      MockFilterChain chain = new MockFilterChain();

      filter.doFilter(stopped("POST", A_WIRE_ROUTE, status), new MockHttpServletResponse(), chain);

      assertThat(chain.getRequest()).as(String.valueOf(status)).isNotNull();
    }
    assertThat(gateway.theFaultsItWasAskedAbout()).isEmpty();
  }

  @Test
  void a_request_that_is_not_an_error_dispatch_at_all_is_passed_on_untouched()
      throws IOException, ServletException {
    MockFilterChain chain = new MockFilterChain();
    MockHttpServletRequest arriving = new MockHttpServletRequest("POST", A_WIRE_ROUTE);

    filter.doFilter(arriving, new MockHttpServletResponse(), chain);

    assertThat(chain.getRequest())
        .as("a provider route that the operation itself answers is not a fault at all")
        .isNotNull();
    assertThat(gateway.theFaultsItWasAskedAbout()).isEmpty();
  }

  @Test
  void an_address_recorded_as_something_other_than_a_path_is_passed_on_rather_than_matched()
      throws IOException, ServletException {
    MockFilterChain chain = new MockFilterChain();
    MockHttpServletRequest arriving = new MockHttpServletRequest("GET", "/error");
    arriving.setAttribute(RequestDispatcher.ERROR_REQUEST_URI, Integer.valueOf(404));
    arriving.setAttribute(RequestDispatcher.ERROR_STATUS_CODE, Integer.valueOf(404));

    filter.doFilter(arriving, new MockHttpServletResponse(), chain);

    assertThat(chain.getRequest()).isNotNull();
  }

  @Test
  void an_error_dispatch_naming_no_status_is_read_as_a_failure_nothing_else_answered()
      throws IOException, ServletException {
    MockHttpServletRequest arriving = new MockHttpServletRequest("POST", "/error");
    arriving.setAttribute(RequestDispatcher.ERROR_REQUEST_URI, A_WIRE_ROUTE);

    filter.doFilter(arriving, new MockHttpServletResponse(), new MockFilterChain());

    assertThat(gateway.theFaultsItWasAskedAbout())
        .containsExactly(WireFault.A_FAILURE_NOTHING_ELSE_ANSWERED);
  }

  @Test
  void a_response_already_sent_to_the_client_is_never_written_over()
      throws IOException, ServletException {
    MockFilterChain chain = new MockFilterChain();
    MockHttpServletResponse alreadySent = new MockHttpServletResponse();
    alreadySent.getOutputStream().write("half an answer".getBytes(StandardCharsets.UTF_8));
    alreadySent.flushBuffer();

    filter.doFilter(stopped("GET", A_WIRE_ROUTE, 405), alreadySent, chain);

    assertThat(alreadySent.getContentAsString()).isEqualTo("half an answer");
    assertThat(chain.getRequest()).isNotNull();
  }

  @Test
  void the_row_is_on_the_trail_before_the_client_can_read_the_answer_it_explains()
      throws IOException, ServletException {
    MockHttpServletResponse answered = new MockHttpServletResponse();
    List<Boolean> whetherTheClientCouldAlreadyHaveRead = new ArrayList<>();
    AWireFaultIsAnsweredByWhoeverWasAskedFilter recordingFirst =
        filterWritingTo(
            whatever -> whetherTheClientCouldAlreadyHaveRead.add(answered.isCommitted()));

    recordingFirst.doFilter(stopped("POST", A_WIRE_ROUTE, 500), answered, new MockFilterChain());

    assertThat(whetherTheClientCouldAlreadyHaveRead)
        .as(
            "the row was written after flushBuffer, so a client reading the trail on the same"
                + " connection it received the 500 on found nothing 37 times in 60: the answer"
                + " and the row are not ordered by anything unless the row goes first")
        .containsExactly(Boolean.FALSE);
    assertThat(answered.isCommitted()).isTrue();
    assertThat(answered.getContentAsByteArray()).isEqualTo(THE_PROVIDERS_OWN_BYTES);
  }

  @Test
  void a_trail_that_cannot_be_written_at_all_still_leaves_the_client_its_whole_answer()
      throws IOException, ServletException {
    MockHttpServletResponse answered = new MockHttpServletResponse();
    AWireFaultIsAnsweredByWhoeverWasAskedFilter withABrokenTrail =
        filterWritingTo(
            whatever -> {
              throw new IllegalStateException("the audit table is gone");
            });

    assertThatThrownBy(
            () ->
                withABrokenTrail.doFilter(
                    stopped("POST", A_WIRE_ROUTE, 500), answered, new MockFilterChain()))
        .isInstanceOf(IllegalStateException.class);

    assertThat(answered.getContentAsByteArray())
        .as(
            "a trail that is itself unreachable must not cost the client its answer, and that is"
                + " bought by writing the answer whatever the record did, not by sequencing the"
                + " record after the flush")
        .isEqualTo(THE_PROVIDERS_OWN_BYTES);
    assertThat(answered.getStatus()).isEqualTo(A_STATUS_THE_PROVIDER_CHOSE);
    assertThat(answered.getContentLength()).isEqualTo(THE_PROVIDERS_OWN_BYTES.length);
    assertThat(answered.isCommitted()).isTrue();
  }

  @Test
  void the_row_names_the_method_the_client_asked_with_and_not_the_one_the_error_dispatch_carries()
      throws IOException, ServletException {
    for (String asked : List.of("POST", "PUT", "DELETE", "PATCH")) {
      RecordedActions written = new RecordedActions();
      MockHttpServletRequest dispatched = stopped("GET", A_WIRE_ROUTE, 500);
      dispatched.setAttribute(
          AWireFaultIsAnsweredByWhoeverWasAskedFilter.THE_METHOD_THE_CLIENT_ASKED_WITH, asked);

      filterRecordingInto(written)
          .doFilter(dispatched, new MockHttpServletResponse(), new MockFilterChain());

      assertThat(written.written().get(0).method())
          .as(
              "an error dispatch carries its own method, so %s %s was filed as GET %s, which is a"
                  + " published operation of the same provider: the row blamed the wrong one",
              asked, A_WIRE_ROUTE, A_WIRE_ROUTE)
          .isEqualTo(asked);
    }
  }

  @Test
  void the_method_a_client_asked_with_is_remembered_as_the_request_arrives_and_not_invented_later()
      throws IOException, ServletException {
    MockHttpServletRequest arriving = new MockHttpServletRequest("DELETE", A_WIRE_ROUTE);

    filter.doFilter(arriving, new MockHttpServletResponse(), new MockFilterChain());

    assertThat(
            arriving.getAttribute(
                AWireFaultIsAnsweredByWhoeverWasAskedFilter.THE_METHOD_THE_CLIENT_ASKED_WITH))
        .as(
            "no jakarta.servlet.error attribute carries the method, so the only place to read it"
                + " is the dispatch that still had it")
        .isEqualTo("DELETE");
  }

  @Test
  void a_method_remembered_as_nothing_a_client_could_have_asked_with_is_not_believed()
      throws IOException, ServletException {
    RecordedActions written = new RecordedActions();
    MockHttpServletRequest dispatched = stopped("PUT", A_WIRE_ROUTE, 500);
    dispatched.setAttribute(
        AWireFaultIsAnsweredByWhoeverWasAskedFilter.THE_METHOD_THE_CLIENT_ASKED_WITH, "   ");

    filterRecordingInto(written)
        .doFilter(dispatched, new MockHttpServletResponse(), new MockFilterChain());

    assertThat(written.written().get(0).method()).isEqualTo("PUT");
  }

  @Test
  void a_method_already_remembered_is_not_overwritten_by_a_later_dispatch_of_the_same_request()
      throws IOException, ServletException {
    MockHttpServletRequest arriving = new MockHttpServletRequest("GET", "/error");
    arriving.setAttribute(
        AWireFaultIsAnsweredByWhoeverWasAskedFilter.THE_METHOD_THE_CLIENT_ASKED_WITH, "DELETE");

    filter.doFilter(arriving, new MockHttpServletResponse(), new MockFilterChain());

    assertThat(
            arriving.getAttribute(
                AWireFaultIsAnsweredByWhoeverWasAskedFilter.THE_METHOD_THE_CLIENT_ASKED_WITH))
        .as(
            "this filter sees a dispatch that is not an error dispatch more than once per request,"
                + " and an error dispatch naming no address is one of them, so remembering again"
                + " would replace the client's method with the dispatch's own")
        .isEqualTo("DELETE");
  }

  @Test
  void an_answer_with_nothing_in_it_is_written_without_a_media_type_and_with_no_bytes()
      throws IOException, ServletException {
    ProviderDouble publishesNothing =
        new ProviderDouble("silent")
            .answering(
                List.of("/api/light"),
                new ProviderWireAnswer.NothingThisContractPublishes(
                    "a signed body on the routes it serves and nothing else"));
    AWireFaultIsAnsweredByWhoeverWasAskedFilter answering =
        new AWireFaultIsAnsweredByWhoeverWasAskedFilter(
            new WhoeverWasAsked(
                new WhatAProviderAnswersOffItsRoutes(
                    new ProviderRegistry(List.of(publishesNothing))),
                new ProviderRegistry(List.of(publishesNothing)),
                List.of(),
                new RecordedActions().asTrail()));
    MockHttpServletResponse answered = new MockHttpServletResponse();

    answering.doFilter(stopped("POST", "/api/light/pay", 503), answered, new MockFilterChain());

    assertThat(answered.getStatus()).isEqualTo(503);
    assertThat(answered.getContentType())
        .as(
            "a media type set from an empty string would label the answer as nothing at all, and"
                + " a client reading Content-Type off it would see a header no vendor sends")
        .isNull();
    assertThat(answered.getContentAsByteArray()).isEmpty();
    assertThat(answered.getContentLength()).isZero();
    assertThat(answered.isCommitted()).isTrue();
  }

  private AWireFaultIsAnsweredByWhoeverWasAskedFilter filterRecordingInto(RecordedActions into) {
    return new AWireFaultIsAnsweredByWhoeverWasAskedFilter(
        new WhoeverWasAsked(
            new WhatAProviderAnswersOffItsRoutes(new ProviderRegistry(List.of(gateway))),
            new ProviderRegistry(List.of(gateway)),
            List.of(),
            into.asTrail()));
  }

  private AWireFaultIsAnsweredByWhoeverWasAskedFilter filterWritingTo(
      Consumer<AuditRecord> writing) {
    return new AWireFaultIsAnsweredByWhoeverWasAskedFilter(
        new WhoeverWasAsked(
            new WhatAProviderAnswersOffItsRoutes(new ProviderRegistry(List.of(gateway))),
            new ProviderRegistry(List.of(gateway)),
            List.of(),
            new ActionTrail(
                new OnlyWatchingTheWrite(writing), new RecordedActions(), new RecordedActions())));
  }

  private record OnlyWatchingTheWrite(Consumer<AuditRecord> writing) implements AuditTrail {

    @Override
    public void record(AuditRecord record) {
      writing.accept(record);
    }

    @Override
    public List<AuditRecord> of(ProviderId provider, TestRunId testRunId) {
      return List.of();
    }

    @Override
    public int clear(ProviderId provider, TestRunId testRunId) {
      return 0;
    }

    @Override
    public int clearAll(ProviderId provider) {
      return 0;
    }
  }

  private static MockHttpServletRequest stopped(String method, String address, int status) {
    MockHttpServletRequest arriving = new MockHttpServletRequest(method, "/error");
    arriving.setAttribute(RequestDispatcher.ERROR_REQUEST_URI, address);
    arriving.setAttribute(RequestDispatcher.ERROR_STATUS_CODE, Integer.valueOf(status));
    return arriving;
  }
}
