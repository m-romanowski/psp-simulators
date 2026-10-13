package dev.marcinromanowski.pspsim.journey;

import static org.assertj.core.api.Assertions.assertThat;

import dev.marcinromanowski.pspsim.PostgresBackedTest;
import dev.marcinromanowski.pspsim.journey.JourneyClient.Kind;
import dev.marcinromanowski.pspsim.kernel.ObjectOrigin;
import dev.marcinromanowski.pspsim.kernel.webhook.LocalMerchantServer;
import dev.marcinromanowski.pspsim.ui.ReadForm;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.ResponseEntity;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

class TesterScreenJourneyIntegrationTest extends PostgresBackedTest {

  private static final String GROW_RUN = "screens-grow";
  private static final String GROW_TOKEN_RUN = "screens-grow-saved-card";
  private static final String AZERICARD_RUN = "screens-azericard";
  private static final String AUTRA_RUN = "screens-autra";
  private static final String ACCOUNT = "merchant-1";
  private static final String HELD = "120.50";
  private static final String TAKEN = "25.00";
  private static final String CURRENCY = "AZN";
  private static final String ORDER = "20260915100002";
  private static final String TERMINAL = "77777777";
  private static final String CHOSEN_CARD = "sandbox-4580";
  private static final String PINNED = "2026-09-15T10:00:00Z";
  private static final String A_HOLD = "createPaymentProcess:chargeType=2";
  private static final String A_SAVED_CARD = "createPaymentProcess:chargeType=3";
  private static final String A_SAVED_CARD_CHARGE = "createTransactionWithToken";
  private static final String A_RETRACE = "getTokenTransactionsByExternalIdentifiers";
  private static final String AN_AUTRA_PAYMENT_LOOK_UP = "GET /v1/acquiring/payments/{paymentId}";
  private static final String THE_AUTRA_PAYMENTS_OF_AN_ESTABLISHMENT = "GET /v1/acquiring/payments";
  private static final String A_STATUS_AUTRA_PUBLISHES_NOWHERE = "NOT_A_STATUS";
  private static final String MY_OWN_IDENTIFIER = "4001";
  private static final String AN_IDENTIFIER_NOBODY_SENT = "9999";
  private static final String A_TOKEN_NO_TEST_RUN_EVER_PRODUCED = "tok_nobody_ever_saved";
  private static final String THE_ERROR_ID_GROW_ANSWERS_AN_UNSAVED_TOKEN_WITH = "140";
  private static final String THE_WORDS_GROW_ANSWERS_AN_UNSAVED_TOKEN_IN =
      "\u05dc\u05d0 \u05e0\u05d9\u05ea\u05df \u05dc\u05d1\u05e6\u05e2 \u05ea\u05e9\u05dc\u05d5\u05dd"
          + " \u05e2\u05dd \u05d8\u05d5\u05e7\u05df, \u05e7\u05d5\u05d3 \u05de\u05d6\u05d4\u05d4"
          + " \u05d8\u05d5\u05e7\u05df \u05d0\u05d9\u05e0\u05d5 \u05ea\u05e7\u05d9\u05df";
  private static final String AN_IDENTIFIER_SHAPED_LIKE_NO_UUID_AUTRA_DRAWS = "pagamento-1";
  private static final String THE_SCREEN_DRAWS_AN_ANSWER_OF_THIS_READ_UNDER = "voidRetryable";
  private static final String A_UUID_NO_PAYMENT_OF_THIS_ESTABLISHMENT_ANSWERS_TO =
      "3f1d4c2e-1111-4a7b-8c1d-5e6f70819a2b";
  private static final String CHARGED_OFF_THE_SAVED_CARD = "41.50";
  private static final String AN_AZERICARD_HOLD = "TRTYPE=0";
  private static final String AN_AUTRA_PIX = "POST /v1/acquiring/pix/checkout";
  private static final String AN_AUTRA_CARD_SESSION = "POST /v1/acquiring/card-sessions";
  private static final String AN_AUTRA_SALE = "POST /v1/acquiring/payments";
  private static final String THE_CARD_AUTRA_PRINTS = "published-4111";
  private static final String CHARGED = "150.00";
  private static final String AUTRA_DOCUMENT = "25055662000190";
  private static final String COLLECTED = "99.90";
  private static final String BRL = "BRL";
  private static final String A_TENANT_SECRET = "a-tenant-callback-secret-of-thirty-two";
  private static final String CONFIRMED = "yes";

  private static final String THE_OBJECTS_THIS_ONE_IS_TIED_TO = "Objects this one is tied to";

  private static final String WHAT_IT_ANSWERED_ABOUT_THE_QUESTION =
      "What it answered about the question you asked";

  private static final String WORKBENCH = "/ui";
  private static final String ACCOUNT_FORM = "/ui/configuration/account";
  private static final String CONFIGURATION = "/ui/configuration";
  private static final String CLOCK = "/ui/configuration/clock";
  private static final String WIZARD = "/ui/objects/new";
  private static final String START = "/ui/objects/start";
  private static final String PAYMENTS = "/ui/objects";
  private static final String COMPLETE = "/ui/objects/complete";
  private static final String ACT = "/ui/objects/act";
  private static final String SEND_A_CALLBACK = "/ui/objects/webhook";
  private static final String DELIVERIES = "/ui/webhooks";
  private static final String TRAIL = "/ui/audit";
  private static final String READS = "/ui/reads";
  private static final String WHERE_AUTRA_PRINTS_THE_WHOLE_RUN_AT_ONCE = "/ui/x/autra/read-back";
  private static final String ASK_A_READ = "/ui/reads/ask";

  private static final Pattern A_STARTED_OBJECT = Pattern.compile("[?&]started=([^&]+)");

  private static final Pattern A_LISTED_OBJECT =
      Pattern.compile("providerObjectId=([A-Za-z0-9-]+)\"");

  private static final Pattern A_FORM_A_TESTER_SUBMITS =
      Pattern.compile("(?s)<form\\b[^>]*>.*?</form>");

  private static final Pattern AN_INPUT_A_FORM_CARRIES = Pattern.compile("(?s)<input\\b[^>]*>");

  private static final Pattern A_FIELD_THE_WIZARD_DRAWS =
      Pattern.compile("name=\"field\\.([^\"]+)\"");

  private static final Pattern A_VALUE_OFFERED =
      Pattern.compile("<option[^>]*value=\"([^\"]*)\"[^>]*label=\"([^\"]*)\"[^>]*>");

  @LocalServerPort private int port;

  private JourneyClient http;
  private LocalMerchantServer merchant;
  private AzericardMerchant keys;

  @BeforeEach
  void standUpTheMerchant() {
    http = new JourneyClient(port);
    merchant = new LocalMerchantServer();
    keys = new AzericardMerchant();
  }

  @AfterEach
  void takeTheMerchantDown() {
    http.discardTheTestRun("grow", GROW_RUN);
    http.discardTheTestRun("grow", GROW_TOKEN_RUN);
    http.discardTheTestRun("azericard", AZERICARD_RUN);
    http.discardTheTestRun("autra", AUTRA_RUN);
    merchant.close();
  }

