package dev.marcinromanowski.pspsim.provider.azericard;

import static org.assertj.core.api.Assertions.assertThat;

import dev.marcinromanowski.pspsim.kernel.ids.IdGenerator;
import dev.marcinromanowski.pspsim.kernel.smoke.CannedTransport;
import dev.marcinromanowski.pspsim.kernel.smoke.CheckJson;
import dev.marcinromanowski.pspsim.kernel.smoke.CheckRequest;
import dev.marcinromanowski.pspsim.kernel.smoke.CheckResponse;
import dev.marcinromanowski.pspsim.kernel.smoke.ContractCheck;
import dev.marcinromanowski.pspsim.kernel.smoke.CountingIds;
import dev.marcinromanowski.pspsim.kernel.smoke.FailureKind;
import dev.marcinromanowski.pspsim.kernel.smoke.SmokeRun;
import dev.marcinromanowski.pspsim.kernel.smoke.SmokeRunner;
import dev.marcinromanowski.pspsim.kernel.smoke.SmokeVerdict;
import dev.marcinromanowski.pspsim.kernel.smoke.VendorSamples;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.security.PublicKey;
import java.security.Signature;
import java.security.interfaces.RSAPublicKey;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

class AzericardCompatibilityChecksTest {

  private static final String CGI_LINK = "/cgi-bin/cgi_link";

  private static final String CONFIG = "/__simulator/azericard/config";

  private static final String AUDIT = "/__simulator/azericard/audit";

  private static final String TEXT_HTML = "text/html";

  private static final String STATUS_JSON = "application/json;charset=UTF-8";

  private static final String THE_TRAIL_OF_ONE_EXCHANGE =
      "[{\"direction\":\"INBOUND_REQUEST\",\"outcome\":null},"
          + "{\"direction\":\"OUTBOUND_RESPONSE\",\"outcome\":\"SUCCESS\"}]";

  private static final String THE_STATUS_REFUSAL =
      "{\"terminal\":\"77777777\",\"actionCode\":\"3\",\"responseCode\":\"-17\","
          + "\"statusMsg\":\"Access denied\",\"tr_type\":\"1\",\"nonce\":\"2c9434b2aa5bb4af\","
          + "\"signature\":\"5EC7B408\",\"timestamp\":\"20260910115344\"}";

  private static final String THE_HUMAN_ERROR_PAGE =
      "<html><body><p>Sorry, due to technical fault your request cannot be processed</p>"
          + "<p>Error message: Error in merchant terminal field</p>"
          + "<p>Please try once again or visit us later</p></body></html>";

  private static final Pattern THE_CONFIGURED_MERCHANT_KEY =
      Pattern.compile("\"apiKey\":\"(-----BEGIN PUBLIC KEY-----[^\"]*-----END PUBLIC KEY-----)\"");

  private static final Pattern A_FORM_FIELD = Pattern.compile("(?:^|&)([^=&]+)=([^&]*)");

  private static final Instant AT = Instant.parse("2026-09-25T12:00:00Z");

  private final IdGenerator ids = new CountingIds();

  private final List<ContractCheck> checks =
      new AzericardCompatibilityChecks(new CheckJson(), ids, AzericardDoubles.clockAt(AT)).all();

  private final SmokeRunner runner = new SmokeRunner(Instant::now, provider -> List.of());

  @Test
  void the_provider_publishes_a_check_for_five_operations_and_withholds_none() {
    assertThat(checks).hasSize(5);
    assertThat(checks).allSatisfy(check -> assertThat(check.expectations()).isNotEmpty());
  }

  @Test
  void the_authorization_reads_its_elements_from_an_excerpt_of_the_published_example() {
    assertThat(evidenceBy(check(0)))
        .containsExactly(
            "contentType from azericard/authorization-content-type, live probe on 2026-09-10,"
                + " an excerpt",
            "action from azericard/authorization-approved, published example, an excerpt",
            "rc from azericard/authorization-approved, published example, an excerpt",
            "desc from azericard/authorization-approved, published example, an excerpt");
  }

