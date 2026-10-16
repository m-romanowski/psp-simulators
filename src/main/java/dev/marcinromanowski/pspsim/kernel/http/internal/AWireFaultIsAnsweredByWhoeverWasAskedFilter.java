package dev.marcinromanowski.pspsim.kernel.http.internal;

import dev.marcinromanowski.pspsim.kernel.http.AFailedRequest;
import dev.marcinromanowski.pspsim.kernel.http.WhoeverWasAsked;
import dev.marcinromanowski.pspsim.kernel.spi.WireAnswer;
import dev.marcinromanowski.pspsim.kernel.spi.WireFault;
import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Optional;
import org.jspecify.annotations.Nullable;

public final class AWireFaultIsAnsweredByWhoeverWasAskedFilter implements Filter {

  static final int NOT_FOUND = 404;

  static final int METHOD_NOT_ALLOWED = 405;

  static final int LOWEST_SERVER_ERROR = 500;

  static final String THE_RUN_A_REQUEST_NAMES = "testRunId";

  static final String THE_METHOD_THE_CLIENT_ASKED_WITH =
      "dev.marcinromanowski.pspsim.kernel.http.theMethodTheClientAskedWith";

  private final WhoeverWasAsked whoeverWasAsked;

  public AWireFaultIsAnsweredByWhoeverWasAskedFilter(WhoeverWasAsked whoeverWasAsked) {
    this.whoeverWasAsked = whoeverWasAsked;
  }

  @Override
  public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
      throws IOException, ServletException {
    if (!(request instanceof HttpServletRequest inbound)
        || !(response instanceof HttpServletResponse outbound)
        || outbound.isCommitted()) {
      chain.doFilter(request, response);
      return;
    }
    Optional<AFailedRequest> asked = whatWasAsked(inbound);
    if (asked.isEmpty()) {
      rememberTheMethodTheClientAskedWith(inbound);
      chain.doFilter(request, response);
      return;
    }
    Optional<WireAnswer> answered = whoeverWasAsked.answers(asked.get());
    if (answered.isEmpty()) {
      chain.doFilter(request, response);
      return;
    }
    try {
      whoeverWasAsked.record(asked.get());
    } finally {
      write(answered.get(), outbound);
    }
  }

  private static void rememberTheMethodTheClientAskedWith(HttpServletRequest inbound) {
    if (inbound.getAttribute(THE_METHOD_THE_CLIENT_ASKED_WITH) == null) {
      inbound.setAttribute(THE_METHOD_THE_CLIENT_ASKED_WITH, inbound.getMethod());
    }
  }

  private static Optional<AFailedRequest> whatWasAsked(HttpServletRequest inbound) {
    String address = addressOf(inbound.getAttribute(RequestDispatcher.ERROR_REQUEST_URI));
    if (address == null) {
      return Optional.empty();
    }
    int status = statusOf(inbound.getAttribute(RequestDispatcher.ERROR_STATUS_CODE));
    WireFault fault = faultOf(status);
    if (fault == null) {
      return Optional.empty();
    }
    return Optional.of(
        new AFailedRequest(
            fault,
            theMethodTheClientAskedWith(inbound),
            address,
            status,
            theFailureOf(inbound.getAttribute(RequestDispatcher.ERROR_EXCEPTION)),
            inbound.getParameter(THE_RUN_A_REQUEST_NAMES)));
  }

  static String theMethodTheClientAskedWith(HttpServletRequest inbound) {
    Object remembered = inbound.getAttribute(THE_METHOD_THE_CLIENT_ASKED_WITH);
    return remembered instanceof String method && !method.isBlank() ? method : inbound.getMethod();
  }

  private static @Nullable Throwable theFailureOf(@Nullable Object recorded) {
    return recorded instanceof Throwable raised ? raised : null;
  }

  private static @Nullable String addressOf(@Nullable Object recorded) {
    return recorded instanceof String address && address.startsWith("/") ? address : null;
  }

  private static int statusOf(@Nullable Object recorded) {
    return recorded instanceof Integer status ? status.intValue() : LOWEST_SERVER_ERROR;
  }

  private static @Nullable WireFault faultOf(int status) {
    if (status == NOT_FOUND) {
      return WireFault.AN_ADDRESS_THIS_PROVIDER_DOES_NOT_SERVE;
    }
    if (status == METHOD_NOT_ALLOWED) {
      return WireFault.A_METHOD_THIS_ADDRESS_DOES_NOT_ANSWER;
    }
    if (status >= LOWEST_SERVER_ERROR) {
      return WireFault.A_FAILURE_NOTHING_ELSE_ANSWERED;
    }
    return null;
  }

  private static void write(WireAnswer answer, HttpServletResponse outbound) throws IOException {
    byte[] body = answer.body();
    outbound.resetBuffer();
    outbound.setStatus(answer.httpStatus());
    answer.headers().forEach(outbound::setHeader);
    if (!answer.contentType().isBlank()) {
      outbound.setContentType(answer.contentType());
    }
    outbound.setContentLength(body.length);
    outbound.getOutputStream().write(body);
    outbound.flushBuffer();
  }
}
