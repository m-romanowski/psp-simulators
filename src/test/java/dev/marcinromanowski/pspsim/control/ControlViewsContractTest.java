package dev.marcinromanowski.pspsim.control;

import static org.assertj.core.api.Assertions.assertThat;

import dev.marcinromanowski.pspsim.control.ControlPayloads.AcceptedValueView;
import dev.marcinromanowski.pspsim.control.ControlPayloads.AccountFieldView;
import dev.marcinromanowski.pspsim.control.ControlPayloads.AccountFieldsResponse;
import dev.marcinromanowski.pspsim.control.ControlPayloads.AddressResponse;
import dev.marcinromanowski.pspsim.control.ControlPayloads.ContractOperationView;
import dev.marcinromanowski.pspsim.control.ControlPayloads.OperationsResponse;
import dev.marcinromanowski.pspsim.kernel.ProviderId;
import dev.marcinromanowski.pspsim.kernel.scenario.StoryKeyLifetime;
import dev.marcinromanowski.pspsim.kernel.spi.AcceptedValue;
import dev.marcinromanowski.pspsim.kernel.spi.ConfigurableField;
import dev.marcinromanowski.pspsim.kernel.spi.DisclosedAccount;
import dev.marcinromanowski.pspsim.kernel.spi.FailureCodes;
import dev.marcinromanowski.pspsim.kernel.spi.MatchFact;
import dev.marcinromanowski.pspsim.kernel.spi.PairingSetting;
import dev.marcinromanowski.pspsim.kernel.spi.PairingValue;
import dev.marcinromanowski.pspsim.kernel.spi.ProcessingStates;
import dev.marcinromanowski.pspsim.kernel.spi.ProviderAccountFields;
import dev.marcinromanowski.pspsim.kernel.spi.ProviderOperation;
import dev.marcinromanowski.pspsim.kernel.spi.ProviderPairing;
import dev.marcinromanowski.pspsim.kernel.spi.ProviderVocabulary;
import dev.marcinromanowski.pspsim.kernel.testrun.TestRunId;
import dev.marcinromanowski.pspsim.ui.PairingScreen;
import dev.marcinromanowski.pspsim.ui.WorkbenchScreen;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;

class ControlViewsContractTest {

  private static final ProviderId PROVIDER = ProviderId.of("grow");

  private static final TestRunId RUN = TestRunId.of("e2e-run-123");

  private static final String BASE_URL = "https://simulator.internal";

  private static final String API_BASE_PATH = "/api/light/server/1.0";

  private static final String THE_ADDRESS_A_CLIENT_DIALS =
      "https://simulator.internal/api/light/server/1.0";

  private static final String ACCOUNT = "merchant-1";

  private static final String THE_SECRET_NOBODY_MAY_READ = "sk_test_never_on_a_monitor";

  private static final String HOW_A_REQUEST_NAMES_ITS_ACCOUNT =
      "every request carries userId and pageCode in its body";

  private static final ProviderAccountFields FIELDS =
      new ProviderAccountFields(
          PROVIDER,
          List.of(
              new ConfigurableField(
                  "userId",
                  "The account id",
                  "a sentence a test does not read",
                  true,
                  false,
                  List.of(),
                  "a value a test does not read"),
              new ConfigurableField(
                  "pageCode",
                  "The page secret",
                  "a sentence a test does not read",
                  true,
                  true,
                  List.of(),
                  "a value a test does not read")));

  private static final ProviderAccountFields FIELDS_WITH_A_CLOSED_SET =
      new ProviderAccountFields(
          PROVIDER,
          List.of(
              new ConfigurableField(
                  "userId",
                  "The account id",
                  "a sentence a test does not read",
                  true,
                  false,
                  List.of(),
                  "a value a test does not read"),
              new ConfigurableField(
                  "wireForm",
                  "which wire form answers carry",
                  "a sentence a test does not read",
                  false,
                  false,
                  List.of(
                      new AcceptedValue("LIVE_WIRE", "answer text/html, as the live wire does"),
                      new AcceptedValue(
                          "PUBLISHED_EXAMPLE", "answer application/json, as the example does")))));

