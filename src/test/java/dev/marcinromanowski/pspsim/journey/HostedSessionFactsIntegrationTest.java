package dev.marcinromanowski.pspsim.journey;

import static org.assertj.core.api.Assertions.assertThat;

import dev.marcinromanowski.pspsim.PostgresBackedTest;
import dev.marcinromanowski.pspsim.journey.JourneyClient.Kind;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.ResponseEntity;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;

class HostedSessionFactsIntegrationTest extends PostgresBackedTest {

  private static final String RUN = "hosted-session-facts";
  private static final String WORLDLINE = "worldline";
  private static final String GROW = "grow";
  private static final String ACCOUNT = "merchant-1";
  private static final String PSPID = "pspid-hosted-facts";
  private static final String PINNED = "2026-09-15T10:00:00Z";

  private static final String A_TOKENIZATION = "POST /v2/{merchantId}/hostedtokenizations";
  private static final String A_HOLD = "createPaymentProcess:chargeType=2";

  private static final String THE_PAYER_GROW_CARRIES = "Dana Levi";

  private static final String A_PUBLISHED_CARD = "sandbox-4580";

  private static final String ACCOUNT_FORM = "/ui/configuration/account";
  private static final String CLOCK = "/ui/configuration/clock";
  private static final String ADVANCE = "/ui/configuration/clock/advance";
  private static final String START = "/ui/objects/start";
  private static final String COMPLETE = "/ui/objects/complete";
  private static final String OBJECTS = "/ui/objects";

  private static final String THE_AREA = "Hosted sessions: the objects a payer finishes";
  private static final String THE_PANEL = "What a payer has still to finish on this one";

  private static final Pattern A_STARTED_OBJECT = Pattern.compile("[?&]started=([^&]+)");

  @LocalServerPort private int port;

  private JourneyClient screens;
  private RestClient control;

  @BeforeEach
  void standUpTheClientsAndPinTheClock() {
    screens = new JourneyClient(port);
    control = RestClient.create("http://localhost:" + port);
    configureTheWorldlineMerchant();
    configureTheGrowAccount();
    pinTheClockOf(WORLDLINE);
    pinTheClockOf(GROW);
  }

  @AfterEach
  void discardTheRun() {
    screens.discardTheTestRun(WORLDLINE, RUN);
    screens.discardTheTestRun(GROW, RUN);
  }

  @Test
  void the_objects_screen_holds_a_hosted_sessions_area_naming_the_account_and_the_operation() {
    String sessionId = aWorldlineTokenization();

    String screen = theObjectsScreenOf(WORLDLINE, sessionId);

    assertThat(screen).contains(THE_AREA);
    assertThat(screen).contains("<td class=\"id\">" + ACCOUNT + "</td>");
    assertThat(screen).contains("<td class=\"id\">" + A_TOKENIZATION + "</td>");
  }

  @Test
  void a_worldline_session_shows_when_it_was_created_and_when_it_expires() {
    String sessionId = aWorldlineTokenization();

    String screen = theObjectsScreenOf(WORLDLINE, sessionId);

    assertThat(screen).contains(THE_PANEL);
    assertThat(theHostedSessionsAreaOf(screen))
        .as("the session's own created cell carries the clock this run pinned")
        .containsPattern("<td class=\"stamp\">" + PINNED + "</td>");
    assertThat(screen).contains("2026-09-15T13:00:00Z");
  }

  @Test
  void a_worldline_session_says_in_worldlines_own_words_that_it_names_no_party() {
    String sessionId = aWorldlineTokenization();

    String screen = theObjectsScreenOf(WORLDLINE, sessionId);

    assertThat(theHostedSessionsAreaOf(screen))
        .containsPattern(
            "class=\"note\"\\s*>provider worldline publishes"
                + " order\\.customer\\.merchantCustomerId");
  }

  @Test
  void a_worldline_session_prints_neither_a_blank_nor_an_invented_party_in_that_cell() {
    String sessionId = aWorldlineTokenization();

    String screen = theObjectsScreenOf(WORLDLINE, sessionId);

    assertThat(theHostedSessionsAreaOf(screen))
        .doesNotContainPattern("class=\"id\"\\s*></span>")
        .doesNotContainPattern("class=\"note\"\\s*></span>")
        .doesNotContain("<td class=\"id\"></td>")
        .doesNotContain("merchantCustomerId</span>");
    assertThat(theHostedSessionsAreaOf(screen))
        .as(
            "a party this simulator does not know is not printed as a word either, and the word is"
                + " forbidden in any case because a screen that chips a status prints it upper and"
                + " a sentence prints it lower, so one spelling is a weaker claim than the one"
                + " this test makes")
        .doesNotContainIgnoringCase("unknown");
  }

