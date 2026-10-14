package dev.marcinromanowski.pspsim.kernel.spi;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public final class WireAnswer {

  public static final String NOTHING_IS_READ_FROM_AN_EMPTY_BODY = "";

  private final int httpStatus;
  private final String contentType;
  private final byte[] body;
  private final Map<String, String> headers;

  public static final String THE_MEDIA_TYPE = "content-type";

  public static final String THE_LENGTH = "content-length";

  public static final Set<String> HEADERS_THE_BODY_DECIDES = Set.of(THE_MEDIA_TYPE, THE_LENGTH);

  public WireAnswer(int httpStatus, String contentType, byte[] body) {
    this(httpStatus, contentType, body, Map.of());
  }

  public WireAnswer(int httpStatus, String contentType, byte[] body, Map<String, String> headers) {
    if (contentType.isBlank() && body.length > 0) {
      throw new IllegalArgumentException(
          "an answer that carries bytes names the media type they are read as, because a client"
              + " that asked for JSON and was handed an unlabelled body reads this simulator's"
              + " answer differently from the vendor's");
    }
    this.httpStatus = httpStatus;
    this.contentType = contentType;
    this.body = body.clone();
    this.headers = theHeadersBesideTheBody(headers);
  }

  public static Map<String, String> theHeadersBesideTheBody(Map<String, String> headers) {
    for (String name : headers.keySet()) {
      if (HEADERS_THE_BODY_DECIDES.contains(name.toLowerCase(Locale.ROOT))) {
        throw new IllegalArgumentException(
            name
                + " is decided by the body an answer carries and its media type, so an answer"
                + " naming it again among its headers would say two things about one body");
      }
    }
    return Collections.unmodifiableMap(new LinkedHashMap<>(headers));
  }

  public Map<String, String> headers() {
    return headers;
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