  private static final ProviderPairing PAIRING =
      new ProviderPairing(
          PROVIDER,
          API_BASE_PATH,
          HOW_A_REQUEST_NAMES_ITS_ACCOUNT,
          List.of(
              new PairingSetting("GROW_BASE_URL", new PairingValue.TheProviderBaseUrl()),
              new PairingSetting("GROW_USER_ID", new PairingValue.FromAccountField("userId")),
              new PairingSetting("GROW_PAGE_CODE", new PairingValue.FromAccountField("pageCode"))));

  private static final String CREATE = "createPaymentProcess";

  private static final String READ = "getPaymentProcessInfo";

  private static final ProviderVocabulary VOCABULARY =
      new ProviderVocabulary(
          PROVIDER,
          List.of(
              new ProviderOperation(
                  CREATE,
                  Set.of(MatchFact.AMOUNT, MatchFact.MERCHANT_ID, MatchFact.ORDER_ID),
                  "orderId",
                  StoryKeyLifetime.AN_IDENTIFIER_THE_OBJECT_KEEPS_FOR_LIFE),
              new ProviderOperation(
                  READ,
                  Set.of(MatchFact.PROVIDER_OBJECT_ID),
                  "processId",
                  StoryKeyLifetime.AN_IDENTIFIER_THE_OBJECT_MAY_REPLACE)),
          Map.of(),
          new FailureCodes("[0-9]{3}", "three digits"),
          ProcessingStates.onTheseOperations(READ));

  @Test
  void the_fields_a_client_without_a_screen_reads_carry_the_values_and_what_each_one_means() {
    AccountFieldsResponse answered =
        ControlViews.accountFieldsOf(PROVIDER.value(), FIELDS_WITH_A_CLOSED_SET);

    assertThat(answered)
        .isEqualTo(
            new AccountFieldsResponse(
                "grow",
                List.of(
                    new AccountFieldView(
                        "userId",
                        "The account id",
                        "a sentence a test does not read",
                        true,
                        false,
                        List.of()),
                    new AccountFieldView(
                        "wireForm",
                        "which wire form answers carry",
                        "a sentence a test does not read",
                        false,
                        false,
                        List.of(
                            new AcceptedValueView(
                                "LIVE_WIRE", "answer text/html, as the live wire does"),
                            new AcceptedValueView(
                                "PUBLISHED_EXAMPLE",
                                "answer application/json, as the example does"))))));
  }

  @Test
  void the_published_fields_are_the_ones_the_screen_draws_in_the_order_the_screen_draws_them() {
    AccountFieldsResponse answered =
        ControlViews.accountFieldsOf(PROVIDER.value(), FIELDS_WITH_A_CLOSED_SET);

    assertThat(answered.accountFields())
        .extracting(AccountFieldView::key)
        .containsExactlyElementsOf(FIELDS_WITH_A_CLOSED_SET.keys());
  }

  @Test
  void the_address_a_harness_reads_is_the_one_the_pairing_screen_shows() {
    AddressResponse answered = ControlViews.addressOf(RUN, PROVIDER.value(), BASE_URL, PAIRING);

    assertThat(answered.providerBaseUrl()).isEqualTo(screen(BASE_URL).providerBaseUrl());
  }

  @Test
  void a_public_base_url_ending_in_a_slash_still_answers_the_address_the_screen_shows() {
    String withATrailingSlash = BASE_URL + "/";

    AddressResponse answered =
        ControlViews.addressOf(RUN, PROVIDER.value(), withATrailingSlash, PAIRING);

    assertThat(answered.providerBaseUrl())
        .isEqualTo(screen(withATrailingSlash).providerBaseUrl())
        .isEqualTo(THE_ADDRESS_A_CLIENT_DIALS);
  }

