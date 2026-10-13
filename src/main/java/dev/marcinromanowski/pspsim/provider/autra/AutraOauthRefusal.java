package dev.marcinromanowski.pspsim.provider.autra;

final class AutraOauthRefusal {

  static final String MISSING_AUTHORIZATION_HEADER = "missing Authorization header";

  static final String MISSING_GRANT_TYPE = "missing grant_type";

  static final String UNSUPPORTED_GRANT_TYPE = "unsupported grant_type";

  static final String MISSING_AUTHORIZATION_BASIC = "missing Authorization Basic";

  static final String INVALID_BASE64 = "invalid base64 in Authorization header";

  static final String INVALID_BASIC_FORMAT =
      "invalid Basic Auth format; expected clientId:clientSecret";

  static final String INVALID_CLIENT_ID = "invalid clientId or inactive credentials";

  static final String INVALID_CLIENT_SECRET = "invalid clientSecret";

  private AutraOauthRefusal() {}
}