  @Test
  void the_approved_authorization_is_the_published_desc_action_and_rc_in_the_published_order() {
    String approved = VendorSamples.of("azericard").sample("authorization-approved").body().value();

    assertThat(approved)
        .isEqualTo("<response><desc>Approved</desc><action>0</action><rc>00</rc></response>\n");
  }

  @Test
  void every_value_a_check_expects_is_the_one_the_azericard_samples_publish() {
    assertThat(expectedBy(check(0)))
        .containsExactly("contentType=text/html", "action=0", "rc=00", "desc=Approved");
    assertThat(expectedBy(check(1)))
        .containsExactly("httpStatus=200", "action=3", "rc=-17", "desc=Access denied");
    assertThat(expectedBy(check(2)))
        .containsExactly(
            "httpStatus=200",
            "contentType=text/html",
            "page.opening=Sorry, due to technical fault your request cannot be processed",
            "page.message=Error message: Error in merchant terminal field");
    assertThat(expectedBy(check(3)))
        .containsExactly(
            "mediaType=application/json",
            "charset=charset=UTF-8",
            "actionCode=3",
            "responseCode=-17",
            "statusMsg=Access denied",
            "tr_type=1");
    assertThat(expectedBy(check(4)))
        .containsExactly("audit.direction=INBOUND_REQUEST", "audit.outcome=SUCCESS");
  }

  @Test
  void the_authorization_holds_when_the_gateway_answers_the_published_pair_and_serves_it_as_html() {
    SmokeRun run =
        runner.run(List.of(check(0)), prepared().answering("POST", CGI_LINK, approved()));

    assertThat(run.verdicts()).singleElement().isInstanceOf(SmokeVerdict.Passed.class);
  }

  @Test
  void the_authorization_fails_naming_the_action_when_the_gateway_declines_the_card_instead() {
    SmokeRun run =
        runner.run(
            List.of(check(0)),
            prepared().answering("POST", CGI_LINK, xml(TEXT_HTML, "2", "00", "Approved")));

    SmokeVerdict.Failed failed = theFailure(run);
    assertThat(failed.field()).isEqualTo("action");
    assertThat(failed.kind()).isEqualTo(FailureKind.NOT_AS_PUBLISHED);
    assertThat(failed.provenance().sample()).isEqualTo("azericard/authorization-approved");
  }

  @Test
  void the_authorization_fails_naming_the_code_when_the_pair_carries_another_response_code() {
    SmokeRun run =
        runner.run(
            List.of(check(0)),
            prepared().answering("POST", CGI_LINK, xml(TEXT_HTML, "0", "05", "Approved")));

    assertThat(theFailure(run).field()).isEqualTo("rc");
  }

  @Test
  void the_authorization_fails_naming_the_content_type_when_the_xml_is_served_as_an_xml_type() {
    SmokeRun run =
        runner.run(
            List.of(check(0)),
            prepared().answering("POST", CGI_LINK, xml("application/xml", "0", "00", "Approved")));

    SmokeVerdict.Failed failed = theFailure(run);
    assertThat(failed.field()).isEqualTo("contentType");
    assertThat(failed.provenance().sample()).isEqualTo("azericard/authorization-content-type");
  }

  @Test
  void the_authorization_fails_naming_the_action_when_the_answer_carries_no_such_element() {
    SmokeRun run =
        runner.run(
            List.of(check(0)),
            prepared()
                .answering(
                    "POST",
                    CGI_LINK,
                    new CheckResponse(
                        200, Map.of("Content-Type", List.of(TEXT_HTML)), "<response></response>")));

    SmokeVerdict.Failed failed = theFailure(run);
    assertThat(failed.field()).isEqualTo("action");
    assertThat(failed.kind()).isEqualTo(FailureKind.NOT_OBSERVED);
  }

  @Test
  void the_refusal_of_a_wrong_mac_holds_when_the_gateway_answers_two_hundred_with_access_denied() {
    SmokeRun run =
        runner.run(
            List.of(check(1)),
            prepared().answering("POST", CGI_LINK, xml(TEXT_HTML, "3", "-17", "Access denied")));

    assertThat(run.verdicts()).singleElement().isInstanceOf(SmokeVerdict.Passed.class);
  }

