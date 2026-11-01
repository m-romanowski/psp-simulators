package dev.marcinromanowski.pspsim.provider.stripe;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;

import dev.marcinromanowski.pspsim.PostgresBackedTest;
import dev.marcinromanowski.pspsim.kernel.testrun.TestRunId;
import dev.marcinromanowski.pspsim.ui.PaymentMethodForm;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;

class StripeRecordedClientTrafficIntegrationTest extends PostgresBackedTest {

  private static final TestRunId RUN = TestRunId.of("stripe-recorded-client-traffic-it");
  private static final Instant FIXED_NOW = Instant.parse("2026-10-31T12:00:00Z");

  private static final String RECORDINGS = "/stripe-ios-recorded-traffic/";

  private static final String MERCHANT = "merchant-1";
  private static final String SECRET_KEY = "sk_test_51RecordedTrafficSimulator";
  private static final String PUBLISHABLE_KEY = "pk_test_51RecordedTrafficSimulator";
  private static final String ANOTHER_MERCHANT = "merchant-2";
  private static final String ANOTHER_SECRET_KEY = "sk_test_51AnotherRecordedTrafficSimulator";
  private static final String ANOTHER_PUBLISHABLE_KEY = "pk_test_51AnotherRecordedTraffic";

  private static final String A_CUSTOMER = "cus_replaying_the_mobile_sdk";
  private static final String THE_SAVED_CARD = "pm_replaying_the_mobile_sdk";
  private static final String THE_PUBLISHED_TEST_CARD = "pm_card_visa";

  private static final Set<String> A_PAYMENT_CARRIES_AND_THIS_SIMULATOR_DOES_NOT_MODEL =
      Set.of(
          "amount_details",
          "canceled_at",
          "confirmation_method",
          "description",
          "next_action",
          "processing",
          "setup_future_usage",
          "shipping",
          "source");

  private static final Set<String> A_CHECKOUT_TOTAL_THIS_SIMULATOR_DOES_NOT_MODEL =
      Set.of("amount_subtotal", "total_details");

  private static final Set<String> A_SETUP_CARRIES_AND_THIS_SIMULATOR_DOES_NOT_MODEL =
      Set.of("cancellation_reason", "description", "next_action");

  private static final Set<String> A_RECORDING_OF_2025_PREDATES =
      Set.of("allowed_payment_method_types", "excluded_payment_method_types");

  private static final List<String> THE_HEADERS_A_BROWSER_READS =
      List.of(
          "access-control-allow-origin",
          "access-control-allow-credentials",
          "access-control-allow-methods",
          "access-control-expose-headers",
          "access-control-max-age",
          "vary");

  private static final Set<String> AN_ERROR_CARRIES_AND_THIS_SIMULATOR_DOES_NOT_MODEL =
      Set.of("request_log_url");

  @LocalServerPort private int port;

