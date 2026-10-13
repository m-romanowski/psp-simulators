package dev.marcinromanowski.pspsim.ui;

import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Map;
import org.jspecify.annotations.Nullable;
import org.springframework.boot.webmvc.autoconfigure.error.ErrorViewResolver;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.view.RedirectView;

@Component
public final class TheScreenAnErrorIsDrawnOn implements ErrorViewResolver {

  static final String THE_VIEW = "not-served";

  @Override
  public @Nullable ModelAndView resolveErrorView(
      HttpServletRequest request, HttpStatus status, Map<String, Object> model) {
    Object address = request.getAttribute(RequestDispatcher.ERROR_REQUEST_URI);
    if (!WhatAScreenCouldNotDo.aScreenWasAskedFor(address)) {
      return null;
    }
    if (status == HttpStatus.METHOD_NOT_ALLOWED) {
      return new ModelAndView(
          new RedirectView(
              WhereARefreshLands.theAddressARefreshIsSentTo(
                  String.valueOf(address),
                  request.getParameter(WhereARefreshLands.THE_PROVIDER),
                  request.getParameter(WhereARefreshLands.THE_RUN))));
    }
    return new ModelAndView(THE_VIEW, model);
  }
}
