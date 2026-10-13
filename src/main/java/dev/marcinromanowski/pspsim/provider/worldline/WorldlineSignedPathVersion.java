package dev.marcinromanowski.pspsim.provider.worldline;

import dev.marcinromanowski.pspsim.kernel.spi.AcceptedValue;
import java.util.List;
import org.jspecify.annotations.Nullable;

enum WorldlineSignedPathVersion {
  V2_AS_THE_SCHEMA_AND_THE_PROPERTY_TABLE_PUBLISH_IT("v2"),

  V1_AS_THE_WORKED_EXAMPLES_SIGN_IT("v1");

  static final String SETTING = "worldline.signed-path-version";

  static final String WHY_THERE_ARE_TWO =
      "provider worldline publishes the version of the request path inside the signed canonical"
          + " string two ways, and the request path is the last line of that string, so a"
          + " simulator that signs one version and a client that signs the other never agree and"
          + " the failure looks like a bad secret. The versioning page names v2 — \"The major"
          + " versions of our APIs are indicated in the URLs (e.g., v2)\" — and the"
          + " manual-authentication page's own property table agrees, describing the request path"
          + " as \"The relative path of the GoPay API you send the request to, including the API"
          + " version and your merchantId i.e. \"/v2/merchantId/hostedcheckouts\"\". The same"
          + " page's worked signature subjects sign v1: the request-with-a-body example ends"
          + " \"/v1/{merchantId}/payments\" and the request-without-a-body example ends"
          + " \"/v1/{merchantId}/payments/{paymentId}\". Every path in the published schema is"
          + " /v2/. This simulator routes v2 either way, because that is what the schema"
          + " publishes and what the SDKs call; this setting decides only which string the"
          + " signature is computed over";

  private final String inTheSignedPath;

  WorldlineSignedPathVersion(String inTheSignedPath) {
    this.inTheSignedPath = inTheSignedPath;
  }

  String signedFormOf(String routedPath) {
    return routedPath.replaceFirst(
        "^/" + V2_AS_THE_SCHEMA_AND_THE_PROPERTY_TABLE_PUBLISH_IT.inTheSignedPath + "/",
        "/" + inTheSignedPath + "/");
  }

  static List<AcceptedValue> acceptedValues() {
    return List.of(
        new AcceptedValue(
            V2_AS_THE_SCHEMA_AND_THE_PROPERTY_TABLE_PUBLISH_IT.name(),
            "your client must sign the path as /v2/..., the version worldline's schema and its own"
                + " property table publish"),
        new AcceptedValue(
            V1_AS_THE_WORKED_EXAMPLES_SIGN_IT.name(),
            "your client must sign the path as /v1/..., the version worldline's worked signature"
                + " examples use"));
  }

  static WorldlineSignedPathVersion of(String property, @Nullable String declared) {
    if (declared == null || declared.isBlank()) {
      return V2_AS_THE_SCHEMA_AND_THE_PROPERTY_TABLE_PUBLISH_IT;
    }
    for (WorldlineSignedPathVersion configured : values()) {
      if (configured.name().equals(declared)) {
        return configured;
      }
    }
    throw new IllegalArgumentException(
        property
            + " names neither of the two path versions provider worldline publishes inside the"
            + " signed canonical string. "
            + WHY_THERE_ARE_TWO
            + ". The two values are "
            + V2_AS_THE_SCHEMA_AND_THE_PROPERTY_TABLE_PUBLISH_IT.name()
            + ", the default, and "
            + V1_AS_THE_WORKED_EXAMPLES_SIGN_IT.name());
  }
}
