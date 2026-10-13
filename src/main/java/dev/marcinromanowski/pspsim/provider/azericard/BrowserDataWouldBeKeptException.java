package dev.marcinromanowski.pspsim.provider.azericard;

final class BrowserDataWouldBeKeptException extends IllegalArgumentException {

  private static final long serialVersionUID = 1L;

  static final String A_SESSION_KEEPS_NO_M_INFO =
      "a payer session keeps no M_INFO, because it carries the payer's phone number and browser"
          + " for a 3-D Secure step this simulator does not run, and nothing the page authorizes,"
          + " signs or calls back with reads it";

  BrowserDataWouldBeKeptException() {
    super(A_SESSION_KEEPS_NO_M_INFO);
  }
}