  @Test
  void a_tester_takes_the_grow_hold_the_quickstart_describes_to_settled_on_screens_alone() {
    walkTheGrowScreens();
  }

  @Test
  void a_tester_saves_a_card_on_grow_and_charges_the_one_this_run_suggests_on_screens() {
    walkTheGrowSavedCardScreens();
  }

  @Test
  void a_tester_retraces_that_charge_by_the_identifier_they_sent_on_screens_alone() {
    retraceTheChargeOnScreensAlone(walkTheGrowSavedCardScreens());
  }

  @Test
  void a_tester_takes_the_azericard_sale_the_quickstart_describes_to_completed_on_screens_alone() {
    walkTheAzericardScreens();
  }

  @Test
  void a_tester_takes_an_autra_pix_from_a_qr_code_to_paid_on_screens_alone() {
    walkTheAutraPixScreens();
  }

  @Test
  void a_tester_stores_a_card_on_autra_and_charges_it_off_session_on_screens_alone() {
    walkTheAutraOffSessionScreens();
  }

  @Test
  void a_tester_asks_autra_both_questions_its_contract_answers_on_screens_alone() {
    askAutraTheQuestionsItPublishesOnScreensAlone(walkTheAutraOffSessionScreens());
  }

  private void walkTheGrowScreens() {
    int owedBefore = merchant.received().size();
    assertThat(http.page("open the workbench", WORKBENCH).getStatusCode().value()).isEqualTo(200);

    ResponseEntity<String> configured =
        http.form(
            "configure the account and the callback target on one form",
            Kind.WRITE,
            ACCOUNT_FORM,
            form(
                "provider",
                "grow",
                "testRunId",
                GROW_RUN,
                "accountId",
                ACCOUNT,
                "callbackUrl",
                merchant.url(),
                "field.userId",
                "screens-grow-user",
                "field.pageCode",
                "screens-grow-page"));
    assertThat(configured.getStatusCode().value()).isEqualTo(302);

    ResponseEntity<String> pinned =
        http.form(
            "pin the clock of this test run",
            Kind.WRITE,
            CLOCK,
            form("provider", "grow", "testRunId", GROW_RUN, "fixedNow", PINNED));
    assertThat(pinned.getStatusCode().value()).isEqualTo(302);
    assertThat(http.page("read the clock back", CONFIGURATION + query("grow", GROW_RUN)).getBody())
        .contains(PINNED);

    String wizard =
        http.page("draw the fields this way of starting a payment reads", growWizard()).getBody();
    assertThat(theFieldsDrawnOn(wizard))
        .contains("sum", "description", "successUrl", "cancelUrl", "notifyUrl");
    assertThat(wizard).contains(merchant.url());

    String processId =
        theObjectStartedBy(
            http.form(
                "start the payment from the wizard, with no client application",
                Kind.WRITE,
                START,
                form(
                    "provider",
                    "grow",
                    "testRunId",
                    GROW_RUN,
                    "accountId",
                    ACCOUNT,
                    "startedAs",
                    A_HOLD,
                    "field.sum",
                    HELD,
                    "field.description",
                    "Journey hold",
                    "field.successUrl",
                    "https://merchant.example/thanks",
                    "field.cancelUrl",
                    "https://merchant.example/cancel",
                    "field.pageField[fullName]",
                    "Israel Israeli",
                    "field.pageField[phone]",
                    "0505503144",
                    "field.notifyUrl",
                    merchant.url())));

    assertThat(theObjectsListedOn("grow", GROW_RUN))
        .as("a started grow process is one row on the Payments screen")
        .containsExactly(processId);

    String waiting = theScreenOf("grow", GROW_RUN, processId);
    assertThat(waiting)
        .contains("NOT_INITIALIZED")
        .as("the created-by cell of this object says a tester made it on a screen")
        .containsPattern("created by</dt>\\s*<dd>" + ObjectOrigin.UI + "</dd>");
    assertThat(theCardsOfferedOn(waiting))
        .as("the payer step offers the cards this run publishes and no card number")
        .contains(CHOSEN_CARD)
        .doesNotContain("4580123412341234");

    String refused =
        http.form(
                "name a card beside an outcome that leaves none",
                Kind.WRITE,
                COMPLETE,
                form(
                    "provider",
                    "grow",
                    "testRunId",
                    GROW_RUN,
                    "providerObjectId",
                    processId,
                    "outcome",
                    "DECLINED",
                    "card",
                    CHOSEN_CARD,
                    "confirm",
                    CONFIRMED))
            .getBody();
    assertThat(refused).contains("leaves no card behind");

    ResponseEntity<String> finished =
        http.form(
            "finish the hosted page as the payer, from the payment's own screen",
            Kind.WRITE,
            COMPLETE,
            form(
                "provider",
                "grow",
                "testRunId",
                GROW_RUN,
                "providerObjectId",
                processId,
                "outcome",
                "PAID",
                "card",
                CHOSEN_CARD,
                "confirm",
                CONFIRMED));
    assertThat(finished.getStatusCode().value()).isEqualTo(302);

    assertThat(merchant.received()).hasSize(owedBefore + 1);
    Map<String, String> callback = merchant.received().get(owedBefore).fields();
    assertThat(callback)
        .containsEntry("data[processId]", processId)
        .containsEntry("data[statusCode]", "11")
        .containsEntry("data[paymentDate]", "15/09/26");

    List<String> both = theObjectsListedOn("grow", GROW_RUN);
    assertThat(both)
        .as("a finished grow page is two rows where a started one was one")
        .hasSize(2)
        .contains(processId);
    String transactionId =
        both.stream().filter(id -> !id.equals(processId)).findFirst().orElseThrow();

    String theProcessAfterItsPayer = theScreenOf("grow", GROW_RUN, processId);
    assertThat(theAreaOfObjectsThisOneIsTiedTo(theProcessAfterItsPayer))
        .as("the process prints the transaction its completion created, and not only a link to it")
        .containsPattern(">" + transactionId + "</(a|span)>");
    assertThat(theProcessAfterItsPayer)
        .as("a finished page offers no second payer step")
        .contains("already reached an outcome");

    String held = theScreenOf("grow", GROW_RUN, transactionId);
    assertThat(held).contains("READY_FOR_CAPTURE");
    assertThat(theAreaOfObjectsThisOneIsTiedTo(held))
        .as("the transaction prints the process it was created on, and not only a link to it")
        .containsPattern(">" + processId + "</(a|span)>");

    ResponseEntity<String> captured =
        http.form(
            "settle the hold from the transaction's own screen",
            Kind.WRITE,
            ACT,
            form(
                "provider",
                "grow",
                "testRunId",
                GROW_RUN,
                "providerObjectId",
                transactionId,
                "action",
                "CAPTURE",
                "amount",
                HELD,
                "confirm",
                CONFIRMED));
    assertThat(captured.getStatusCode().value()).isEqualTo(302);
    assertThat(theScreenOf("grow", GROW_RUN, transactionId)).contains("SUCCEEDED");

    assertThat(http.page("read the delivery queue", DELIVERIES + query("grow", GROW_RUN)).getBody())
        .contains("DELIVERED")
        .contains("payment_process.completed");

    assertThat(theTrailOf("grow", GROW_RUN))
        .as("every screen action this journey took is in the trail under its own path")
        .contains(ACCOUNT_FORM, CLOCK, START, COMPLETE, ACT);

    assertThat(theControlApiCallsOf(http.steps()))
        .as("a tester clicking makes no control-API call and no provider call")
        .isEmpty();
  }

