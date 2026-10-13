package dev.marcinromanowski.pspsim.journey;

import static org.assertj.core.api.Assertions.assertThat;

import dev.marcinromanowski.pspsim.PostgresBackedTest;
import dev.marcinromanowski.pspsim.journey.JourneyClient.Kind;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.ResponseEntity;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

class WorldlineScreenJourneyIntegrationTest extends PostgresBackedTest {

  private static final String RUN = "screens-worldline";
  private static final String PROVIDER = "worldline";
  private static final String ACCOUNT = "merchant-1";
  private static final String MERCHANT = "pspid-screens";
  private static final String PINNED = "2026-09-15T10:00:00Z";
  private static final String CONFIRMED = "yes";
  private static final String HELD = "5000";
  private static final String CURRENCY = "PLN";
  private static final String THE_CARD_WORLDLINE_PRINTS = "published-1111";

  private static final String A_TOKENIZATION = "POST /v2/{merchantId}/hostedtokenizations";
  private static final String A_PAYMENT = "POST /v2/{merchantId}/payments";

  private static final String ACCOUNT_FORM = "/ui/configuration/account";
  private static final String CLOCK = "/ui/configuration/clock";
  private static final String WIZARD = "/ui/objects/new";
  private static final String START = "/ui/objects/start";
  private static final String PAYMENTS = "/ui/objects";
  private static final String COMPLETE = "/ui/objects/complete";
  private static final String ACT = "/ui/objects/act";
  private static final String TRAIL = "/ui/audit";

  private static final Pattern A_LISTED_OBJECT =
      Pattern.compile("providerObjectId=([A-Za-z0-9_-]+)\"");