  @Test
  void a_grow_process_names_the_payer_grow_carries_on_it() {
    String processId = aGrowProcess();

    String screen = theObjectsScreenOf(GROW, processId);

    assertThat(theHostedSessionsAreaOf(screen))
        .containsPattern("class=\"id\"\\s*>" + THE_PAYER_GROW_CARRIES + "</span>");
    assertThat(screen).contains("<td class=\"id\">" + A_HOLD + "</td>");
  }

  @Test
  void a_grow_process_says_in_grows_own_words_that_it_publishes_no_deadline() {
    String processId = aGrowProcess();

    String screen = theObjectsScreenOf(GROW, processId);

    assertThat(screen).contains("provider grow publishes no deadline on a payment process at all");
    assertThat(screen).contains("nothing here expires this object");
  }

  @Test
  void a_grow_process_is_never_reported_as_past_a_deadline_however_far_its_clock_is_advanced() {
    String processId = aGrowProcess();
    advanceTheClockOf(GROW, "PT720H");

    String screen = theObjectsScreenOf(GROW, processId);

    assertThat(screen).doesNotContain("deadline has passed against the clock");
  }

  @Test
  void a_worldline_session_the_clock_of_this_run_has_not_reached_is_not_shown_past_its_deadline() {
    String sessionId = aWorldlineTokenization();
    advanceTheClockOf(WORLDLINE, "PT2H59M");

    String screen = theObjectsScreenOf(WORLDLINE, sessionId);

    assertThat(screen).contains(THE_PANEL);
    assertThat(screen).doesNotContain("deadline has passed against the clock");
  }

  @Test
  void a_worldline_session_the_clock_of_this_run_has_passed_is_shown_past_its_deadline() {
    String sessionId = aWorldlineTokenization();
    advanceTheClockOf(WORLDLINE, "PT4H");

    String screen = theObjectsScreenOf(WORLDLINE, sessionId);

    assertThat(screen).contains("deadline has passed against the clock");
    assertThat(theHostedSessionsAreaOf(screen))
        .containsPattern("class=\"tag fact\"\\s*>2026-09-15T13:00:00Z</span>");
  }

  @Test
  void an_object_no_payer_finishes_is_given_no_hosted_session_panel_at_all() {
    String processId = aGrowProcess();
    theProcessFinishedByItsPayer(processId);

    String screen = theObjectsScreenOf(GROW, theTransactionOf(processId));

    assertThat(screen).doesNotContain(THE_PANEL);
  }

  @Test
  void the_hosted_sessions_area_holds_the_process_and_not_the_transaction_it_left_behind() {
    String processId = aGrowProcess();
    theProcessFinishedByItsPayer(processId);
    String transactionId = theTransactionOf(processId);

    String area = theHostedSessionsAreaOf(theObjectsScreenOf(GROW, processId));

    assertThat(area).contains(processId);
    assertThat(area).doesNotContain(transactionId);
  }

  private static String theHostedSessionsAreaOf(String screen) {
    int opened = screen.indexOf(THE_AREA);
    assertThat(opened).as("the Payments screen holds a hosted sessions area").isNotNegative();
    return screen.substring(opened, screen.indexOf("</section>", opened));
  }

  private void theProcessFinishedByItsPayer(String processId) {
    ResponseEntity<String> finished =
        screens.form(
            "finish the hosted page as the payer",
            Kind.WRITE,
            COMPLETE,
            form(
                "provider", GROW,
                "testRunId", RUN,
                "providerObjectId", processId,
                "outcome", "PAID",
                "card", A_PUBLISHED_CARD,
                "confirm", "yes"));
    assertThat(finished.getStatusCode().value()).isEqualTo(302);
  }

  private String theTransactionOf(String processId) {
    for (Map<String, Object> state : theControlListingOf(GROW)) {
      if (state.get("hostedSession") == null) {
        return String.valueOf(state.get("providerObjectId"));
      }
    }
    throw new AssertionError(
        "a completed grow process leaves a transaction behind, and this run holds none beside "
            + processId);
  }

