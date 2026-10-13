package dev.marcinromanowski.pspsim.ui;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;

import dev.marcinromanowski.pspsim.PostgresBackedTest;
import dev.marcinromanowski.pspsim.kernel.testrun.TestRunId;
import dev.marcinromanowski.pspsim.kernel.testrun.TestRuns;
import java.io.IOException;
import java.net.CookieManager;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.HtmlUtils;

class AccountFormIntegrationTest extends PostgresBackedTest {

  private static final TestRunId RUN = TestRunId.of("ui-account-form-it");

  private static final TestRunId A_RUN_NOTHING_CONFIGURED = TestRunId.of("ui-account-form-it-new");

  private static final String FORM = "/ui/configuration/account";

  private static final String CONFIGURATION = "/ui/configuration";

  private static final String CREATE = "/api/light/server/1.0/createPaymentProcess";

  private static final String FIRST_USER = "account-form-user-one";
  private static final String FIRST_PAGE = "account-form-page-one";
  private static final String SECOND_USER = "account-form-user-two";
  private static final String SECOND_PAGE = "account-form-page-two";

  @LocalServerPort private int port;

  @Autowired private TestRuns testRuns;
  @Autowired private JdbcClient jdbc;

  private RestClient http;
  private HttpClient wire;

  @BeforeEach
  void connect() {
    http = RestClient.create("http://localhost:" + port);
    wire =
        HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .cookieHandler(new CookieManager())
            .followRedirects(HttpClient.Redirect.NEVER)
            .build();
  }

  @AfterEach
  void clearWhatThisTestConfigured() {
    jdbc.sql("delete from grow_account where test_run_id = ?").param(RUN.value()).update();
    jdbc.sql("delete from simulator_provider_account where test_run_id = ?")
        .param(RUN.value())
        .update();
    jdbc.sql("delete from simulator_webhook_target where test_run_id = ?")
        .param(RUN.value())
        .update();
  }

  @Test
  void the_form_asks_for_a_providers_own_fields_by_label_without_naming_another_provider() {
    String form = page(FORM + "?provider=grow&testRunId=" + RUN.value());

    assertThat(form).contains("Grow user id").contains("Grow page code");
    assertThat(form).doesNotContain("terminal id").doesNotContain("test-mode secret key");
  }

  @Test
  void the_form_marks_the_fields_it_refuses_without_and_marks_nothing_on_the_rest() {
    String form = page(FORM + "?provider=grow&testRunId=" + RUN.value());

    assertThat(form)
        .as(
            "grow requires userId and pageCode, and the configuration call is refused without an"
                + " account identity, so three of the thirteen fields carry a required badge")
        .containsOnlyOnce("3 of the 13 fields below are required");
    assertThat(theBadgesOf(form)).containsExactly("required", "required", "required");
    assertThat(form)
        .as(
            "a field with no badge is one nothing refuses, so the form marks the exception and"
                + " never labels ten fields with the rule")
        .doesNotContain(">optional<");
  }

  @Test
  void every_field_of_the_form_carries_the_sentence_its_provider_wrote_beside_it() {
    String form = page(FORM + "?provider=grow&testRunId=" + RUN.value());

    assertThat(form.split("<summary>what this is for</summary>", -1))
        .as("one disclosure for each of the eleven grow fields, the identity and the target")
        .hasSize(14);
    assertThat(form).contains("The userId grow names this merchant by on every call.");
    assertThat(form).contains(AccountForm.THE_IDENTITY_NAMES_THE_ACCOUNT);
  }

  @Test
  void a_field_whose_values_are_a_closed_set_is_drawn_as_a_list_and_the_rest_stay_boxes() {
    String form = theGrowForm();

    assertThat(theFieldsDrawnAs(form, "select"))
        .as("the seven settings grow declares the values of")
        .containsExactly(
            "callbackAcknowledgement",
            "settlementCeiling",
            "refundAllowance",
            "refundExcessRejection",
            "merchantBalance",
            "processLookupRejection",
            "wireForm");
    assertThat(theFieldsDrawnAs(form, "input"))
        .as(
            "two credentials grow issues, typed out, and two spans of time, each a number beside"
                + " a unit this form offers under its own name")
        .containsExactly("userId", "pageCode", "captureWindow", "automaticVoidAfter");
  }

