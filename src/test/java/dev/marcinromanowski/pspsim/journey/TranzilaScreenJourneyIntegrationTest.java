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
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.ResponseEntity;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

class TranzilaScreenJourneyIntegrationTest extends PostgresBackedTest {

  private static final String RUN = "journey-tranzila";
  private static final String ACCOUNT = "merchant-1";
  private static final String TERMINAL = "journeyterminal";
  private static final String APP_KEY = "journey-tranzila-app-key";
  private static final String THE_CHALLENGE_CARD = "4918914107195005";
  private static final String A_PLAIN_CARD = "4111111111111111";
  private static final String HOSTED_FIELDS = "/tranzila/hf/process/ajax_process_transaction";
  private static final String PAYMENTS = "/ui/objects";
  private static final String START = "/ui/objects/start";
  private static final String COMPLETE = "/ui/objects/complete";
  private static final String A_PAGE_PAYMENT = "GET /tranzila/directng/{terminal}/iframenew.php";
  private static final String A_PAYER_PAGE_PATH = "/tranzila/directng/" + TERMINAL + "/payer/";
  private static final String A_CHALLENGE_PAGE_PATH = "/tranzila/3ds/challenge/";

  private static final Pattern A_STARTED_OBJECT = Pattern.compile("[?&]started=([^&]+)");

  private static final Map<String, String> THE_APP_KEY_ALONE =
      Map.of(
          "X-tranzila-api-app-key", APP_KEY,
          "X-tranzila-api-request-time", "1",
          "X-tranzila-api-nonce", "journey-nonce",
          "X-tranzila-api-access-token", "unverified");

  @LocalServerPort private int port;

  private JourneyClient http;

  @BeforeEach
  void configureTheTerminal() {
    http = new JourneyClient(port);
    http.json(
        "configure a terminal whose access token is not verified",
        Kind.WRITE,
        "PUT",
        "/__simulator/tranzila/config",
        """
        {"testRunId":"%s",
         "clock":{"fixedNow":"2026-10-14T09:00:00Z"},
         "accounts":[{"accountId":"%s","configuration":{
           "terminalName":"%s","appKey":"%s","appSecret":"journey-secret",
           "tranzila.authentication":"THE_ACCESS_TOKEN_IS_NOT_VERIFIED"}}],
         "webhooks":[]}
        """
            .formatted(RUN, ACCOUNT, TERMINAL, APP_KEY));
  }

  @AfterEach
  void discardTheRun() {
    http.discardTheTestRun("tranzila", RUN);
    http.json(
        "unconfigure the terminal",
        Kind.WRITE,
        "PUT",
        "/__simulator/tranzila/config",
        """
        {"testRunId":"%s","accounts":[],"webhooks":[]}
        """
            .formatted(RUN));
  }