  private record TheChargeThisRunTook(String processId, String transactionId, String cardToken) {}

  private void retraceTheChargeOnScreensAlone(TheChargeThisRunTook charged) {
    String questions =
        String.valueOf(
            http.page("open the screen that looks a payment up", READS + growTokenRun()).getBody());
    assertThat(questions)
        .as("the question grow publishes is offered by what it answers and not by its operation")
        .contains("Find out whether a charge on a saved card went through")
        .contains(A_RETRACE);

    String theQuestionItAsks =
        String.valueOf(http.page("pick grow's retrace", theRetraceForm()).getBody());
    assertThat(theQuestionItAsks)
        .as(
            "grow answers an empty transactions list, so the form says what an empty answer of"
                + " this question means before a tester asks it")
        .contains(ReadForm.WHAT_IT_MEANS_WHEN_THIS_ONE_ANSWERS_NOTHING)
        .doesNotContain(ReadForm.THIS_ONE_NEVER_ANSWERS_NOTHING);
    assertThat(theQuestionItAsks)
        .as(
            "grow names this read among the operations a scenario rule may be declared on, so a"
                + " rule of this run may answer it and a rule carrying a story moves that story"
                + " on when it does; the screen says so before the tester presses, and the"
                + " sentence that says asking twice leaves the run as it was is not the one it"
                + " shows here")
        .contains(ReadForm.A_RULE_OF_THIS_RUN_MAY_DECIDE_THIS_QUESTION)
        .doesNotContain(ReadForm.NO_RULE_OF_THIS_RUN_IS_MATCHED_ON_THIS_QUESTION);
    assertThat(theFieldsDrawnOn(theQuestionItAsks))
        .as("the form asks for the two identifiers a merchant sent itself and for no userId")
        .containsExactly("cardToken", "transactionUniqueIdentifier", "transactionGroupIdentifier")
        .doesNotContain("userId");
    assertThat(theValuesSuggestedFor(theQuestionItAsks, "cardToken"))
        .as(
            "the cards this run minted are suggested on the box, and the box is what makes grow's"
                + " own refusal of a token it never minted reachable by typing one")
        .containsExactly(charged.cardToken());

    String refusedByGrow =
        String.valueOf(
            http.page(
                    "type a token no test run ever produced, picking nothing at all",
                    theRetraceAskedWith(
                        theQuestionItAsks, A_TOKEN_NO_TEST_RUN_EVER_PRODUCED, MY_OWN_IDENTIFIER))
                .getBody());
    assertThat(refusedByGrow)
        .as(
            "grow resolves a cardToken against the process that saved it on the same business and"
                + " answers err.id %s for one that business never saved, which is this provider's"
                + " own refusal and reaches this screen in this provider's own words",
            THE_ERROR_ID_GROW_ANSWERS_AN_UNSAVED_TOKEN_WITH)
        .contains(THE_ERROR_ID_GROW_ANSWERS_AN_UNSAVED_TOKEN_WITH)
        .contains(THE_WORDS_GROW_ANSWERS_AN_UNSAVED_TOKEN_IN)
        .doesNotContain(charged.transactionId());

    String answered =
        String.valueOf(
            http.page(
                    "ask grow whether the charge went through",
                    theRetraceAskedWith(theQuestionItAsks, charged.cardToken(), MY_OWN_IDENTIFIER))
                .getBody());
    assertThat(answered)
        .as("the answer a rule of this run may decide says so beside the rows it answered")
        .contains(ReadForm.A_RULE_OF_THIS_RUN_MAY_DECIDE_THIS_QUESTION);
    assertThat(answered)
        .as("the answer names the charge grow holds and what it holds about it")
        .contains(charged.transactionId())
        .contains(CHARGED_OFF_THE_SAVED_CARD)
        .contains("providerObjectId=" + charged.transactionId());
    assertThat(answered)
        .as(
            "grow answers the transactions and says nothing of its own about the identifiers it"
                + " was asked with, so this screen prints no panel for one")
        .doesNotContain(WHAT_IT_ANSWERED_ABOUT_THE_QUESTION);

    String nothing =
        String.valueOf(
            http.page(
                    "ask grow about an identifier this business never sent",
                    theRetraceAskedWith(
                        theQuestionItAsks, charged.cardToken(), AN_IDENTIFIER_NOBODY_SENT))
                .getBody());
    assertThat(nothing)
        .as(
            "an empty answer is grow's own answer, so the screen says what it means rather than"
                + " leaving a tester to read an empty panel as a read that failed")
        .contains("Grow answers an empty transactions list")
        .doesNotContain(charged.transactionId());

    assertThat(theRowsNaming(theTrailOf("grow", GROW_TOKEN_RUN), ASK_A_READ))
        .as(
            "grow names this read among the operations a rule of a test run may be declared on,"
                + " so asking it is a thing that happened to this run: each of the three"
                + " questions asked above stands on the trail of this run, the one grow refused"
                + " among them, while drawing the form that asks them wrote nothing")
        .isEqualTo(3);

    assertThat(theControlApiCallsOf(http.steps()))
        .as("a tester clicking makes no control-API call and no provider call")
        .isEmpty();
  }

  private String growTokenRun() {
    return query("grow", GROW_TOKEN_RUN);
  }

  private String theRetraceForm() {
    return READS + growTokenRun() + "&accountId=" + ACCOUNT + "&readNamed=" + A_RETRACE;
  }

  private String theRetraceAskedWith(
      String theFormAsDrawn, String cardToken, String myOwnIdentifier) {
    Map<String, String> typed = new LinkedHashMap<>();
    typed.put("field.cardToken", cardToken);
    typed.put("field.transactionUniqueIdentifier", myOwnIdentifier);
    return theAddressABrowserSubmittingWouldAskFor(theFormAsDrawn, ASK_A_READ, typed);
  }