  @Test
  void the_values_a_list_offers_are_the_sentences_the_provider_wrote_and_not_its_wire_words() {
    String form = theGrowForm();

    assertThat(form)
        .contains(">answer as the live wire does: content type text/html, and every non-ASCII")
        .contains("value=\"LIVE_WIRE\"")
        .contains(">send nothing<");
  }

  @Test
  void a_value_picked_from_a_list_is_the_one_the_account_comes_to_hold() {
    configure("merchant-1", FIRST_USER, FIRST_PAGE, Map.of("wireForm", "PUBLISHED_EXAMPLE"));

    String shown =
        page(
            CONFIGURATION
                + "?provider=grow&testRunId="
                + RUN.value()
                + "&open=merchant-1&values=show");

    assertThat(shown).contains("PUBLISHED_EXAMPLE");
  }

  @Test
  void a_span_of_time_is_a_number_and_a_unit_and_a_credential_hints_a_value_grow_would_take() {
    String form = theGrowForm();

    assertThat(form)
        .as(
            "a tester reading captureWindow in a grey box wanting P7D had to read a provider"
                + " contract; the number this form hints is the one the provider publishes and"
                + " the unit beside it is picked from a list")
        .contains("name=\"unitOf.captureWindow\"")
        .contains("name=\"unitOf.automaticVoidAfter\"")
        .contains("placeholder=\"7\"")
        .contains("placeholder=\"10\"")
        .doesNotContain("placeholder=\"P7D\"");
    assertThat(form)
        .as(
            "and the credential beside them hints a userId grow would take rather than the word"
                + " userId, which a tester reading it off the screen would type into the box and"
                + " be refused for")
        .contains("placeholder=\"grow-user-1\"")
        .contains("placeholder=\"grow-page-1\"")
        .doesNotContain("placeholder=\"userId\"")
        .doesNotContain("placeholder=\"pageCode\"");
    assertThat(theUnitsOffered(form, "captureWindow"))
        .containsExactly("seconds", "minutes", "hours", "days");
  }

  @Test
  void the_screen_that_reports_what_was_configured_reads_a_declared_value_back_as_its_sentence() {
    configure("merchant-1", FIRST_USER, FIRST_PAGE, Map.of("wireForm", "PUBLISHED_EXAMPLE"));

    String shown =
        page(
            CONFIGURATION
                + "?provider=grow&testRunId="
                + RUN.value()
                + "&open=merchant-1&values=show");

    assertThat(shown)
        .as("the form offers this value as a sentence, so the screen reporting it says the same")
        .contains("PUBLISHED_EXAMPLE")
        .contains("answer as the published example does");
  }

  private String theGrowForm() {
    return page(FORM + "?provider=grow&testRunId=" + RUN.value());
  }

  @Test
  void whether_autras_acquirer_names_its_transaction_is_picked_from_a_list_on_the_screen() {
    String form = page(FORM + "?provider=autra&testRunId=" + RUN.value());

    assertThat(theFieldsDrawnAs(form, "select"))
        .as("every autra setting whose values are a closed set, the new one among them")
        .contains("autra.acquirer-transaction-identifier");
    assertThat(theOptionsOffered(form, "autra.acquirer-transaction-identifier"))
        .containsExactly("REPORTED", "NOT_REPORTED");
    assertThat(form)
        .contains("the acquirer names the transaction it took, and every answer that publishes")
        .contains("the acquirer names no transaction, and every answer that would publish");
  }

  private static List<String> theOptionsOffered(String form, String key) {
    Matcher list =
        Pattern.compile(
                "<select[^>]*name=\"field\\." + key + "\"[^>]*>(.*?)</select>", Pattern.DOTALL)
            .matcher(form);
    assertThat(list.find()).as(key + " is drawn as a list").isTrue();
    List<String> offered = new ArrayList<>();
    Matcher option = Pattern.compile("value=\"([A-Z_]+)\"").matcher(list.group(1));
    while (option.find()) {
      offered.add(option.group(1));
    }
    return offered;
  }

