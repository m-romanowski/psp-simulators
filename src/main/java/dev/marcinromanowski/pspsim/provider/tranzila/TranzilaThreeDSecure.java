package dev.marcinromanowski.pspsim.provider.tranzila;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.SequencedMap;
import java.util.Set;
import org.jspecify.annotations.Nullable;

record TranzilaThreeDSecure(
    boolean forceTxnOnFail,
    boolean forceChallenge,
    @Nullable String redirectUrl,
    SequencedMap<String, String> redirectParams) {

  static final String THREE_DS_SETTINGS = "3ds_settings";

  static final String BROWSER = "browser";

  static final String FORCE_TXN_ON_3DS_FAIL = "force_txn_on_3ds_fail";

  static final String FORCE_CHALLENGE = "force_challenge";

  static final String AUTH_3DS_REDIRECT = "auth_3ds_redirect";

  static final String URL = "url";

  static final String PARAMS = "params";

  static final String KEY = "key";

  static final String YES = "Y";

  static final String NO = "N";

  static final Set<String> THE_TOP_LEVEL_MEMBERS_THE_COMPLETE_EXAMPLE_ECHOES =
      Set.of(AUTH_3DS_REDIRECT, FORCE_TXN_ON_3DS_FAIL);

  static final Set<String> THE_SETTINGS =
      Set.of(BROWSER, FORCE_TXN_ON_3DS_FAIL, FORCE_CHALLENGE, AUTH_3DS_REDIRECT);

  static final Set<String> THE_BROWSER_TEXTS =
      Set.of("language", "ip", "user_agent", "accept_header", "window_size");

  static final String JAVA_ENABLED = "java_enabled";

  static final String COLOR_DEPTH = "color_depth";

  static final String TIME_ZONE = "time_zone";

  static final Set<Long> THE_COLOR_DEPTHS = Set.of(1L, 4L, 8L, 15L, 16L, 24L, 32L, 48L);

  static final Set<Long> A_FLAG = Set.of(0L, 1L);

  static final Map<String, String> THE_BROWSER_INTEGERS =
      Map.of(
          JAVA_ENABLED,
          " is neither 0 nor 1",
          COLOR_DEPTH,
          " is none of 1, 4, 8, 15, 16, 24, 32 and 48",
          TIME_ZONE,
          " must be an integer",
          "screen_height",
          " must not be negative",
          "screen_width",
          " must not be negative");

  static final Set<String> THE_REDIRECT_MEMBERS = Set.of(URL, PARAMS);

  TranzilaThreeDSecure {
    redirectParams = Collections.unmodifiableSequencedMap(new LinkedHashMap<>(redirectParams));
  }

  static TranzilaThreeDSecure asTheHostedFieldsAsk(boolean forceTxnOnFail, boolean forceChallenge) {
    return new TranzilaThreeDSecure(forceTxnOnFail, forceChallenge, null, new LinkedHashMap<>());
  }

  static void refuseWhatTheSchemaDoesNotAdmit(TranzilaJson.JsonObject body) {
    if (body.has(FORCE_TXN_ON_3DS_FAIL)
        && body.text(FORCE_TXN_ON_3DS_FAIL)
            .filter(sent -> YES.equals(sent) || NO.equals(sent))
            .isEmpty()) {
      throw new TranzilaSchemaMismatch(FORCE_TXN_ON_3DS_FAIL + " is neither Y nor N");
    }
    if (body.has(THREE_DS_SETTINGS)) {
      settingsIn(body);
    }
    if (body.has(AUTH_3DS_REDIRECT)) {
      theRedirectIn(body);
    }
  }

  static TranzilaThreeDSecure read(TranzilaJson.JsonObject body) {
    TranzilaJson.JsonObject settings =
        body.has(THREE_DS_SETTINGS) ? settingsIn(body) : TranzilaJson.object(Map.of());
    SequencedMap<String, String> params = new LinkedHashMap<>();
    String url = null;
    if (body.has(AUTH_3DS_REDIRECT)) {
      TranzilaJson.JsonObject redirect = theRedirectIn(body);
      url = redirect.text(URL).orElse(null);
      if (redirect.has(PARAMS)) {
        params.putAll(pairsIn(redirect, PARAMS));
      }
    }
    if (settings.has(AUTH_3DS_REDIRECT)) {
      pairsIn(settings, AUTH_3DS_REDIRECT).forEach(params::putIfAbsent);
    }
    return new TranzilaThreeDSecure(
        settings
            .text(FORCE_TXN_ON_3DS_FAIL)
            .or(() -> body.text(FORCE_TXN_ON_3DS_FAIL))
            .filter(YES::equals)
            .isPresent(),
        TranzilaCreateRequest.integer(settings, FORCE_CHALLENGE)
            .filter(asked -> asked.longValue() == 1L)
            .isPresent(),
        url,
        params);
  }

  private static TranzilaJson.JsonObject settingsIn(TranzilaJson.JsonObject body) {
    TranzilaJson.JsonObject settings =
        body.object(THREE_DS_SETTINGS)
            .orElseThrow(
                () -> new TranzilaSchemaMismatch(THREE_DS_SETTINGS + " must be an object"));
    for (String sent : settings.members().keySet()) {
      if (!THE_SETTINGS.contains(sent)) {
        throw new TranzilaSchemaMismatch(
            THREE_DS_SETTINGS + "." + sent + " is not a field of the 3DS schema");
      }
    }
    if (settings.has(FORCE_TXN_ON_3DS_FAIL)
        && settings
            .text(FORCE_TXN_ON_3DS_FAIL)
            .filter(sent -> YES.equals(sent) || NO.equals(sent))
            .isEmpty()) {
      throw new TranzilaSchemaMismatch(
          THREE_DS_SETTINGS + "." + FORCE_TXN_ON_3DS_FAIL + " is neither Y nor N");
    }
    if (settings.has(FORCE_CHALLENGE)
        && !A_FLAG.contains(
            TranzilaCreateRequest.integer(settings, FORCE_CHALLENGE).orElseThrow())) {
      throw new TranzilaSchemaMismatch(
          THREE_DS_SETTINGS + "." + FORCE_CHALLENGE + " is neither 0 nor 1");
    }
    if (settings.has(BROWSER)) {
      refuseABrowserOutsideTheSchema(settings);
    }
    if (settings.has(AUTH_3DS_REDIRECT)) {
      pairsIn(settings, AUTH_3DS_REDIRECT);
    }
    return settings;
  }

  private static void refuseABrowserOutsideTheSchema(TranzilaJson.JsonObject settings) {
    String where = THREE_DS_SETTINGS + "." + BROWSER;
    TranzilaJson.JsonObject browser =
        settings
            .object(BROWSER)
            .orElseThrow(() -> new TranzilaSchemaMismatch(where + " must be an object"));
    for (String sent : browser.members().keySet()) {
      String field = where + "." + sent;
      if (THE_BROWSER_TEXTS.contains(sent)) {
        if (browser.text(sent).isEmpty()) {
          throw new TranzilaSchemaMismatch(field + " must be a string");
        }
      } else if (THE_BROWSER_INTEGERS.containsKey(sent)) {
        long value = TranzilaCreateRequest.integer(browser, sent).orElseThrow().longValue();
        if (!theBrowserAdmits(sent, value)) {
          throw new TranzilaSchemaMismatch(field + THE_BROWSER_INTEGERS.get(sent));
        }
      } else {
        throw new TranzilaSchemaMismatch(field + " is not a field of the 3DS schema");
      }
    }
  }

  static boolean theBrowserAdmits(String field, long value) {
    return switch (field) {
      case JAVA_ENABLED -> A_FLAG.contains(Long.valueOf(value));
      case COLOR_DEPTH -> THE_COLOR_DEPTHS.contains(Long.valueOf(value));
      case TIME_ZONE -> true;
      default -> value >= 0;
    };
  }

  private static TranzilaJson.JsonObject theRedirectIn(TranzilaJson.JsonObject body) {
    TranzilaJson.JsonObject redirect =
        body.object(AUTH_3DS_REDIRECT)
            .orElseThrow(
                () ->
                    new TranzilaSchemaMismatch(
                        AUTH_3DS_REDIRECT
                            + " at the top of the body is the object {url, params} the complete"
                            + " example carries; as an array of {key, value} it belongs in "
                            + THREE_DS_SETTINGS));
    for (String sent : redirect.members().keySet()) {
      if (!THE_REDIRECT_MEMBERS.contains(sent)) {
        throw new TranzilaSchemaMismatch(
            AUTH_3DS_REDIRECT + "." + sent + " is not a field of the complete example");
      }
    }
    String url =
        redirect
            .text(URL)
            .orElseThrow(
                () -> new TranzilaSchemaMismatch(AUTH_3DS_REDIRECT + "." + URL + " is required"));
    if (!TranzilaPageRequest.isAnAddress(url)) {
      throw new TranzilaSchemaMismatch(
          AUTH_3DS_REDIRECT
              + "."
              + URL
              + " is not an absolute http or https address a browser could be sent to");
    }
    if (redirect.has(PARAMS)) {
      pairsIn(redirect, PARAMS);
    }
    return redirect;
  }

  private static SequencedMap<String, String> pairsIn(TranzilaJson.JsonObject holder, String name) {
    List<TranzilaJson> listed =
        holder
            .array(name)
            .map(TranzilaJson.JsonArray::elements)
            .orElseThrow(() -> new TranzilaSchemaMismatch(name + " must be an array"));
    SequencedMap<String, String> pairs = new LinkedHashMap<>();
    for (TranzilaJson element : listed) {
      if (!(element instanceof TranzilaJson.JsonObject pair)) {
        throw new TranzilaSchemaMismatch("every element of " + name + " is an object");
      }
      for (String sent : pair.members().keySet()) {
        if (!KEY.equals(sent) && !TranzilaFields.VALUE.equals(sent)) {
          throw new TranzilaSchemaMismatch(name + "." + sent + " is not a field of {key, value}");
        }
      }
      String key =
          pair.text(KEY)
              .orElseThrow(
                  () -> new TranzilaSchemaMismatch(name + "." + KEY + " must be a string"));
      String value =
          pair.text(TranzilaFields.VALUE)
              .orElseThrow(
                  () ->
                      new TranzilaSchemaMismatch(
                          name + "." + TranzilaFields.VALUE + " must be a string"));
      pairs.putIfAbsent(key, value);
    }
    return pairs;
  }
}