  @Test
  void the_refusal_of_a_wrong_mac_fails_naming_the_http_status_when_the_gateway_answers_a_4xx() {
    SmokeRun run =
        runner.run(
            List.of(check(1)),
            prepared()
                .answering(
                    "POST",
                    CGI_LINK,
                    new CheckResponse(
                        403,
                        Map.of("Content-Type", List.of(TEXT_HTML)),
                        bodyOf("3", "-17", "Access denied"))));

    SmokeVerdict.Failed failed = theFailure(run);
    assertThat(failed.field()).isEqualTo("httpStatus");
    assertThat(failed.provenance().sample()).isEqualTo("azericard/refused-p-sign");
  }

  @Test
  void the_error_page_holds_when_the_gateway_answers_the_two_published_sentences_as_html() {
    SmokeRun run =
        runner.run(
            List.of(check(2)),
            prepared()
                .answering(
                    "POST",
                    CGI_LINK,
                    new CheckResponse(
                        200, Map.of("Content-Type", List.of(TEXT_HTML)), THE_HUMAN_ERROR_PAGE)));

    assertThat(run.verdicts()).singleElement().isInstanceOf(SmokeVerdict.Passed.class);
  }

  @Test
  void the_error_page_fails_naming_the_message_when_the_page_carries_only_its_opening_sentence() {
    SmokeRun run =
        runner.run(
            List.of(check(2)),
            prepared()
                .answering(
                    "POST",
                    CGI_LINK,
                    new CheckResponse(
                        200,
                        Map.of("Content-Type", List.of(TEXT_HTML)),
                        "<html><body><p>Sorry, due to technical fault your request cannot be"
                            + " processed</p></body></html>")));

    SmokeVerdict.Failed failed = theFailure(run);
    assertThat(failed.field()).isEqualTo("page.message");
    assertThat(failed.kind()).isEqualTo(FailureKind.NOT_OBSERVED);
  }

  @Test
  void the_error_page_fails_naming_its_opening_when_the_answer_is_no_page_at_all() {
    SmokeRun run =
        runner.run(
            List.of(check(2)),
            prepared()
                .answering(
                    "POST",
                    CGI_LINK,
                    new CheckResponse(200, Map.of("Content-Type", List.of(TEXT_HTML)), "")));

    SmokeVerdict.Failed failed = theFailure(run);
    assertThat(failed.field()).isEqualTo("page.opening");
    assertThat(failed.kind()).isEqualTo(FailureKind.NOT_OBSERVED);
  }

  @Test
  void the_status_refusal_holds_when_the_answer_is_json_under_the_keys_the_probe_settled() {
    SmokeRun run =
        runner.run(List.of(check(3)), prepared().answering("POST", CGI_LINK, aStatusRefusal()));

    assertThat(run.verdicts()).singleElement().isInstanceOf(SmokeVerdict.Passed.class);
  }

  @Test
  void the_status_refusal_fails_naming_the_charset_when_the_media_type_carries_no_parameter() {
    SmokeRun run =
        runner.run(
            List.of(check(3)),
            prepared()
                .answering(
                    "POST",
                    CGI_LINK,
                    new CheckResponse(
                        200,
                        Map.of("Content-Type", List.of("application/json")),
                        THE_STATUS_REFUSAL)));

    SmokeVerdict.Failed failed = theFailure(run);
    assertThat(failed.field()).isEqualTo("charset");
    assertThat(failed.kind()).isEqualTo(FailureKind.NOT_OBSERVED);
  }

  @Test
  void the_status_refusal_fails_naming_the_media_type_when_the_answer_declares_no_content_type() {
    SmokeRun run =
        runner.run(
            List.of(check(3)),
            prepared()
                .answering("POST", CGI_LINK, new CheckResponse(200, Map.of(), THE_STATUS_REFUSAL)));

    SmokeVerdict.Failed failed = theFailure(run);
    assertThat(failed.field()).isEqualTo("mediaType");
    assertThat(failed.kind()).isEqualTo(FailureKind.NOT_OBSERVED);
  }

