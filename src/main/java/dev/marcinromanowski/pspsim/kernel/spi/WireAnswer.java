package dev.marcinromanowski.pspsim.kernel.spi;

public final class WireAnswer {

  public static final String NOTHING_IS_READ_FROM_AN_EMPTY_BODY = "";

  private final int httpStatus;
  private final String contentType;
  private final byte[] body;

  public WireAnswer(int httpStatus, String contentType, byte[] body) {
    if (contentType.isBlank() && body.length > 0) {
      throw new IllegalArgumentException(
          "an answer that carries bytes names the media type they are read as, because a client"
              + " that asked for JSON and was handed an unlabelled body reads this simulator's"
              + " answer differently from the vendor's");
    }
    this.httpStatus = httpStatus;
    this.contentType = contentType;
    this.body = body.clone();
  }

  public static WireAnswer withNothingInIt(int httpStatus) {
    return new WireAnswer(httpStatus, NOTHING_IS_READ_FROM_AN_EMPTY_BODY, new byte[0]);
  }

  public int httpStatus() {
    return httpStatus;
  }

  public String contentType() {
    return contentType;
  }

  public byte[] body() {
    return body.clone();
  }
}
