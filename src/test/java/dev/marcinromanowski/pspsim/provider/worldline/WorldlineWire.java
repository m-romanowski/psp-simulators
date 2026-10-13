package dev.marcinromanowski.pspsim.provider.worldline;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class WorldlineWire {

  static final String POST = "POST";

  static final String GET = "GET";

  static final String MERCHANT = "merchant-1";

  static final String ACCOUNT = "account-1";

  static final String PUBLIC_BASE_URL = "https://simulator.internal";

  private WorldlineWire() {}

  static WorldlineApiCall call(String method, String path, String body) {
    return call(method, path, Map.of(), body);
  }

  static WorldlineApiCall call(
      String method, String path, Map<String, List<String>> headers, String body) {
    Map<String, List<String>> sent = new LinkedHashMap<>(headers);
    sent.putIfAbsent(WorldlineFields.AUTHORIZATION, List.of(permissively(ACCOUNT)));
    return WorldlineApiCall.of(method, path, sent, Map.of(), body);
  }

  static String permissively(String accountId) {
    return WorldlineSignature.SCHEME + "key-" + accountId + ":not-a-signature";
  }

  static Map<String, List<String>> signed(
      WorldlineMerchant merchant, String method, String path, String contentType, Instant at) {
    return signed(merchant, method, path, contentType, at, Map.of());
  }

  static Map<String, List<String>> signed(
      WorldlineMerchant merchant,
      String method,
      String path,
      String contentType,
      Instant at,
      Map<String, List<String>> canonical) {
    Map<String, List<String>> headers = new LinkedHashMap<>(canonical);
    String date = WorldlineSignature.asWorldlineWritesIt(at);
    headers.put(WorldlineFields.DATE, List.of(date));
    if (!contentType.isEmpty()) {
      headers.put(WorldlineFields.CONTENT_TYPE, List.of(contentType));
    }
    String subject =
        WorldlineSignature.subjectOf(
            method,
            contentType,
            date,
            WorldlineSignature.canonicalHeaderLinesOf(headers),
            merchant.signedPathVersion().signedFormOf(path));
    headers.put(
        WorldlineFields.AUTHORIZATION,
        List.of(
            WorldlineSignature.SCHEME
                + merchant.apiKey()
                + WorldlineSignature.FIELD_SEPARATOR
                + WorldlineSignature.signed(merchant.apiSecret(), subject)));
    return headers;
  }

  static String bodyOf(WorldlineApiResponse response) {
    return new String(response.body(), StandardCharsets.UTF_8);
  }

  static String headerOf(WorldlineApiResponse response, String name) {
    return response.headers().getOrDefault(name, List.of()).stream().findFirst().orElse("");
  }

  static String aPaymentBody(long amount, String currencyCode, String token, String mode) {
    return "{\"order\":{\"amountOfMoney\":{\"amount\":"
        + amount
        + ",\"currencyCode\":\""
        + currencyCode
        + "\"}},\"cardPaymentMethodSpecificInput\":{\"token\":\""
        + token
        + "\",\"authorizationMode\":\""
        + mode
        + "\"}}";
  }
}
