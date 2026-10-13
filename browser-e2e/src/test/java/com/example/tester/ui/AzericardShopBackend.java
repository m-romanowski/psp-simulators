package com.example.tester.ui;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpRequest;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.Signature;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

public final class AzericardShopBackend {

  public static final String AZERICARD = "azericard";

  public static final String CARD_SAVE_URL = "/token/cgi_link";

  public static final String GATEWAY_URL = "/cgi-bin/cgi_link";

  private static final DateTimeFormatter WIRE_TIME = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

  private final String baseUrl;
  private final TheProviderApi providerApi;
  private final String terminal;
  private final KeyPair merchant = aKeyPair();
  private final KeyPair gateway = aKeyPair();

  public AzericardShopBackend(String baseUrl, TheProviderApi providerApi) {
    this.baseUrl = baseUrl;
    this.providerApi = providerApi;
    this.terminal = String.valueOf(ThreadLocalRandom.current().nextLong(10_000_000L, 99_999_999L));
  }

  public String terminal() {
    return terminal;
  }

  public String itsPublicKey() {
    return pem("PUBLIC KEY", merchant.getPublic().getEncoded());
  }

  public String theKeyTheGatewaySignsWith() {
    return pem("PRIVATE KEY", gateway.getPrivate().getEncoded());
  }

  public String anOrder() {
    return ZonedDateTime.now(ZoneOffset.UTC).format(WIRE_TIME)
        + ThreadLocalRandom.current().nextInt(100, 999);
  }

  public Map<String, String> aSignedCardSave(
      String order, String amount, String merchantUrl, String callbackUrl) {
    Map<String, String> form = anAuthorization("1", order, amount, merchantUrl, callbackUrl);
    form.put("TOKEN_ACTION", "REGISTER");
    form.put("MERCH_TRAN_STATE", "S");
    form.put("MIT_AGREEMENT", "N");
    form.put("MERCH_RN_ID", aNonce());
    return signed(form);
  }

  public Map<String, String> aSignedHold(
      String order, String amount, String merchantUrl, String callbackUrl) {
    return signed(anAuthorization("0", order, amount, merchantUrl, callbackUrl));
  }

  public String paysOffSessionWith(
      String token, String extNetRef, String order, String amount, String callbackUrl) {
    Map<String, String> form = anAuthorization("1", order, amount, "", callbackUrl);
    form.remove("MERCH_URL");
    form.put("MERCH_TRAN_STATE", "M");
    form.put("TOKEN", token);
    form.put("EXT_NET_REF", extNetRef);
    form.put("MERCH_RN_ID", aNonce());
    return post(CARD_SAVE_URL, signed(form));
  }

  public String completes(Map<String, String> callback, String amount) {
    return post(GATEWAY_URL, settlement("21", callback, amount));
  }

  public String releases(Map<String, String> callback, String amount) {
    return post(GATEWAY_URL, settlement("22", callback, amount));
  }

  public String asksTheStatusOf(String order, String originalType) {
    Map<String, String> form = new LinkedHashMap<>();
    form.put("TRAN_TRTYPE", originalType);
    form.put("ORDER", order);
    form.put("TERMINAL", terminal);
    form.put("TRTYPE", "90");
    form.put("TIMESTAMP", now());
    form.put("NONCE", aNonce());
    form.put(
        "P_SIGN",
        sign(
            form.get("ORDER"),
            form.get("TERMINAL"),
            form.get("TRTYPE"),
            form.get("TIMESTAMP"),
            form.get("NONCE")));
    return post(GATEWAY_URL, form);
  }

  public boolean theGatewaySigned(Map<String, String> callback) {
    try {
      Signature verifier = Signature.getInstance("SHA256withRSA");
      verifier.initVerify(gateway.getPublic());
      verifier.update(
          macSource(
                  callback.get("AMOUNT"),
                  callback.get("TERMINAL"),
                  callback.get("APPROVAL"),
                  callback.get("RRN"),
                  callback.get("INT_REF"))
              .getBytes(StandardCharsets.UTF_8));
      return verifier.verify(HexFormat.of().parseHex(callback.get("P_SIGN")));
    } catch (GeneralSecurityException | IllegalArgumentException notASignature) {
      return false;
    }
  }

  public static String element(String tag, String xml) {
    int start = xml.indexOf("<" + tag + ">");
    if (start < 0) {
      return "";
    }
    return xml.substring(start + tag.length() + 2, xml.indexOf("</" + tag + ">", start));
  }

