package dev.marcinromanowski.pspsim.journey;

import static org.assertj.core.api.Assertions.assertThat;

import dev.marcinromanowski.pspsim.PostgresBackedTest;
import dev.marcinromanowski.pspsim.journey.JourneyClient.Kind;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;

class ExpiryIsOneFactIntegrationTest extends PostgresBackedTest {

  private static final String RUN = "expiry-is-one-fact";
  private static final String WORLDLINE = "worldline";
  private static final String AUTRA = "autra";
  private static final String ACCOUNT = "merchant-1";
  private static final String MERCHANT = "pspid-one-fact";
  private static final String DOCUMENT = "25055662000190";
  private static final String PINNED = "2026-09-15T10:00:00Z";

  private static final String A_TOKENIZATION = "POST /v2/{merchantId}/hostedtokenizations";
  private static final String A_CARD_SESSION = "POST /v1/acquiring/card-sessions";
  private static final String A_PIX = "POST /v1/acquiring/pix/checkout";

  private static final String THE_CARD_WORLDLINE_PRINTS = "published-1111";
  private static final String THE_CARD_AUTRA_PRINTS = "published-4111";

  private static final String ACCOUNT_FORM = "/ui/configuration/account";
  private static final String CLOCK = "/ui/configuration/clock";
  private static final String ADVANCE = "/ui/configuration/clock/advance";
  private static final String START = "/ui/objects/start";
  private static final String OBJECTS = "/ui/objects";

  private static final Pattern A_STARTED_OBJECT = Pattern.compile("[?&]started=([^&]+)");

  @LocalServerPort private int port;
  @Autowired private JdbcTemplate jdbc;

  private JourneyClient screens;
  private RestClient control;

  @BeforeEach
  void standUpTheClientsAndPinTheClock() {
    screens = new JourneyClient(port);
    control = RestClient.create("http://localhost:" + port);
    configureTheWorldlineMerchant();
    configureTheAutraMerchant();
    pinTheClockOf(WORLDLINE);
    pinTheClockOf(AUTRA);
  }

  @AfterEach
  void discardTheRun() {
    screens.discardTheTestRun(WORLDLINE, RUN);
    screens.discardTheTestRun(AUTRA, RUN);
  }

  @Test
  void a_worldline_session_the_clock_passed_reads_expired_on_the_screen_and_the_control_state() {
    String sessionId = anObjectStartedAs(WORLDLINE, A_TOKENIZATION);

    advanceTheClockOf(WORLDLINE, "PT4H");

    assertThat(theScreenOf(WORLDLINE, sessionId))
        .contains("EXPIRED")
        .contains("past the three hours")
        .doesNotContain(THE_CARD_WORLDLINE_PRINTS);
    assertThat(theControlStateOf(WORLDLINE, sessionId))
        .containsEntry("nativeStatus", "EXPIRED")
        .containsEntry("normalizedStatus", "CANCELLED");
  }

  @Test
  void a_worldline_session_short_of_its_life_span_reads_awaiting_its_payer_on_both() {
    String sessionId = anObjectStartedAs(WORLDLINE, A_TOKENIZATION);

    advanceTheClockOf(WORLDLINE, "PT2H59M");

    assertThat(theScreenOf(WORLDLINE, sessionId))
        .contains("AWAITING_THE_PAYER")
        .contains(THE_CARD_WORLDLINE_PRINTS)
        .doesNotContain("past the three hours");
    assertThat(theControlStateOf(WORLDLINE, sessionId))
        .containsEntry("nativeStatus", "AWAITING_THE_PAYER")
        .containsEntry("normalizedStatus", "PROCESSING");
  }

  @Test
  void an_autra_card_session_the_clock_passed_reads_expired_on_the_screen_and_the_control_state() {
    String sessionId = anObjectStartedAs(AUTRA, A_CARD_SESSION);

    advanceTheClockOf(AUTRA, "PT15M");

    assertThat(theScreenOf(AUTRA, sessionId))
        .contains("EXPIRED")
        .contains("no longer loads")
        .doesNotContain(THE_CARD_AUTRA_PRINTS);
    assertThat(theControlStateOf(AUTRA, sessionId))
        .containsEntry("nativeStatus", "EXPIRED")
        .containsEntry("normalizedStatus", "CANCELLED");
  }