  @Test
  void the_address_answer_names_the_run_the_header_that_carries_it_and_the_path_and_nothing_else() {
    AddressResponse answered = ControlViews.addressOf(RUN, PROVIDER.value(), BASE_URL, PAIRING);

    assertThat(answered)
        .isEqualTo(
            new AddressResponse(
                "e2e-run-123",
                "grow",
                BASE_URL,
                THE_ADDRESS_A_CLIENT_DIALS,
                API_BASE_PATH,
                "X-Simulator-Test-Run-Id",
                HOW_A_REQUEST_NAMES_ITS_ACCOUNT));
  }

  @Test
  void the_address_answer_carries_no_account_and_no_setting_value_where_the_screen_carries_both() {
    AddressResponse answered = ControlViews.addressOf(RUN, PROVIDER.value(), BASE_URL, PAIRING);
    PairingScreen.View shown = screen(BASE_URL);

    assertThat(shown.snippets()).extracting(PairingScreen.Snippet::accountId).contains(ACCOUNT);
    assertThat(everyWordOf(answered))
        .noneMatch(said -> said.contains(ACCOUNT))
        .noneMatch(said -> said.contains(THE_SECRET_NOBODY_MAY_READ));
  }

  @Test
  void the_operations_a_harness_reads_are_the_ones_the_workbench_panel_lists() {
    OperationsResponse answered = ControlViews.operationsOf(RUN, PROVIDER.value(), VOCABULARY);
    List<WorkbenchScreen.Operation> shown = WorkbenchScreen.operationsOf(VOCABULARY, List.of());

    assertThat(answered.operations()).hasSameSizeAs(shown);
    for (int at = 0; at < shown.size(); at++) {
      ContractOperationView read = answered.operations().get(at);
      WorkbenchScreen.Operation panel = shown.get(at);
      assertThat(read.name()).isEqualTo(panel.name());
      assertThat(read.carries()).isEqualTo(panel.carries());
      assertThat(read.storiesKeyedOn()).isEqualTo(panel.storiesKeyedOn());
      assertThat(read.itsFlowKeyMayBeReplaced()).isEqualTo(panel.itsFlowKeyMayBeReplaced());
      assertThat(read.reportsAProcessingState()).isEqualTo(panel.reportsAProcessingState());
    }
  }

  @Test
  void an_operation_is_answered_with_the_facts_it_carries_in_one_order_whatever_it_declared_them() {
    OperationsResponse answered = ControlViews.operationsOf(RUN, PROVIDER.value(), VOCABULARY);

    assertThat(answered.operations())
        .extracting(ContractOperationView::carries)
        .containsExactly(
            List.of("match.merchantId", "match.orderId", "match.amount"),
            List.of("match.providerObjectId"));
  }

  @Test
  void the_operations_answer_names_the_run_the_provider_and_what_each_operation_can_do() {
    OperationsResponse answered = ControlViews.operationsOf(RUN, PROVIDER.value(), VOCABULARY);

    assertThat(answered.testRunId()).isEqualTo("e2e-run-123");
    assertThat(answered.provider()).isEqualTo("grow");
    assertThat(answered.operations())
        .containsExactly(
            new ContractOperationView(
                CREATE,
                List.of("match.merchantId", "match.orderId", "match.amount"),
                "orderId",
                false,
                false,
                "three digits"),
            new ContractOperationView(
                READ, List.of("match.providerObjectId"), "processId", true, true, "three digits"));
  }

  private static List<String> everyWordOf(AddressResponse answered) {
    return List.of(
        answered.testRunId(),
        answered.provider(),
        answered.publicBaseUrl(),
        answered.providerBaseUrl(),
        answered.apiBasePath(),
        answered.testRunHeader(),
        answered.howARequestNamesItsAccount());
  }

  private static PairingScreen.View screen(String publicBaseUrl) {
    return PairingScreen.of(
        PROVIDER.value(),
        RUN.value(),
        publicBaseUrl,
        PAIRING,
        FIELDS,
        List.of(ACCOUNT),
        accountId ->
            Optional.of(
                new DisclosedAccount(
                    accountId,
                    Map.of(
                        "userId",
                        List.of(ACCOUNT),
                        "pageCode",
                        List.of(THE_SECRET_NOBODY_MAY_READ)))),
        List.of(),
        List.of());
  }
}
