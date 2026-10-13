package dev.marcinromanowski.pspsim.journey;

import static org.assertj.core.api.Assertions.assertThat;

import dev.marcinromanowski.pspsim.PostgresBackedTest;
import dev.marcinromanowski.pspsim.journey.JourneyClient.Kind;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.SequencedMap;
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
import org.springframework.web.util.HtmlUtils;

class AzericardSettingsAreConfiguredByClickingIntegrationTest extends PostgresBackedTest {

  private static final String RUN = "clicking-azericard-settings";

  private static final String ACCOUNT_FORM = "/ui/configuration/account";
  private static final String CONFIGURATION = "/ui/configuration";
  private static final String THE_GATEWAYS_OWN_ROUTE = "/cgi-bin/cgi_link";
  private static final String THE_SAVED_CARD_ROUTE = "/token/cgi_link";

  private static final String THE_IDENTITY_BOX = "accountId";
  private static final String THE_TERMINAL_BOX = "field.terminalId";
  private static final String THE_MERCHANT_KEY_BOX = "field.apiKey";
  private static final String THE_GATEWAY_KEY_BOX = "field.apiSecret";
  private static final String THE_SOURCE_TERMINALS_BOX = "field.storedCardSourceTerminalIds";
  private static final String THE_SETTLEMENT_MAC_LIST = "field.azericard.settlement-mac";

  private static final String STORED_CARD_SOURCE_TERMINALS = "storedCardSourceTerminalIds";
  private static final String SETTLEMENT_MAC = "azericard.settlement-mac";

  private static final String THE_ACCOUNT_THAT_SAVES_THE_CARD = "merchant-that-saves-the-card";
  private static final String THE_ACCOUNT_THAT_CHARGES_IT = "merchant-that-charges-it";
  private static final String THE_ACCOUNT_SIGNING_AS_THE_MANUAL = "merchant-signing-as-the-manual";
  private static final String THE_ACCOUNT_SIGNING_AS_THE_WIRE = "merchant-signing-as-the-wire";

  private static final String THE_TERMINAL_THAT_SAVES_THE_CARD = "70000041";
  private static final String THE_TERMINAL_THAT_CHARGES_IT = "70000042";
  private static final String THE_TERMINAL_SIGNING_AS_THE_MANUAL = "70000043";
  private static final String THE_TERMINAL_SIGNING_AS_THE_WIRE = "70000044";

  private static final String LIVE_WIRE = "LIVE_WIRE";
  private static final String PUBLISHED_EXAMPLE = "PUBLISHED_EXAMPLE";
  private static final String WHAT_A_LIST_SENDS_WHEN_NOTHING_IS_PICKED = "";

  private static final String AMOUNT = "25.00";
  private static final String CURRENCY = "AZN";
  private static final String NONCE = "F2B2DD7E603A7ADA";
  private static final String A_HOLD = "0";
  private static final String THE_TRTYPE_THE_SAVED_CARD_ROUTE_TAKES = "1";
  private static final String A_COMPLETION = "21";
  private static final String APPROVED = "0";
  private static final String REFUSED = "3";
  private static final String RC_APPROVED = "00";
  private static final String RC_UNUSABLE_TOKEN = "-4";
  private static final String RC_ACCESS_DENIED = "-17";

  private static final String THE_PUBLISHED_TEST_CARD = "4127208104942601";

  private static final DateTimeFormatter AS_AZERICARD_WRITES_A_TIMESTAMP =
      DateTimeFormatter.ofPattern("uuuuMMddHHmmss").withZone(ZoneOffset.UTC);

  private static final Pattern A_FORM_A_TESTER_SUBMITS =
      Pattern.compile("(?s)<form\\b[^>]*>.*?</form>");

  private static final Pattern THE_OPENING_TAG_OF_A_FORM = Pattern.compile("(?s)<form\\b[^>]*>");

  private static final Pattern A_BOX_A_FORM_CARRIES = Pattern.compile("<input\\b[^>]*>");

  private static final Pattern A_BOX_OF_MANY_LINES_A_FORM_CARRIES =
      Pattern.compile("(?s)<textarea\\b([^>]*)>(.*?)</textarea>");

  private static final Pattern A_LIST_A_FORM_CARRIES =
      Pattern.compile("(?s)<select\\b([^>]*)>(.*?)</select>");

  private static final Pattern A_VALUE_A_LIST_OFFERS =
      Pattern.compile("(?s)<option\\b([^>]*)>(.*?)</option>");

  private enum TheOrderACompletionIsSignedIn {
    AS_THE_LIVE_GATEWAY_TAKES_IT,
    AS_THE_MANUAL_PUBLISHES_IT
  }

  private record AValueOffered(String value, String whatItSays) {}

  private record TheFormAsDrawn(
      String address,
      SequencedMap<String, String> controls,
      Map<String, List<AValueOffered>> lists,
      Map<String, String> boxesOfManyLines,
      Map<String, String> hints) {}