  public static String theWebviewPosting(String action, Map<String, String> fields) {
    StringBuilder inputs = new StringBuilder();
    fields.forEach(
        (name, value) ->
            inputs
                .append("<input type=\"hidden\" name=\"")
                .append(escaped(name))
                .append("\" value=\"")
                .append(escaped(value))
                .append("\">"));
    return "<!DOCTYPE html><html lang=\"en\"><head><meta charset=\"utf-8\"><title>the app</title>"
        + "</head><body><form method=\"post\" action=\""
        + escaped(action)
        + "\">"
        + inputs
        + "</form><script>document.forms[0].submit()</script></body></html>";
  }

  private Map<String, String> anAuthorization(
      String trtype, String order, String amount, String merchantUrl, String callbackUrl) {
    Map<String, String> form = new LinkedHashMap<>();
    form.put("AMOUNT", amount);
    form.put("CURRENCY", "AZN");
    form.put("ORDER", order);
    form.put("DESC", "Order " + order);
    form.put("MERCH_NAME", "The Shop");
    form.put("MERCH_URL", merchantUrl);
    form.put("TERMINAL", terminal);
    form.put("TRTYPE", trtype);
    form.put("BACKREF", callbackUrl);
    form.put("TIMESTAMP", now());
    form.put("NONCE", aNonce());
    return form;
  }

  private Map<String, String> signed(Map<String, String> form) {
    form.put(
        "P_SIGN",
        sign(
            form.get("AMOUNT"),
            form.get("CURRENCY"),
            form.get("TERMINAL"),
            form.get("TRTYPE"),
            form.get("TIMESTAMP"),
            form.get("NONCE"),
            form.get("MERCH_URL")));
    return form;
  }

  private Map<String, String> settlement(
      String trtype, Map<String, String> callback, String amount) {
    Map<String, String> form = new LinkedHashMap<>();
    form.put("AMOUNT", amount);
    form.put("CURRENCY", "AZN");
    form.put("ORDER", callback.get("ORDER"));
    form.put("RRN", callback.get("RRN"));
    form.put("INT_REF", callback.get("INT_REF"));
    form.put("TERMINAL", terminal);
    form.put("TRTYPE", trtype);
    form.put("TIMESTAMP", now());
    form.put("NONCE", aNonce());
    form.put(
        "P_SIGN",
        sign(
            amount,
            "AZN",
            form.get("ORDER"),
            form.get("RRN"),
            form.get("INT_REF"),
            terminal,
            trtype,
            form.get("TIMESTAMP"),
            form.get("NONCE")));
    return form;
  }

  private String post(String path, Map<String, String> form) {
    StringBuilder body = new StringBuilder();
    form.forEach(
        (name, value) -> {
          if (!body.isEmpty()) {
            body.append('&');
          }
          body.append(URLEncoder.encode(name, StandardCharsets.UTF_8))
              .append('=')
              .append(URLEncoder.encode(value, StandardCharsets.UTF_8));
        });
    return providerApi
        .send(
            HttpRequest.newBuilder(URI.create(baseUrl + path))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(body.toString(), StandardCharsets.UTF_8))
                .build())
        .body();
  }

  private String sign(String... values) {
    try {
      PrivateKey key = merchant.getPrivate();
      Signature signer = Signature.getInstance("SHA256withRSA");
      signer.initSign(key);
      signer.update(macSource(values).getBytes(StandardCharsets.UTF_8));
      return HexFormat.of().formatHex(signer.sign()).toUpperCase(Locale.ROOT);
    } catch (GeneralSecurityException unusable) {
      throw new IllegalStateException("the shop could not sign its request", unusable);
    }
  }

  private static String macSource(String... values) {
    StringBuilder source = new StringBuilder();
    for (String value : values) {
      if (value == null || value.isEmpty()) {
        source.append('-');
      } else {
        source.append(value.length()).append(value);
      }
    }
    return source.toString();
  }

  private static String now() {
    return ZonedDateTime.now(ZoneOffset.UTC).format(WIRE_TIME);
  }

  private static String aNonce() {
    byte[] random = new byte[8];
    ThreadLocalRandom.current().nextBytes(random);
    return HexFormat.of().formatHex(random).toUpperCase(Locale.ROOT);
  }

  private static String escaped(String text) {
    return text.replace("&", "&amp;").replace("\"", "&quot;").replace("<", "&lt;");
  }

  private static String pem(String label, byte[] encoded) {
    String body = Base64.getMimeEncoder(64, new byte[] {'\n'}).encodeToString(encoded);
    return "-----BEGIN " + label + "-----\n" + body + "\n-----END " + label + "-----\n";
  }

  private static KeyPair aKeyPair() {
    try {
      KeyPairGenerator keys = KeyPairGenerator.getInstance("RSA");
      keys.initialize(2048);
      return keys.generateKeyPair();
    } catch (GeneralSecurityException unavailable) {
      throw new IllegalStateException("no RSA key pair could be generated", unavailable);
    }
  }
}
