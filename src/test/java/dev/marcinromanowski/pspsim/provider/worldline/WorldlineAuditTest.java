package dev.marcinromanowski.pspsim.provider.worldline;

import static org.assertj.core.api.Assertions.assertThat;

import dev.marcinromanowski.pspsim.kernel.ObjectOrigin;
import dev.marcinromanowski.pspsim.kernel.audit.AuditDirection;
import dev.marcinromanowski.pspsim.kernel.audit.AuditRecord;
import dev.marcinromanowski.pspsim.kernel.audit.ProviderOutcome;
import dev.marcinromanowski.pspsim.kernel.fixture.InMemoryPaymentMethodFixtures;
import dev.marcinromanowski.pspsim.kernel.ids.IdGenerator;
import dev.marcinromanowski.pspsim.kernel.scenario.InMemoryScenarioRules;
import dev.marcinromanowski.pspsim.kernel.scenario.NewScenarioRule;
import dev.marcinromanowski.pspsim.kernel.scenario.ScenarioAction;
import dev.marcinromanowski.pspsim.kernel.scenario.ScenarioEngine;
import dev.marcinromanowski.pspsim.kernel.scenario.ScenarioMatch;
import dev.marcinromanowski.pspsim.kernel.testrun.TestRunId;
import dev.marcinromanowski.pspsim.kernel.testrun.TestRunResolutionRule;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class WorldlineAuditTest {

  private static final TestRunId THE_RUN_THAT_CONFIGURES_THE_MERCHANT =
      TestRunId.of("run-worldline");

  private static final TestRunId THE_DEFAULT_RUN_THAT_CONFIGURES_NOTHING =
      TestRunId.of("run-nothing-is-configured-in");

  private static final Instant WHEN = Instant.parse("2026-09-22T10:00:00Z");

  private final WorldlineDoubles.Merchants merchants =
      new WorldlineDoubles.Merchants()
          .with(
              WorldlineDoubles.merchant(
                  THE_RUN_THAT_CONFIGURES_THE_MERCHANT,
                  WorldlineWire.ACCOUNT,
                  WorldlineWire.MERCHANT));
  private final WorldlineDoubles.Tokenizations sessions = new WorldlineDoubles.Tokenizations();
  private final WorldlineDoubles.Payments payments = new WorldlineDoubles.Payments();
  private final WorldlineDoubles.Keys keys = new WorldlineDoubles.Keys();
  private final WorldlineDoubles.Audit audit = new WorldlineDoubles.Audit();
  private final InMemoryScenarioRules rules = new InMemoryScenarioRules();
  private final InMemoryPaymentMethodFixtures fixtures = new InMemoryPaymentMethodFixtures();
  private final IdGenerator ids = new WorldlineDoubles.CountingIds();

  private WorldlineApi api() {
    return WorldlineWiring.api(
        merchants,
        sessions,
        payments,
        keys,
        audit,
        THE_DEFAULT_RUN_THAT_CONFIGURES_NOTHING,
        WHEN,
        new ScenarioEngine(rules),
        ids,
        fixtures);
  }

  private WorldlineApiResponse open() {
    return open("{}");
  }

  private WorldlineApiResponse open(String body) {
    return api()
        .createHostedTokenization(
            WorldlineWire.call(
                WorldlineWire.POST,
                WorldlineApiController.theHostedTokenizationsOf(WorldlineWire.MERCHANT),
                body),
            WorldlineWire.MERCHANT);
  }

  @Test
  void a_call_lands_in_the_run_its_api_key_belongs_to_rather_than_in_the_default_one() {
    WorldlineApiResponse answered = open();

    assertThat(answered.httpStatus()).isEqualTo(WorldlineAnswers.OK);
    assertThat(answered.testRunId()).isEqualTo(THE_RUN_THAT_CONFIGURES_THE_MERCHANT);
    assertThat(audit.all())
        .isNotEmpty()
        .allSatisfy(
            record -> {
              assertThat(record.testRunId()).isEqualTo(THE_RUN_THAT_CONFIGURES_THE_MERCHANT);
              assertThat(record.resolutionRule())
                  .isEqualTo(TestRunResolutionRule.ACCOUNT_CREDENTIALS);
            });
  }

  @Test
  void an_api_key_no_account_of_any_run_holds_leaves_the_call_in_the_default_run_and_refused() {
    WorldlineApiResponse answered =
        api()
            .createHostedTokenization(
                WorldlineApiCall.of(
                    WorldlineWire.POST,
                    WorldlineApiController.theHostedTokenizationsOf(WorldlineWire.MERCHANT),
                    Map.of(
                        WorldlineFields.AUTHORIZATION,
                        List.of(WorldlineSignature.SCHEME + "a-key-nobody-configured:s")),
                    Map.of(),
                    "{}"),
                WorldlineWire.MERCHANT);

    assertThat(answered.httpStatus()).isEqualTo(WorldlineAnswers.NOT_AUTHORIZED);
    assertThat(answered.testRunId()).isEqualTo(THE_DEFAULT_RUN_THAT_CONFIGURES_NOTHING);
  }

  @Test
  void an_api_key_of_one_account_never_authenticates_a_call_addressed_to_another_merchant() {
    merchants.with(
        WorldlineDoubles.merchant(THE_RUN_THAT_CONFIGURES_THE_MERCHANT, "account-2", "merchant-2"));

    WorldlineApiResponse answered =
        api()
            .createHostedTokenization(
                WorldlineApiCall.of(
                    WorldlineWire.POST,
                    WorldlineApiController.theHostedTokenizationsOf("merchant-2"),
                    Map.of(
                        WorldlineFields.AUTHORIZATION,
                        List.of(WorldlineWire.permissively(WorldlineWire.ACCOUNT))),
                    Map.of(),
                    "{}"),
                "merchant-2");

    assertThat(answered.httpStatus()).isEqualTo(WorldlineAnswers.NOT_AUTHORIZED);
    assertThat(WorldlineWire.bodyOf(answered))
        .contains(WorldlineApi.NO_ACCOUNT_ANSWERS_TO_THAT_KEY_AND_MERCHANT);
  }

  @Test
  void every_call_writes_the_request_it_received_and_the_response_it_delivered() {
    open();

    assertThat(audit.all())
        .extracting(AuditRecord::direction)
        .containsExactly(AuditDirection.INBOUND_REQUEST, AuditDirection.OUTBOUND_RESPONSE);
  }

  @Test
  void every_row_this_provider_writes_carries_an_audit_id_of_the_shape_the_trail_draws_them_in() {
    open();

    assertThat(audit.all())
        .allSatisfy(record -> assertThat(record.auditId()).matches("aud_[0-9a-f]{24}"))
        .extracting(AuditRecord::auditId)
        .doesNotHaveDuplicates();
  }

  @Test
  void the_inbound_row_carries_the_fields_the_request_body_named() {
    open("{\"tokens\":[\"token-one\"]}");

    assertThat(theInbound().sanitizedBody())
        .containsEntry(WorldlineFields.TOKENS, List.of("token-one"));
  }

  @Test
  void a_call_carrying_no_body_at_all_writes_an_inbound_row_naming_no_request_field() {
    api()
        .getHostedTokenization(
            WorldlineWire.call(
                WorldlineWire.GET,
                WorldlineApiController.theHostedTokenizationOf(
                    WorldlineWire.MERCHANT, "0".repeat(32)),
                ""),
            WorldlineWire.MERCHANT,
            "0".repeat(32));

    assertThat(theInbound().sanitizedBody()).isEmpty();
  }

  @Test
  void the_inbound_and_the_outbound_row_name_the_method_and_the_path_the_call_arrived_on() {
    open();

    assertThat(audit.all())
        .allSatisfy(
            record -> {
              assertThat(record.method()).isEqualTo(WorldlineWire.POST);
              assertThat(record.path())
                  .isEqualTo(
                      WorldlineApiController.theHostedTokenizationsOf(WorldlineWire.MERCHANT));
            });
  }

  @Test
  void the_outbound_row_of_a_json_answer_records_the_media_type_the_wire_carried() {
    open();

    assertThat(theOutbound().sanitizedHeaders())
        .containsEntry(WorldlineFields.CONTENT_TYPE, List.of(WorldlineFields.JSON));
    assertThat(theOutbound().httpStatus()).isEqualTo(WorldlineAnswers.OK);
    assertThat(theOutbound().outcome()).isEqualTo(ProviderOutcome.SUCCESS);
    assertThat(theOutbound().providerObjectId()).isNotNull();
  }

  @Test
  void the_outbound_row_of_a_refused_credential_names_the_refusal_and_no_object_at_all() {
    api()
        .createHostedTokenization(
            WorldlineApiCall.of(
                WorldlineWire.POST,
                WorldlineApiController.theHostedTokenizationsOf(WorldlineWire.MERCHANT),
                Map.of(),
                Map.of(),
                "{}"),
            WorldlineWire.MERCHANT);

    assertThat(theOutbound().httpStatus()).isEqualTo(WorldlineAnswers.NOT_AUTHORIZED);
    assertThat(theOutbound().outcome()).isEqualTo(ProviderOutcome.FAILURE);
    assertThat(theOutbound().providerObjectId()).isNull();
    assertThat(theOutbound().refusal()).isNotNull();
  }

  @Test
  void the_outbound_row_names_the_rule_that_decided_the_call_and_names_none_where_none_did() {
    open();
    assertThat(theOutbound().scenarioId()).isNull();

    rules.add(
        new NewScenarioRule(
            WorldlineApi.WORLDLINE,
            THE_RUN_THAT_CONFIGURES_THE_MERCHANT,
            1,
            ScenarioMatch.anyRequest().naming(WorldlineOperations.CREATE_HOSTED_TOKENIZATION),
            new ScenarioAction.TerminalFailure(WorldlineErrors.THE_CARD_WAS_DECLINED),
            null));
    open();

    assertThat(theOutbound().scenarioId()).isNotNull();
  }

  @Test
  void every_row_of_this_provider_is_written_as_traffic_of_the_provider_own_wire() {
    open();

    assertThat(audit.all())
        .allSatisfy(
            record -> {
              assertThat(record.provider()).isEqualTo(WorldlineApi.WORLDLINE);
              assertThat(record.origin()).isEqualTo(ObjectOrigin.PROVIDER_API);
            });
  }

  private AuditRecord theInbound() {
    return audit.all().stream()
        .filter(record -> record.direction() == AuditDirection.INBOUND_REQUEST)
        .reduce((first, last) -> last)
        .orElseThrow();
  }

  private AuditRecord theOutbound() {
    return audit.all().stream()
        .filter(record -> record.direction() == AuditDirection.OUTBOUND_RESPONSE)
        .reduce((first, last) -> last)
        .orElseThrow();
  }
}
