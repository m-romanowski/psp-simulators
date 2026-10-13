package dev.marcinromanowski.pspsim.journey;

import static org.assertj.core.api.Assertions.assertThat;

import dev.marcinromanowski.pspsim.PostgresBackedTest;
import dev.marcinromanowski.pspsim.journey.JourneyClient.Kind;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.ResponseEntity;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

class ClockScreenJourneyIntegrationTest extends PostgresBackedTest {

  private static final String RUN = "screens-clock-advance";
  private static final String PROVIDER = "worldline";
  private static final String ACCOUNT = "merchant-1";
  private static final String MERCHANT = "pspid-clock";
  private static final String PINNED = "2026-09-15T10:00:00Z";
  private static final String CONFIRMED = "yes";
  private static final String THE_CARD_WORLDLINE_PRINTS = "published-1111";

  private static final String A_TOKENIZATION = "POST /v2/{merchantId}/hostedtokenizations";

  private static final String ACCOUNT_FORM = "/ui/configuration/account";
  private static final String CLOCK = "/ui/configuration/clock";
  private static final String ADVANCE = "/ui/configuration/clock/advance";
  private static final String CONFIGURATION = "/ui/configuration";
  private static final String START = "/ui/objects/start";
  private static final String OBJECTS = "/ui/objects";
  private static final String COMPLETE = "/ui/objects/complete";

  private static final Pattern A_STARTED_OBJECT = Pattern.compile("[?&]started=([^&]+)");

  @LocalServerPort private int port;

  private JourneyClient http;

  @BeforeEach
  void standUpTheClient() {
    http = new JourneyClient(port);
  }

  @AfterEach
  void discardTheRun() {
    http.discardTheTestRun(PROVIDER, RUN);
  }

  @Test
  void a_screen_clock_advanced_past_a_hosted_session_stops_offering_that_session_a_payer() {
    configureTheMerchant();
    String sessionId = openAHostedPageNoPayerWillFinish();
    String waiting = theScreenOf(sessionId);

    ResponseEntity<String> jumped = advanceTheClock("PT4H");

    String afterTheJump = theScreenOf(sessionId);
    ResponseEntity<String> completed = finishThePageAsThePayer(sessionId);

    assertThat(waiting).contains("AWAITING_THE_PAYER").contains(THE_CARD_WORLDLINE_PRINTS);
    assertThat(jumped.getStatusCode().value()).isEqualTo(302);
    assertThat(afterTheJump)
        .contains("past the three hours")
        .doesNotContain(THE_CARD_WORLDLINE_PRINTS);
    assertThat(completed.getStatusCode().value()).isEqualTo(200);
    assertThat(String.valueOf(completed.getBody())).contains("past the three hours");
    assertThat(theControlApiCallsOf(http.steps())).isEmpty();
  }

  @Test
  void a_tester_who_advances_a_screen_clock_short_of_the_deadline_still_finishes_the_page() {
    configureTheMerchant();
    String sessionId = openAHostedPageNoPayerWillFinish();

    advanceTheClock("PT2H");

    finishThePageAsThePayer(sessionId);
    assertThat(theScreenOf(sessionId)).contains("CREATED");
    assertThat(theScreenOf(sessionId))
        .as(
            "a page finished short of its deadline is not expired under any spelling, and the"
                + " status this screen chips is read in one case while a vendor's own word for it"
                + " is published in another")
        .doesNotContainIgnoringCase("EXPIRED");
    assertThat(theControlApiCallsOf(http.steps())).isEmpty();
  }

  @Test
  void the_clock_a_tester_advanced_is_the_clock_the_configuration_screen_reads_back_to_them() {
    configureTheMerchant();

    advanceTheClock("PT4H");

    assertThat(theConfigurationScreen())
        .contains("2026-09-15T14:00:00Z")
        .doesNotContain("2026-09-15T10:00:00Z");
  }

  @Test
  void a_screen_that_cannot_read_a_duration_refuses_the_jump_and_leaves_the_clock_where_it_was() {
    configureTheMerchant();

    ResponseEntity<String> refused = advanceTheClock("four hours");

    assertThat(refused.getStatusCode().value()).isEqualTo(200);
    assertThat(String.valueOf(refused.getBody()))
        .contains("advanceBy")
        .doesNotContain("four hours");
    assertThat(theConfigurationScreen()).contains(PINNED);
  }

  private ResponseEntity<String> advanceTheClock(String advanceBy) {
    return http.form(
        "advance the clock of this test run from the Configuration screen",
        Kind.WRITE,
        ADVANCE,
        form("provider", PROVIDER, "testRunId", RUN, "advanceBy", advanceBy));
  }

  private String openAHostedPageNoPayerWillFinish() {
    return theObjectStartedBy(
        http.form(
            "open a hosted tokenization page from the wizard, with no client application",
            Kind.WRITE,
            START,
            form(
                "provider", PROVIDER,
                "testRunId", RUN,
                "accountId", ACCOUNT,
                "startedAs", A_TOKENIZATION)));
  }

  private ResponseEntity<String> finishThePageAsThePayer(String sessionId) {
    return http.form(
        "type the card on the hosted page as the payer, from the session's own screen",
        Kind.WRITE,
        COMPLETE,
        form(
            "provider", PROVIDER,
            "testRunId", RUN,
            "providerObjectId", sessionId,
            "outcome", "PAID",
            "card", THE_CARD_WORLDLINE_PRINTS,
            "confirm", CONFIRMED));
  }

  private void configureTheMerchant() {
    ResponseEntity<String> configured =
        http.form(
            "configure the worldline account this journey uses",
            Kind.WRITE,
            ACCOUNT_FORM,
            form(
                "provider", PROVIDER,
                "testRunId", RUN,
                "accountId", ACCOUNT,
                "field.merchantId", MERCHANT,
                "field.apiKey", "screens-clock-key",
                "field.apiSecret", "screens-clock-secret"));
    assertThat(configured.getStatusCode().value()).isEqualTo(302);

    ResponseEntity<String> pinned =
        http.form(
            "pin the clock of this test run",
            Kind.WRITE,
            CLOCK,
            form("provider", PROVIDER, "testRunId", RUN, "fixedNow", PINNED));
    assertThat(pinned.getStatusCode().value()).isEqualTo(302);
  }

  private String theScreenOf(String providerObjectId) {
    return String.valueOf(
        http.page(
                "open the hosted session on the Payments screen",
                OBJECTS + query() + "&providerObjectId=" + providerObjectId)
            .getBody());
  }

  private String theConfigurationScreen() {
    return String.valueOf(
        http.page("read the clock this test run now keeps", CONFIGURATION + query()).getBody());
  }

  private static List<JourneyClient.Step> theControlApiCallsOf(List<JourneyClient.Step> steps) {
    List<JourneyClient.Step> control = new ArrayList<>();
    for (JourneyClient.Step step : steps) {
      if (step.path().startsWith("/__simulator")) {
        control.add(step);
      }
    }
    return List.copyOf(control);
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

  private static String query() {
    return "?provider=" + PROVIDER + "&testRunId=" + RUN;
  }

  private static MultiValueMap<String, String> form(String... namesAndValues) {
    MultiValueMap<String, String> fields = new LinkedMultiValueMap<>();
    for (int index = 0; index < namesAndValues.length; index += 2) {
      fields.add(namesAndValues[index], namesAndValues[index + 1]);
    }
    return fields;
  }
}
