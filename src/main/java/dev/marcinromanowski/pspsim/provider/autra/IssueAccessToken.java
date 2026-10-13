package dev.marcinromanowski.pspsim.provider.autra;

import dev.marcinromanowski.pspsim.kernel.ids.IdGenerator;
import dev.marcinromanowski.pspsim.kernel.testrun.TestRunId;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import org.jspecify.annotations.Nullable;

final class IssueAccessToken {

  static final int TOKEN_ATTEMPTS = 5;

  static final int TOKEN_ENTROPY_BYTES = 24;

  private static final String JWT_HEADER = "{\"alg\":\"HS256\",\"typ\":\"JWT\"}";

  private final AutraSessions sessions;
  private final IdGenerator ids;

  IssueAccessToken(AutraSessions sessions, IdGenerator ids) {
    this.sessions = sessions;
    this.ids = ids;
  }

  AutraOperationResult handle(
      AutraApiCall call, TestRunId testRunId, @Nullable AutraMerchant candidate, Instant now) {
    AutraOauthFailure failure =
        candidate == null
            ? AutraOauthFailure.BAD_REQUEST_WITH_ERROR_STRING
            : candidate.oauthFailure();
    String refused = theRefusalOf(call, candidate);
    if (refused != null) {
      return refusal(failure, refused);
    }
    AutraMerchant merchant = Objects.requireNonNull(candidate);
    AutraAccessToken token = aTokenClaimedFor(testRunId, merchant, now);
    return AutraOperationResult.succeeded(null, granted(merchant, token));
  }

  private static @Nullable String theRefusalOf(
      AutraApiCall call, @Nullable AutraMerchant candidate) {
    Optional<String> authorization = call.header(AutraFields.AUTHORIZATION);
    if (authorization.isEmpty()) {
      return AutraOauthRefusal.MISSING_AUTHORIZATION_HEADER;
    }
    if (!call.namesTheQueryParameter(AutraFields.GRANT_TYPE)) {
      return AutraOauthRefusal.MISSING_GRANT_TYPE;
    }
    if (!call.queryParameter(AutraFields.GRANT_TYPE)
        .filter(AutraFields.CLIENT_CREDENTIALS::equals)
        .isPresent()) {
      return AutraOauthRefusal.UNSUPPORTED_GRANT_TYPE;
    }
    String sent = authorization.get();
    if (!sent.startsWith(AutraFields.BASIC)) {
      return AutraOauthRefusal.MISSING_AUTHORIZATION_BASIC;
    }
    String decoded;
    try {
      decoded =
          new String(
              Base64.getDecoder().decode(sent.substring(AutraFields.BASIC.length())),
              StandardCharsets.UTF_8);
    } catch (IllegalArgumentException notBase64) {
      return AutraOauthRefusal.INVALID_BASE64;
    }
    int separator = decoded.indexOf(':');
    if (separator < 0) {
      return AutraOauthRefusal.INVALID_BASIC_FORMAT;
    }
    if (candidate == null) {
      return AutraOauthRefusal.INVALID_CLIENT_ID;
    }
    if (!candidate.clientSecret().equals(decoded.substring(separator + 1))) {
      return AutraOauthRefusal.INVALID_CLIENT_SECRET;
    }
    return null;
  }

  static Optional<String> clientIdIn(AutraApiCall call) {
    String sent = call.header(AutraFields.AUTHORIZATION).orElse("");
    if (!sent.startsWith(AutraFields.BASIC)) {
      return Optional.empty();
    }
    String decoded;
    try {
      decoded =
          new String(
              Base64.getDecoder().decode(sent.substring(AutraFields.BASIC.length())),
              StandardCharsets.UTF_8);
    } catch (IllegalArgumentException notBase64) {
      return Optional.empty();
    }
    int separator = decoded.indexOf(':');
    return separator < 0 ? Optional.empty() : Optional.of(decoded.substring(0, separator));
  }

  private static AutraOperationResult refusal(AutraOauthFailure failure, String message) {
    return AutraOperationResult.failed(
        AutraRefusals.theCredentialWasRefused(),
        AutraBody.of(failure.httpStatus(), AutraErrors.oauthEnvelope(failure.bodyFor(message))));
  }

  private AutraAccessToken aTokenClaimedFor(
      TestRunId testRunId, AutraMerchant merchant, Instant now) {
    for (int attempt = 0; attempt < TOKEN_ATTEMPTS; attempt++) {
      AutraAccessToken token =
          new AutraAccessToken(
              testRunId,
              aJwtShapedToken(),
              merchant.accountId(),
              now,
              now.plus(AutraAccessToken.PUBLISHED_VALIDITY));
      if (sessions.mint(token)) {
        return token;
      }
    }
    throw new IllegalStateException(
        "no Autra access token could be claimed in " + TOKEN_ATTEMPTS + " attempts");
  }

  private String aJwtShapedToken() {
    Base64.Encoder encoder = Base64.getUrlEncoder().withoutPadding();
    String header = encoder.encodeToString(JWT_HEADER.getBytes(StandardCharsets.UTF_8));
    String payload =
        encoder.encodeToString(ids.hex(TOKEN_ENTROPY_BYTES).getBytes(StandardCharsets.UTF_8));
    String signature =
        encoder.encodeToString(ids.hex(TOKEN_ENTROPY_BYTES).getBytes(StandardCharsets.UTF_8));
    return header + "." + payload + "." + signature;
  }

  private static AutraBody granted(AutraMerchant merchant, AutraAccessToken token) {
    AutraOauthFieldCasing casing = merchant.oauthFieldCasing();
    Map<String, AutraJson> granted = new LinkedHashMap<>();
    granted.put(casing.accessToken(), AutraJson.text(token.value()));
    granted.put(casing.tokenType(), AutraJson.text(AutraAccessToken.TOKEN_TYPE));
    granted.put(casing.expiresIn(), AutraJson.number(Long.toString(token.expiresInSeconds())));
    return AutraBody.of(200, AutraJson.object(granted));
  }
}