  private static List<String> theFieldsDrawnAs(String form, String element) {
    List<String> keys = new ArrayList<>();
    Matcher drawn = Pattern.compile("<" + element + "[^>]*name=\"field\\.([^\"]+)\"").matcher(form);
    while (drawn.find()) {
      keys.add(drawn.group(1));
    }
    return keys;
  }

  private static List<String> theUnitsOffered(String form, String key) {
    Matcher list =
        Pattern.compile("name=\"unitOf\\." + key + "\"(.*?)</select>", Pattern.DOTALL)
            .matcher(form);
    assertThat(list.find()).as("the form draws a unit list for %s", key).isTrue();
    List<String> offered = new ArrayList<>();
    Matcher unit = Pattern.compile("<option[^>]*>([a-z]+)</option>").matcher(list.group(1));
    while (unit.find()) {
      offered.add(unit.group(1));
    }
    return offered;
  }

  private static List<String> theBadgesOf(String form) {
    List<String> badges = new ArrayList<>();
    Matcher marked = Pattern.compile("class=\"tag fact\">([a-z]+)<").matcher(form);
    while (marked.find()) {
      badges.add(marked.group(1));
    }
    return badges;
  }

  @Test
  void a_tester_configures_an_account_from_the_screen_and_the_run_answers_a_grow_request_on_it() {
    Answer configured = configure("merchant-1", FIRST_USER, FIRST_PAGE, Map.of());

    assertThat(configured.status()).isEqualTo(302);
    assertThat(configured.location()).contains(CONFIGURATION);
    assertThat(testRuns.exists(RUN)).isTrue();
    assertThat(aGrowProcessFor(FIRST_USER, FIRST_PAGE)).contains("\"status\":1");
  }

  @Test
  void a_second_account_is_configured_from_the_screen_without_the_first_ones_secrets_retyped() {
    configure("merchant-1", FIRST_USER, FIRST_PAGE, Map.of());

    Answer second = configure("merchant-2", SECOND_USER, SECOND_PAGE, Map.of());

    assertThat(second.status()).isEqualTo(302);
    assertThat(aGrowProcessFor(FIRST_USER, FIRST_PAGE)).contains("\"status\":1");
    assertThat(aGrowProcessFor(SECOND_USER, SECOND_PAGE)).contains("\"status\":1");
    assertThat(page(CONFIGURATION + "?provider=grow&testRunId=" + RUN.value()))
        .contains("merchant-1")
        .contains("merchant-2");
  }

  @Test
  void
      a_span_a_tester_states_as_a_number_and_a_unit_reaches_grow_as_the_span_the_two_of_them_name() {
    Map<String, String> form = theGrowFormFilledIn("merchant-1");
    form.put("field.captureWindow", "7");
    form.put("unitOf.captureWindow", "DAYS");
    form.put("field.automaticVoidAfter", "240");
    form.put("unitOf.automaticVoidAfter", "HOURS");

    Answer configured = submit(FORM, form);

    assertThat(configured.status())
        .as("the whole path a tester clicks: the form, the controller, the configuration call")
        .isEqualTo(302);
    assertThat(theStoredHoldWindowOf("merchant-1"))
        .as(
            "the column keeps Duration.toString(), so seven days is PT168H and ten days of"
                + " hours is PT240H, whichever unit the tester stated them in")
        .containsExactly("PT168H", "PT240H");
    assertThat(theOpenedAccountOn("merchant-1"))
        .as("and the screen reads that span back in the words it was stated in")
        .contains("7 days")
        .contains("10 days");
  }