  private static String theAddressABrowserSubmittingWouldAskFor(
      String page, String submittedTo, Map<String, String> typed) {
    String form = theFormSubmittedTo(page, submittedTo);
    assertThat(theAddressOf(form))
        .as(
            "a browser submitting a form whose method is get replaces the query of the address"
                + " it submits to with the fields of the form, so a value named only in that"
                + " address never leaves the screen: %s",
            form)
        .isEqualTo(submittedTo);
    Map<String, String> sent = new LinkedHashMap<>();
    Matcher inputs = AN_INPUT_A_FORM_CARRIES.matcher(form);
    while (inputs.find()) {
      String name = theAttributeOf(inputs.group(), "name");
      if (name != null) {
        sent.put(name, typed.getOrDefault(name, theAttributeOf(inputs.group(), "value")));
      }
    }
    assertThat(sent.keySet())
        .as(
            "a browser sends the fields of the form and nothing of the address it submits to, so"
                + " every value this route needs stands among them: %s",
            form)
        .containsAll(typed.keySet());
    List<String> pairs = new ArrayList<>();
    sent.forEach(
        (name, value) -> {
          if (value != null && !value.isEmpty()) {
            pairs.add(
                URLEncoder.encode(name, StandardCharsets.UTF_8)
                    + "="
                    + URLEncoder.encode(value, StandardCharsets.UTF_8));
          }
        });
    return theAddressOf(form) + "?" + String.join("&", pairs);
  }

  private static String theFormSubmittedTo(String page, String submittedTo) {
    Matcher forms = A_FORM_A_TESTER_SUBMITS.matcher(page);
    while (forms.find()) {
      if (theAddressOf(forms.group()).split("\\?", -1)[0].equals(submittedTo)) {
        return forms.group();
      }
    }
    throw new AssertionError("no form of this screen submits to " + submittedTo);
  }

  private static String theAddressOf(String form) {
    return String.valueOf(theAttributeOf(form, "action"));
  }

  private static @Nullable String theAttributeOf(String element, String named) {
    Matcher attribute = Pattern.compile(named + "=\"([^\"]*)\"", Pattern.DOTALL).matcher(element);
    return attribute.find() ? attribute.group(1) : null;
  }

  private TheChargeThisRunTook walkTheGrowSavedCardScreens() {
    ResponseEntity<String> configured =
        http.form(
            "configure the grow account this saved card is charged on",
            Kind.WRITE,
            ACCOUNT_FORM,
            form(
                "provider",
                "grow",
                "testRunId",
                GROW_TOKEN_RUN,
                "accountId",
                ACCOUNT,
                "callbackUrl",
                merchant.url(),
                "field.userId",
                "screens-grow-saved-card-user",
                "field.pageCode",
                "screens-grow-saved-card-page"));
    assertThat(configured.getStatusCode().value()).isEqualTo(302);

    String emptyWizard =
        http.page("draw the fields a grow stored-card charge reads", theChargeWizard()).getBody();
    assertThat(theFieldsDrawnOn(String.valueOf(emptyWizard)))
        .as("the wizard asks for the token, and never for the pageCode or the chargeType")
        .contains("cardToken", "paymentType", "paymentNum", "transactionUniqueIdentifier")
        .doesNotContain("pageCode", "chargeType", "notifyUrl");
    assertThat(theValuesSuggestedFor(String.valueOf(emptyWizard), "cardToken"))
        .as("a run that has saved no card suggests no card to charge, and still takes one typed")
        .isEmpty();
    assertThat(emptyWizard)
        .contains("This test run has produced no value this field could suggest yet");

    String processId =
        theObjectStartedBy(
            http.form(
                "start a card registration from the wizard, with no client application",
                Kind.WRITE,
                START,
                form(
                    "provider",
                    "grow",
                    "testRunId",
                    GROW_TOKEN_RUN,
                    "accountId",
                    ACCOUNT,
                    "startedAs",
                    A_SAVED_CARD,
                    "field.sum",
                    "1.00",
                    "field.description",
                    "Keep this card for later",
                    "field.successUrl",
                    "https://merchant.example/saved",
                    "field.cancelUrl",
                    "https://merchant.example/cancel",
                    "field.pageField[fullName]",
                    "Israel Israeli",
                    "field.pageField[phone]",
                    "0505503144",
                    "field.notifyUrl",
                    merchant.url())));

    ResponseEntity<String> saved =
        http.form(
            "type the card on the hosted page as the payer, from the process's own screen",
            Kind.WRITE,
            COMPLETE,
            form(
                "provider",
                "grow",
                "testRunId",
                GROW_TOKEN_RUN,
                "providerObjectId",
                processId,
                "outcome",
                "PAID",
                "card",
                CHOSEN_CARD,
                "confirm",
                CONFIRMED));
    assertThat(saved.getStatusCode().value()).isEqualTo(302);

    String theProcessThatSavedTheCard = theScreenOf("grow", GROW_TOKEN_RUN, processId);
    String theTokenTheProcessPrints = theRelatedObjectOn(theProcessThatSavedTheCard, "cardToken");
    assertThat(theTokenTheProcessPrints).isNotBlank();

    String chargeWizard =
        http.page("draw the stored-card charge again, now that a card is saved", theChargeWizard())
            .getBody();
    List<String> offered = theValuesSuggestedFor(String.valueOf(chargeWizard), "cardToken");
    assertThat(offered)
        .as("the wizard suggests the card this run saved, so nothing has to be typed or pasted")
        .containsExactly(theTokenTheProcessPrints);
    assertThat(theLabelOfferedFor(String.valueOf(chargeWizard), theTokenTheProcessPrints))
        .as("a tester reads the saved card by the process and payer it came from")
        .contains(processId)
        .contains("Israel Israeli")
        .doesNotContain(theTokenTheProcessPrints);

    String transactionId =
        theObjectStartedBy(
            http.form(
                "charge the saved card by picking it from the list the wizard offers",
                Kind.WRITE,
                START,
                form(
                    "provider",
                    "grow",
                    "testRunId",
                    GROW_TOKEN_RUN,
                    "accountId",
                    ACCOUNT,
                    "startedAs",
                    A_SAVED_CARD_CHARGE,
                    "field.cardToken",
                    offered.get(0),
                    "field.sum",
                    CHARGED_OFF_THE_SAVED_CARD,
                    "field.description",
                    "The money that card was saved for",
                    "field.paymentType",
                    "2",
                    "field.paymentNum",
                    "1",
                    "field.transactionUniqueIdentifier",
                    MY_OWN_IDENTIFIER,
                    "field.pageField[fullName]",
                    "Israel Israeli",
                    "field.pageField[phone]",
                    "0505503144")));

    String charged = theScreenOf("grow", GROW_TOKEN_RUN, transactionId);
    assertThat(charged)
        .contains("SUCCEEDED")
        .contains(CHARGED_OFF_THE_SAVED_CARD)
        .as("the created-by cell of this charge says a tester made it on a screen")
        .containsPattern("created by</dt>\\s*<dd>" + ObjectOrigin.UI + "</dd>");

    assertThat(theObjectsListedOn("grow", GROW_TOKEN_RUN))
        .as("the charge, the transaction the registration left and the process are three rows")
        .hasSize(3)
        .contains(transactionId, processId);

    assertThat(theTrailOf("grow", GROW_TOKEN_RUN)).contains(ACCOUNT_FORM, START, COMPLETE);
    assertThat(theControlApiCallsOf(http.steps()))
        .as("a tester clicking makes no control-API call and no provider call")
        .isEmpty();
    return new TheChargeThisRunTook(processId, transactionId, offered.get(0));
  }