  private record TheHoldToSettle(String order, String intRef, String rrn) {}

  @LocalServerPort private int port;

  private JourneyClient http;
  private AzericardMerchant keys;
  private long orders;

  @BeforeEach
  void standUpTheTester() {
    http = new JourneyClient(port);
    keys = new AzericardMerchant();
    orders = 0;
  }

  @AfterEach
  void discardWhatTheTesterConfigured() {
    http.discardTheTestRun("azericard", RUN);
  }

  @Test
  void both_settings_are_offered_on_the_account_screen_as_controls_a_tester_can_work_by_clicking() {
    TheFormAsDrawn form = theAccountFormAsDrawn();

    assertThat(form.controls().keySet())
        .as(
            "the two settings an azericard terminal declares last are controls of the form a"
                + " tester submits, and not values the route reads from somewhere this screen"
                + " never drew")
        .contains(THE_SOURCE_TERMINALS_BOX, THE_SETTLEMENT_MAC_LIST);
    assertThat(form.lists().keySet())
        .as(
            "the settlement MAC order is one of two named values, so the form offers the two"
                + " rather than asking a tester to type an enum name they were never shown, and"
                + " the source terminals are a list of a provider's own TERMINAL values, which is"
                + " no closed set and stays a box")
        .contains(THE_SETTLEMENT_MAC_LIST)
        .doesNotContain(THE_SOURCE_TERMINALS_BOX);
    assertThat(theValuesOf(form.lists().get(THE_SETTLEMENT_MAC_LIST)))
        .containsExactly(WHAT_A_LIST_SENDS_WHEN_NOTHING_IS_PICKED, LIVE_WIRE, PUBLISHED_EXAMPLE);
    assertThat(theSentencesOf(form.lists().get(THE_SETTLEMENT_MAC_LIST)))
        .as("each value the list offers reads as the field order it signs in, not as its name")
        .containsExactly(
            "send nothing",
            "sign as the gateway a real merchant reaches does: AMOUNT, CURRENCY, ORDER, RRN,"
                + " INT_REF, TERMINAL, TRTYPE, TIMESTAMP, NONCE",
            "sign as the manual publishes and its worked example computes: AMOUNT, CURRENCY,"
                + " TERMINAL, TRTYPE, ORDER, RRN, INT_REF, with TIMESTAMP and NONCE left out");
  }

  @Test
  void the_two_key_boxes_take_a_whole_pem_and_hint_the_line_the_block_begins_with() {
    TheFormAsDrawn form = theAccountFormAsDrawn();

    assertThat(form.boxesOfManyLines().keySet())
        .as(
            "a key is 2048 bits of base64 over many lines, and a one-line box left a tester"
                + " pasting a block into a control that holds one line of it")
        .containsExactlyInAnyOrder(THE_MERCHANT_KEY_BOX, THE_GATEWAY_KEY_BOX);
    assertThat(form.hints().get(THE_MERCHANT_KEY_BOX)).isEqualTo("-----BEGIN PUBLIC KEY-----");
    assertThat(form.hints().get(THE_GATEWAY_KEY_BOX)).isEqualTo("-----BEGIN PRIVATE KEY-----");
    assertThat(keys.merchantPublicKeyPem())
        .as(
            "the hint is the first line of the very block this walk pastes in, so a tester"
                + " comparing the two reads the same words")
        .startsWith(String.valueOf(form.hints().get(THE_MERCHANT_KEY_BOX)));
    assertThat(keys.gatewayPrivateKeyPem())
        .startsWith(String.valueOf(form.hints().get(THE_GATEWAY_KEY_BOX)));
    assertThat(form.hints().get(THE_TERMINAL_BOX))
        .as("and the terminal box hints a TERMINAL, which is a value, not the word terminalId")
        .isEqualTo("77777777");
    assertThat(form.hints().values())
        .as(
            "no box on this screen hints the name azericard reads its value under: %s",
            form.hints())
        .doesNotContain("terminalId", "apiKey", "apiSecret", "storedCardSourceTerminalIds");
  }

