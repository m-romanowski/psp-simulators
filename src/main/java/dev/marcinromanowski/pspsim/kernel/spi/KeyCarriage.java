package dev.marcinromanowski.pspsim.kernel.spi;

public enum KeyCarriage {
  HEADER("sent as an HTTP request header"),
  REQUEST_FIELD("sent as a field of the request itself"),
  PATH_SEGMENT("named by the request path rather than sent as a field");

  private final String describedAs;

  KeyCarriage(String describedAs) {
    this.describedAs = describedAs;
  }

  public String describedAs() {
    return describedAs;
  }
}