  private String theChargeWizard() {
    return WIZARD
        + query("grow", GROW_TOKEN_RUN)
        + "&accountId="
        + ACCOUNT
        + "&startedAs="
        + A_SAVED_CARD_CHARGE;
  }

  private static List<String> theValuesSuggestedFor(String page, String field) {
    List<String> suggested = new ArrayList<>();
    Matcher options = A_VALUE_OFFERED.matcher(theSuggestionsDrawnFor(page, field));
    while (options.find()) {
      if (!options.group(1).isEmpty()) {
        suggested.add(options.group(1));
      }
    }
    return List.copyOf(suggested);
  }

  private static String theLabelOfferedFor(String page, String value) {
    Matcher options = A_VALUE_OFFERED.matcher(page);
    while (options.find()) {
      if (options.group(1).equals(value)) {
        return options.group(2);
      }
    }
    return "";
  }

  private static String theSuggestionsDrawnFor(String page, String field) {
    Matcher box =
        Pattern.compile(
                "<input[^>]*name=\"field\\." + Pattern.quote(field) + "\"[^>]*>", Pattern.DOTALL)
            .matcher(page);
    assertThat(box.find())
        .as(
            "%s is read by the provider, so this screen draws it as a box a tester types any"
                + " value into and never as a list a tester can only pick from",
            field)
        .isTrue();
    Matcher named = Pattern.compile("list=\"([^\"]*)\"").matcher(box.group());
    if (!named.find()) {
      return "";
    }
    Matcher suggestions =
        Pattern.compile(
                "<datalist[^>]*id=\"" + Pattern.quote(named.group(1)) + "\"[^>]*>(.*?)</datalist>",
                Pattern.DOTALL)
            .matcher(page);
    assertThat(suggestions.find())
        .as("the box names the list its suggestions come from, so that list is on the page")
        .isTrue();
    return suggestions.group(1);
  }

  private static String theRelatedObjectOn(String screen, String name) {
    Matcher named =
        Pattern.compile(
                "<dt>" + Pattern.quote(name) + "</dt>\\s*<dd[^>]*>\\s*<(?:a|span)[^>]*>([^<]+)<")
            .matcher(theAreaOfObjectsThisOneIsTiedTo(screen));
    return named.find() ? named.group(1).strip() : "";
  }

  private void walkTheAzericardScreens() {
    int owedBefore = merchant.received().size();
    ResponseEntity<String> configured =
        http.form(
            "configure the terminal, both keys and the callback target on one form",
            Kind.WRITE,
            ACCOUNT_FORM,
            form(
                "provider",
                "azericard",
                "testRunId",
                AZERICARD_RUN,
                "accountId",
                ACCOUNT,
                "callbackUrl",
                merchant.url(),
                "field.terminalId",
                TERMINAL,
                "field.apiKey",
                keys.merchantPublicKeyPem(),
                "field.apiSecret",
                keys.gatewayPrivateKeyPem()));
    assertThat(configured.getStatusCode().value()).isEqualTo(302);

    String wizard =
        http.page("draw the fields an azericard authorization reads", azericardWizard()).getBody();
    assertThat(theFieldsDrawnOn(wizard)).contains("ORDER", "AMOUNT", "CURRENCY", "BACKREF");

    String intRef =
        theObjectStartedBy(
            http.form(
                "start the authorization from the wizard, with no client application",
                Kind.WRITE,
                START,
                form(
                    "provider",
                    "azericard",
                    "testRunId",
                    AZERICARD_RUN,
                    "accountId",
                    ACCOUNT,
                    "startedAs",
                    AN_AZERICARD_HOLD,
                    "field.ORDER",
                    ORDER,
                    "field.AMOUNT",
                    TAKEN,
                    "field.CURRENCY",
                    CURRENCY,
                    "field.DESC",
                    "Journey preauthorization",
                    "field.BACKREF",
                    merchant.url())));

    assertThat(theObjectsListedOn("azericard", AZERICARD_RUN))
        .as("an azericard authorization is one row and stays one row")
        .containsExactly(intRef);

    String authorized = theScreenOf("azericard", AZERICARD_RUN, intRef);
    assertThat(authorized)
        .contains("READY_FOR_CAPTURE")
        .contains(CURRENCY)
        .as("the created-by cell of this object says a tester made it on a screen")
        .containsPattern("created by</dt>\\s*<dd>" + ObjectOrigin.UI + "</dd>");
    assertThat(theCardsOfferedOn(authorized))
        .as("azericard leaves no payer step for a screen to offer")
        .isEmpty();
    assertThat(authorized).contains("leaves a payer step on a payment page and on nothing else");

    assertThat(merchant.received())
        .as("an azericard authorization delivers nothing on its own")
        .hasSize(owedBefore);

    ResponseEntity<String> sent =
        http.form(
            "send the callback the authorization itself does not, from the payment's own screen",
            Kind.WRITE,
            SEND_A_CALLBACK,
            form(
                "provider",
                "azericard",
                "testRunId",
                AZERICARD_RUN,
                "providerObjectId",
                intRef,
                "confirm",
                CONFIRMED));
    assertThat(sent.getStatusCode().value()).isEqualTo(302);

    assertThat(merchant.received()).hasSize(owedBefore + 1);
    Map<String, String> callback = merchant.received().get(owedBefore).fields();
    assertThat(callback).containsEntry("INT_REF", intRef).containsEntry("ORDER", ORDER);
    assertThat(
            keys.verifiesAsTheGateway(
                callback.get("P_SIGN"),
                callback.get("AMOUNT"),
                callback.get("TERMINAL"),
                callback.get("APPROVAL"),
                callback.get("RRN"),
                callback.get("INT_REF")))
        .as("the callback a screen sent is signed the way the gateway signs one")
        .isTrue();

    ResponseEntity<String> captured =
        http.form(
            "complete the sale from the payment's own screen",
            Kind.WRITE,
            ACT,
            form(
                "provider",
                "azericard",
                "testRunId",
                AZERICARD_RUN,
                "providerObjectId",
                intRef,
                "action",
                "CAPTURE",
                "amount",
                TAKEN,
                "confirm",
                CONFIRMED));
    assertThat(captured.getStatusCode().value()).isEqualTo(302);

    assertThat(theScreenOf("azericard", AZERICARD_RUN, intRef)).contains("SUCCEEDED");
    assertThat(theObjectsListedOn("azericard", AZERICARD_RUN)).containsExactly(intRef);

    assertThat(
            http.page("read the delivery queue", DELIVERIES + query("azericard", AZERICARD_RUN))
                .getBody())
        .contains("DELIVERED");

    assertThat(theTrailOf("azericard", AZERICARD_RUN)).contains(ACCOUNT_FORM, START, ACT);

    assertThat(theControlApiCallsOf(http.steps())).isEmpty();
  }