  @Test
  void the_control_api_listing_carries_the_same_facts_the_screen_shows() {
    aWorldlineTokenization();

    List<Map<String, Object>> listed = theControlListingOf(WORLDLINE);

    assertThat(listed).hasSize(1);
    assertThat(theHostedSessionIn(listed.get(0)))
        .containsEntry("account", ACCOUNT)
        .containsEntry("operation", A_TOKENIZATION)
        .containsEntry("aPartyIsNamed", false)
        .containsEntry("itExpiresAtAKnownTime", true)
        .containsEntry("itsDeadlineHasPassed", false)
        .containsEntry("expires", "2026-09-15T13:00:00Z");
  }

  @Test
  void the_control_api_listing_carries_the_payer_and_the_absent_deadline_grow_publishes() {
    aGrowProcess();

    List<Map<String, Object>> listed = theControlListingOf(GROW);

    assertThat(listed).hasSize(1);
    assertThat(theHostedSessionIn(listed.get(0)))
        .containsEntry("party", THE_PAYER_GROW_CARRIES)
        .containsEntry("aPartyIsNamed", true)
        .containsEntry("operation", A_HOLD)
        .containsEntry("itExpiresAtAKnownTime", false)
        .containsEntry("itsDeadlineHasPassed", false);
  }

  @Test
  void the_control_api_reports_a_deadline_the_clock_of_this_run_has_passed() {
    aWorldlineTokenization();
    advanceTheClockOf(WORLDLINE, "PT4H");

    List<Map<String, Object>> listed = theControlListingOf(WORLDLINE);

    assertThat(theHostedSessionIn(listed.get(0))).containsEntry("itsDeadlineHasPassed", true);
  }

  @SuppressWarnings("unchecked")
  private static Map<String, Object> theHostedSessionIn(Map<String, Object> state) {
    Object session = state.get("hostedSession");
    assertThat(session).as("the control read carries the hosted session facts").isNotNull();
    return (Map<String, Object>) session;
  }

  private List<Map<String, Object>> theControlListingOf(String provider) {
    return control
        .get()
        .uri("/__simulator/" + provider + "/objects?testRunId=" + RUN)
        .retrieve()
        .body(new ParameterizedTypeReference<List<Map<String, Object>>>() {});
  }

  private String theObjectsScreenOf(String provider, String providerObjectId) {
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

  private String aWorldlineTokenization() {
    return theObjectStartedBy(
        screens.form(
            "open a hosted tokenization session from the wizard",
            Kind.WRITE,
            START,
            form(
                "provider", WORLDLINE,
                "testRunId", RUN,
                "accountId", ACCOUNT,
                "startedAs", A_TOKENIZATION)));
  }

  private String aGrowProcess() {
    MultiValueMap<String, String> fields =
        form(
            "provider",
            GROW,
            "testRunId",
            RUN,
            "accountId",
            ACCOUNT,
            "startedAs",
            A_HOLD,
            "field.sum",
            "10.00",
            "field.description",
            "one seat, row F",
            "field.successUrl",
            "https://merchant.example/paid",
            "field.cancelUrl",
            "https://merchant.example/gaveup");
    fields.add("field.pageField[fullName]", THE_PAYER_GROW_CARRIES);
    fields.add("field.pageField[phone]", "0501234567");
    return theObjectStartedBy(
        screens.form("open a grow payment process from the wizard", Kind.WRITE, START, fields));
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
                "field.merchantId", PSPID,
                "field.apiKey", "hosted-facts-worldline-key",
                "field.apiSecret", "hosted-facts-worldline-secret"));
    assertThat(configured.getStatusCode().value()).isEqualTo(302);
  }

  private void configureTheGrowAccount() {
    ResponseEntity<String> configured =
        screens.form(
            "configure the grow account this journey uses",
            Kind.WRITE,
            ACCOUNT_FORM,
            form(
                "provider",
                GROW,
                "testRunId",
                RUN,
                "accountId",
                ACCOUNT,
                "field.userId",
                "hosted-facts-grow-user",
                "field.pageCode",
                "hosted-facts-grow-page",
                "callbackUrl",
                "https://merchant.example/callbacks"));
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

  private void advanceTheClockOf(String provider, String advanceBy) {
    ResponseEntity<String> jumped =
        screens.form(
            "advance the clock of this test run from the Configuration screen",
            Kind.WRITE,
            ADVANCE,
            form("provider", provider, "testRunId", RUN, "advanceBy", advanceBy));
    assertThat(jumped.getStatusCode().value()).isEqualTo(302);
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