  @Test
  void a_refusal_of_a_box_names_that_box_rather_than_where_the_value_sits_in_a_request_body() {
    TheFormAsDrawn form = theAccountFormAsDrawn();

    ResponseEntity<String> noTerminal =
        submit(
            "leave the terminal box empty and configure the account",
            form,
            Map.of(
                THE_IDENTITY_BOX,
                THE_ACCOUNT_THAT_CHARGES_IT,
                THE_TERMINAL_BOX,
                "",
                THE_MERCHANT_KEY_BOX,
                theMerchantKeyABoxOfManyLinesCarries(),
                THE_GATEWAY_KEY_BOX,
                theGatewayKeyABoxOfManyLinesCarries()));

    assertThat(theRefusalOn(noTerminal))
        .as(
            "a tester reads the label of the box they left empty; accounts[0].configuration is"
                + " where azericard's own key sits in the body a CI suite posts, and the screen"
                + " draws no accounts[0] anywhere")
        .isEqualTo(
            "terminal id is required by provider azericard, because TERMINAL is the field an"
                + " Azericard request carries the account it belongs to on")
        .doesNotContain("accounts[0]");

    ResponseEntity<String> noKey =
        submit(
            "type something into the merchant key box that is no key at all",
            form,
            Map.of(
                THE_IDENTITY_BOX,
                THE_ACCOUNT_THAT_CHARGES_IT,
                THE_TERMINAL_BOX,
                THE_TERMINAL_THAT_CHARGES_IT,
                THE_MERCHANT_KEY_BOX,
                "not a pem at all",
                THE_GATEWAY_KEY_BOX,
                theGatewayKeyABoxOfManyLinesCarries()));

    assertThat(theRefusalOn(noKey))
        .isEqualTo("merchant public key (PEM) must hold an RSA PUBLIC KEY in PEM form")
        .doesNotContain("accounts[0]");
  }

  private static String theRefusalOn(ResponseEntity<String> answered) {
    assertThat(answered.getStatusCode().value())
        .as("a refusal of a box lands on the form a tester typed it into, not on a redirect")
        .isEqualTo(200);
    Matcher printed =
        Pattern.compile("(?s)<div class=\"refusal\">(.*?)</div>")
            .matcher(String.valueOf(answered.getBody()));
    assertThat(printed.find()).as("the form prints the refusal it came back with").isTrue();
    return HtmlUtils.htmlUnescape(String.valueOf(printed.group(1))).strip();
  }

  @Test
  void the_screen_says_beside_each_of_the_two_settings_what_the_provider_does_with_it() {
    String screen = theAccountScreen();

    assertThat(theSentenceBeside(screen, STORED_CARD_SOURCE_TERMINALS))
        .as(
            "a comma-separated list is a shape, so a tester reads what separates one terminal"
                + " from the next twice: in the example the box hints and in this sentence,"
                + " which is the only one of the two that says what the list is for")
        .isEqualTo(
            "A comma-separated list of TERMINAL values whose saved-card TOKENs this terminal may"
                + " use. Leaving it empty only allows cards this terminal stored itself.");
    assertThat(theSentenceBeside(screen, SETTLEMENT_MAC))
        .isEqualTo(
            "Which field order a completion or a reversal is signed in. Leaving it empty signs in"
                + " the order the gateway a real merchant reaches accepts, which is not the order"
                + " the manual publishes.");
    assertThat(theLabelOf(screen, STORED_CARD_SOURCE_TERMINALS))
        .isEqualTo("stored-card source terminals");
    assertThat(theLabelOf(screen, SETTLEMENT_MAC)).isEqualTo("settlement MAC order");
  }

  @Test
  void the_account_form_posts_the_provider_and_the_test_run_without_a_tester_naming_either() {
    TheFormAsDrawn form = theAccountFormAsDrawn();

    assertThat(form.controls().keySet())
        .as(
            "the provider and the test run are no controls of this form, so the only road they"
                + " take to the route is the address the form submits to: %s",
            form.address())
        .doesNotContain("provider", "testRunId");
    assertThat(theFormsThisScreenCarriesTo(ACCOUNT_FORM))
        .as(
            "two forms of this screen carry the same address: the bar's own, which a tester picks"
                + " a provider and a run with, and this one, which a tester posts to configure an"
                + " account; the two are told apart by their method and by nothing else, so a"
                + " walk that read the wrong one would send the bar's two fields and none of the"
                + " account's")
        .containsExactly("get", "post");
    assertThat(form.address())
        .as(
            "a browser posts the fields of the form to the address of the form, and a form whose"
                + " address dropped the provider or the run would ask a tester to name what they"
                + " already chose on the screen that drew it")
        .isEqualTo(ACCOUNT_FORM + "?provider=azericard&testRunId=" + RUN);

    ResponseEntity<String> configured =
        submit(
            "configure a terminal with nothing but what the form drew",
            form,
            Map.of(
                THE_IDENTITY_BOX,
                THE_ACCOUNT_SIGNING_AS_THE_WIRE,
                THE_TERMINAL_BOX,
                THE_TERMINAL_SIGNING_AS_THE_WIRE,
                THE_MERCHANT_KEY_BOX,
                theMerchantKeyABoxOfManyLinesCarries(),
                THE_GATEWAY_KEY_BOX,
                theGatewayKeyABoxOfManyLinesCarries()));

    assertThat(configured.getStatusCode().value())
        .as(
            "the whole road a tester clicks: the form as it was drawn, its own fields, its own"
                + " address, and no value this walk invented; the body it answered is %s",
            configured.getBody())
        .isEqualTo(302);
    assertThat(theConfiguredValuesOf(THE_ACCOUNT_SIGNING_AS_THE_WIRE))
        .as("and the account the post configured is the one the screen reports back")
        .contains(THE_TERMINAL_SIGNING_AS_THE_WIRE);
  }