  private void walkTheAutraPixScreens() {
    int owedBefore = merchant.received().size();
    ResponseEntity<String> configured =
        http.form(
            "configure the autra merchant, its callback secret and its callback target",
            Kind.WRITE,
            ACCOUNT_FORM,
            form(
                "provider",
                "autra",
                "testRunId",
                AUTRA_RUN,
                "accountId",
                ACCOUNT,
                "callbackUrl",
                merchant.url(),
                "field.clientId",
                "screens-autra-client",
                "field.clientSecret",
                "screens-autra-secret",
                "field.documentId",
                AUTRA_DOCUMENT,
                "field.webhookSecret",
                A_TENANT_SECRET));
    assertThat(configured.getStatusCode().value()).isEqualTo(302);

    String wizard =
        http.page("draw the fields an autra Pix checkout reads", autraWizard()).getBody();
    assertThat(theFieldsDrawnOn(wizard)).contains("amount", "currency", "description", "orderId");

    String pixId =
        theObjectStartedBy(
            http.form(
                "hand out a Pix QR code from the wizard, with no client application",
                Kind.WRITE,
                START,
                form(
                    "provider",
                    "autra",
                    "testRunId",
                    AUTRA_RUN,
                    "accountId",
                    ACCOUNT,
                    "startedAs",
                    AN_AUTRA_PIX,
                    "field.amount",
                    COLLECTED,
                    "field.currency",
                    BRL,
                    "field.description",
                    "Journey Pix",
                    "field.orderId",
                    ORDER)));

    assertThat(theObjectsListedOn("autra", AUTRA_RUN))
        .as("a Pix handed out is one row on the Payments screen")
        .containsExactly(pixId);

    String waiting = theScreenOf("autra", AUTRA_RUN, pixId);
    assertThat(waiting).contains("PENDING").contains("PROCESSING").contains(BRL);
    assertThat(waiting)
        .as("the screen hands the tester the QR payload the payer would scan")
        .contains("00020126580014BR.GOV.BCB.PIX01" + pixId);
    assertThat(theCardsOfferedOn(waiting))
        .as("a Pix payer names no card, so the payer step offers none")
        .isEmpty();
    assertThat(waiting).contains("own bank app");

    assertThat(merchant.received())
        .as("a Pix waiting for its payer delivers nothing")
        .hasSize(owedBefore);

    ResponseEntity<String> paid =
        http.form(
            "pay the QR as the payer, from the payment's own screen",
            Kind.WRITE,
            COMPLETE,
            form(
                "provider",
                "autra",
                "testRunId",
                AUTRA_RUN,
                "providerObjectId",
                pixId,
                "outcome",
                "PAID",
                "confirm",
                CONFIRMED));
    assertThat(paid.getStatusCode().value()).isEqualTo(302);

    String settledScreen = theScreenOf("autra", AUTRA_RUN, pixId);
    assertThat(settledScreen).contains("AUTHORIZED").contains("SUCCEEDED");
    assertThat(theObjectsListedOn("autra", AUTRA_RUN))
        .as("a paid Pix stays the one row it was")
        .containsExactly(pixId);

    assertThat(merchant.received()).hasSize(owedBefore + 1);
    String delivered = merchant.received().get(owedBefore).body();
    assertThat(delivered)
        .contains("acquiring.financial_transaction")
        .contains("\"pixId\":\"" + pixId + "\"")
        .contains("\"status\":\"AUTHORIZED\"");

    assertThat(
            http.page("read the delivery queue", DELIVERIES + query("autra", AUTRA_RUN)).getBody())
        .contains("DELIVERED")
        .contains("acquiring.financial_transaction");

    assertThat(theTrailOf("autra", AUTRA_RUN)).contains(ACCOUNT_FORM, START, COMPLETE);

    assertThat(theControlApiCallsOf(http.steps())).isEmpty();
  }

  private String walkTheAutraOffSessionScreens() {
    ResponseEntity<String> configured =
        http.form(
            "configure the autra merchant this off-session journey charges",
            Kind.WRITE,
            ACCOUNT_FORM,
            form(
                "provider",
                "autra",
                "testRunId",
                AUTRA_RUN,
                "accountId",
                ACCOUNT,
                "field.clientId",
                "screens-autra-off-session-client",
                "field.clientSecret",
                "screens-autra-off-session-secret",
                "field.documentId",
                AUTRA_DOCUMENT));
    assertThat(configured.getStatusCode().value()).isEqualTo(302);

    String sessionWizard =
        http.page(
                "draw the fields an autra hosted card session reads",
                WIZARD
                    + query("autra", AUTRA_RUN)
                    + "&accountId="
                    + ACCOUNT
                    + "&startedAs="
                    + AN_AUTRA_CARD_SESSION)
            .getBody();
    assertThat(theFieldsDrawnOn(String.valueOf(sessionWizard))).containsExactly("nickname");

    String sessionId =
        theObjectStartedBy(
            http.form(
                "open a hosted card session from the wizard, with no client application",
                Kind.WRITE,
                START,
                form(
                    "provider",
                    "autra",
                    "testRunId",
                    AUTRA_RUN,
                    "accountId",
                    ACCOUNT,
                    "startedAs",
                    AN_AUTRA_CARD_SESSION,
                    "field.nickname",
                    "Personal Visa")));

    String waiting = theScreenOf("autra", AUTRA_RUN, sessionId);
    assertThat(waiting).contains("PENDING").contains("PROCESSING");
    assertThat(waiting)
        .as("a payer-facing url is never shown, so the screen carries no page token")
        .doesNotContain("?token=");
    assertThat(theCardsOfferedToAnAutraPayerOn(waiting))
        .as("a card page offers the one card autra prints anywhere")
        .contains(THE_CARD_AUTRA_PRINTS);

    ResponseEntity<String> tokenized =
        http.form(
            "type the card on the hosted page as the payer, from the session's own screen",
            Kind.WRITE,
            COMPLETE,
            form(
                "provider",
                "autra",
                "testRunId",
                AUTRA_RUN,
                "providerObjectId",
                sessionId,
                "outcome",
                "PAID",
                "card",
                THE_CARD_AUTRA_PRINTS,
                "confirm",
                CONFIRMED));
    assertThat(tokenized.getStatusCode().value()).isEqualTo(302);

    String stored = theScreenOf("autra", AUTRA_RUN, sessionId);
    assertThat(stored).contains("TOKENIZED").contains("SUCCEEDED");
    String slugToken = theStoredCardHalfOn(stored, "slugToken");
    String slugStoredCard = theStoredCardHalfOn(stored, "slugStoredCard");
    assertThat(slugToken).isNotBlank();
    assertThat(slugStoredCard).isNotBlank();

    String saleWizard =
        http.page(
                "draw the fields an autra off-session sale reads",
                WIZARD
                    + query("autra", AUTRA_RUN)
                    + "&accountId="
                    + ACCOUNT
                    + "&startedAs="
                    + AN_AUTRA_SALE)
            .getBody();
    assertThat(theFieldsDrawnOn(String.valueOf(saleWizard)))
        .contains("amount", "currency", "transactionType", "slugToken", "slugStoredCard");

    String paymentId =
        theObjectStartedBy(
            http.form(
                "charge the stored card off-session from the wizard",
                Kind.WRITE,
                START,
                form(
                    "provider",
                    "autra",
                    "testRunId",
                    AUTRA_RUN,
                    "accountId",
                    ACCOUNT,
                    "startedAs",
                    AN_AUTRA_SALE,
                    "field.amount",
                    CHARGED,
                    "field.currency",
                    BRL,
                    "field.transactionType",
                    "CREDIT",
                    "field.installments",
                    "1",
                    "field.slugToken",
                    slugToken,
                    "field.slugStoredCard",
                    slugStoredCard,
                    "field.orderId",
                    "pedido-off-session-1")));

    String charged = theScreenOf("autra", AUTRA_RUN, paymentId);
    assertThat(charged).contains("ACCEPTED").contains("SUCCEEDED").contains(CHARGED);
    assertThat(theObjectsListedOn("autra", AUTRA_RUN))
        .as("the sale and the session it was charged against are both rows of this run")
        .containsExactly(paymentId, sessionId);

    assertThat(theTrailOf("autra", AUTRA_RUN)).contains(ACCOUNT_FORM, START, COMPLETE);
    assertThat(theControlApiCallsOf(http.steps())).isEmpty();
    return paymentId;
  }

