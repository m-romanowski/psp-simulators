package dev.marcinromanowski.pspsim.kernel.spi;

import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

public final class WireAnswer {

  public static final String NOTHING_IS_READ_FROM_AN_EMPTY_BODY = "";

  private final int httpStatus;
  private final String contentType;
  private final byte[] body;
  private final Map<String, String> headers;

  public static final String THE_MEDIA_TYPE = "content-type";

  public static final String THE_LENGTH = "content-length";

  public static final Set<String> HEADERS_THE_BODY_DECIDES = Set.of(THE_MEDIA_TYPE, THE_LENGTH);

  public static final String A_COOKIE = "set-cookie";

  public static final String THE_FRAMING = "transfer-encoding";

  public static final Set<String> HEADERS_NO_ANSWER_OFF_A_ROUTE_SETS =
      Set.of(A_COOKIE, THE_FRAMING);

  private static final Pattern A_TOKEN = Pattern.compile("[!#$%&'*+.^_`|~0-9A-Za-z-]+");

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
    Set<String> named = new HashSet<>();
    for (Map.Entry<String, String> header : headers.entrySet()) {
      String name = header.getKey();
      String value = header.getValue();
      if (name == null || value == null) {
        throw new IllegalArgumentException(
            "a header beside an answer's body carries a name and a value, and this one is missing"
                + " one of them");
      }
      if (!A_TOKEN.matcher(name).matches()) {
        throw new IllegalArgumentException(
            name + " is not a header name: a header name is a token of RFC 9110");
      }
      if (value.indexOf('\r') >= 0 || value.indexOf('\n') >= 0 || value.indexOf('\0') >= 0) {
        throw new IllegalArgumentException(
            "the value of header "
                + name
                + " breaks its line, so a client would read a header or a body nobody wrote");
      }
      String lowered = name.toLowerCase(Locale.ROOT);
      if (HEADERS_THE_BODY_DECIDES.contains(lowered)) {
        throw new IllegalArgumentException(
            name
                + " is decided by the body an answer carries and its media type, so an answer"
                + " naming it again among its headers would say two things about one body");
      }
      if (HEADERS_NO_ANSWER_OFF_A_ROUTE_SETS.contains(lowered)) {
        throw new IllegalArgumentException(
            name
                + " changes how the connection carries the answer or what the client keeps after"
                + " it, which an answer off a route never decides");
      }
      if (!named.add(lowered)) {
        throw new IllegalArgumentException(
            name + " is named twice, under two spellings, among an answer's headers");
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