  @Test
  void
      a_span_posted_again_from_the_form_it_came_back_on_leaves_the_provider_holding_what_it_held() {
    Map<String, String> first = theGrowFormFilledIn("merchant-1");
    first.put("field.captureWindow", "7");
    first.put("unitOf.captureWindow", "DAYS");
    submit(FORM, first);
    List<String> held = theStoredHoldWindowOf("merchant-1");

    Map<String, String> refusedPost = theGrowFormFilledIn("merchant-1");
    refusedPost.put("field.captureWindow", "7");
    refusedPost.put("unitOf.captureWindow", "DAYS");
    refusedPost.put("field.pageCode", "");
    String refused = submit(FORM, refusedPost).body();

    Map<String, String> again = theGrowFormFilledIn("merchant-1");
    again.put("field.captureWindow", theNumberStatedFor("captureWindow", refused));
    again.put("unitOf.captureWindow", theUnitStatedFor("captureWindow", refused));

    assertThat(refused).contains("Grow page code is required by provider grow");
    assertThat(theNumberStatedFor("captureWindow", refused))
        .as("the browser posts a number and a unit, and the refused form states them again")
        .isEqualTo("7");
    assertThat(submit(FORM, again).status()).isEqualTo(302);
    assertThat(theStoredHoldWindowOf("merchant-1"))
        .as(
            "every post of this test is the pair a browser sends; the refused form stated the"
                + " span back as the number and unit it was stated in, and submitting that"
                + " without touching the control left the provider holding %s, the value it"
                + " already had",
            held)
        .isEqualTo(held);
  }

  @Test
  void a_span_of_zero_posted_from_the_form_is_refused_on_the_form_and_stores_nothing() {
    Map<String, String> born = theGrowFormFilledIn("merchant-1");
    born.put("field.captureWindow", "0");
    born.put("unitOf.captureWindow", "DAYS");

    Answer refused = submit(FORM, born);

    assertThat(refused.status())
        .as(
            "the span control carries no min and is no type=number, so 0 is a value a tester can"
                + " state by clicking, and the provider is what refuses it")
        .isEqualTo(200);
    assertThat(refused.body())
        .contains("how long a hold is capturable must be longer than nothing")
        .contains("To watch a settlement refused on a hold that has expired");
    assertThat(refused.body()).contains("name=\"field.captureWindow\" value=\"0\"");
    assertThat(theStoredHoldWindowsOfTheRun())
        .as("a refused post leaves the account unconfigured rather than half written")
        .isEmpty();
  }

  @Test
  void a_negative_span_typed_on_a_desk_keyboard_is_refused_on_the_form_too() {
    Map<String, String> back = theGrowFormFilledIn("merchant-1");
    back.put("field.captureWindow", "-5");
    back.put("unitOf.captureWindow", "DAYS");

    Answer refused = submit(FORM, back);

    assertThat(refused.status()).isEqualTo(200);
    assertThat(refused.body())
        .contains("how long a hold is capturable must be longer than nothing");
    assertThat(theStoredHoldWindowsOfTheRun()).isEmpty();
  }

  @Test
  void a_span_beyond_the_ceiling_posted_from_the_form_is_refused_on_the_form() {
    Map<String, String> toofar = theGrowFormFilledIn("merchant-1");
    toofar.put("field.automaticVoidAfter", "400");
    toofar.put("unitOf.automaticVoidAfter", "DAYS");

    Answer refused = submit(FORM, toofar);

    assertThat(refused.status()).isEqualTo(200);
    assertThat(refused.body())
        .contains("when an uncaptured hold voids must be at most 365 days")
        .contains("own bound and not a figure grow publishes");
    assertThat(theStoredHoldWindowsOfTheRun()).isEmpty();
  }

  @Test
  void a_refusal_lands_on_the_form_with_the_secret_fields_emptied_and_the_rest_kept() {
    configure("merchant-1", FIRST_USER, FIRST_PAGE, Map.of());

    Answer refused =
        configure("merchant-2", FIRST_USER, SECOND_PAGE, Map.of("captureWindow", "PT48H"));

    assertThat(refused.status()).isEqualTo(200);
    assertThat(refused.body()).contains("already configured for another account");
    assertThat(refused.body())
        .as(
            "this post wrote the span the way a control call does, with no unit beside it, and"
                + " the form states it back as the number and unit that name it: 48 hours is"
                + " exactly 2 days")
        .contains("name=\"field.captureWindow\" value=\"2\"");
    assertThat(refused.body()).contains("name=\"field.pageCode\" value=\"\"");
    assertThat(refused.body()).contains("name=\"field.userId\" value=\"\"");
    assertThat(refused.body()).doesNotContain(FIRST_USER).doesNotContain(SECOND_PAGE);
    assertThat(refused.body()).contains("retype this secret");
  }