  @Test
  void a_saved_card_crosses_to_a_second_terminal_only_while_a_tester_names_the_first_a_source() {
    configureByClicking(
        "configure the terminal that saves the card",
        THE_ACCOUNT_THAT_SAVES_THE_CARD,
        THE_TERMINAL_THAT_SAVES_THE_CARD,
        Map.of());
    String token = theTokenSavedOn(THE_TERMINAL_THAT_SAVES_THE_CARD);

    configureByClicking(
        "configure the second terminal with its source terminals box as it was drawn",
        THE_ACCOUNT_THAT_CHARGES_IT,
        THE_TERMINAL_THAT_CHARGES_IT,
        Map.of());
    String refusedBeforeItWasNamed =
        theAnswerToChargingTheSavedCard(THE_TERMINAL_THAT_CHARGES_IT, token);

    assertThat(AzericardMerchant.element("action", refusedBeforeItWasNamed)).isEqualTo(REFUSED);
    assertThat(AzericardMerchant.element("rc", refusedBeforeItWasNamed))
        .as(
            "a terminal that names no source terminal may charge only the cards it stored"
                + " itself, so this token reaches it as a token it cannot use")
        .isEqualTo(RC_UNUSABLE_TOKEN);

    configureByClicking(
        "type two terminals into the second one's source terminals box, commas and spaces and all",
        THE_ACCOUNT_THAT_CHARGES_IT,
        THE_TERMINAL_THAT_CHARGES_IT,
        Map.of(
            THE_SOURCE_TERMINALS_BOX,
            THE_TERMINAL_SIGNING_AS_THE_MANUAL + ", " + THE_TERMINAL_THAT_SAVES_THE_CARD));
    String takenOnceItWasNamed =
        theAnswerToChargingTheSavedCard(THE_TERMINAL_THAT_CHARGES_IT, token);

    assertThat(AzericardMerchant.element("action", takenOnceItWasNamed))
        .as(
            "the sentence beside the box promises a comma-separated list, and what a tester types"
                + " after a comma is a space; two terminals typed that way are two terminals,"
                + " and the second of them is the one that saved %s",
            token)
        .isEqualTo(APPROVED);
    assertThat(AzericardMerchant.element("rc", takenOnceItWasNamed)).isEqualTo(RC_APPROVED);
    assertThat(theConfiguredValuesOf(THE_ACCOUNT_THAT_CHARGES_IT))
        .as("and the screen that reports what was configured reads the source terminal back")
        .contains(THE_TERMINAL_THAT_SAVES_THE_CARD);

    configureByClicking(
        "empty the source terminals box again",
        THE_ACCOUNT_THAT_CHARGES_IT,
        THE_TERMINAL_THAT_CHARGES_IT,
        Map.of(THE_SOURCE_TERMINALS_BOX, ""));
    String refusedOnceItWasUnnamed =
        theAnswerToChargingTheSavedCard(THE_TERMINAL_THAT_CHARGES_IT, token);

    assertThat(AzericardMerchant.element("rc", refusedOnceItWasUnnamed))
        .as(
            "emptying the box is as reachable by clicking as filling it, and the card the same"
                + " terminal charged a moment ago is refused again, so the box and nothing else"
                + " decided both answers")
        .isEqualTo(RC_UNUSABLE_TOKEN);
  }

  @Test
  void a_source_terminal_wider_than_the_store_holds_is_refused_on_the_form_it_was_typed_into() {
    TheFormAsDrawn form = theAccountFormAsDrawn();
    String tooWide = "7".repeat(33);

    ResponseEntity<String> refused =
        submit(
            "type a source terminal wider than anything this simulator keeps",
            form,
            Map.of(
                THE_IDENTITY_BOX,
                THE_ACCOUNT_THAT_CHARGES_IT,
                THE_TERMINAL_BOX,
                THE_TERMINAL_THAT_CHARGES_IT,
                THE_MERCHANT_KEY_BOX,
                theMerchantKeyABoxOfManyLinesCarries(),
                THE_GATEWAY_KEY_BOX,
                theGatewayKeyABoxOfManyLinesCarries(),
                THE_SOURCE_TERMINALS_BOX,
                tooWide));

    assertThat(refused.getStatusCode().value())
        .as("a refusal of this box lands on the form a tester typed it into, not on a redirect")
        .isEqualTo(200);
    String screen = String.valueOf(refused.getBody());
    assertThat(theRefusalOn(refused))
        .as(
            "the refusal names the box this terminal was typed into, which is the only name a"
                + " tester has for it")
        .startsWith("stored-card source terminals names a terminal of more than 32 characters")
        .doesNotContain(STORED_CARD_SOURCE_TERMINALS);
    assertThat(theFormPostedTo(screen, ACCOUNT_FORM).controls())
        .as(
            "and the form comes back holding what was typed into this box, so a tester corrects"
                + " one terminal rather than filling the account in again")
        .containsEntry(THE_SOURCE_TERMINALS_BOX, tooWide)
        .containsEntry(THE_TERMINAL_BOX, THE_TERMINAL_THAT_CHARGES_IT);
    assertThat(theConfiguredValuesOf(THE_ACCOUNT_THAT_CHARGES_IT))
        .as("and the refused account was never written half way")
        .contains("This provider has no account in this test run")
        .doesNotContain(THE_TERMINAL_THAT_CHARGES_IT);
  }