  @Test
  void the_status_refusal_fails_naming_the_original_type_when_the_answer_echoes_another_one() {
    SmokeRun run =
        runner.run(
            List.of(check(3)),
            prepared()
                .answering(
                    "POST",
                    CGI_LINK,
                    new CheckResponse(
                        200,
                        Map.of("Content-Type", List.of(STATUS_JSON)),
                        THE_STATUS_REFUSAL.replace("\"tr_type\":\"1\"", "\"tr_type\":\"0\""))));

    SmokeVerdict.Failed failed = theFailure(run);
    assertThat(failed.field()).isEqualTo("tr_type");
    assertThat(failed.provenance().sample()).isEqualTo("azericard/status-inquiry-refused");
  }

  @Test
  void the_trail_check_holds_when_the_trail_opens_with_the_request_and_carries_the_verdict() {
    SmokeRun run =
        runner.run(
            List.of(check(4)),
            prepared()
                .answering("POST", CGI_LINK, approved())
                .answering("GET", AUDIT, 200, THE_TRAIL_OF_ONE_EXCHANGE));

    assertThat(run.verdicts()).singleElement().isInstanceOf(SmokeVerdict.Passed.class);
  }

  @Test
  void the_trail_check_fails_naming_the_outcome_when_the_second_row_carries_another_verdict() {
    SmokeRun run =
        runner.run(
            List.of(check(4)),
            prepared()
                .answering("POST", CGI_LINK, approved())
                .answering(
                    "GET",
                    AUDIT,
                    200,
                    THE_TRAIL_OF_ONE_EXCHANGE.replace("\"SUCCESS\"", "\"FAILURE\"")));

    assertThat(theFailure(run).field()).isEqualTo("audit.outcome");
  }

  @Test
  void the_merchant_key_it_configures_is_an_rsa_key_of_the_published_floor_of_2048_bits()
      throws Exception {
    CannedTransport transport = prepared().answering("POST", CGI_LINK, approved());

    runner.run(List.of(check(0)), transport);

    PublicKey configured = theMerchantKeyConfiguredBy(transport);
    assertThat(configured.getAlgorithm()).isEqualTo("RSA");
    assertThat(((RSAPublicKey) configured).getModulus().bitLength()).isEqualTo(2048);
  }

  @Test
  void the_mac_it_sends_is_the_published_field_order_and_verifies_with_the_key_it_configured()
      throws Exception {
    CannedTransport transport = prepared().answering("POST", CGI_LINK, approved());

    runner.run(List.of(check(0)), transport);

    Map<String, String> form = theFormOfTheAuthorizationSentBy(transport);
    String published =
        lengthPrefixed(form.get("AMOUNT"))
            + lengthPrefixed(form.get("CURRENCY"))
            + lengthPrefixed(form.get("TERMINAL"))
            + lengthPrefixed(form.get("TRTYPE"))
            + lengthPrefixed(form.get("TIMESTAMP"))
            + lengthPrefixed(form.get("NONCE"))
            + lengthPrefixed(form.get("MERCH_URL"));
    Signature verifier = Signature.getInstance("SHA256withRSA");
    verifier.initVerify(theMerchantKeyConfiguredBy(transport));
    verifier.update(published.getBytes(StandardCharsets.UTF_8));

    assertThat(verifier.verify(bytesOf(form.get("P_SIGN")))).isTrue();
  }

  @Test
  void the_amount_and_currency_it_signs_are_the_ones_it_posts() {
    CannedTransport transport = prepared().answering("POST", CGI_LINK, approved());

    runner.run(List.of(check(0)), transport);

    Map<String, String> form = theFormOfTheAuthorizationSentBy(transport);
    assertThat(form.get("AMOUNT")).isEqualTo("120.50");
    assertThat(form.get("CURRENCY")).isEqualTo("AZN");
    assertThat(form.get("TRTYPE")).isEqualTo("1");
  }

  @Test
  void the_order_it_posts_is_numeric_and_inside_the_width_the_vendor_publishes_for_it() {
    CannedTransport transport = prepared().answering("POST", CGI_LINK, approved());

    runner.run(List.of(check(0)), transport);

    assertThat(theFormOfTheAuthorizationSentBy(transport).get("ORDER"))
        .matches("[0-9]+")
        .hasSizeGreaterThanOrEqualTo(6)
        .hasSizeLessThanOrEqualTo(32);
  }

