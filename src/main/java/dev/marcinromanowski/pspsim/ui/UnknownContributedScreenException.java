package dev.marcinromanowski.pspsim.ui;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.NOT_FOUND)
public class UnknownContributedScreenException extends RuntimeException {

  private static final long serialVersionUID = 1L;

  public UnknownContributedScreenException(String whatWasAskedFor) {
    super(whatWasAskedFor);
  }
}