  private RestClient http;
  private final HttpClient wire =
      HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NEVER).build();

  @BeforeEach
  void configureTheAccounts() {
    http = RestClient.create("http://localhost:" + port);
    reset();
    configure(
        List.of(
            anAccount(MERCHANT, SECRET_KEY, PUBLISHABLE_KEY),
            anAccount(ANOTHER_MERCHANT, ANOTHER_SECRET_KEY, ANOTHER_PUBLISHABLE_KEY)));
    seed(THE_SAVED_CARD);
  }

  @AfterEach
  void unconfigureTheAccounts() {
    reset();
    configure(List.of());
  }

  @Test
  void a_payment_sheet_retrieve_of_2026_is_answered_with_the_members_it_recorded() {
    Recording recorded =
        Recording.of(
            "PaymentSheetLPMConfirmFlowTests/testLinkCardConfirmFlows/"
                + "0002_get_v1_payment_intents_pi_3UVdlcGoesj9fw9Q1dp9Xowy.tail");
    Created payment = aPayment(SECRET_KEY);

    HttpResponse<byte[]> replayed = replay(recorded, payment, Map.of());

    assertThat(replayed.statusCode()).isEqualTo(recorded.status());
    assertThat(membersOf(bodyOf(replayed)))
        .containsExactlyElementsOf(
            recorded.members(A_PAYMENT_CARRIES_AND_THIS_SIMULATOR_DOES_NOT_MODEL, Set.of()));
    assertThat(replayed.headers().firstValue("Stripe-Version")).contains("2020-08-27");
  }

  @Test
  void a_payment_sheet_retrieve_expanding_the_payment_method_is_answered_as_recorded() {
    Recording recorded =
        Recording.of(
            "PaymentSheetAPITest/testDeferredConfirmvalidsavedcard/"
                + "0002_get_v1_payment_intents_pi_3RyFvBFY0qyl6XeW1nCMOTKN.tail");
    Created payment = aPayment(SECRET_KEY);

    HttpResponse<byte[]> replayed = replay(recorded, payment, Map.of());

    assertThat(recorded.path()).endsWith("&expand%5B0%5D=payment_method");
    assertThat(replayed.statusCode()).isEqualTo(recorded.status());
    assertThat(membersOf(bodyOf(replayed)))
        .containsExactlyElementsOf(
            recorded.members(
                union(
                    A_PAYMENT_CARRIES_AND_THIS_SIMULATOR_DOES_NOT_MODEL,
                    A_CHECKOUT_TOTAL_THIS_SIMULATOR_DOES_NOT_MODEL),
                Set.of("allowed_payment_method_types")));
  }

  @Test
  void a_payment_sheet_confirm_by_a_published_test_id_is_answered_with_a_new_copy_of_it() {
    Recording recorded =
        Recording.of(
            "PaymentSheetAPITest/testPaymentSheetLoadAndConfirmWithPaymentIntentAttachedPaymentMethod/"
                + "0002_post_v1_payment_intents_pi_3RW1PFFY0qyl6XeW1c5Mc41w_confirm.tail");
    Created payment = aPayment(SECRET_KEY);

    HttpResponse<byte[]> replayed = replay(recorded, payment, Map.of());

    assertThat(recorded.form())
        .contains("payment_method=pm_card_visa")
        .contains("expand[0]=payment_method")
        .contains("use_stripe_sdk=true");
    assertThat(replayed.statusCode()).as(bodyOf(replayed)).isEqualTo(recorded.status());
    assertThat(membersOf(bodyOf(replayed)))
        .containsExactlyElementsOf(
            recorded.members(
                union(
                    A_PAYMENT_CARRIES_AND_THIS_SIMULATOR_DOES_NOT_MODEL,
                    A_CHECKOUT_TOTAL_THIS_SIMULATOR_DOES_NOT_MODEL),
                A_RECORDING_OF_2025_PREDATES));
    assertThat(recorded.body()).contains("\"object\" : \"payment_method\"");
    assertThat(bodyOf(replayed))
        .contains("\"object\":\"payment_method\"")
        .contains("\"status\":\"succeeded\"")
        .doesNotContain("\"id\":\"" + THE_PUBLISHED_TEST_CARD + "\"");
    StripeJson.JsonObject copied = objectAt(bodyOf(replayed), "payment_method");
    StripeJson.JsonObject recordedCopy = objectAt(recorded.body(), "payment_method");
    assertThat(textOf(copied, "id")).matches("pm_sim_[0-9a-f]{20}");
    assertThat(textOf(recordedCopy, "id")).startsWith("pm_1");
    assertThat(new TreeSet<>(copied.members().keySet()))
        .isEqualTo(new TreeSet<>(recordedCopy.members().keySet()));
    assertThat(cardOf(copied)).isEqualTo(theRecordedCardFacts(recordedCopy, "6", "2026"));
  }

  @Test
  void a_payment_method_created_with_card_details_is_answered_with_the_members_it_recorded() {
    Recording recorded =
        Recording.of(
            "STPPaymentMethodFunctionalTest/testCreateCardPaymentMethod/"
                + "0000_post_v1_payment_methods.tail");

    HttpResponse<byte[]> replayed = replayWithoutAnIntent(recorded);

    assertThat(recorded.form())
        .contains("card[number]=4242424242424242")
        .contains("metadata[test_key]=test_value");
    assertThat(replayed.statusCode()).as(bodyOf(replayed)).isEqualTo(recorded.status());
    assertThat(membersOf(bodyOf(replayed))).containsExactlyElementsOf(membersOf(recorded.body()));
    StripeJson.JsonObject created = (StripeJson.JsonObject) StripeJsonReader.read(bodyOf(replayed));
    StripeJson.JsonObject answered = (StripeJson.JsonObject) StripeJsonReader.read(recorded.body());
    assertThat(textOf(created, "id")).matches("pm_sim_[0-9a-f]{20}");
    assertThat(created.members().get("billing_details"))
        .isEqualTo(answered.members().get("billing_details"));
    assertThat(cardOf(created)).isEqualTo(cardOf(answered));
    for (String member : List.of("allow_redisplay", "customer", "livemode", "object", "type")) {
      assertThat(created.members().get(member))
          .as(member)
          .isEqualTo(answered.members().get(member));
    }
    assertThat(bodyOf(replayed)).doesNotContain("4242424242424242").doesNotContain("test_value");
  }

  @Test
  void a_payment_method_created_with_a_two_digit_year_is_answered_with_the_year_it_recorded() {
    Recording recorded =
        Recording.of(
            "StripeAPIBridgeNetworkTests/testCreatePaymentMethod/0000_post_v1_payment_methods.tail");

    HttpResponse<byte[]> replayed = replayWithoutAnIntent(recorded);

    assertThat(recorded.form()).contains("card[exp_year]=42");
    assertThat(replayed.statusCode()).as(bodyOf(replayed)).isEqualTo(recorded.status());
    assertThat(cardOf((StripeJson.JsonObject) StripeJsonReader.read(bodyOf(replayed))))
        .isEqualTo(cardOf((StripeJson.JsonObject) StripeJsonReader.read(recorded.body())));
  }

  @Test
  void a_payment_confirmed_with_payment_method_data_is_answered_with_the_members_it_recorded() {
    Recording recorded =
        Recording.of(
            "STPPaymentIntentFunctionalTest/testConfirmCardWithoutNetworkParam/"
                + "0001_post_v1_payment_intents_pi_3SHqvTFY0qyl6XeW1L2WGE3T_confirm.tail");
    Created payment = aPayment(SECRET_KEY);

    HttpResponse<byte[]> replayed = replay(recorded, payment, Map.of());

    assertThat(recorded.form()).contains("payment_method_data[card][number]=4242424242424242");
    assertThat(replayed.statusCode()).as(bodyOf(replayed)).isEqualTo(recorded.status());
    assertThat(membersOf(bodyOf(replayed)))
        .containsExactlyElementsOf(
            recorded.members(
                union(
                    A_PAYMENT_CARRIES_AND_THIS_SIMULATOR_DOES_NOT_MODEL,
                    A_CHECKOUT_TOTAL_THIS_SIMULATOR_DOES_NOT_MODEL),
                Set.of("allowed_payment_method_types")));
    StripeJson.JsonObject confirmed =
        (StripeJson.JsonObject) StripeJsonReader.read(bodyOf(replayed));
    assertThat(textOf(confirmed, "status")).isEqualTo("succeeded");
    assertThat(textOf(confirmed, "payment_method")).matches("pm_sim_[0-9a-f]{20}");
    assertThat(
            textOf(
                (StripeJson.JsonObject) StripeJsonReader.read(recorded.body()), "payment_method"))
        .startsWith("pm_1");
  }

  @Test
  void a_setup_confirmed_with_payment_method_data_is_answered_with_the_members_it_recorded() {
    Recording recorded =
        Recording.of(
            "StripeAPIBridgeNetworkTests/testConfirmSetupIntent/"
                + "0001_post_v1_setup_intents_seti_1RxtukFY0qyl6XeWvxRtOLup_confirm.tail");
    Created setup = aSetup();

    HttpResponse<byte[]> replayed = replay(recorded, setup, Map.of());

    assertThat(recorded.form()).contains("payment_method_data[card][exp_year]=42");
    assertThat(replayed.statusCode()).as(bodyOf(replayed)).isEqualTo(recorded.status());
    assertThat(membersOf(bodyOf(replayed)))
        .containsExactlyElementsOf(
            recorded.members(
                A_SETUP_CARRIES_AND_THIS_SIMULATOR_DOES_NOT_MODEL, A_RECORDING_OF_2025_PREDATES));
    String created =
        textOf((StripeJson.JsonObject) StripeJsonReader.read(bodyOf(replayed)), "payment_method");
    assertThat(created).matches("pm_sim_[0-9a-f]{20}");
    HttpResponse<byte[]> saved =
        get(SECRET_KEY, "/v1/customers/" + A_CUSTOMER + "/payment_methods");
    assertThat(bodyOf(saved)).contains("\"id\":\"" + created + "\"").contains("\"exp_year\":2042");
  }

  @Test
  void payment_method_data_without_its_card_is_refused_the_way_it_was_recorded() {
    Recording recorded =
        Recording.of(
            "STPPaymentHandlerFunctionalTests/testconfirmsetupintentsendsanalytic/"
                + "0000_post_v1_setup_intents_seti_1P1xLBFY0qyl6XeWc7c2LrMK_confirm.tail");
    Created setup = aSetup();
    Map<String, String> confirming = new LinkedHashMap<>();
    confirming.put("payment_method", THE_SAVED_CARD);
    assertThat(
            post(SECRET_KEY, "/v1/setup_intents/" + setup.id() + "/confirm", confirming)
                .statusCode())
        .isEqualTo(200);

    HttpResponse<byte[]> replayed =
        replay(
            recorded,
            setup,
            Map.of(
                recorded.clientSecret().replace(recorded.intentId(), setup.id()),
                setup.clientSecret()));

    assertThat(recorded.body()).contains("\"status\" : \"succeeded\"");
    assertThat(replayed.statusCode()).isEqualTo(recorded.status());
    assertTheSameRefusal(recorded, replayed, setup);
    assertThat(textOf(objectAt(bodyOf(replayed), "error"), "param"))
        .isEqualTo("payment_method_data[card]");
  }

  @Test
  void a_payment_sheet_retrieve_of_a_setup_of_2026_is_answered_with_the_members_it_recorded() {
    Recording recorded =
        Recording.of(
            "PaymentSheetLPMConfirmFlowTests/testApplePayConfirmFlows/"
                + "0088_get_v1_setup_intents_seti_1UVdbRFY0qyl6XeWLqgNm5qa.tail");
    Created setup = aSetup();

    HttpResponse<byte[]> replayed = replay(recorded, setup, Map.of());

    assertThat(replayed.statusCode()).isEqualTo(recorded.status());
    assertThat(membersOf(bodyOf(replayed)))
        .containsExactlyElementsOf(
            recorded.members(A_SETUP_CARRIES_AND_THIS_SIMULATOR_DOES_NOT_MODEL, Set.of()));
  }

  @Test
  void a_payment_sheet_confirm_of_a_setup_is_answered_with_the_members_it_recorded() {
    Recording recorded =
        Recording.of(
            "PaymentSheetAPITest/testDeferredConfirmvalidsavedcard/"
                + "0012_post_v1_setup_intents_seti_1RyFvFFY0qyl6XeWN8iE5v5p_confirm.tail");
    Created setup = aSetup();

    HttpResponse<byte[]> replayed =
        replay(recorded, setup, Map.of("pm_1RyFvFFY0qyl6XeWLT7J0aVm", THE_SAVED_CARD));

    assertThat(replayed.statusCode()).as(bodyOf(replayed)).isEqualTo(recorded.status());
    assertThat(membersOf(bodyOf(replayed)))
        .containsExactlyElementsOf(
            recorded.members(
                A_SETUP_CARRIES_AND_THIS_SIMULATOR_DOES_NOT_MODEL, A_RECORDING_OF_2025_PREDATES));
    assertThat(bodyOf(replayed))
        .contains("\"status\":\"succeeded\"")
        .contains("\"payment_method\":{\"id\":\"" + THE_SAVED_CARD + "\"");
  }

  @Test
  void a_retrieve_with_a_wrong_secret_is_refused_the_way_it_was_recorded() {
    Recording recorded =
        Recording.of(
            "STPPaymentIntentFunctionalTest/testRetrieveWithWrongSecret/"
                + "0000_get_v1_payment_intents_pi_1GGCGfFY0qyl6XeWbSAsh2hn.tail");
    Created payment = aPayment(SECRET_KEY);

    HttpResponse<byte[]> replayed = replay(recorded, payment, Map.of());

    assertThat(recorded.path()).endsWith("_secret_bad-secret");
    assertThat(replayed.statusCode()).isEqualTo(recorded.status());
    assertTheSameRefusal(recorded, replayed, payment);
  }

  @Test
  void a_retrieve_of_an_intent_of_another_account_is_refused_the_way_it_was_recorded() {
    Recording recorded =
        Recording.of(
            "STPPaymentIntentFunctionalTest/testRetrieveMismatchedPublishableKey/"
                + "0000_get_v1_payment_intents_pi_1GGCGfFY0qyl6XeWbSAsh2hn.tail");
    Created theirs = aPayment(ANOTHER_SECRET_KEY);

    HttpResponse<byte[]> replayed = replay(recorded, theirs, Map.of());

    assertThat(replayed.statusCode()).isEqualTo(recorded.status());
    assertTheSameRefusal(recorded, replayed, theirs);
  }

  @Test
  void a_declined_payment_confirm_replays_the_recorded_error_and_embedded_intent() {
    Recording recorded =
        Recording.of(
            "PaymentSheetAPITest/testDeferredConfirmsavedinsufficientfundscard/"
                + "0002_post_v1_payment_intents_pi_3RW1OTFY0qyl6XeW1v2ixP94_confirm.tail");
    Created payment = aPayment(SECRET_KEY);
    HttpResponse<byte[]> replayed =
        replay(recorded, payment, Map.of(recorded.clientSecret(), payment.clientSecret()));
    assertRecordedDecline(recorded, replayed, "payment_intent", "last_payment_error");
  }

  @Test
  void a_declined_setup_confirm_replays_the_recorded_error_and_embedded_intent() {
    Recording recorded =
        Recording.of(
            "PaymentSheetAPITest/testDeferredConfirmsavedinsufficientfundscard/"
                + "0006_post_v1_setup_intents_seti_1RW1OVFY0qyl6XeWTBEXiXIe_confirm.tail");
    Created setup = aSetup();
    HttpResponse<byte[]> replayed =
        replay(recorded, setup, Map.of(recorded.clientSecret(), setup.clientSecret()));
    assertRecordedDecline(recorded, replayed, "setup_intent", "last_setup_error");
  }

  @Test
  void the_declined_checkout_creation_body_matches_the_card_error_from_a_confirming_creation() {
    Recording recorded =
        Recording.of(
            "CheckoutConfirmationFunctionalTests/testconfirmwithdeclinedcardallowsretrywithvalidcard/"
                + "0004_post_v1_checkout_client_cs_test_a16zwjB2W92T7K1rsfTdZFaTuVGUvRcAogIlG1J7DdFWdtj51dqYfmml1X_confirm.tail");
    Map<String, String> card = new LinkedHashMap<>();
    card.put("amount", "1050");
    card.put("currency", "usd");
    card.put("payment_method_types[0]", "card");
    card.put("confirm", "true");
    card.put("payment_method_data[type]", "card");
    card.put("payment_method_data[card][number]", "4000000000000002");
    card.put("payment_method_data[card][exp_month]", "12");
    card.put("payment_method_data[card][exp_year]", "2040");
    HttpResponse<byte[]> replayed = post(SECRET_KEY, "/v1/payment_intents", card);
    assertThat(replayed.statusCode()).isEqualTo(recorded.status());
    StripeJson.JsonObject expected = errorIn(recorded.body());
    StripeJson.JsonObject actual = errorIn(bodyOf(replayed));
    Set<String> expectedMembers = new TreeSet<>(expected.members().keySet());
    expectedMembers.remove("request_log_url");
    expectedMembers.remove("extra_fields");
    if (actual.members().containsKey("payment_intent")
        && !expected.members().containsKey("payment_intent")) {
      expectedMembers.add("payment_intent");
    }
    assertThat(actual.members().keySet()).containsExactlyInAnyOrderElementsOf(expectedMembers);
    for (String member : List.of("type", "code", "decline_code", "message", "param")) {
      assertThat(actual.members().get(member)).as(member).isEqualTo(expected.members().get(member));
    }
    assertThat(cardOf(objectAt(StripeJsonWriter.toText(actual), "payment_method")))
        .isEqualTo(cardOf(objectAt(StripeJsonWriter.toText(expected), "payment_method")));
    assertThat(objectAt(StripeJsonWriter.toText(actual), "payment_intent").members().get("status"))
        .isEqualTo(StripeJson.text("requires_payment_method"));
  }

  private static void assertRecordedDecline(
      Recording recorded, HttpResponse<byte[]> replayed, String intent, String lastError) {
    assertThat(replayed.statusCode()).as(bodyOf(replayed)).isEqualTo(recorded.status());
    StripeJson.JsonObject expected = errorIn(recorded.body());
    StripeJson.JsonObject actual = errorIn(bodyOf(replayed));
    Set<String> expectedMembers = new TreeSet<>(expected.members().keySet());
    expectedMembers.remove("request_log_url");
    expectedMembers.remove("extra_fields");
    if (actual.members().containsKey("payment_intent")
        && !expected.members().containsKey("payment_intent")) {
      expectedMembers.add("payment_intent");
    }
    assertThat(actual.members().keySet()).containsExactlyInAnyOrderElementsOf(expectedMembers);
    for (String member : List.of("type", "code", "decline_code", "message", "param")) {
      assertThat(actual.members().get(member)).as(member).isEqualTo(expected.members().get(member));
    }
    StripeJson.JsonObject expectedIntent = objectAt(StripeJsonWriter.toText(expected), intent);
    StripeJson.JsonObject actualIntent = objectAt(StripeJsonWriter.toText(actual), intent);
    Set<String> embeddedMembers = new TreeSet<>(expectedIntent.members().keySet());
    embeddedMembers.removeAll(
        "setup_intent".equals(intent)
            ? A_SETUP_CARRIES_AND_THIS_SIMULATOR_DOES_NOT_MODEL
            : union(
                A_PAYMENT_CARRIES_AND_THIS_SIMULATOR_DOES_NOT_MODEL,
                A_CHECKOUT_TOTAL_THIS_SIMULATOR_DOES_NOT_MODEL));
    embeddedMembers.addAll(A_RECORDING_OF_2025_PREDATES);
    assertThat(actualIntent.members().keySet())
        .containsExactlyInAnyOrderElementsOf(embeddedMembers);

    for (String member : List.of("status", "payment_method")) {
      assertThat(actualIntent.members().get(member))
          .as(member)
          .isEqualTo(expectedIntent.members().get(member));
    }
    StripeJson.JsonObject expectedLast =
        objectAt(StripeJsonWriter.toText(expectedIntent), lastError);
    StripeJson.JsonObject actualLast = objectAt(StripeJsonWriter.toText(actualIntent), lastError);
    assertThat(actualLast.members().keySet())
        .containsExactlyInAnyOrderElementsOf(expectedLast.members().keySet());
    for (String member : List.of("type", "code", "decline_code", "message", "param")) {
      assertThat(actualLast.members().get(member))
          .as(member)
          .isEqualTo(expectedLast.members().get(member));
    }
    assertThat(actual.members().get("payment_method"))
        .isEqualTo(actualLast.members().get("payment_method"));
    Map<String, StripeJson> card =
        cardOf(objectAt(StripeJsonWriter.toText(actual), "payment_method"));
    card.put("exp_month", StripeJson.number("6"));
    card.put("exp_year", StripeJson.number("2026"));
    assertThat(card)
        .isEqualTo(cardOf(objectAt(StripeJsonWriter.toText(expected), "payment_method")));
  }

  private static void assertTheSameRefusal(
      Recording recorded, HttpResponse<byte[]> replayed, Created intent) {
    StripeJson.JsonObject answered = errorIn(bodyOf(replayed));
    StripeJson.JsonObject expected = errorIn(recorded.body());
    Set<String> carried = new TreeSet<>(expected.members().keySet());
    carried.removeAll(AN_ERROR_CARRIES_AND_THIS_SIMULATOR_DOES_NOT_MODEL);
    assertThat(new TreeSet<>(answered.members().keySet())).isEqualTo(carried);
    for (String member : List.of("type", "code", "param")) {
      assertThat(answered.members().get(member))
          .as(member)
          .isEqualTo(expected.members().get(member));
    }
    assertThat(answered.members().get("message"))
        .isEqualTo(
            StripeJson.text(
                ((StripeJson.JsonText) Objects.requireNonNull(expected.members().get("message")))
                    .value()
                    .replace(recorded.intentId(), intent.id())));
  }

  private HttpResponse<byte[]> replay(
      Recording recorded, Created intent, Map<String, String> alsoSubstituting) {
    Map<String, String> substituted = new LinkedHashMap<>();
    if (recorded.status() == 200 || recorded.status() == 402) {
      substituted.put(recorded.clientSecret(), intent.clientSecret());
    }
    substituted.put(recorded.intentId(), intent.id());
    substituted.putAll(alsoSubstituting);
    HttpRequest.Builder request =
        HttpRequest.newBuilder(
                URI.create(
                    "http://localhost:" + port + substitutedIn(recorded.path(), substituted)))
            .header("Authorization", "Bearer " + PUBLISHABLE_KEY)
            .header("Stripe-Version", recorded.header("stripe-version"))
            .timeout(Duration.ofSeconds(20));
    if ("POST".equals(recorded.method())) {
      request =
          request
              .header("Content-Type", "application/x-www-form-urlencoded")
              .POST(
                  HttpRequest.BodyPublishers.ofString(substitutedIn(recorded.form(), substituted)));
    } else {
      request = request.GET();
    }
    HttpResponse<byte[]> replayed = sent(request.build());
    for (String header : THE_HEADERS_A_BROWSER_READS) {
      assertThat(replayed.headers().allValues(header))
          .as(header)
          .containsExactly(recorded.header(header));
    }
    return replayed;
  }

  private static String substitutedIn(String recorded, Map<String, String> substituted) {
    String replayed = recorded;
    for (Map.Entry<String, String> one : substituted.entrySet()) {
      replayed = replayed.replace(one.getKey(), one.getValue());
    }
    return replayed;
  }

  private Created aPayment(String secretKey) {
    Map<String, String> form = new LinkedHashMap<>();
    form.put("amount", "1050");
    form.put("currency", "usd");
    form.put("customer", A_CUSTOMER);
    form.put("payment_method_types[0]", "card");
    return createdBy(post(secretKey, "/v1/payment_intents", form));
  }

  private Created aSetup() {
    return createdBy(
        post(
            SECRET_KEY,
            "/v1/setup_intents",
            Map.of("customer", A_CUSTOMER, "payment_method_types[0]", "card")));
  }

  private static Created createdBy(HttpResponse<byte[]> created) {
    assertThat(created.statusCode()).as(bodyOf(created)).isEqualTo(200);
    StripeJson.JsonObject object = (StripeJson.JsonObject) StripeJsonReader.read(bodyOf(created));
    return new Created(textOf(object, "id"), textOf(object, "client_secret"));
  }

  private static String textOf(StripeJson.JsonObject object, String member) {
    return ((StripeJson.JsonText) Objects.requireNonNull(object.members().get(member))).value();
  }

  private HttpResponse<byte[]> replayWithoutAnIntent(Recording recorded) {
    return sent(
        HttpRequest.newBuilder(URI.create("http://localhost:" + port + recorded.path()))
            .header("Authorization", "Bearer " + PUBLISHABLE_KEY)
            .header("Stripe-Version", recorded.header("stripe-version"))
            .header("Idempotency-Key", recorded.header("idempotency-key"))
            .header("Content-Type", "application/x-www-form-urlencoded")
            .timeout(Duration.ofSeconds(20))
            .POST(HttpRequest.BodyPublishers.ofString(recorded.form()))
            .build());
  }

  private HttpResponse<byte[]> get(String key, String path) {
    return sent(
        HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
            .header("Authorization", "Bearer " + key)
            .timeout(Duration.ofSeconds(20))
            .GET()
            .build());
  }

  private static StripeJson.JsonObject objectAt(String body, String member) {
    StripeJson.JsonObject whole = (StripeJson.JsonObject) StripeJsonReader.read(body);
    return (StripeJson.JsonObject) Objects.requireNonNull(whole.members().get(member), member);
  }

  private static Map<String, StripeJson> cardOf(StripeJson.JsonObject method) {
    return new TreeMap<>(
        ((StripeJson.JsonObject) Objects.requireNonNull(method.members().get("card"))).members());
  }

  private static Map<String, StripeJson> theRecordedCardFacts(
      StripeJson.JsonObject method, String month, String year) {
    Map<String, StripeJson> facts = cardOf(method);
    assertThat(facts.get("exp_month")).isEqualTo(StripeJson.number(month));
    assertThat(facts.get("exp_year")).isEqualTo(StripeJson.number(year));
    facts.put("exp_month", StripeJson.number("10"));
    facts.put("exp_year", StripeJson.number("2027"));
    return facts;
  }

  private static List<String> membersOf(String body) {
    return new ArrayList<>(
        new TreeSet<>(((StripeJson.JsonObject) StripeJsonReader.read(body)).members().keySet()));
  }

  private static StripeJson.JsonObject errorIn(String body) {
    return (StripeJson.JsonObject)
        Objects.requireNonNull(
            ((StripeJson.JsonObject) StripeJsonReader.read(body)).members().get("error"));
  }

  private static Set<String> union(Set<String> one, Set<String> other) {
    Set<String> both = new TreeSet<>(one);
    both.addAll(other);
    return both;
  }

  private void seed(String paymentMethodId) {
    Map<String, String> form = new LinkedHashMap<>();
    form.put("provider", "stripe");
    form.put("testRunId", RUN.value());
    form.put("merchantId", MERCHANT);
    form.put("ownerId", "owner-1");
    form.put("type", "card");
    form.put("alias", paymentMethodId);
    form.put("brand", "visa");
    form.put("last4", "4242");
    form.put("expMonth", "12");
    form.put("expYear", "2030");
    form.put("funding", "credit");
    form.put(PaymentMethodForm.SUBMITTED_ID_PREFIX + "paymentMethodId", paymentMethodId);
    form.put(PaymentMethodForm.SUBMITTED_ID_PREFIX + "customerId", A_CUSTOMER);
    HttpResponse<byte[]> seeded =
        sent(
            HttpRequest.newBuilder(
                    URI.create("http://localhost:" + port + "/ui/payment-methods/seed"))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .timeout(Duration.ofSeconds(20))
                .POST(HttpRequest.BodyPublishers.ofString(encoded(form)))
                .build());
    assertThat(seeded.statusCode()).isEqualTo(302);
  }

  private static Map<String, Object> anAccount(
      String accountId, String secretKey, String publishableKey) {
    return Map.of(
        "accountId",
        accountId,
        "configuration",
        Map.of(
            StripeAccount.SECRET_KEY_PROPERTY,
            secretKey,
            StripeAccount.PUBLISHABLE_KEY_PROPERTY,
            publishableKey,
            StripeCaptureWindow.SETTING,
            "P3D",
            StripeApiVersion.SETTING,
            "2026-08-26.dahlia"));
  }

  private void configure(List<Map<String, Object>> accounts) {
    http.put()
        .uri("/__simulator/stripe/config")
        .contentType(MediaType.APPLICATION_JSON)
        .body(
            Map.of(
                "testRunId",
                RUN.value(),
                "clock",
                accounts.isEmpty() ? Map.of() : Map.of("fixedNow", FIXED_NOW.toString()),
                "accounts",
                accounts,
                "webhooks",
                List.of()))
        .retrieve()
        .toBodilessEntity();
  }

  private void reset() {
    http.post()
        .uri("/__simulator/stripe/reset")
        .contentType(MediaType.APPLICATION_JSON)
        .body(Map.of("testRunId", RUN.value()))
        .retrieve()
        .toBodilessEntity();
  }

  private HttpResponse<byte[]> post(String key, String path, Map<String, String> form) {
    return sent(
        HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
            .header("Content-Type", "application/x-www-form-urlencoded")
            .header("Authorization", "Bearer " + key)
            .timeout(Duration.ofSeconds(20))
            .POST(HttpRequest.BodyPublishers.ofString(encoded(form)))
            .build());
  }

  private HttpResponse<byte[]> sent(HttpRequest request) {
    try {
      return wire.send(request, HttpResponse.BodyHandlers.ofByteArray());
    } catch (InterruptedException interrupted) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException(interrupted);
    } catch (IOException unreachable) {
      throw new IllegalStateException(unreachable);
    }
  }

  private static String encoded(Map<String, String> form) {
    List<String> pairs = new ArrayList<>();
    form.forEach(
        (name, value) ->
            pairs.add(URLEncoder.encode(name, UTF_8) + "=" + URLEncoder.encode(value, UTF_8)));
    return String.join("&", pairs);
  }

  private static String bodyOf(HttpResponse<byte[]> response) {
    return new String(response.body(), UTF_8);
  }

  private record Created(String id, String clientSecret) {}

  private record Recording(
      String method, String path, int status, Map<String, String> headers, String body) {

    private static final Pattern AN_INTENT_ID =
        Pattern.compile("/v1/[a-z_]+/((?:pi|seti)_[A-Za-z0-9]+)");

    private static final Pattern A_CLIENT_SECRET =
        Pattern.compile("client_secret=((?:pi|seti)_[A-Za-z0-9]+_secret_[A-Za-z0-9-]+)");

    private static final String THE_REQUEST_BODY = "x-stripe-mock-request";

    static Recording of(String name) {
      String text;
      try (InputStream in =
          Objects.requireNonNull(
              StripeRecordedClientTrafficIntegrationTest.class.getResourceAsStream(
                  RECORDINGS + name),
              name)) {
        text = new String(in.readAllBytes(), UTF_8);
      } catch (IOException unreadable) {
        throw new UncheckedIOException(unreadable);
      }
      String[] lines = text.split("\n", -1);
      Map<String, String> headers = new LinkedHashMap<>();
      int at = 4;
      for (; at < lines.length && !lines[at].isEmpty(); at++) {
        int colon = lines[at].indexOf(':');
        headers.put(
            lines[at].substring(0, colon).strip().toLowerCase(Locale.ROOT),
            lines[at].substring(colon + 1).strip());
      }
      String body = String.join("\n", List.of(lines).subList(at + 1, lines.length));
      return new Recording(
          lines[0],
          unescaped(lines[1]).replace("https://api.stripe.com", ""),
          Integer.parseInt(lines[2]),
          headers,
          body);
    }

    private static String unescaped(String recorded) {
      String pattern =
          recorded.endsWith("$") ? recorded.substring(0, recorded.length() - 1) : recorded;
      return pattern.replaceAll("\\\\(.)", "$1");
    }

    String header(String name) {
      return Objects.requireNonNull(headers.get(name), name);
    }

    String form() {
      return unescaped(header(THE_REQUEST_BODY));
    }

    String intentId() {
      Matcher found = AN_INTENT_ID.matcher(path);
      assertThat(found.find()).isTrue();
      return found.group(1);
    }

    String clientSecret() {
      Matcher found = A_CLIENT_SECRET.matcher("POST".equals(method) ? form() : path);
      assertThat(found.find()).isTrue();
      return found.group(1);
    }

    List<String> members(Set<String> notModelled, Set<String> addedSince) {
      Set<String> expected =
          new TreeSet<>(((StripeJson.JsonObject) StripeJsonReader.read(body)).members().keySet());
      assertThat(expected).containsAll(notModelled);
      for (String added : addedSince) {
        assertThat(expected).doesNotContain(added);
      }
      expected.removeAll(notModelled);
      expected.addAll(addedSince);
      return new ArrayList<>(expected);
    }
  }
}