  private void askAutraTheQuestionsItPublishesOnScreensAlone(String paymentId) {
    String questions =
        String.valueOf(
            http.page("open the screen that looks a payment up", READS + autraRun()).getBody());
    assertThat(questions)
        .as("both read-only addresses autra publishes are offered by the question each answers")
        .contains("Read one payment back with every step it has been through")
        .contains("Find the payments one establishment took in a window of dates")
        .contains(AN_AUTRA_PAYMENT_LOOK_UP)
        .contains(THE_AUTRA_PAYMENTS_OF_AN_ESTABLISHMENT);

    String theLookUp =
        String.valueOf(
            http.page(
                    "pick the question that reads one payment back",
                    theFormOf(AN_AUTRA_PAYMENT_LOOK_UP))
                .getBody());
    assertThat(theLookUp)
        .as(
            "autra names neither of its reads among the operations a scenario rule may be"
                + " declared on, so no rule of this run can decide this one and asking it twice"
                + " does leave the run as it was, which is the sentence this screen shows for a"
                + " question of autra and not the one it shows for a question of grow")
        .contains(ReadForm.NO_RULE_OF_THIS_RUN_IS_MATCHED_ON_THIS_QUESTION)
        .doesNotContain(ReadForm.A_RULE_OF_THIS_RUN_MAY_DECIDE_THIS_QUESTION);
    assertThat(theFieldsDrawnOn(theLookUp))
        .as("the look-up asks for the identifier autra draws and never for the establishment")
        .containsExactly("paymentId")
        .doesNotContain("documentId");
    assertThat(theValuesSuggestedFor(theLookUp, "paymentId"))
        .as(
            "what this run has already taken is suggested on the box, and the box is what makes"
                + " autra's own refusals of an identifier it answers for nothing reachable")
        .containsExactly(paymentId);

    String notShapedLikeAUuid =
        String.valueOf(
            http.page(
                    "type an identifier shaped like no uuid autra draws, picking nothing at all",
                    theQuestionAskedWith(
                        theLookUp, "paymentId", AN_IDENTIFIER_SHAPED_LIKE_NO_UUID_AUTRA_DRAWS))
                .getBody());
    assertThat(notShapedLikeAUuid)
        .as(
            "autra types this path parameter as a uuid and answers 400 INVALID_PAYMENT_ID for a"
                + " value of any other shape, in its own words")
        .contains("INVALID_PAYMENT_ID")
        .contains("string of format uuid")
        .doesNotContain(THE_SCREEN_DRAWS_AN_ANSWER_OF_THIS_READ_UNDER);

    String noPaymentAnswersToIt =
        String.valueOf(
            http.page(
                    "type a uuid no payment of this establishment answers to",
                    theQuestionAskedWith(
                        theLookUp, "paymentId", A_UUID_NO_PAYMENT_OF_THIS_ESTABLISHMENT_ANSWERS_TO))
                .getBody());
    assertThat(noPaymentAnswersToIt)
        .as(
            "autra publishes one refusal for a payment that is inexistent, of another tenant or"
                + " of another establishment, and a tester reads that one at 404 rather than an"
                + " answer with nothing in it")
        .contains("PAYMENT_NOT_FOUND")
        .contains("inexistente, de outro tenant ou de outro estabelecimento")
        .doesNotContain(THE_SCREEN_DRAWS_AN_ANSWER_OF_THIS_READ_UNDER);

    assertThat(theLookUp)
        .as(
            "autra answers the whole payment or refuses the call, so the form says that it never"
                + " answers nothing rather than describing an empty answer it cannot give")
        .contains(ReadForm.THIS_ONE_NEVER_ANSWERS_NOTHING)
        .doesNotContain(ReadForm.WHAT_IT_MEANS_WHEN_THIS_ONE_ANSWERS_NOTHING);

    String read =
        String.valueOf(
            http.page(
                    "ask autra everything it holds about that payment",
                    theQuestionAskedWith(theLookUp, "paymentId", paymentId))
                .getBody());
    assertThat(read)
        .as("the answer is what autra answers, the timeline included, under the names it uses")
        .contains(paymentId)
        .contains("paymentID")
        .contains("voidRetryable")
        .contains(CHARGED)
        .contains("providerObjectId=" + paymentId);
    assertThat(read)
        .as(
            "a step of the timeline is drawn under events, the name autra answers it under, with"
                + " the members of each step beneath the place it was answered in")
        .containsSubsequence(
            ">events<", ">" + ReadForm.THE_PLACE_A_LIST_ANSWERED_IT_IN + "1<", ">at<")
        .doesNotContain("events.1.type");

    String theListing =
        String.valueOf(
            http.page(
                    "pick the question that lists what one establishment took",
                    theFormOf(THE_AUTRA_PAYMENTS_OF_AN_ESTABLISHMENT))
                .getBody());
    assertThat(theFieldsDrawnOn(theListing))
        .as("the listing asks for the query parameters autra publishes on that address")
        .containsExactly("from", "to", "status", "type", "page", "limit");

    String listed =
        String.valueOf(
            http.page(
                    "ask autra what this establishment has taken", theQuestionAskedWith(theListing))
                .getBody());
    assertThat(listed)
        .as("a tester who named no window is told which window and which page autra applied")
        .contains(paymentId)
        .contains(WHAT_IT_ANSWERED_ABOUT_THE_QUESTION)
        .contains("hasMore")
        .contains("limit");

    String refused =
        String.valueOf(
            http.page(
                    "ask it for a status autra publishes nowhere",
                    theQuestionAskedWith(theListing, "status", A_STATUS_AUTRA_PUBLISHES_NOWHERE))
                .getBody());
    assertThat(refused)
        .as("the listing reads what a tester typed and refuses it in autra's own words")
        .contains("INVALID_STATUS")
        .contains("Valor desconhecido")
        .doesNotContain(paymentId);

    String tooWide =
        String.valueOf(
            http.page(
                    "ask it for a window wider than autra answers",
                    theQuestionAskedWith(theListing, "from", "2026-01-01", "to", "2026-09-22"))
                .getBody());
    assertThat(tooWide)
        .as("the maximum window is the vendor's own and the refusal says so")
        .contains("RANGE_TOO_LARGE")
        .contains("maxima: 31");

    String nothing =
        String.valueOf(
            http.page(
                    "ask it for a status this establishment holds no payment at",
                    theQuestionAskedWith(theListing, "status", "DECLINED"))
                .getBody());
    assertThat(nothing)
        .as(
            "an empty listing is autra's own answer, so the screen says what it means rather than"
                + " leaving a tester to read an empty panel as a read that failed")
        .contains("Autra answers an empty items list")
        .doesNotContain(paymentId);
    assertThat(nothing)
        .as(
            "the window autra applied is answered beside the payments whether any matched or"
                + " not, so a tester whose listing matched nothing reads both it and the sentence"
                + " that says what an empty answer means")
        .contains(WHAT_IT_ANSWERED_ABOUT_THE_QUESTION)
        .contains("hasMore");

    String atAGlance =
        String.valueOf(
            http.page(
                    "open the contributed screen that prints the whole run at once",
                    WHERE_AUTRA_PRINTS_THE_WHOLE_RUN_AT_ONCE + autraRun())
                .getBody());
    assertThat(atAGlance)
        .as(
            "the contributed screen performs neither route, so it hands a tester on to the screen"
                + " that does and documents none of the query autra refuses there")
        .contains("Look a payment up")
        .contains(paymentId)
        .doesNotContain("INVALID_STATUS")
        .doesNotContain("RANGE_TOO_LARGE");

    assertThat(theControlApiCallsOf(http.steps()))
        .as("a tester clicking makes no control-API call and no provider call")
        .isEmpty();
  }