  @Test
  void a_completion_signed_as_the_manual_publishes_is_taken_by_the_terminal_picked_for_it() {
    configureByClicking(
        "pick the published order from the list the form offers",
        THE_ACCOUNT_SIGNING_AS_THE_MANUAL,
        THE_TERMINAL_SIGNING_AS_THE_MANUAL,
        Map.of(THE_SETTLEMENT_MAC_LIST, PUBLISHED_EXAMPLE));
    assertThat(theConfiguredValuesOf(THE_ACCOUNT_SIGNING_AS_THE_MANUAL))
        .as("the screen reads the picked order back as the value and as the sentence beside it")
        .contains(PUBLISHED_EXAMPLE)
        .contains("sign as the manual publishes and its worked example computes");

    TheHoldToSettle held = aHoldOn(THE_TERMINAL_SIGNING_AS_THE_MANUAL);
    String taken =
        theAnswerToCompleting(
            THE_TERMINAL_SIGNING_AS_THE_MANUAL,
            held,
            TheOrderACompletionIsSignedIn.AS_THE_MANUAL_PUBLISHES_IT);

    assertThat(AzericardMerchant.element("action", taken)).isEqualTo(APPROVED);
    assertThat(AzericardMerchant.element("rc", taken)).isEqualTo(RC_APPROVED);
    assertThat(AzericardMerchant.element("trtype", taken)).isEqualTo(A_COMPLETION);

    TheHoldToSettle second = aHoldOn(THE_TERMINAL_SIGNING_AS_THE_MANUAL);
    String refused =
        theAnswerToCompleting(
            THE_TERMINAL_SIGNING_AS_THE_MANUAL,
            second,
            TheOrderACompletionIsSignedIn.AS_THE_LIVE_GATEWAY_TAKES_IT);

    assertThat(AzericardMerchant.element("rc", refused))
        .as(
            "a terminal that names one order refuses the other, so a suite written against the"
                + " manual meets the bank's order as a signature failure and not as a pass")
        .isEqualTo(RC_ACCESS_DENIED);
    assertThat(AzericardMerchant.element("desc", refused)).isEqualTo("Access denied");
  }

  @Test
  void a_completion_signed_as_the_live_gateway_takes_it_is_taken_by_a_list_left_as_it_was_drawn() {
    assertThat(theAccountFormAsDrawn().controls().get(THE_SETTLEMENT_MAC_LIST))
        .as(
            "a browser sends the value of the option a list is drawn with, and this list is drawn"
                + " with the one that sends nothing, so leaving it alone is what a tester who"
                + " never opened it does")
        .isEqualTo(WHAT_A_LIST_SENDS_WHEN_NOTHING_IS_PICKED);

    configureByClicking(
        "configure a terminal without opening the settlement MAC list at all",
        THE_ACCOUNT_SIGNING_AS_THE_WIRE,
        THE_TERMINAL_SIGNING_AS_THE_WIRE,
        Map.of());
    assertThat(theConfiguredValuesOf(THE_ACCOUNT_SIGNING_AS_THE_WIRE))
        .as("a list left as drawn sends nothing, and azericard answers with its own default")
        .contains(LIVE_WIRE);

    TheHoldToSettle held = aHoldOn(THE_TERMINAL_SIGNING_AS_THE_WIRE);
    String taken =
        theAnswerToCompleting(
            THE_TERMINAL_SIGNING_AS_THE_WIRE,
            held,
            TheOrderACompletionIsSignedIn.AS_THE_LIVE_GATEWAY_TAKES_IT);

    assertThat(AzericardMerchant.element("action", taken)).isEqualTo(APPROVED);
    assertThat(AzericardMerchant.element("rc", taken)).isEqualTo(RC_APPROVED);

    TheHoldToSettle second = aHoldOn(THE_TERMINAL_SIGNING_AS_THE_WIRE);
    String refused =
        theAnswerToCompleting(
            THE_TERMINAL_SIGNING_AS_THE_WIRE,
            second,
            TheOrderACompletionIsSignedIn.AS_THE_MANUAL_PUBLISHES_IT);

    assertThat(AzericardMerchant.element("rc", refused))
        .as(
            "and the published order, which the other terminal of this run takes, is refused"
                + " here, so the two orders are told apart per terminal and not per run")
        .isEqualTo(RC_ACCESS_DENIED);
  }