  private static final Pattern A_FIELD_THE_WIZARD_DRAWS =
      Pattern.compile("name=\"field\\.([^\"]+)\"");

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
  void a_tester_stores_a_card_on_worldline_holds_an_amount_and_captures_it_on_screens_alone() {
    configureTheMerchant();

    String tokenizationWizard =
        String.valueOf(
            http.page(
                    "draw the fields a worldline hosted tokenization reads",
                    theWizardFor(A_TOKENIZATION))
                .getBody());
    assertThat(theFieldsDrawnOn(tokenizationWizard)).containsExactly("tokens");

    String sessionId =
        theObjectStartedBy(
            http.form(
                "open a hosted tokenization page from the wizard, with no client application",
                Kind.WRITE,
                START,
                form(
                    "provider", PROVIDER,
                    "testRunId", RUN,
                    "accountId", ACCOUNT,
                    "startedAs", A_TOKENIZATION)));

    String waiting = theScreenOf(sessionId);
    assertThat(waiting).contains("AWAITING_THE_PAYER").contains("PROCESSING");
    assertThat(waiting)
        .as(
            "the link the provider answers with is never printed on a screen, which is what the"
                + " wizard tells the tester in this provider's own words, so a working address of"
                + " this session's payer page is not what a tester is handed here and the payer"
                + " step below is")
        .doesNotContain("hostedtokenization/tokenization/form/" + sessionId);
    assertThat(theCardsOfferedOn(waiting))
        .as("the payer step offers the card the published samples print")
        .contains(THE_CARD_WORLDLINE_PRINTS);
    assertThat(waiting)
        .as(
            "a step this page does not admit is not drawn as a choice, and the reason worldline"
                + " publishes for not having one is still on the screen that drew no choice for it")
        .doesNotContain("value=\"DECLINED\"")
        .contains("This provider offers no payer step where the payer met a decline")
        .contains("There is no acquirer on that page to decline anything");

    ResponseEntity<String> tokenized =
        http.form(
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
    assertThat(tokenized.getStatusCode().value()).isEqualTo(302);

    String stored = theScreenOf(sessionId);
    assertThat(stored).contains("CREATED").contains("SUCCEEDED").contains("************1111");
    String token = theRelatedValueOn(stored, "token");
    assertThat(token).isNotBlank();

    String paymentWizard =
        String.valueOf(
            http.page("draw the fields a worldline payment reads", theWizardFor(A_PAYMENT))
                .getBody());
    assertThat(theFieldsDrawnOn(paymentWizard))
        .contains(
            "amount",
            "currencyCode",
            "authorizationMode",
            "token",
            "hostedTokenizationId",
            "merchantReference",
            "merchantCustomerId");

    String paymentId =
        theObjectStartedBy(
            http.form(
                "hold an amount against the stored card from the wizard",
                Kind.WRITE,
                START,
                form(
                    "provider", PROVIDER,
                    "testRunId", RUN,
                    "accountId", ACCOUNT,
                    "startedAs", A_PAYMENT,
                    "field.amount", HELD,
                    "field.currencyCode", CURRENCY,
                    "field.authorizationMode", "PRE_AUTHORIZATION",
                    "field.token", token,
                    "field.merchantReference", "order-123")));

    String held = theScreenOf(paymentId);
    assertThat(held).contains("PENDING_CAPTURE").contains("READY_FOR_CAPTURE").contains(HELD);
    assertThat(theActionsOfferedOn(held)).contains("CAPTURE", "CANCEL");

    ResponseEntity<String> captured =
        http.form(
            "ask worldline to capture the hold, from the payment's own screen",
            Kind.WRITE,
            ACT,
            form(
                "provider", PROVIDER,
                "testRunId", RUN,
                "providerObjectId", paymentId,
                "action", "CAPTURE",
                "amount", HELD,
                "confirm", CONFIRMED));
    assertThat(captured.getStatusCode().value()).isEqualTo(302);

    String settled = theScreenOf(paymentId);
    assertThat(settled).contains("CAPTURED").contains("SUCCEEDED");
    assertThat(theIdentifiersLinkedOn(settled))
        .as("the screen links every identifier the payment has answered to")
        .contains(paymentId)
        .hasSizeGreaterThan(1);

    assertThat(theObjectsListedOn())
        .as("the payment and the tokenization it charged are both rows of this run")
        .containsExactly(paymentId, sessionId);

    assertThat(theTrailOf()).contains(ACCOUNT_FORM, START, COMPLETE, ACT);
    assertThat(theControlApiCallsOf(http.steps())).isEmpty();
  }

  @Test
  void a_tester_holds_an_amount_on_worldline_and_cancels_it_on_screens_alone() {
    configureTheMerchant();
    String paymentId = aHoldOnTheScreens();

    String held = theScreenOf(paymentId);
    assertThat(theActionsOfferedOn(held)).contains("CANCEL");

    ResponseEntity<String> cancelled =
        http.form(
            "ask worldline to cancel the hold, from the payment's own screen",
            Kind.WRITE,
            ACT,
            form(
                "provider", PROVIDER,
                "testRunId", RUN,
                "providerObjectId", paymentId,
                "action", "CANCEL",
                "confirm", CONFIRMED));
    assertThat(cancelled.getStatusCode().value()).isEqualTo(302);

    String released = theScreenOf(paymentId);
    assertThat(released).contains("CANCELLED");
    assertThat(theIdentifiersLinkedOn(released)).hasSizeGreaterThan(1);
    assertThat(theControlApiCallsOf(http.steps())).isEmpty();
  }

  @Test
  void a_refund_posted_at_the_act_route_cancels_nothing_and_answers_in_worldlines_own_words() {
    configureTheMerchant();
    String paymentId = aHoldOnTheScreens();

    assertThat(theActionsOfferedOn(theScreenOf(paymentId)))
        .as("the screen offers a tester only the actions this module serves")
        .doesNotContain("REFUND");

    ResponseEntity<String> refused =
        http.form(
            "post a refund the screen never offered, straight at the action route",
            Kind.WRITE,
            ACT,
            form(
                "provider", PROVIDER,
                "testRunId", RUN,
                "providerObjectId", paymentId,
                "action", "REFUND",
                "amount", HELD,
                "confirm", CONFIRMED));

    assertThat(refused.getStatusCode().value()).isEqualTo(200);
    assertThat(String.valueOf(refused.getBody()))
        .as("the refusal carries worldline's own words about what it has in place of the refund")
        .contains("this slice does not serve it")
        .contains("statusOutput.statusCode=71");

    String standing = theScreenOf(paymentId);
    assertThat(theValueOn(standing, "provider status"))
        .as(
            "the hold still stands, so the cancel route this action once fell through to was never"
                + " taken")
        .isEqualTo("PENDING_CAPTURE");
    assertThat(theControlApiCallsOf(http.steps())).isEmpty();
  }

  @Test
  void a_payment_reached_by_the_identifier_its_capture_minted_opens_the_same_screen() {
    configureTheMerchant();
    String paymentId = aHoldOnTheScreens();
    http.form(
        "capture part of the hold",
        Kind.WRITE,
        ACT,
        form(
            "provider", PROVIDER,
            "testRunId", RUN,
            "providerObjectId", paymentId,
            "action", "CAPTURE",
            "amount", "2000",
            "confirm", CONFIRMED));

    String minted =
        theIdentifiersLinkedOn(theScreenOf(paymentId)).stream()
            .filter(identifier -> !identifier.equals(paymentId))
            .findFirst()
            .orElseThrow();

    String byTheMinted = theScreenOf(minted);
    assertThat(byTheMinted)
        .as("the screen says which identifier it was addressed by when it is not the canonical one")
        .containsPattern("addressed by</dt>\\s*<dd\\s*class=\"id\"\\s*>" + minted + "</dd>")
        .containsPattern(
            "id the provider answered with</dt>\\s*<dd class=\"id\">" + paymentId + "</dd>");
    assertThat(theControlApiCallsOf(http.steps())).isEmpty();
  }

  private String aHoldOnTheScreens() {
    String sessionId =
        theObjectStartedBy(
            http.form(
                "open a hosted tokenization page",
                Kind.WRITE,
                START,
                form(
                    "provider", PROVIDER,
                    "testRunId", RUN,
                    "accountId", ACCOUNT,
                    "startedAs", A_TOKENIZATION)));
    http.form(
        "finish the page as the payer",
        Kind.WRITE,
        COMPLETE,
        form(
            "provider", PROVIDER,
            "testRunId", RUN,
            "providerObjectId", sessionId,
            "outcome", "PAID",
            "card", THE_CARD_WORLDLINE_PRINTS,
            "confirm", CONFIRMED));
    String token = theRelatedValueOn(theScreenOf(sessionId), "token");
    return theObjectStartedBy(
        http.form(
            "hold an amount against the stored card",
            Kind.WRITE,
            START,
            form(
                "provider", PROVIDER,
                "testRunId", RUN,
                "accountId", ACCOUNT,
                "startedAs", A_PAYMENT,
                "field.amount", HELD,
                "field.currencyCode", CURRENCY,
                "field.authorizationMode", "PRE_AUTHORIZATION",
                "field.token", token)));
  }

  private void configureTheMerchant() {
    ResponseEntity<String> configured =
        http.form(
            "configure the worldline account this journey signs for",
            Kind.WRITE,
            ACCOUNT_FORM,
            form(
                "provider", PROVIDER,
                "testRunId", RUN,
                "accountId", ACCOUNT,
                "field.merchantId", MERCHANT,
                "field.apiKey", "screens-worldline-key",
                "field.apiSecret", "screens-worldline-secret"));
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
                "open the payment on the Payments screen",
                PAYMENTS + query() + "&providerObjectId=" + providerObjectId)
            .getBody());
  }

  private List<String> theObjectsListedOn() {
    String page =
        String.valueOf(http.page("list what this test run holds", PAYMENTS + query()).getBody());
    return theIdentifiersLinkedOn(page);
  }

  private String theTrailOf() {
    return String.valueOf(
        http.page("read the trail of what this tester did", TRAIL + query() + "&origin=UI")
            .getBody());
  }

  private static List<String> theIdentifiersLinkedOn(String page) {
    List<String> listed = new ArrayList<>();
    Matcher rows = A_LISTED_OBJECT.matcher(page);
    while (rows.find()) {
      if (!listed.contains(rows.group(1))) {
        listed.add(rows.group(1));
      }
    }
    return List.copyOf(listed);
  }

  private static Set<String> theFieldsDrawnOn(String page) {
    Set<String> drawn = new LinkedHashSet<>();
    Matcher fields = A_FIELD_THE_WIZARD_DRAWS.matcher(page);
    while (fields.find()) {
      drawn.add(fields.group(1));
    }
    return drawn;
  }

  private static Set<String> theCardsOfferedOn(String page) {
    Set<String> offered = new LinkedHashSet<>();
    Matcher cards = Pattern.compile("<option\\s+value=\"([^\"]+)\"").matcher(page);
    while (cards.find()) {
      offered.add(cards.group(1));
    }
    return offered;
  }

  private static Set<String> theActionsOfferedOn(String page) {
    Set<String> offered = new LinkedHashSet<>();
    Matcher actions = Pattern.compile("name=\"action\"\\s+value=\"([^\"]+)\"").matcher(page);
    while (actions.find()) {
      offered.add(actions.group(1));
    }
    return offered;
  }

  private static String theValueOn(String page, String name) {
    Matcher value = Pattern.compile("<dt>" + name + "</dt>\\s*<dd[^>]*>([^<]+)</dd>").matcher(page);
    return value.find() ? value.group(1).strip() : "";
  }

  private static String theRelatedValueOn(String page, String name) {
    Matcher value =
        Pattern.compile("<dt>" + name + "</dt>\\s*<dd[^>]*>\\s*<(?:a|span)[^>]*>([^<]+)<")
            .matcher(page);
    return value.find() ? value.group(1).strip() : "";
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

  private static URI theWizardFor(String startedAs) {
    return URI.create(
        WIZARD
            + query()
            + "&accountId="
            + ACCOUNT
            + "&startedAs="
            + URLEncoder.encode(startedAs, StandardCharsets.UTF_8));
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