  @Test
  void every_object_a_payer_finishes_reads_on_the_screens_without_an_address_of_its_payer_page() {
    String page =
        theObjectStartedBy(
            http.form(
                "send a payer to the payment page from the wizard",
                Kind.WRITE,
                START,
                form(
                    "provider",
                    "tranzila",
                    "testRunId",
                    RUN,
                    "accountId",
                    ACCOUNT,
                    "startedAs",
                    A_PAGE_PAYMENT,
                    "field.sum",
                    "10.53",
                    "field.currency",
                    "1",
                    "field.tranmode",
                    "A")));
    String waitingPage = theScreenOf(page);

    ResponseEntity<String> created =
        http.posted(
            "create a debit the card holder must answer a challenge for",
            Kind.WRITE,
            "/v1/transaction/credit_card/create",
            """
            {"terminal_name":"%s","txn_type":"debit","activate_3ds":"Y",
             "card_number":"%s","expire_month":12,"expire_year":2030,
             "items":[{"name":"a","units_number":1,"unit_price":10}]}
            """
                .formatted(TERMINAL, THE_CHALLENGE_CARD),
            THE_APP_KEY_ALONE);
    String apiChallenge = textIn(created.getBody(), "track_id");
    assertThat(textIn(created.getBody(), "challengeUrl")).contains(A_CHALLENGE_PAGE_PATH);
    String waitingChallenge = theScreenOf(apiChallenge);

    String challengedThtk = aHandshakeLocking("12");
    ResponseEntity<String> challengedOnTheFields =
        http.form(
            "charge a card typed into the hosted fields that a challenge stops",
            Kind.WRITE,
            HOSTED_FIELDS,
            aHostedFieldsCharge(challengedThtk, "12", THE_CHALLENGE_CARD));
    String hostedChallenge = textIn(challengedOnTheFields.getBody(), "track_id");
    String waitingHostedChallenge = theScreenOf(hostedChallenge);

    String chargedThtk = aHandshakeLocking("14");
    ResponseEntity<String> chargedOnTheFields =
        http.form(
            "charge a card typed into the hosted fields",
            Kind.WRITE,
            HOSTED_FIELDS,
            aHostedFieldsCharge(chargedThtk, "14", A_PLAIN_CARD));
    String hostedCharge = textIn(chargedOnTheFields.getBody(), "transaction_id");
    String charged = theScreenOf(hostedCharge);

    ResponseEntity<String> paid =
        http.form(
            "pay the page as its payer from its own screen",
            Kind.WRITE,
            COMPLETE,
            form(
                "provider",
                "tranzila",
                "testRunId",
                RUN,
                "providerObjectId",
                page,
                "outcome",
                "PAID",
                "card",
                "published-9022",
                "confirm",
                "yes"));
    assertThat(paid.getStatusCode().value()).isEqualTo(302);
    String paidPage = theScreenOf(page);
    String listed = theScreenListingTheRun();

    assertThat(waitingPage).contains(page).contains("awaiting the payer");
    assertThat(waitingChallenge).contains(apiChallenge).contains("challenge awaiting the payer");
    assertThat(waitingHostedChallenge)
        .contains(hostedChallenge)
        .contains("challenge awaiting the payer");
    assertThat(charged).contains(hostedCharge).contains("debit 000");
    assertThat(paidPage).contains(page).contains("SUCCEEDED");
    assertThat(listed).contains(page, apiChallenge, hostedChallenge, hostedCharge);
    for (String screen :
        new String[] {
          waitingPage, waitingChallenge, waitingHostedChallenge, charged, paidPage, listed
        }) {
      assertThat(screen).doesNotContain(A_PAYER_PAGE_PATH).doesNotContain(A_CHALLENGE_PAGE_PATH);
    }
  }

  private String aHandshakeLocking(String sum) {
    ResponseEntity<String> created =
        http.posted(
            "mint a handshake locking the sum the fields will charge",
            Kind.WRITE,
            "/v2/handshake/create",
            "{\"terminal_name\":\"" + TERMINAL + "\",\"sum\":" + sum + "}",
            THE_APP_KEY_ALONE);
    return textIn(created.getBody(), "thtk");
  }

  private static MultiValueMap<String, String> aHostedFieldsCharge(
      String thtk, String amount, String card) {
    return form(
        "terminal_name",
        TERMINAL,
        "thtk",
        thtk,
        "amount",
        amount,
        "credit_card_number",
        card,
        "cvv",
        "737",
        "expiry_month",
        "12",
        "expiry_year",
        "2030");
  }

  private String theScreenListingTheRun() {
    String listing = PAYMENTS + query();
    return String.valueOf(http.page("list what this test run holds", listing).getBody());
  }

  private String theScreenOf(String providerObjectId) {
    return String.valueOf(
        http.page(
                "open the object on the Payments screen",
                PAYMENTS + query() + "&providerObjectId=" + providerObjectId)
            .getBody());
  }

  private static String theObjectStartedBy(ResponseEntity<String> answered) {
    assertThat(answered.getStatusCode().value()).isEqualTo(302);
    Matcher started =
        A_STARTED_OBJECT.matcher(String.valueOf(answered.getHeaders().getFirst("Location")));
    assertThat(started.find()).as("the wizard hands the started page back").isTrue();
    return started.group(1);
  }

  private static String textIn(String json, String field) {
    Matcher found = Pattern.compile("\"" + field + "\":\"([^\"]*)\"").matcher(String.valueOf(json));
    assertThat(found.find()).as("a " + field + " in " + json).isTrue();
    return found.group(1);
  }

  private static String query() {
    return "?provider=tranzila&testRunId=" + RUN;
  }

  private static MultiValueMap<String, String> form(String... namesAndValues) {
    MultiValueMap<String, String> fields = new LinkedMultiValueMap<>();
    for (int index = 0; index < namesAndValues.length; index += 2) {
      fields.add(namesAndValues[index], namesAndValues[index + 1]);
    }
    return fields;
  }
}