  private void configureByClicking(
      String what, String accountId, String terminalId, Map<String, String> typed) {
    Map<String, String> filledIn = new LinkedHashMap<>();
    filledIn.put(THE_IDENTITY_BOX, accountId);
    filledIn.put(THE_TERMINAL_BOX, terminalId);
    filledIn.put(THE_MERCHANT_KEY_BOX, theMerchantKeyABoxOfManyLinesCarries());
    filledIn.put(THE_GATEWAY_KEY_BOX, theGatewayKeyABoxOfManyLinesCarries());
    filledIn.putAll(typed);

    ResponseEntity<String> configured = submit(what, theAccountFormAsDrawn(), filledIn);

    assertThat(configured.getStatusCode().value())
        .as("the account screen took what the tester typed; it answered %s", configured.getBody())
        .isEqualTo(302);
  }

  private String theMerchantKeyABoxOfManyLinesCarries() {
    return keys.merchantPublicKeyPem();
  }

  private String theGatewayKeyABoxOfManyLinesCarries() {
    return keys.gatewayPrivateKeyPem();
  }

  private String theTokenSavedOn(String terminalId) {
    String order = anOrderNobodySentYet();
    Map<String, String> save =
        anAuthorizationOn(terminalId, order, THE_TRTYPE_THE_SAVED_CARD_ROUTE_TAKES);
    save.put("CARD", THE_PUBLISHED_TEST_CARD);
    save.put("EXP", "05");
    save.put("EXP_YEAR", "31");
    save.put("CVC2", "796");
    save.put("TOKEN_ACTION", "REGISTER");
    save.put("MERCH_TRAN_STATE", "S");
    save.put(
        "P_SIGN",
        keys.sign(
            AMOUNT,
            CURRENCY,
            terminalId,
            THE_TRTYPE_THE_SAVED_CARD_ROUTE_TAKES,
            save.get("TIMESTAMP"),
            NONCE,
            null));

    String saved =
        http.form("save a card on this terminal", Kind.WRITE, THE_SAVED_CARD_ROUTE, sent(save))
            .getBody();
    assertThat(AzericardMerchant.element("action", saved)).isEqualTo(APPROVED);

    Map<String, Object> payment =
        http.get(
            "read back the token the card save minted, which a merchant reads off its callback",
            "/__simulator/azericard/state/"
                + AzericardMerchant.element("intref", saved)
                + "?testRunId="
                + RUN);
    @SuppressWarnings("unchecked")
    Map<String, Object> related = (Map<String, Object>) payment.get("relatedObjects");
    String token = String.valueOf(related.get("token"));
    assertThat(token).isNotBlank();
    return token;
  }

  private String theAnswerToChargingTheSavedCard(String terminalId, String token) {
    String order = anOrderNobodySentYet();
    Map<String, String> charge =
        anAuthorizationOn(terminalId, order, THE_TRTYPE_THE_SAVED_CARD_ROUTE_TAKES);
    charge.put("TOKEN", token);
    charge.put(
        "P_SIGN",
        keys.sign(
            AMOUNT,
            CURRENCY,
            terminalId,
            THE_TRTYPE_THE_SAVED_CARD_ROUTE_TAKES,
            charge.get("TIMESTAMP"),
            NONCE,
            null));
    return http.form(
            "charge the saved card from this terminal",
            Kind.WRITE,
            THE_SAVED_CARD_ROUTE,
            sent(charge))
        .getBody();
  }

  private TheHoldToSettle aHoldOn(String terminalId) {
    String order = anOrderNobodySentYet();
    Map<String, String> hold = anAuthorizationOn(terminalId, order, A_HOLD);
    hold.put("CARD", THE_PUBLISHED_TEST_CARD);
    hold.put("EXP", "05");
    hold.put("EXP_YEAR", "31");
    hold.put("CVC2", "796");
    hold.put(
        "P_SIGN",
        keys.sign(AMOUNT, CURRENCY, terminalId, A_HOLD, hold.get("TIMESTAMP"), NONCE, null));

    String held =
        http.form(
                "take a hold this terminal has something to complete",
                Kind.WRITE,
                THE_GATEWAYS_OWN_ROUTE,
                sent(hold))
            .getBody();
    assertThat(AzericardMerchant.element("action", held))
        .as(
            "a PEM is written over several lines and a box of one line carries no newline, so the"
                + " merchant key this terminal was configured with by clicking is the key with"
                + " its lines run together; this hold was signed with the pair that key belongs"
                + " to, and the terminal verified it")
        .isEqualTo(APPROVED);
    return new TheHoldToSettle(
        order, AzericardMerchant.element("intref", held), AzericardMerchant.element("rrn", held));
  }

