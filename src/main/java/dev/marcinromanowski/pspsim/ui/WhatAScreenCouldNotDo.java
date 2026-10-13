package dev.marcinromanowski.pspsim.ui;

import dev.marcinromanowski.pspsim.kernel.audit.TheCausesOfAFailure;
import jakarta.servlet.RequestDispatcher;
import java.util.List;
import java.util.Map;
import org.jspecify.annotations.Nullable;
import org.springframework.boot.web.error.ErrorAttributeOptions;
import org.springframework.boot.web.error.ErrorAttributeOptions.Include;
import org.springframework.boot.webmvc.error.DefaultErrorAttributes;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.WebRequest;

@Component
public final class WhatAScreenCouldNotDo extends DefaultErrorAttributes {

  static final String THE_CAUSES = TheCausesOfAFailure.THE_CAUSES;

  static final String A_TEMPLATE_NAMES_THE_LINE_IT_STOPPED_ON = "aTemplateNamesTheLine";

  static final String WHERE_A_REFRESH_WOULD_HAVE_GONE = "theRunItWasIn";

  static final String WHAT_A_CAUSE_NAMES_A_TEMPLATE_BY = "(template:";

  static final String THE_STATUS = "status";

  static final String WHERE_A_SCREEN_LIVES = "/ui";

  private static final int WHAT_NOTHING_DECIDED = 500;

  @Override
  public Map<String, @Nullable Object> getErrorAttributes(
      WebRequest request, ErrorAttributeOptions options) {
    Object address =
        request.getAttribute(RequestDispatcher.ERROR_REQUEST_URI, RequestAttributes.SCOPE_REQUEST);
    if (!aScreenWasAskedFor(address)) {
      return super.getErrorAttributes(request, options);
    }
    Map<String, @Nullable Object> attributes =
        super.getErrorAttributes(request, options.including(Include.MESSAGE));
    List<String> causes = TheCausesOfAFailure.of(getError(request));
    if (nothingDecidedThisAnswer(attributes) && !causes.isEmpty()) {
      attributes.put(THE_CAUSES, causes);
      attributes.put(A_TEMPLATE_NAMES_THE_LINE_IT_STOPPED_ON, aCauseNamesATemplate(causes));
    }
    theAddressItWasAskedAt(request, String.valueOf(address), attributes);
    return attributes;
  }

  static boolean aCauseNamesATemplate(List<String> causes) {
    for (String cause : causes) {
      if (cause.contains(WHAT_A_CAUSE_NAMES_A_TEMPLATE_BY)) {
        return true;
      }
    }
    return false;
  }

  private static void theAddressItWasAskedAt(
      WebRequest request, String asked, Map<String, @Nullable Object> attributes) {
    String provider = request.getParameter(WhereARefreshLands.THE_PROVIDER);
    String testRunId = request.getParameter(WhereARefreshLands.THE_RUN);
    attributes.put(WhereARefreshLands.THE_PROVIDER, provider);
    attributes.put(WhereARefreshLands.THE_RUN, testRunId);
    attributes.put(
        WHERE_A_REFRESH_WOULD_HAVE_GONE,
        WhereARefreshLands.theAddressARefreshIsSentTo(asked, provider, testRunId));
  }

  static boolean aScreenWasAskedFor(@Nullable Object address) {
    if (!(address instanceof String asked)) {
      return false;
    }
    return asked.equals(WHERE_A_SCREEN_LIVES) || asked.startsWith(WHERE_A_SCREEN_LIVES + "/");
  }

  static boolean nothingDecidedThisAnswer(Map<String, @Nullable Object> attributes) {
    return attributes.get(THE_STATUS) instanceof Integer answered
        && answered >= WHAT_NOTHING_DECIDED;
  }
}