  @Test
  void a_field_the_provider_requires_is_refused_by_the_name_of_the_box_it_was_left_blank_in() {
    Answer refused = configure("merchant-1", "", FIRST_PAGE, Map.of());

    assertThat(refused.status()).isEqualTo(200);
    assertThat(theRefusalOn(refused.body()))
        .as(
            "the refusal a tester reads names the box on their screen. accounts[0].configuration"
                + " is where this value sits in the body the control API takes, and a tester who"
                + " clicked a form has no accounts[0] to go and look at")
        .contains("Grow user id is required by provider grow")
        .doesNotContain("accounts[0]")
        .doesNotContain("userId");
    assertThat(refused.body()).contains(">refused<");
    assertThat(testRuns.exists(RUN)).isTrue();
    assertThat(page(CONFIGURATION + "?provider=grow&testRunId=" + RUN.value()))
        .contains("This provider has no account in this test run");
  }

  @Test
  void a_value_the_provider_refuses_never_comes_back_in_the_refusal_the_screen_prints() {
    Answer refused =
        configure(
            "merchant-1",
            FIRST_USER,
            FIRST_PAGE,
            Map.of("callbackAcknowledgement", "WHEN_THE_MOON_IS_FULL"));

    assertThat(refused.status()).isEqualTo(200);
    assertThat(theRefusalOn(refused.body()))
        .as(
            "the refusal names the box the value was picked in rather than the key grow reads it"
                + " under, and carries none of what was picked")
        .contains("when a callback is acknowledged")
        .doesNotContain("callbackAcknowledgement")
        .doesNotContain("WHEN_THE_MOON_IS_FULL");
    assertThat(refused.body())
        .as(
            "the list offers the values this provider accepts and nothing else, so a value that"
                + " reached the screen's own service by some other road comes back picked by no"
                + " option rather than written into one")
        .doesNotContain("WHEN_THE_MOON_IS_FULL");
    assertThat(theValuePickedIn(refused.body(), "callbackAcknowledgement")).isEmpty();
  }

  @Test
  void a_refusal_lands_on_the_form_with_the_value_a_tester_picked_from_a_list_still_picked() {
    configure("merchant-1", FIRST_USER, FIRST_PAGE, Map.of());

    Answer refused =
        configure("merchant-2", FIRST_USER, SECOND_PAGE, Map.of("wireForm", "PUBLISHED_EXAMPLE"));

    assertThat(refused.status()).isEqualTo(200);
    assertThat(theValuePickedIn(refused.body(), "wireForm")).isEqualTo("PUBLISHED_EXAMPLE");
  }

  private static String theValuePickedIn(String form, String key) {
    Matcher list =
        Pattern.compile(
                "<select[^>]*name=\"field\\." + key + "\"[^>]*>(.*?)</select>", Pattern.DOTALL)
            .matcher(form);
    if (!list.find()) {
      return "";
    }
    Matcher picked = Pattern.compile("value=\"([^\"]*)\"[^>]*selected").matcher(list.group(1));
    return picked.find() ? picked.group(1) : "";
  }

  @Test
  void a_form_reached_with_no_provider_named_asks_the_tester_to_pick_one_and_registers_nothing() {
    String form = page(FORM + "?testRunId=" + A_RUN_NOTHING_CONFIGURED.value());

    assertThat(form).contains("Choose a provider");
    assertThat(form).contains("provider=grow").contains(A_RUN_NOTHING_CONFIGURED.value());
    assertThat(testRuns.exists(A_RUN_NOTHING_CONFIGURED)).isFalse();
  }