  private String autraRun() {
    return query("autra", AUTRA_RUN);
  }

  private URI theFormOf(String readNamed) {
    return URI.create(
        READS
            + autraRun()
            + "&accountId="
            + ACCOUNT
            + "&readNamed="
            + asOneValueOfAnAddress(readNamed));
  }

  private static URI theQuestionAskedWith(String theFormAsDrawn, String... fields) {
    Map<String, String> typed = new LinkedHashMap<>();
    for (int named = 0; named < fields.length; named += 2) {
      typed.put("field." + fields[named], fields[named + 1]);
    }
    return URI.create(theAddressABrowserSubmittingWouldAskFor(theFormAsDrawn, ASK_A_READ, typed));
  }

  private static String asOneValueOfAnAddress(String value) {
    return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
  }

  private static Set<String> theCardsOfferedToAnAutraPayerOn(String page) {
    Set<String> offered = new LinkedHashSet<>();
    Matcher cards = Pattern.compile("<option\\s+value=\"([^\"]+)\"").matcher(page);
    while (cards.find()) {
      offered.add(cards.group(1));
    }
    return offered;
  }

  private static String theStoredCardHalfOn(String page, String name) {
    Matcher half =
        Pattern.compile("<dt>" + name + "</dt>\\s*<dd[^>]*>\\s*<(?:a|span)[^>]*>([^<]+)<")
            .matcher(page);
    return half.find() ? half.group(1).strip() : "";
  }

  private String autraWizard() {
    return WIZARD
        + query("autra", AUTRA_RUN)
        + "&accountId="
        + ACCOUNT
        + "&startedAs="
        + AN_AUTRA_PIX;
  }

  private String growWizard() {
    return WIZARD + query("grow", GROW_RUN) + "&accountId=" + ACCOUNT + "&startedAs=" + A_HOLD;
  }

  private String azericardWizard() {
    return WIZARD
        + query("azericard", AZERICARD_RUN)
        + "&accountId="
        + ACCOUNT
        + "&startedAs="
        + AN_AZERICARD_HOLD;
  }

  private String theScreenOf(String provider, String testRunId, String providerObjectId) {
    return String.valueOf(
        http.page(
                "open the payment on the Payments screen",
                PAYMENTS + query(provider, testRunId) + "&providerObjectId=" + providerObjectId)
            .getBody());
  }

  private List<String> theObjectsListedOn(String provider, String testRunId) {
    String page =
        String.valueOf(
            http.page("list what this test run holds", PAYMENTS + query(provider, testRunId))
                .getBody());
    List<String> listed = new ArrayList<>();
    Matcher rows = A_LISTED_OBJECT.matcher(page);
    while (rows.find()) {
      if (!listed.contains(rows.group(1))) {
        listed.add(rows.group(1));
      }
    }
    return List.copyOf(listed);
  }

  private static int theRowsNaming(String trail, String path) {
    return String.valueOf(trail).split("<td class=\"wrap\">" + path + "</td>", -1).length - 1;
  }

  private String theTrailOf(String provider, String testRunId) {
    return String.valueOf(
        http.page(
                "read the trail this journey wrote",
                TRAIL + query(provider, testRunId) + "&origin=UI")
            .getBody());
  }

  private static Set<String> theFieldsDrawnOn(String page) {
    Set<String> drawn = new LinkedHashSet<>();
    Matcher fields = A_FIELD_THE_WIZARD_DRAWS.matcher(String.valueOf(page));
    while (fields.find()) {
      drawn.add(fields.group(1));
    }
    return drawn;
  }

  private static String theAreaOfObjectsThisOneIsTiedTo(String screen) {
    int opened = screen.indexOf(THE_OBJECTS_THIS_ONE_IS_TIED_TO);
    assertThat(opened)
        .as("the Payments screen holds an area for the objects this one is tied to")
        .isNotNegative();
    return screen.substring(opened, screen.indexOf("</section>", opened));
  }

  private static Set<String> theCardsOfferedOn(String page) {
    Set<String> offered = new LinkedHashSet<>();
    Matcher cards = Pattern.compile("<option\\s+value=\"(sandbox-[^\"]+)\"").matcher(page);
    while (cards.find()) {
      offered.add(cards.group(1));
    }
    return offered;
  }

  private static String theObjectStartedBy(ResponseEntity<String> answered) {
    assertThat(answered.getStatusCode().value()).isEqualTo(302);
    String location = String.valueOf(answered.getHeaders().getFirst("Location"));
    Matcher started = A_STARTED_OBJECT.matcher(location);
    assertThat(started.find())
        .as("the wizard hands the started payment back on its redirect")
        .isTrue();
    return started.group(1);
  }

  private static List<String> theControlApiCallsOf(List<JourneyClient.Step> steps) {
    return steps.stream()
        .map(JourneyClient.Step::path)
        .filter(path -> !path.startsWith(WORKBENCH))
        .toList();
  }

  private static String query(String provider, String testRunId) {
    return "?provider=" + provider + "&testRunId=" + testRunId;
  }

  private static MultiValueMap<String, String> form(String... namesAndValues) {
    MultiValueMap<String, String> fields = new LinkedMultiValueMap<>();
    for (int index = 0; index < namesAndValues.length; index += 2) {
      fields.add(namesAndValues[index], namesAndValues[index + 1]);
    }
    return fields;
  }
}
