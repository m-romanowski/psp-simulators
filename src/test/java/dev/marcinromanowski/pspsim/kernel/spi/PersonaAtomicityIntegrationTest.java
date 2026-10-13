package dev.marcinromanowski.pspsim.kernel.spi;

import static org.assertj.core.api.Assertions.assertThat;

import dev.marcinromanowski.pspsim.PostgresBackedTest;
import dev.marcinromanowski.pspsim.kernel.ObjectOrigin;
import dev.marcinromanowski.pspsim.kernel.ProviderId;
import dev.marcinromanowski.pspsim.kernel.fixture.FixtureCard;
import dev.marcinromanowski.pspsim.kernel.fixture.PaymentMethodFixtures;
import dev.marcinromanowski.pspsim.kernel.fixture.PaymentMethodKind;
import dev.marcinromanowski.pspsim.kernel.scenario.ScenarioAction;
import dev.marcinromanowski.pspsim.kernel.scenario.ScenarioPreset;
import dev.marcinromanowski.pspsim.kernel.scenario.ScenarioRules;
import dev.marcinromanowski.pspsim.kernel.testrun.TestRunId;
import dev.marcinromanowski.pspsim.kernel.testrun.TestRuns;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;

class PersonaAtomicityIntegrationTest extends PostgresBackedTest {

  private static final ProviderId ID = ProviderId.of("persona-atomicity-it");

  private static final TestRunId RUN = TestRunId.of("persona-atomicity-it");

  private static final String ACCOUNT = "acct-atomicity-it";

  private static final String TOKEN = ProviderPaymentMethodFields.TOKEN;

  private final ProviderDouble sharingOneToken =
      new ProviderDouble(ID.value())
          .expandingForTheRunAsked(new ScenarioAction.FixedSuccess())
          .publishing(aCatalogueOfTwoInstrumentsSharingOneToken());

  @Autowired private PersonaAdmission personas;
  @Autowired private PaymentMethodFixtures fixtures;
  @Autowired private ScenarioRules scenarios;
  @Autowired private TestRuns testRuns;
  @Autowired private JdbcClient jdbc;

  @BeforeEach
  @AfterEach
  void forgetTheRun() {
    jdbc.sql("delete from simulator_payment_method_fixture_claim where provider = ?")
        .param(ID.value())
        .update();
    jdbc.sql("delete from simulator_payment_method_fixture where provider = ?")
        .param(ID.value())
        .update();
    jdbc.sql("delete from simulator_scenario_rule where provider = ?").param(ID.value()).update();
    jdbc.sql("delete from simulator_test_run where test_run_id = ?").param(RUN.value()).update();
  }

  @Test
  void
      a_persona_whose_second_instrument_the_real_store_already_claims_the_token_of_is_refused_as_a_conflict_and_leaves_nothing_behind() {
    PersonaAdmission.Verdict verdict =
        personas.seed(
            sharingOneToken,
            new DeclaredPersonaSeeding(RUN.value(), "two-cards", ACCOUNT),
            ObjectOrigin.CONTROL_API);

    assertThat(verdict)
        .isInstanceOfSatisfying(
            PersonaAdmission.Verdict.Conflicting.class,
            conflicting -> {
              assertThat(conflicting.message()).contains("providerIds." + TOKEN);
              assertThat(conflicting.theRefusalItReports().httpStatus()).isEqualTo(409);
            });
    assertThat(scenarios.of(ID, RUN)).isEmpty();
    assertThat(fixtures.of(ID, RUN)).isEmpty();
    assertThat(theClaimsOfThisProvider()).isEmpty();
    assertThat(testRuns.exists(RUN)).isFalse();
  }

  private List<String> theClaimsOfThisProvider() {
    return jdbc.sql(
            "select id_value from simulator_payment_method_fixture_claim where provider = ?")
        .param(ID.value())
        .query(String.class)
        .list();
  }

  private static ProviderPersonas aCatalogueOfTwoInstrumentsSharingOneToken() {
    return new ProviderPersonas(
        ID,
        List.of(
            new DeclaredPersona(
                PersonaIntent.A_SAVED_CARD_THAT_SUCCEEDS,
                "two-cards",
                "a persona whose two instruments mint the same provider-native token",
                List.of(ScenarioPreset.PREAUTH_SUCCESS_CAPTURE_SUCCESS),
                List.of(
                    new PersonaFixture(
                        "first-of-two",
                        "an-owner",
                        PaymentMethodKind.CARD,
                        aCard(),
                        List.of(TOKEN),
                        "the double mints a token of its own",
                        () -> Map.of(TOKEN, "shared-token"),
                        null),
                    new PersonaFixture(
                        "second-of-two",
                        "an-owner",
                        PaymentMethodKind.CARD,
                        aCard(),
                        List.of(TOKEN),
                        "the double mints a token of its own",
                        () -> Map.of(TOKEN, "shared-token"),
                        null)))),
        theIntentsItPublishesNoneFor());
  }

  private static Map<PersonaIntent, String> theIntentsItPublishesNoneFor() {
    return Map.of(
        PersonaIntent.A_SAVED_CARD_THAT_IS_DECLINED,
        "this double declines nothing",
        PersonaIntent.A_SAVED_CARD_WHOSE_CAPTURE_ANSWER_IS_LOST,
        "this double drops no connection");
  }

  private static FixtureCard aCard() {
    return new FixtureCard("VISA", "1111", 12, 2030, "credit");
  }
}