  @Test
  void an_autra_card_session_short_of_its_ttl_reads_pending_on_both() {
    String sessionId = anObjectStartedAs(AUTRA, A_CARD_SESSION);

    advanceTheClockOf(AUTRA, "PT14M");

    assertThat(theScreenOf(AUTRA, sessionId))
        .contains("PENDING")
        .contains(THE_CARD_AUTRA_PRINTS)
        .doesNotContain("no longer loads");
    assertThat(theControlStateOf(AUTRA, sessionId))
        .containsEntry("nativeStatus", "PENDING")
        .containsEntry("normalizedStatus", "PROCESSING");
  }

  @Test
  void an_autra_pix_the_clock_passed_reads_expired_on_the_screen_and_the_control_state() {
    String pixId = aPixStarted();

    advanceTheClockOf(AUTRA, "PT5M");

    assertThat(theScreenOf(AUTRA, pixId)).contains("EXPIRED").contains("expired unpaid");
    assertThat(theControlStateOf(AUTRA, pixId))
        .containsEntry("nativeStatus", "EXPIRED")
        .containsEntry("normalizedStatus", "CANCELLED");
  }

  @Test
  void an_autra_pix_short_of_its_deadline_reads_pending_on_both() {
    String pixId = aPixStarted();

    advanceTheClockOf(AUTRA, "PT4M");

    assertThat(theScreenOf(AUTRA, pixId))
        .contains("PENDING")
        .contains("bank app")
        .doesNotContain("expired unpaid");
    assertThat(theControlStateOf(AUTRA, pixId))
        .containsEntry("nativeStatus", "PENDING")
        .containsEntry("normalizedStatus", "PROCESSING");
  }

  @Test
  void a_worldline_row_written_awaiting_a_payer_before_its_deadline_passed_reads_expired_now() {
    String sessionId = anObjectStartedAs(WORLDLINE, A_TOKENIZATION);
    jdbc.update(
        "update worldline_hosted_tokenization"
            + " set created_at = ?::timestamptz, expires_at = ?::timestamptz"
            + " where test_run_id = ? and hosted_tokenization_id = ?",
        "2026-09-15T05:00:00Z",
        "2026-09-15T08:00:00Z",
        RUN,
        sessionId);

    Map<String, Object> read = theControlStateOf(WORLDLINE, sessionId);

    assertThat(theStoredStatusOf(sessionId)).isEqualTo("AWAITING_THE_PAYER");
    assertThat(read)
        .containsEntry("nativeStatus", "EXPIRED")
        .containsEntry("normalizedStatus", "CANCELLED");
    assertThat(theStoredStatusOf(sessionId)).isEqualTo("AWAITING_THE_PAYER");
  }

  @Test
  void an_autra_row_written_pending_before_its_deadline_passed_reads_expired_now() {
    String sessionId = anObjectStartedAs(AUTRA, A_CARD_SESSION);
    jdbc.update(
        "update autra_card_session"
            + " set created_at = ?::timestamptz, expires_at = ?::timestamptz"
            + " where test_run_id = ? and card_session_id = ?",
        "2026-09-15T09:00:00Z",
        "2026-09-15T09:15:00Z",
        RUN,
        sessionId);

    Map<String, Object> read = theControlStateOf(AUTRA, sessionId);

    assertThat(read)
        .containsEntry("nativeStatus", "EXPIRED")
        .containsEntry("normalizedStatus", "CANCELLED");
    assertThat(
            jdbc.queryForObject(
                "select native_status from autra_card_session"
                    + " where test_run_id = ? and card_session_id = ?",
                String.class,
                RUN,
                sessionId))
        .isEqualTo("PENDING");
  }

  private String theStoredStatusOf(String sessionId) {
    return jdbc.queryForObject(
        "select native_status from worldline_hosted_tokenization"
            + " where test_run_id = ? and hosted_tokenization_id = ?",
        String.class,
        RUN,
        sessionId);
  }