  @Test
  void the_timestamp_it_posts_is_the_gmt_second_the_simulator_clock_of_the_check_run_reads() {
    CannedTransport transport = prepared().answering("POST", CGI_LINK, approved());

    runner.run(List.of(check(0)), transport);

    assertThat(theFormOfTheAuthorizationSentBy(transport).get("TIMESTAMP"))
        .isEqualTo("20260925120000");
  }

  private static String lengthPrefixed(String value) {
    return value.length() + value;
  }

  private static byte[] bytesOf(String hex) {
    byte[] decoded = new byte[hex.length() / 2];
    for (int index = 0; index < decoded.length; index++) {
      decoded[index] =
          (byte)
              ((Character.digit(hex.charAt(index * 2), 16) << 4)
                  | Character.digit(hex.charAt(index * 2 + 1), 16));
    }
    return decoded;
  }

  private static PublicKey theMerchantKeyConfiguredBy(CannedTransport transport) {
    String body = theBodyOf(transport, "PUT", CONFIG);
    Matcher key = THE_CONFIGURED_MERCHANT_KEY.matcher(body);
    assertThat(key.find()).isTrue();
    return AzericardKeys.publicKeyOf("apiKey", key.group(1));
  }

  private static Map<String, String> theFormOfTheAuthorizationSentBy(CannedTransport transport) {
    Matcher field = A_FORM_FIELD.matcher(theBodyOf(transport, "POST", CGI_LINK));
    Map<String, String> form = new LinkedHashMap<>();
    while (field.find()) {
      form.put(
          URLDecoder.decode(field.group(1), StandardCharsets.UTF_8),
          URLDecoder.decode(field.group(2), StandardCharsets.UTF_8));
    }
    return form;
  }

  private static String theBodyOf(CannedTransport transport, String method, String path) {
    for (CheckRequest sent : transport.sent()) {
      if (method.equals(sent.method()) && sent.path().startsWith(path)) {
        return String.valueOf(sent.body());
      }
    }
    throw new AssertionError("no " + method + " " + path + " was sent");
  }

  private static List<String> evidenceBy(ContractCheck check) {
    return check.expectations().stream()
        .map(
            expectation ->
                expectation.field()
                    + " from "
                    + expectation.published().provenance().sample()
                    + ", "
                    + expectation.published().provenance().evidence().rendered())
        .toList();
  }

  private static List<String> expectedBy(ContractCheck check) {
    return check.expectations().stream()
        .map(expectation -> expectation.field() + "=" + expectation.published().value())
        .toList();
  }

  private ContractCheck check(int ordinal) {
    return checks.get(ordinal);
  }

  private static SmokeVerdict.Failed theFailure(SmokeRun run) {
    assertThat(run.verdicts()).singleElement().isInstanceOf(SmokeVerdict.Failed.class);
    return (SmokeVerdict.Failed) run.verdicts().get(0);
  }

  private static CannedTransport prepared() {
    return new CannedTransport()
        .answering("POST", "/__simulator/azericard/reset", 200, "{}")
        .answering("PUT", CONFIG, 200, "{}");
  }

  private static CheckResponse approved() {
    return xml(TEXT_HTML, "0", "00", "Approved");
  }

  private static CheckResponse aStatusRefusal() {
    return new CheckResponse(200, Map.of("Content-Type", List.of(STATUS_JSON)), THE_STATUS_REFUSAL);
  }

  private static CheckResponse xml(String contentType, String action, String rc, String desc) {
    return new CheckResponse(
        200, Map.of("Content-Type", List.of(contentType)), bodyOf(action, rc, desc));
  }

  private static String bodyOf(String action, String rc, String desc) {
    return "<response><desc>"
        + desc
        + "</desc><terminal>77777777</terminal><trtype>1</trtype><order>20220628084800</order>"
        + "<amount>120.50</amount><currency>AZN</currency><action>"
        + action
        + "</action><rc>"
        + rc
        + "</rc><approval>111111</approval><rrn>112676199769</rrn>"
        + "<intref>0A7E21C778330DF2</intref><timestamp>20260925120000</timestamp>"
        + "<nonce>7fdc0caafb113590</nonce><p_sign>5dc32feb</p_sign></response>";
  }
}