  private String theAnswerToCompleting(
      String terminalId, TheHoldToSettle held, TheOrderACompletionIsSignedIn signedIn) {
    String sentAt = aTimestampTheGatewayTakes();
    Map<String, String> completion = new LinkedHashMap<>();
    completion.put("TERMINAL", terminalId);
    completion.put("TRTYPE", A_COMPLETION);
    completion.put("ORDER", held.order());
    completion.put("AMOUNT", AMOUNT);
    completion.put("CURRENCY", CURRENCY);
    completion.put("RRN", held.rrn());
    completion.put("INT_REF", held.intRef());
    completion.put("TIMESTAMP", sentAt);
    completion.put("NONCE", NONCE);
    completion.put(
        "P_SIGN",
        switch (signedIn) {
          case AS_THE_LIVE_GATEWAY_TAKES_IT ->
              keys.sign(
                  AMOUNT,
                  CURRENCY,
                  held.order(),
                  held.rrn(),
                  held.intRef(),
                  terminalId,
                  A_COMPLETION,
                  sentAt,
                  NONCE);
          case AS_THE_MANUAL_PUBLISHES_IT ->
              keys.sign(
                  AMOUNT,
                  CURRENCY,
                  terminalId,
                  A_COMPLETION,
                  held.order(),
                  held.rrn(),
                  held.intRef());
        });
    return http.form(
            "complete the sale over the gateway's own route",
            Kind.WRITE,
            THE_GATEWAYS_OWN_ROUTE,
            sent(completion))
        .getBody();
  }

  private Map<String, String> anAuthorizationOn(String terminalId, String order, String trtype) {
    Map<String, String> form = new LinkedHashMap<>();
    form.put("TERMINAL", terminalId);
    form.put("TRTYPE", trtype);
    form.put("ORDER", order);
    form.put("AMOUNT", AMOUNT);
    form.put("CURRENCY", CURRENCY);
    form.put("TIMESTAMP", aTimestampTheGatewayTakes());
    form.put("NONCE", NONCE);
    return form;
  }

  private String anOrderNobodySentYet() {
    orders++;
    return "2026101210%04d".formatted(orders);
  }

  private static String aTimestampTheGatewayTakes() {
    return AS_AZERICARD_WRITES_A_TIMESTAMP.format(Instant.now());
  }

  private static MultiValueMap<String, String> sent(Map<String, String> form) {
    MultiValueMap<String, String> fields = new LinkedMultiValueMap<>();
    form.forEach(fields::add);
    return fields;
  }

  private String theConfiguredValuesOf(String accountId) {
    return String.valueOf(
        http.page(
                "read back what this account was configured with",
                CONFIGURATION
                    + "?provider=azericard&testRunId="
                    + RUN
                    + "&open="
                    + accountId
                    + "&values=show")
            .getBody());
  }

  private String theAccountScreen() {
    return String.valueOf(
        http.page(
                "open the account screen for azericard and this test run",
                ACCOUNT_FORM + "?provider=azericard&testRunId=" + RUN)
            .getBody());
  }

  private List<String> theFormsThisScreenCarriesTo(String address) {
    List<String> methods = new ArrayList<>();
    Matcher forms = A_FORM_A_TESTER_SUBMITS.matcher(theAccountScreen());
    while (forms.find()) {
      String opening = theOpeningTagOf(forms.group());
      String action = HtmlUtils.htmlUnescape(String.valueOf(theAttributeOf(opening, "action")));
      if (action.split("\\?", -1)[0].equals(address)) {
        methods.add(String.valueOf(theAttributeOf(opening, "method")));
      }
    }
    return List.copyOf(methods);
  }

  private TheFormAsDrawn theAccountFormAsDrawn() {
    return theFormPostedTo(theAccountScreen(), ACCOUNT_FORM);
  }

  private ResponseEntity<String> submit(
      String what, TheFormAsDrawn form, Map<String, String> typed) {
    assertThat(form.controls().keySet())
        .as(
            "a tester fills in the controls this form drew and nothing else, so every value this"
                + " walk sends stands among them: %s",
            form.controls().keySet())
        .containsAll(typed.keySet());
    MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
    form.controls().forEach((name, drawn) -> body.add(name, typed.getOrDefault(name, drawn)));
    return http.form(what, Kind.WRITE, form.address(), body);
  }