  private Map<String, Object> theControlStateOf(String provider, String providerObjectId) {
    return control
        .get()
        .uri("/__simulator/" + provider + "/state/" + providerObjectId + "?testRunId=" + RUN)
        .retrieve()
        .body(new ParameterizedTypeReference<Map<String, Object>>() {});
  }

  private String theScreenOf(String provider, String providerObjectId) {
    return String.valueOf(
        screens
            .page(
                "open the object on the Payments screen",
                OBJECTS
                    + "?provider="
                    + provider
                    + "&testRunId="
                    + RUN
                    + "&providerObjectId="
                    + providerObjectId)
            .getBody());
  }

  private void advanceTheClockOf(String provider, String advanceBy) {
    ResponseEntity<String> jumped =
        screens.form(
            "advance the clock of this test run from the Configuration screen",
            Kind.WRITE,
            ADVANCE,
            form("provider", provider, "testRunId", RUN, "advanceBy", advanceBy));
    assertThat(jumped.getStatusCode().value()).isEqualTo(302);
  }

  private String anObjectStartedAs(String provider, String startedAs) {
    return theObjectStartedBy(
        screens.form(
            "open an object from the wizard, with no client application",
            Kind.WRITE,
            START,
            form(
                "provider",
                provider,
                "testRunId",
                RUN,
                "accountId",
                ACCOUNT,
                "startedAs",
                startedAs)));
  }

  private String aPixStarted() {
    return theObjectStartedBy(
        screens.form(
            "hand out a Pix QR from the wizard, with no client application",
            Kind.WRITE,
            START,
            form(
                "provider", AUTRA,
                "testRunId", RUN,
                "accountId", ACCOUNT,
                "startedAs", A_PIX,
                "field.amount", "99.90",
                "field.currency", "BRL")));
  }

  private void configureTheWorldlineMerchant() {
    ResponseEntity<String> configured =
        screens.form(
            "configure the worldline account this journey uses",
            Kind.WRITE,
            ACCOUNT_FORM,
            form(
                "provider", WORLDLINE,
                "testRunId", RUN,
                "accountId", ACCOUNT,
                "field.merchantId", MERCHANT,
                "field.apiKey", "one-fact-worldline-key",
                "field.apiSecret", "one-fact-worldline-secret"));
    assertThat(configured.getStatusCode().value()).isEqualTo(302);
  }

  private void configureTheAutraMerchant() {
    ResponseEntity<String> configured =
        screens.form(
            "configure the autra account this journey uses",
            Kind.WRITE,
            ACCOUNT_FORM,
            form(
                "provider", AUTRA,
                "testRunId", RUN,
                "accountId", ACCOUNT,
                "field.clientId", "one-fact-autra-client",
                "field.clientSecret", "one-fact-autra-secret",
                "field.documentId", DOCUMENT));
    assertThat(configured.getStatusCode().value()).isEqualTo(302);
  }

  private void pinTheClockOf(String provider) {
    ResponseEntity<String> pinned =
        screens.form(
            "pin the clock of this test run",
            Kind.WRITE,
            CLOCK,
            form("provider", provider, "testRunId", RUN, "fixedNow", PINNED));
    assertThat(pinned.getStatusCode().value()).isEqualTo(302);
  }

  private static String theObjectStartedBy(ResponseEntity<String> answered) {
    assertThat(answered.getStatusCode().value()).isEqualTo(302);
    String location = String.valueOf(answered.getHeaders().getFirst("Location"));
    Matcher started = A_STARTED_OBJECT.matcher(location);
    assertThat(started.find())
        .as("the wizard hands the started object back on its redirect")
        .isTrue();
    return started.group(1);
  }

  private static MultiValueMap<String, String> form(String... namesAndValues) {
    MultiValueMap<String, String> fields = new LinkedMultiValueMap<>();
    for (int index = 0; index < namesAndValues.length; index += 2) {
      fields.add(namesAndValues[index], namesAndValues[index + 1]);
    }
    return fields;
  }
}