  @Test
  void an_optional_field_left_empty_leaves_the_provider_to_answer_with_its_own_default() {
    configure("merchant-1", FIRST_USER, FIRST_PAGE, Map.of());

    String shown =
        page(
            CONFIGURATION
                + "?provider=grow&testRunId="
                + RUN.value()
                + "&open=merchant-1&values=show");

    assertThat(shown).contains("HTTP_2XX");
  }

  @Test
  void a_tester_removes_an_account_from_the_screen_and_the_run_stops_answering_on_it() {
    configure("merchant-1", FIRST_USER, FIRST_PAGE, Map.of());
    configure("merchant-2", SECOND_USER, SECOND_PAGE, Map.of());

    Answer removed = remove("merchant-1", "yes");

    assertThat(removed.status()).isEqualTo(302);
    String screen = page(removed.location());
    assertThat(screen)
        .contains(
            "<div class=\"done\" role=\"status\">"
                + HtmlUtils.htmlEscape(Done.theAccountIsRemoved("merchant-1"))
                + "</div>")
        .doesNotContain("class=\"caution\"")
        .contains("merchant-2");
    assertThat(removed.location()).doesNotContain("removed");
    assertThat(aGrowProcessFor(FIRST_USER, FIRST_PAGE)).contains("\"id\":701");
    assertThat(aGrowProcessFor(SECOND_USER, SECOND_PAGE)).contains("\"status\":1");
  }

  @Test
  void a_removal_the_tester_did_not_confirm_removes_nothing_and_says_what_it_wants() {
    configure("merchant-1", FIRST_USER, FIRST_PAGE, Map.of());

    Answer unconfirmed = remove("merchant-1", "");

    assertThat(unconfirmed.status()).isEqualTo(200);
    assertThat(unconfirmed.body()).contains("asks for it twice");
    assertThat(aGrowProcessFor(FIRST_USER, FIRST_PAGE)).contains("\"status\":1");
  }

  @Test
  void removing_the_last_account_of_a_run_leaves_the_run_listed_and_its_clock_alone() {
    configure("merchant-1", FIRST_USER, FIRST_PAGE, Map.of());

    remove("merchant-1", "yes");

    assertThat(testRuns.exists(RUN)).isTrue();
    assertThat(page(CONFIGURATION + "?provider=grow&testRunId=" + RUN.value()))
        .contains("This provider has no account in this test run");
  }

  @Test
  void an_account_this_run_never_configured_is_refused_on_the_screen_rather_than_redirecting() {
    configure("merchant-1", FIRST_USER, FIRST_PAGE, Map.of());

    Answer absent = remove("merchant-9", "yes");

    assertThat(absent.status()).isEqualTo(200);
    assertThat(absent.body()).contains("accountId must name an account configured");
    assertThat(aGrowProcessFor(FIRST_USER, FIRST_PAGE)).contains("\"status\":1");
  }

  private Answer remove(String accountId, String confirm) {
    Map<String, String> form = new LinkedHashMap<>();
    form.put("provider", "grow");
    form.put("testRunId", RUN.value());
    form.put("accountId", accountId);
    form.put("confirm", confirm);
    return submit(FORM + "/remove", form);
  }

  private Map<String, String> theGrowFormFilledIn(String accountId) {
    Map<String, String> form = new LinkedHashMap<>();
    form.put("provider", "grow");
    form.put("testRunId", RUN.value());
    form.put("accountId", accountId);
    form.put("field.userId", FIRST_USER);
    form.put("field.pageCode", FIRST_PAGE);
    return form;
  }

  private List<String> theStoredHoldWindowOf(String accountId) {
    return jdbc.sql(
            "select capture_window, automatic_void_after from grow_account"
                + " where test_run_id = ? and account_id = ?")
        .param(RUN.value())
        .param(accountId)
        .query(
            (row, number) ->
                List.of(row.getString("capture_window"), row.getString("automatic_void_after")))
        .single();
  }

  private List<String> theStoredHoldWindowsOfTheRun() {
    return jdbc.sql("select capture_window from grow_account where test_run_id = ?")
        .param(RUN.value())
        .query(String.class)
        .list();
  }

