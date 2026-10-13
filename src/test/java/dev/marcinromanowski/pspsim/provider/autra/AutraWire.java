package dev.marcinromanowski.pspsim.provider.autra;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class AutraWire {

  static final String POST = "POST";

  static final String GET = "GET";

  private AutraWire() {}

  static AutraApiCall call(String path, Map<String, List<String>> headers, String body) {
    return AutraApiCall.of(POST, path, headers, Map.of(), body);
  }

  static AutraApiCall get(
      String path, Map<String, List<String>> headers, Map<String, List<String>> query) {
    return AutraApiCall.of(GET, path, headers, query, "");
  }

  static Map<String, List<String>> query(String... pairs) {
    Map<String, List<String>> asked = new LinkedHashMap<>();
    for (int at = 0; at < pairs.length; at += 2) {
      asked.put(pairs[at], List.of(pairs[at + 1]));
    }
    return asked;
  }

  static AutraApiCall tokenCall(String grantType, String authorization) {
    Map<String, List<String>> query = new LinkedHashMap<>();
    if (grantType != null) {
      query.put(AutraFields.GRANT_TYPE, List.of(grantType));
    }
    Map<String, List<String>> headers = new LinkedHashMap<>();
    if (authorization != null) {
      headers.put(AutraFields.AUTHORIZATION, List.of(authorization));
    }
    return AutraApiCall.of(POST, AutraApiController.OAUTH_TOKEN, headers, query, "");
  }

  static String basic(String clientId, String clientSecret) {
    return AutraFields.BASIC
        + Base64.getEncoder()
            .encodeToString((clientId + ":" + clientSecret).getBytes(StandardCharsets.UTF_8));
  }

  static Map<String, List<String>> bearer(String token) {
    return Map.of(AutraFields.AUTHORIZATION, List.of(AutraFields.BEARER + token));
  }

  static String bodyOf(AutraApiResponse response) {
    return new String(response.body(), StandardCharsets.UTF_8);
  }
}