  private static TheFormAsDrawn theFormPostedTo(String page, String submittedTo) {
    Matcher forms = A_FORM_A_TESTER_SUBMITS.matcher(page);
    while (forms.find()) {
      String form = forms.group();
      String opening = theOpeningTagOf(form);
      String address = HtmlUtils.htmlUnescape(String.valueOf(theAttributeOf(opening, "action")));
      if (!address.split("\\?", -1)[0].equals(submittedTo)
          || !"post".equals(theAttributeOf(opening, "method"))) {
        continue;
      }
      SequencedMap<String, String> controls = new LinkedHashMap<>();
      Map<String, String> hints = new LinkedHashMap<>();
      Matcher boxes = A_BOX_A_FORM_CARRIES.matcher(form);
      while (boxes.find()) {
        String name = theAttributeOf(boxes.group(), "name");
        if (name != null) {
          controls.put(name, whatABoxWasDrawnWith(boxes.group()));
          theHintOf(boxes.group(), name, hints);
        }
      }
      Map<String, String> boxesOfManyLines = new LinkedHashMap<>();
      Matcher pasted = A_BOX_OF_MANY_LINES_A_FORM_CARRIES.matcher(form);
      while (pasted.find()) {
        String name = theAttributeOf(pasted.group(1), "name");
        if (name != null) {
          String drawn = HtmlUtils.htmlUnescape(String.valueOf(pasted.group(2)));
          controls.put(name, drawn);
          boxesOfManyLines.put(name, drawn);
          theHintOf(pasted.group(1), name, hints);
        }
      }
      Map<String, List<AValueOffered>> lists = new LinkedHashMap<>();
      Matcher drawn = A_LIST_A_FORM_CARRIES.matcher(form);
      while (drawn.find()) {
        String name = theAttributeOf(drawn.group(1), "name");
        if (name != null) {
          List<AValueOffered> offered = theValuesOfferedIn(drawn.group(2));
          lists.put(name, offered);
          controls.put(name, whatAListWasDrawnWith(drawn.group(2), offered));
        }
      }
      return new TheFormAsDrawn(
          address, controls, Map.copyOf(lists), Map.copyOf(boxesOfManyLines), Map.copyOf(hints));
    }
    throw new AssertionError("no form of this screen posts to " + submittedTo);
  }

  private static String theOpeningTagOf(String form) {
    Matcher opening = THE_OPENING_TAG_OF_A_FORM.matcher(form);
    assertThat(opening.find()).as("a form block opens with a form tag").isTrue();
    return opening.group();
  }

  private static void theHintOf(String drawn, String name, Map<String, String> hints) {
    String hint = theAttributeOf(drawn, "placeholder");
    if (hint != null) {
      hints.put(name, HtmlUtils.htmlUnescape(hint));
    }
  }

  private static String whatABoxWasDrawnWith(String box) {
    String value = theAttributeOf(box, "value");
    return value == null ? "" : HtmlUtils.htmlUnescape(value);
  }

  private static String whatAListWasDrawnWith(String options, List<AValueOffered> offered) {
    Matcher value = A_VALUE_A_LIST_OFFERS.matcher(options);
    while (value.find()) {
      if (value.group(1).contains("selected")) {
        return whatAValueOffers(value.group(1));
      }
    }
    assertThat(offered).as("a list a browser can submit offers at least one value").isNotEmpty();
    return offered.get(0).value();
  }

  private static List<AValueOffered> theValuesOfferedIn(String options) {
    List<AValueOffered> offered = new ArrayList<>();
    Matcher value = A_VALUE_A_LIST_OFFERS.matcher(options);
    while (value.find()) {
      offered.add(
          new AValueOffered(
              whatAValueOffers(value.group(1)), HtmlUtils.htmlUnescape(value.group(2)).strip()));
    }
    return List.copyOf(offered);
  }

  private static String whatAValueOffers(String attributes) {
    String value = theAttributeOf(attributes, "value");
    return value == null ? "" : HtmlUtils.htmlUnescape(value);
  }

  private static List<String> theValuesOf(List<AValueOffered> offered) {
    List<String> values = new ArrayList<>();
    offered.forEach(value -> values.add(value.value()));
    return List.copyOf(values);
  }

  private static List<String> theSentencesOf(List<AValueOffered> offered) {
    List<String> sentences = new ArrayList<>();
    offered.forEach(value -> sentences.add(value.whatItSays()));
    return List.copyOf(sentences);
  }

  private static String theSentenceBeside(String screen, String key) {
    Matcher said =
        Pattern.compile(
                "(?s)for=\"" + Pattern.quote(key) + "\">.*?<span class=\"note wrap\">(.*?)</span>")
            .matcher(screen);
    assertThat(said.find()).as("the screen says what %s is for", key).isTrue();
    return HtmlUtils.htmlUnescape(said.group(1)).strip();
  }

  private static String theLabelOf(String screen, String key) {
    Matcher labelled =
        Pattern.compile("(?s)for=\"" + Pattern.quote(key) + "\">\\s*<span>(.*?)</span>")
            .matcher(screen);
    assertThat(labelled.find()).as("the screen labels %s", key).isTrue();
    return HtmlUtils.htmlUnescape(labelled.group(1)).strip();
  }

  private static @Nullable String theAttributeOf(String element, String named) {
    Matcher attribute = Pattern.compile(named + "=\"([^\"]*)\"", Pattern.DOTALL).matcher(element);
    return attribute.find() ? attribute.group(1) : null;
  }
}