  private String theOpenedAccountOn(String accountId) {
    return page(
        CONFIGURATION
            + "?provider=grow&testRunId="
            + RUN.value()
            + "&open="
            + accountId
            + "&values=show");
  }

  private static String theNumberStatedFor(String key, String markup) {
    Matcher box =
        Pattern.compile("<input[^>]*name=\"field\\." + key + "\"[^>]*value=\"([^\"]*)\"")
            .matcher(markup);
    assertThat(box.find()).as("the form came back with a box for %s", key).isTrue();
    return box.group(1);
  }

  private static String theUnitStatedFor(String key, String markup) {
    Matcher list =
        Pattern.compile("name=\"unitOf\\." + key + "\"(.*?)</select>", Pattern.DOTALL)
            .matcher(markup);
    assertThat(list.find()).as("the form came back with a unit list for %s", key).isTrue();
    Matcher chosen =
        Pattern.compile("<option[^>]*value=\"([A-Z]+)\"[^>]*selected").matcher(list.group(1));
    assertThat(chosen.find()).as("with one unit selected").isTrue();
    return chosen.group(1);
  }

  private Answer configure(
      String accountId, String userId, String pageCode, Map<String, String> settings) {
    Map<String, String> form = new LinkedHashMap<>();
    form.put("provider", "grow");
    form.put("testRunId", RUN.value());
    form.put("accountId", accountId);
    form.put("field.userId", userId);
    form.put("field.pageCode", pageCode);
    settings.forEach((key, value) -> form.put("field." + key, value));
    return submit(FORM, form);
  }

  private String aGrowProcessFor(String userId, String pageCode) {
    Map<String, String> form = new LinkedHashMap<>();
    form.put("userId", userId);
    form.put("pageCode", pageCode);
    form.put("chargeType", "2");
    form.put("sum", "120.50");
    form.put("description", "an account configured from the screen");
    form.put("successUrl", "https://merchant.test/thanks");
    form.put("cancelUrl", "https://merchant.test/cancel");
    form.put("pageField[fullName]", "Israel Israeli");
    form.put("pageField[phone]", "0505503144");
    return String.valueOf(
        http.post()
            .uri(CREATE)
            .contentType(MediaType.APPLICATION_FORM_URLENCODED)
            .body(encoded(form))
            .retrieve()
            .body(String.class));
  }

  private Answer submit(String path, Map<String, String> form) {
    HttpResponse<byte[]> response =
        send(
            HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .timeout(Duration.ofSeconds(20))
                .POST(HttpRequest.BodyPublishers.ofString(encoded(form))));
    return new Answer(
        response.statusCode(),
        new String(response.body(), UTF_8),
        response.headers().firstValue("location").orElse(""));
  }

  private String page(String uri) {
    HttpResponse<byte[]> response =
        send(HttpRequest.newBuilder(at(uri)).timeout(Duration.ofSeconds(20)).GET());
    assertThat(response.statusCode()).isEqualTo(200);
    return new String(response.body(), UTF_8);
  }

  private URI at(String uri) {
    return URI.create(uri.startsWith("http") ? uri : "http://localhost:" + port + uri);
  }

  private HttpResponse<byte[]> send(HttpRequest.Builder request) {
    try {
      return wire.send(request.build(), HttpResponse.BodyHandlers.ofByteArray());
    } catch (IOException unreachable) {
      throw new IllegalStateException(unreachable);
    } catch (InterruptedException interrupted) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException(interrupted);
    }
  }

  private static String theRefusalOn(String page) {
    int opened = page.indexOf("<div class=\"refusal\">");
    assertThat(opened).isNotNegative();
    return page.substring(opened, page.indexOf("</div>", opened));
  }

  private static String encoded(Map<String, String> form) {
    List<String> pairs = new ArrayList<>();
    form.forEach(
        (name, value) ->
            pairs.add(URLEncoder.encode(name, UTF_8) + "=" + URLEncoder.encode(value, UTF_8)));
    return String.join("&", pairs);
  }

  private record Answer(int status, String body, String location) {}
}
