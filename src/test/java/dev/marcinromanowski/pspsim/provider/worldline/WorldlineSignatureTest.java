package dev.marcinromanowski.pspsim.provider.worldline;

import static org.assertj.core.api.Assertions.assertThat;

import dev.marcinromanowski.pspsim.kernel.testrun.TestRunId;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class WorldlineSignatureTest {

  private static final TestRunId RUN = TestRunId.of("run-worldline");

  private static final Instant WHEN = Instant.parse("2026-09-22T10:00:00Z");

  private final WorldlineDoubles.Merchants merchants = new WorldlineDoubles.Merchants();
  private final WorldlineDoubles.Tokenizations sessions = new WorldlineDoubles.Tokenizations();
  private final WorldlineDoubles.Payments payments = new WorldlineDoubles.Payments();
  private final WorldlineDoubles.Keys keys = new WorldlineDoubles.Keys();
  private final WorldlineDoubles.Audit audit = new WorldlineDoubles.Audit();

  private WorldlineMerchant verifying(Map<String, String> configuration) {
    Map<String, String> declared = new LinkedHashMap<>(configuration);
    declared.put(
        WorldlineAuthentication.SETTING, WorldlineAuthentication.THE_SIGNATURE_IS_VERIFIED.name());
    WorldlineMerchant merchant =
        WorldlineDoubles.merchant(RUN, WorldlineWire.ACCOUNT, WorldlineWire.MERCHANT, declared);
    merchants.with(merchant);
    return merchant;
  }

  private WorldlineApi api() {
    return WorldlineWiring.api(merchants, sessions, payments, keys, audit, RUN, WHEN);
  }

  @Test
  void the_canonical_string_is_the_published_one_and_ends_with_the_request_path() {
    String subject =
        WorldlineSignature.subjectOf(
            "GET",
            "",
            "Tue, 22 Sep 2026 10:00:00 GMT",
            List.of("x-gcs-idempotency-key:abc"),
            "/v2/merchant-1/payments/1_0");
    assertThat(subject)
        .isEqualTo(
            """
            GET

            Tue, 22 Sep 2026 10:00:00 GMT
            x-gcs-idempotency-key:abc
            /v2/merchant-1/payments/1_0
            """);
  }

  @Test
  void a_canonical_header_is_lowercased_trimmed_and_its_internal_whitespace_collapsed() {
    List<String> lines =
        WorldlineSignature.canonicalHeaderLinesOf(
            Map.of("X-GCS-Something", List.of("  two   words  ")));
    assertThat(lines).containsExactly("x-gcs-something:two words");
  }

  @Test
  void the_canonical_header_lines_are_sorted_alphabetically_whatever_order_they_arrived_in() {
    Map<String, List<String>> asTheClientSentThem = new LinkedHashMap<>();
    asTheClientSentThem.put("X-GCS-Zulu", List.of("z"));
    asTheClientSentThem.put("X-GCS-Mike", List.of("m"));
    asTheClientSentThem.put("X-GCS-Alpha", List.of("a"));

    assertThat(WorldlineSignature.canonicalHeaderLinesOf(asTheClientSentThem))
        .containsExactly("x-gcs-alpha:a", "x-gcs-mike:m", "x-gcs-zulu:z");
  }

  @Test
  void a_merchant_id_of_exactly_the_length_the_store_holds_is_configured_rather_than_refused() {
    assertThat(
            WorldlineDoubles.merchant(
                    RUN,
                    WorldlineWire.ACCOUNT,
                    "m".repeat(WorldlineMerchant.LONGEST_MERCHANT_ID_THE_STORE_HOLDS))
                .merchantId())
        .hasSize(WorldlineMerchant.LONGEST_MERCHANT_ID_THE_STORE_HOLDS);
  }

  @Test
  void the_error_identifier_of_a_refusal_is_the_uuid_shape_the_published_example_prints() {
    verifying(Map.of());
    String path = WorldlineApiController.theHostedTokenizationsOf(WorldlineWire.MERCHANT);

    WorldlineApiResponse refused =
        api()
            .createHostedTokenization(
                WorldlineApiCall.of(WorldlineWire.POST, path, Map.of(), Map.of(), "{}"),
                WorldlineWire.MERCHANT);

    assertThat(WorldlineWire.bodyOf(refused))
        .containsPattern(
            "\"errorId\":\"[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}\"");
  }

  @Test
  void a_header_that_does_not_begin_with_the_published_prefix_is_not_part_of_the_signed_string() {
    List<String> lines =
        WorldlineSignature.canonicalHeaderLinesOf(
            Map.of("Authorization", List.of("GCS v1HMAC:k:s"), "X-Other", List.of("v")));
    assertThat(lines).isEmpty();
  }

  @Test
  void a_request_signed_over_the_published_canonical_string_is_answered_by_the_operation() {
    WorldlineMerchant merchant = verifying(Map.of());
    String path = WorldlineApiController.theHostedTokenizationsOf(WorldlineWire.MERCHANT);
    WorldlineApiResponse answered =
        api()
            .createHostedTokenization(
                WorldlineApiCall.of(
                    WorldlineWire.POST,
                    path,
                    WorldlineWire.signed(
                        merchant, WorldlineWire.POST, path, WorldlineFields.JSON, WHEN),
                    Map.of(),
                    "{}"),
                WorldlineWire.MERCHANT);
    assertThat(answered.httpStatus()).isEqualTo(WorldlineAnswers.OK);
  }

  @Test
  void a_request_whose_signature_was_made_with_another_secret_is_answered_403_and_not_401() {
    WorldlineMerchant merchant = verifying(Map.of());
    String path = WorldlineApiController.theHostedTokenizationsOf(WorldlineWire.MERCHANT);
    Map<String, List<String>> headers =
        new LinkedHashMap<>(
            WorldlineWire.signed(merchant, WorldlineWire.POST, path, WorldlineFields.JSON, WHEN));
    headers.put(
        WorldlineFields.AUTHORIZATION,
        List.of(WorldlineSignature.SCHEME + merchant.apiKey() + ":not-the-signature"));
    WorldlineApiResponse answered =
        api()
            .createHostedTokenization(
                WorldlineApiCall.of(WorldlineWire.POST, path, headers, Map.of(), "{}"),
                WorldlineWire.MERCHANT);
    assertThat(answered.httpStatus()).isEqualTo(WorldlineAnswers.NOT_AUTHORIZED);
    assertThat(WorldlineWire.bodyOf(answered))
        .contains(WorldlineErrors.ACCESS_TO_MERCHANT_NOT_ALLOWED);
  }

  @Test
  void a_request_whose_date_is_older_than_the_five_minutes_the_platform_publishes_is_refused() {
    WorldlineMerchant merchant = verifying(Map.of());
    String path = WorldlineApiController.theHostedTokenizationsOf(WorldlineWire.MERCHANT);
    Instant stale = WHEN.minusSeconds(301);
    WorldlineApiResponse answered =
        api()
            .createHostedTokenization(
                WorldlineApiCall.of(
                    WorldlineWire.POST,
                    path,
                    WorldlineWire.signed(
                        merchant, WorldlineWire.POST, path, WorldlineFields.JSON, stale),
                    Map.of(),
                    "{}"),
                WorldlineWire.MERCHANT);
    assertThat(answered.httpStatus()).isEqualTo(WorldlineAnswers.NOT_AUTHORIZED);
    assertThat(WorldlineWire.bodyOf(answered)).contains("older than 5 minutes");
  }

  @Test
  void a_date_exactly_five_minutes_old_is_still_inside_the_window_the_platform_publishes() {
    WorldlineMerchant merchant = verifying(Map.of());
    String path = WorldlineApiController.theHostedTokenizationsOf(WorldlineWire.MERCHANT);
    Instant edge = WHEN.minusSeconds(300);
    WorldlineApiResponse answered =
        api()
            .createHostedTokenization(
                WorldlineApiCall.of(
                    WorldlineWire.POST,
                    path,
                    WorldlineWire.signed(
                        merchant, WorldlineWire.POST, path, WorldlineFields.JSON, edge),
                    Map.of(),
                    "{}"),
                WorldlineWire.MERCHANT);
    assertThat(answered.httpStatus()).isEqualTo(WorldlineAnswers.OK);
  }

  @Test
  void a_request_carrying_no_date_header_at_all_is_refused_where_the_signature_is_verified() {
    verifying(Map.of());
    String path = WorldlineApiController.theHostedTokenizationsOf(WorldlineWire.MERCHANT);
    WorldlineApiResponse answered =
        api()
            .createHostedTokenization(
                WorldlineApiCall.of(
                    WorldlineWire.POST,
                    path,
                    Map.of(
                        WorldlineFields.AUTHORIZATION,
                        List.of(WorldlineSignature.SCHEME + "key-" + WorldlineWire.ACCOUNT + ":s")),
                    Map.of(),
                    "{}"),
                WorldlineWire.MERCHANT);
    assertThat(answered.httpStatus()).isEqualTo(WorldlineAnswers.NOT_AUTHORIZED);
    assertThat(WorldlineWire.bodyOf(answered)).contains("RFC");
  }

  @Test
  void an_authorization_header_that_is_not_the_published_scheme_names_no_account_at_all() {
    verifying(Map.of());
    String path = WorldlineApiController.theHostedTokenizationsOf(WorldlineWire.MERCHANT);
    WorldlineApiResponse answered =
        api()
            .createHostedTokenization(
                WorldlineApiCall.of(
                    WorldlineWire.POST,
                    path,
                    Map.of(WorldlineFields.AUTHORIZATION, List.of("Bearer something")),
                    Map.of(),
                    "{}"),
                WorldlineWire.MERCHANT);
    assertThat(answered.httpStatus()).isEqualTo(WorldlineAnswers.NOT_AUTHORIZED);
    assertThat(WorldlineWire.bodyOf(answered))
        .contains(WorldlineApi.NO_ACCOUNT_ANSWERS_TO_THAT_KEY_AND_MERCHANT);
  }

  @Test
  void
      an_account_that_signs_the_path_version_the_worked_examples_use_refuses_a_v2_signed_request() {
    WorldlineMerchant merchant =
        verifying(
            Map.of(
                WorldlineSignedPathVersion.SETTING,
                WorldlineSignedPathVersion.V1_AS_THE_WORKED_EXAMPLES_SIGN_IT.name()));
    String path = WorldlineApiController.theHostedTokenizationsOf(WorldlineWire.MERCHANT);
    Map<String, List<String>> asV2 =
        new LinkedHashMap<>(
            WorldlineWire.signed(merchant, WorldlineWire.POST, path, WorldlineFields.JSON, WHEN));
    String date = asV2.get(WorldlineFields.DATE).get(0);
    String v2Subject =
        WorldlineSignature.subjectOf(
            WorldlineWire.POST,
            WorldlineFields.JSON,
            date,
            WorldlineSignature.canonicalHeaderLinesOf(asV2),
            path);
    asV2.put(
        WorldlineFields.AUTHORIZATION,
        List.of(
            WorldlineSignature.SCHEME
                + merchant.apiKey()
                + ":"
                + WorldlineSignature.signed(merchant.apiSecret(), v2Subject)));
    WorldlineApiResponse answered =
        api()
            .createHostedTokenization(
                WorldlineApiCall.of(WorldlineWire.POST, path, asV2, Map.of(), "{}"),
                WorldlineWire.MERCHANT);
    assertThat(answered.httpStatus()).isEqualTo(WorldlineAnswers.NOT_AUTHORIZED);
    assertThat(WorldlineWire.bodyOf(answered)).contains(WorldlineApi.THE_SIGNATURE_DOES_NOT_MATCH);
  }

  @Test
  void the_v1_reading_signs_the_same_path_with_the_other_version_and_is_accepted_under_it() {
    WorldlineMerchant merchant =
        verifying(
            Map.of(
                WorldlineSignedPathVersion.SETTING,
                WorldlineSignedPathVersion.V1_AS_THE_WORKED_EXAMPLES_SIGN_IT.name()));
    String path = WorldlineApiController.theHostedTokenizationsOf(WorldlineWire.MERCHANT);
    WorldlineApiResponse answered =
        api()
            .createHostedTokenization(
                WorldlineApiCall.of(
                    WorldlineWire.POST,
                    path,
                    WorldlineWire.signed(
                        merchant, WorldlineWire.POST, path, WorldlineFields.JSON, WHEN),
                    Map.of(),
                    "{}"),
                WorldlineWire.MERCHANT);
    assertThat(answered.httpStatus()).isEqualTo(WorldlineAnswers.OK);
  }

  @Test
  void the_v1_setting_rewrites_only_the_version_segment_of_the_path_it_signs() {
    assertThat(
            WorldlineSignedPathVersion.V1_AS_THE_WORKED_EXAMPLES_SIGN_IT.signedFormOf(
                "/v2/merchant-1/payments/3066019730_0/capture"))
        .isEqualTo("/v1/merchant-1/payments/3066019730_0/capture");
  }

  @Test
  void the_v2_setting_leaves_the_path_it_signs_exactly_as_the_request_carried_it() {
    assertThat(
            WorldlineSignedPathVersion.V2_AS_THE_SCHEMA_AND_THE_PROPERTY_TABLE_PUBLISH_IT
                .signedFormOf("/v2/merchant-1/payments"))
        .isEqualTo("/v2/merchant-1/payments");
  }

  @Test
  void an_account_that_names_neither_published_path_version_is_refused_at_configuration_time() {
    assertThat(
            Assertions.assertThrows(
                IllegalArgumentException.class,
                () ->
                    WorldlineSignedPathVersion.of(
                        WorldlineSignedPathVersion.SETTING, "V3_NOBODY_PUBLISHES")))
        .hasMessageContaining(WorldlineSignedPathVersion.SETTING);
  }

  @Test
  void a_request_of_an_account_that_does_not_verify_the_signature_needs_no_date_and_no_signature() {
    merchants.with(WorldlineDoubles.merchant(RUN, WorldlineWire.ACCOUNT, WorldlineWire.MERCHANT));
    WorldlineApiResponse answered =
        api()
            .createHostedTokenization(
                WorldlineWire.call(
                    WorldlineWire.POST,
                    WorldlineApiController.theHostedTokenizationsOf(WorldlineWire.MERCHANT),
                    "{}"),
                WorldlineWire.MERCHANT);
    assertThat(answered.httpStatus()).isEqualTo(WorldlineAnswers.OK);
  }

  @Test
  void a_call_naming_a_merchant_the_configured_key_does_not_belong_to_is_refused() {
    verifying(Map.of());
    WorldlineApiResponse answered =
        api()
            .createHostedTokenization(
                WorldlineWire.call(
                    WorldlineWire.POST,
                    WorldlineApiController.theHostedTokenizationsOf("another-merchant"),
                    "{}"),
                "another-merchant");
    assertThat(answered.httpStatus()).isEqualTo(WorldlineAnswers.NOT_AUTHORIZED);
  }

  @Test
  void the_authorization_header_is_never_written_into_the_audit_trail() {
    verifying(Map.of());
    String path = WorldlineApiController.theHostedTokenizationsOf(WorldlineWire.MERCHANT);
    Map<String, List<String>> headers =
        WorldlineWire.signed(
            WorldlineDoubles.merchant(
                RUN,
                WorldlineWire.ACCOUNT,
                WorldlineWire.MERCHANT,
                Map.of(
                    WorldlineAuthentication.SETTING,
                    WorldlineAuthentication.THE_SIGNATURE_IS_VERIFIED.name())),
            WorldlineWire.POST,
            path,
            WorldlineFields.JSON,
            WHEN);
    api()
        .createHostedTokenization(
            WorldlineApiCall.of(WorldlineWire.POST, path, headers, Map.of(), "{}"),
            WorldlineWire.MERCHANT);
    assertThat(audit.all())
        .allSatisfy(
            record ->
                assertThat(record.sanitizedHeaders().get(WorldlineFields.AUTHORIZATION))
                    .isNotEqualTo(headers.get(WorldlineFields.AUTHORIZATION)));
  }

  @Test
  void a_date_that_is_not_rfc_1123_reads_as_no_date_at_all() {
    assertThat(WorldlineSignature.dateIn("2026-09-22T10:00:00Z")).isEmpty();
  }

  @Test
  void a_date_this_simulator_writes_reads_back_as_the_instant_it_was_written_from() {
    assertThat(WorldlineSignature.dateIn(WorldlineSignature.asWorldlineWritesIt(WHEN)))
        .contains(WHEN);
  }
}
