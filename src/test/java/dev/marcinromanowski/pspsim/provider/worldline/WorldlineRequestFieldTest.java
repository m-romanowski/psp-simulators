package dev.marcinromanowski.pspsim.provider.worldline;

import static org.assertj.core.api.Assertions.assertThat;

import dev.marcinromanowski.pspsim.kernel.ObjectOrigin;
import dev.marcinromanowski.pspsim.kernel.fixture.FixtureCard;
import dev.marcinromanowski.pspsim.kernel.fixture.InMemoryPaymentMethodFixtures;
import dev.marcinromanowski.pspsim.kernel.fixture.NewPaymentMethodFixture;
import dev.marcinromanowski.pspsim.kernel.fixture.PaymentMethodFixturePlan;
import dev.marcinromanowski.pspsim.kernel.fixture.PaymentMethodKind;
import dev.marcinromanowski.pspsim.kernel.ids.IdGenerator;
import dev.marcinromanowski.pspsim.kernel.scenario.InMemoryScenarioRules;
import dev.marcinromanowski.pspsim.kernel.scenario.ScenarioEngine;
import dev.marcinromanowski.pspsim.kernel.spi.HostedCompletionRequest;
import dev.marcinromanowski.pspsim.kernel.spi.PayerOutcome;
import dev.marcinromanowski.pspsim.kernel.spi.ProviderPaymentMethodFields;
import dev.marcinromanowski.pspsim.kernel.testrun.TestRunId;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class WorldlineRequestFieldTest {

  private static final TestRunId RUN = TestRunId.of("run-worldline");

  private static final Instant WHEN = Instant.parse("2026-09-22T10:00:00Z");

  private static final String WHITESPACE = "   ";

  private static final String AN_IDENTIFIER_THIS_SIMULATOR_DREW = "<drawn by this simulator>";

  private final WorldlineDoubles.Merchants merchants =
      new WorldlineDoubles.Merchants()
          .with(WorldlineDoubles.merchant(RUN, WorldlineWire.ACCOUNT, WorldlineWire.MERCHANT));
  private final WorldlineDoubles.Tokenizations sessions = new WorldlineDoubles.Tokenizations();
  private final WorldlineDoubles.Payments payments = new WorldlineDoubles.Payments();
  private final WorldlineDoubles.Keys keys = new WorldlineDoubles.Keys();
  private final WorldlineDoubles.Audit audit = new WorldlineDoubles.Audit();
  private final InMemoryScenarioRules rules = new InMemoryScenarioRules();
  private final InMemoryPaymentMethodFixtures fixtures = new InMemoryPaymentMethodFixtures();
  private final IdGenerator ids = new WorldlineDoubles.CountingIds();

  static Stream<Arguments> everyFieldOfACreatePaymentThisContractReadsAsTextIfItIsThere() {
    return Stream.of(
        Arguments.of(
            WorldlineFields.CURRENCY_CODE,
            "{\"order\":{\"amountOfMoney\":{\"amount\":5000%s}},"
                + "\"cardPaymentMethodSpecificInput\":{\"token\":\"%s\","
                + "\"authorizationMode\":\"SALE\"}}",
            ",\"currencyCode\":\"" + WHITESPACE + "\"",
            ""),
        Arguments.of(
            WorldlineFields.MERCHANT_REFERENCE,
            "{\"order\":{\"amountOfMoney\":{\"amount\":5000,\"currencyCode\":\"PLN\"}%s},"
                + "\"cardPaymentMethodSpecificInput\":{\"token\":\"%s\","
                + "\"authorizationMode\":\"SALE\"}}",
            ",\"references\":{\"merchantReference\":\"" + WHITESPACE + "\"}",
            ""),
        Arguments.of(
            WorldlineFields.MERCHANT_CUSTOMER_ID,
            "{\"order\":{\"amountOfMoney\":{\"amount\":5000,\"currencyCode\":\"PLN\"}%s},"
                + "\"cardPaymentMethodSpecificInput\":{\"token\":\"%s\","
                + "\"authorizationMode\":\"SALE\"}}",
            ",\"customer\":{\"merchantCustomerId\":\"" + WHITESPACE + "\"}",
            ""),
        Arguments.of(
            WorldlineFields.TOKEN,
            "{\"order\":{\"amountOfMoney\":{\"amount\":5000,\"currencyCode\":\"PLN\"}},"
                + "\"cardPaymentMethodSpecificInput\":{%s\"authorizationMode\":\"SALE\"}}"
                + "%s",
            "\"token\":\"" + WHITESPACE + "\",",
            ""),
        Arguments.of(
            WorldlineFields.HOSTED_TOKENIZATION_ID,
            "{\"order\":{\"amountOfMoney\":{\"amount\":5000,\"currencyCode\":\"PLN\"}}%s,"
                + "\"cardPaymentMethodSpecificInput\":{\"token\":\"%s\","
                + "\"authorizationMode\":\"SALE\"}}",
            ",\"hostedTokenizationId\":\"" + WHITESPACE + "\"",
            ""),
        Arguments.of(
            WorldlineFields.AUTHORIZATION_MODE,
            "{\"order\":{\"amountOfMoney\":{\"amount\":5000,\"currencyCode\":\"PLN\"}},"
                + "\"cardPaymentMethodSpecificInput\":{\"token\":\"%2$s\"%1$s}}",
            ",\"authorizationMode\":\"" + WHITESPACE + "\"",
            ""));
  }

  @ParameterizedTest(name = "{0} sent as whitespace is read as absent")
  @MethodSource("everyFieldOfACreatePaymentThisContractReadsAsTextIfItIsThere")
  void a_field_sent_as_whitespace_is_answered_exactly_as_one_the_request_left_out(
      String field, String shape, String blank, String absent) {
    String token = aStoredToken();

    String whenBlank = theAnswerAndTheObjectOf(String.format(shape, blank, token));
    String whenAbsent = theAnswerAndTheObjectOf(String.format(shape, absent, token));

    assertThat(whenBlank)
        .as("a %s of nothing but whitespace is a %s this request did not send", field, field)
        .isEqualTo(whenAbsent);
  }

  @Test
  void a_currency_of_nothing_but_whitespace_is_refused_for_the_currency_it_did_not_send() {
    String token = aStoredToken();

    WorldlineApiResponse refused =
        create(
            "{\"order\":{\"amountOfMoney\":{\"amount\":5000,\"currencyCode\":\""
                + WHITESPACE
                + "\"}},\"cardPaymentMethodSpecificInput\":{\"token\":\""
                + token
                + "\",\"authorizationMode\":\"SALE\"}}");

    assertThat(refused.httpStatus()).isEqualTo(WorldlineAnswers.BAD_REQUEST);
    assertThat(WorldlineWire.bodyOf(refused)).contains(CreatePayment.THE_CURRENCY_IS_REQUIRED);
  }

  @Test
  void a_merchant_reference_of_nothing_but_whitespace_is_on_no_screen_of_the_payment_it_started() {
    String token = aStoredToken();

    WorldlineApiResponse created =
        create(
            "{\"order\":{\"amountOfMoney\":{\"amount\":5000,\"currencyCode\":\"PLN\"},"
                + "\"references\":{\"merchantReference\":\""
                + WHITESPACE
                + "\"},\"customer\":{\"merchantCustomerId\":\""
                + WHITESPACE
                + "\"}},\"cardPaymentMethodSpecificInput\":{\"token\":\""
                + token
                + "\",\"authorizationMode\":\"SALE\"}}");

    WorldlinePayment payment =
        payments.findById(RUN, String.valueOf(created.providerObjectId())).orElseThrow();
    assertThat(payment.merchantReference()).isNull();
    assertThat(payment.merchantCustomerId()).isNull();
  }

  @Test
  void a_token_of_nothing_but_whitespace_names_no_instrument_rather_than_an_unknown_one() {
    WorldlineApiResponse refused =
        create(
            "{\"order\":{\"amountOfMoney\":{\"amount\":5000,\"currencyCode\":\"PLN\"}},"
                + "\"cardPaymentMethodSpecificInput\":{\"token\":\""
                + WHITESPACE
                + "\",\"authorizationMode\":\"SALE\"}}");

    assertThat(refused.httpStatus()).isEqualTo(WorldlineAnswers.BAD_REQUEST);
    assertThat(WorldlineWire.bodyOf(refused))
        .contains("one of the two is required")
        .doesNotContain("names no token this test run holds");
  }

  @Test
  void a_hosted_tokenization_id_of_nothing_but_whitespace_leaves_the_token_the_only_instrument() {
    String token = aStoredToken();

    WorldlineApiResponse created =
        create(
            "{\"order\":{\"amountOfMoney\":{\"amount\":5000,\"currencyCode\":\"PLN\"}},"
                + "\"hostedTokenizationId\":\""
                + WHITESPACE
                + "\",\"cardPaymentMethodSpecificInput\":{\"token\":\""
                + token
                + "\",\"authorizationMode\":\"SALE\"}}");

    assertThat(created.httpStatus()).isEqualTo(WorldlineAnswers.CREATED);
  }

  @Test
  void an_authorization_mode_of_nothing_but_whitespace_behaves_as_the_unspecified_case() {
    String token = aStoredToken();

    WorldlineApiResponse created =
        create(
            "{\"order\":{\"amountOfMoney\":{\"amount\":5000,\"currencyCode\":\"PLN\"}},"
                + "\"cardPaymentMethodSpecificInput\":{\"token\":\""
                + token
                + "\",\"authorizationMode\":\""
                + WHITESPACE
                + "\"}}");

    assertThat(created.httpStatus()).isEqualTo(WorldlineAnswers.CREATED);
    assertThat(
            payments
                .findById(RUN, String.valueOf(created.providerObjectId()))
                .orElseThrow()
                .authorizationMode())
        .isEqualTo(WorldlineAuthorizationMode.FINAL_AUTHORIZATION);
  }

  @Test
  void a_hosted_tokenization_session_of_another_account_of_this_run_charges_nothing() {
    merchants.with(WorldlineDoubles.merchant(RUN, "account-2", "merchant-2"));
    String sessionId = aFinishedSession();

    WorldlineApiResponse refused =
        api()
            .createPayment(
                WorldlineApiCall.of(
                    WorldlineWire.POST,
                    WorldlineApiController.thePaymentsOf("merchant-2"),
                    Map.of(
                        WorldlineFields.AUTHORIZATION,
                        List.of(WorldlineWire.permissively("account-2"))),
                    Map.of(),
                    "{\"order\":{\"amountOfMoney\":{\"amount\":5000,\"currencyCode\":\"PLN\"}},"
                        + "\"hostedTokenizationId\":\""
                        + sessionId
                        + "\",\"cardPaymentMethodSpecificInput\":"
                        + "{\"authorizationMode\":\"SALE\"}}"),
                "merchant-2");

    assertThat(refused.httpStatus()).isEqualTo(WorldlineAnswers.BAD_REQUEST);
    assertThat(WorldlineWire.bodyOf(refused))
        .contains("hostedTokenizationId names no hosted tokenization session of this merchant");
  }

  @Test
  void a_create_gives_up_after_the_attempts_it_publishes_when_every_identifier_it_draws_is_taken() {
    WorldlineDoubles.CountingIds counted = new WorldlineDoubles.CountingIds();
    payments.refusingEveryIdentifierClaim();

    CreatePayment creating =
        new CreatePayment(
            payments,
            sessions,
            new WorldlinePayerCards(fixtures),
            keys,
            new WorldlineAnswers(counted),
            counted,
            new ScenarioEngine(rules));
    WorldlineApiCall call =
        WorldlineWire.call(
            WorldlineWire.POST,
            WorldlineApiController.thePaymentsOf(WorldlineWire.MERCHANT),
            WorldlineWire.aPaymentBody(5000L, "PLN", aSeededToken(), "SALE"));
    Map<String, List<String>> noHeaders = Map.of();
    WorldlineMerchant merchant = merchants.find(RUN, WorldlineWire.ACCOUNT).orElseThrow();

    Assertions.assertThrows(
        IllegalStateException.class,
        () -> creating.handle(call, noHeaders, RUN, merchant, WHEN, ObjectOrigin.PROVIDER_API));

    assertThat(counted.howManyOfLength(WorldlineIdentifiers.PAYID_DIGITS))
        .isEqualTo(CreatePayment.PAYMENT_ID_ATTEMPTS);
  }

  @Test
  void a_tokenization_gives_up_after_the_attempts_it_publishes_when_every_session_id_is_taken() {
    WorldlineDoubles.CountingIds counted = new WorldlineDoubles.CountingIds();
    sessions.refusingEveryInsert();

    CreateHostedTokenization creating =
        new CreateHostedTokenization(
            sessions,
            new WorldlineAnswers(counted),
            counted,
            new ScenarioEngine(rules),
            WorldlineWire.PUBLIC_BASE_URL);
    WorldlineApiCall call =
        WorldlineWire.call(
            WorldlineWire.POST,
            WorldlineApiController.theHostedTokenizationsOf(WorldlineWire.MERCHANT),
            "{}");
    Map<String, List<String>> noHeaders = Map.of();
    WorldlineMerchant merchant = merchants.find(RUN, WorldlineWire.ACCOUNT).orElseThrow();

    Assertions.assertThrows(
        IllegalStateException.class,
        () -> creating.handle(call, noHeaders, RUN, merchant, WHEN, ObjectOrigin.PROVIDER_API));

    assertThat(counted.howManyOfLength(WorldlineIdentifiers.HOSTED_TOKENIZATION_HEX_DIGITS))
        .isEqualTo(CreateHostedTokenization.SESSION_ATTEMPTS);
  }

  private String theAnswerAndTheObjectOf(String body) {
    WorldlineApiResponse answered = create(body);
    String stored =
        payments
            .findById(RUN, String.valueOf(answered.providerObjectId()))
            .map(
                payment ->
                    payment.authorizationMode()
                        + "/"
                        + payment.merchantReference()
                        + "/"
                        + payment.merchantCustomerId()
                        + "/"
                        + payment.token()
                        + "/"
                        + payment.hostedTokenizationId()
                        + "/"
                        + payment.currencyCode())
            .orElse("no payment was stored");
    return answered.httpStatus()
        + " "
        + withoutTheIdentifiersThisSimulatorDraws(WorldlineWire.bodyOf(answered))
        + " "
        + stored;
  }

  private static String withoutTheIdentifiersThisSimulatorDraws(String body) {
    return body.replaceAll("[0-9]{10}_[0-9]+", AN_IDENTIFIER_THIS_SIMULATOR_DREW)
        .replaceAll(
            "[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}",
            AN_IDENTIFIER_THIS_SIMULATOR_DREW);
  }

  private WorldlineApi api() {
    return WorldlineWiring.api(
        merchants,
        sessions,
        payments,
        keys,
        audit,
        RUN,
        WHEN,
        new ScenarioEngine(rules),
        ids,
        fixtures);
  }

  private WorldlineApiResponse create(String body) {
    return api()
        .createPayment(
            WorldlineWire.call(
                WorldlineWire.POST,
                WorldlineApiController.thePaymentsOf(WorldlineWire.MERCHANT),
                body),
            WorldlineWire.MERCHANT);
  }

  private String aFinishedSession() {
    WorldlineApiResponse opened =
        api()
            .createHostedTokenization(
                WorldlineWire.call(
                    WorldlineWire.POST,
                    WorldlineApiController.theHostedTokenizationsOf(WorldlineWire.MERCHANT),
                    "{}"),
                WorldlineWire.MERCHANT);
    String sessionId = String.valueOf(opened.providerObjectId());
    new TokenizeOnTheHostedPage(sessions, new WorldlinePayerCards(fixtures), ids)
        .complete(
            new HostedCompletionRequest(
                RUN,
                sessionId,
                PayerOutcome.PAID,
                WorldlinePayerCards.THE_ONE_CARD_WORLDLINE_PRINTS,
                ObjectOrigin.CONTROL_API),
            sessions.findById(RUN, sessionId).orElseThrow(),
            WHEN);
    return sessionId;
  }

  private String aStoredToken() {
    return String.valueOf(sessions.findById(RUN, aFinishedSession()).orElseThrow().token());
  }

  private String aSeededToken() {
    fixtures.seed(
        new NewPaymentMethodFixture(
            WorldlineApi.WORLDLINE,
            RUN,
            "merchant-1",
            "owner-1",
            PaymentMethodKind.CARD,
            "seeded-card",
            new FixtureCard("MASTERCARD", "4444", 1, 2031, "credit"),
            Map.of(ProviderPaymentMethodFields.TOKEN, "token-seeded"),
            null,
            ObjectOrigin.CONTROL_API),
        PaymentMethodFixturePlan.storingNothingOfItsOwn(),
        Set.of());
    return "token-seeded";
  }
}
