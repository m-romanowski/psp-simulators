package dev.marcinromanowski.pspsim.kernel;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.marcinromanowski.pspsim.kernel.clock.FixedTimes;
import dev.marcinromanowski.pspsim.kernel.clock.SimulatorClock;
import dev.marcinromanowski.pspsim.kernel.clock.internal.HostBackedSimulatorClock;
import dev.marcinromanowski.pspsim.kernel.credentials.AccountRemovalPlan;
import dev.marcinromanowski.pspsim.kernel.credentials.ProviderConfigurationPlan;
import dev.marcinromanowski.pspsim.kernel.fixture.NewPaymentMethodFixture;
import dev.marcinromanowski.pspsim.kernel.fixture.PaymentMethodFixture;
import dev.marcinromanowski.pspsim.kernel.fixture.PaymentMethodFixturePlan;
import dev.marcinromanowski.pspsim.kernel.fixture.PaymentMethodFixtureRemoval;
import dev.marcinromanowski.pspsim.kernel.idempotency.KeyRole;
import dev.marcinromanowski.pspsim.kernel.idempotency.KeyedExchangePage;
import dev.marcinromanowski.pspsim.kernel.idempotency.KeyedExchangeQuery;
import dev.marcinromanowski.pspsim.kernel.ids.IdGenerator;
import dev.marcinromanowski.pspsim.kernel.ids.internal.SecureRandomIdGenerator;
import dev.marcinromanowski.pspsim.kernel.scenario.StoryKeyLifetime;
import dev.marcinromanowski.pspsim.kernel.spi.AFieldATesterFillsIn;
import dev.marcinromanowski.pspsim.kernel.spi.ConfigurableField;
import dev.marcinromanowski.pspsim.kernel.spi.DeclaredKey;
import dev.marcinromanowski.pspsim.kernel.spi.DeclaredOrigination;
import dev.marcinromanowski.pspsim.kernel.spi.DisclosedAccount;
import dev.marcinromanowski.pspsim.kernel.spi.FailureCodes;
import dev.marcinromanowski.pspsim.kernel.spi.HostedCompletionOffer;
import dev.marcinromanowski.pspsim.kernel.spi.HostedCompletionOutcome;
import dev.marcinromanowski.pspsim.kernel.spi.HostedCompletionRequest;
import dev.marcinromanowski.pspsim.kernel.spi.HostedPageContract;
import dev.marcinromanowski.pspsim.kernel.spi.KeyCarriage;
import dev.marcinromanowski.pspsim.kernel.spi.ObjectActionOutcome;
import dev.marcinromanowski.pspsim.kernel.spi.ObjectActionRequest;
import dev.marcinromanowski.pspsim.kernel.spi.ObjectActions;
import dev.marcinromanowski.pspsim.kernel.spi.OriginationOutcome;
import dev.marcinromanowski.pspsim.kernel.spi.OriginationRequest;
import dev.marcinromanowski.pspsim.kernel.spi.PairingSetting;
import dev.marcinromanowski.pspsim.kernel.spi.PairingValue;
import dev.marcinromanowski.pspsim.kernel.spi.PresetExpansion;
import dev.marcinromanowski.pspsim.kernel.spi.PresetRequest;
import dev.marcinromanowski.pspsim.kernel.spi.ProcessingStates;
import dev.marcinromanowski.pspsim.kernel.spi.ProviderAccountFields;
import dev.marcinromanowski.pspsim.kernel.spi.ProviderConfiguration;
import dev.marcinromanowski.pspsim.kernel.spi.ProviderKeyMechanisms;
import dev.marcinromanowski.pspsim.kernel.spi.ProviderObjectPage;
import dev.marcinromanowski.pspsim.kernel.spi.ProviderObjectQuery;
import dev.marcinromanowski.pspsim.kernel.spi.ProviderObjectState;
import dev.marcinromanowski.pspsim.kernel.spi.ProviderOperation;
import dev.marcinromanowski.pspsim.kernel.spi.ProviderOriginationFields;
import dev.marcinromanowski.pspsim.kernel.spi.ProviderPairing;
import dev.marcinromanowski.pspsim.kernel.spi.ProviderPaymentMethodFields;
import dev.marcinromanowski.pspsim.kernel.spi.ProviderRegistry;
import dev.marcinromanowski.pspsim.kernel.spi.ProviderVocabulary;
import dev.marcinromanowski.pspsim.kernel.spi.ProviderWireAnswer;
import dev.marcinromanowski.pspsim.kernel.spi.ProviderWireRoutes;
import dev.marcinromanowski.pspsim.kernel.spi.ResetOutcome;
import dev.marcinromanowski.pspsim.kernel.spi.ResetScope;
import dev.marcinromanowski.pspsim.kernel.spi.ScheduledWebhook;
import dev.marcinromanowski.pspsim.kernel.spi.SimulatorProvider;
import dev.marcinromanowski.pspsim.kernel.spi.TestKeyClaims;
import dev.marcinromanowski.pspsim.kernel.spi.ThePayerStep;
import dev.marcinromanowski.pspsim.kernel.spi.WebhookScheduleRequest;
import dev.marcinromanowski.pspsim.kernel.spi.WireFault;
import dev.marcinromanowski.pspsim.kernel.testrun.TestRunId;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class KernelServicesTest {

  private static final TestRunId PINNED_RUN = TestRunId.of("pinned-run");
  private static final TestRunId LIVE_RUN = TestRunId.of("live-run");
  private static final Instant HOST_NOW = Instant.parse("2020-01-01T00:00:00Z");

  @Test
  void a_test_run_that_pinned_its_time_reads_that_time() {
    Instant pinned = Instant.parse("2026-09-07T12:00:00Z");
    FixedTimes fixedTimes = new InMemoryFixedTimes();
    fixedTimes.pin(PINNED_RUN, pinned);

    assertThat(clock(fixedTimes).now(PINNED_RUN)).isEqualTo(pinned);
  }

  @Test
  void a_test_run_that_pinned_nothing_reads_the_host_clock() {
    FixedTimes fixedTimes = new InMemoryFixedTimes();
    fixedTimes.pin(PINNED_RUN, Instant.parse("2026-09-07T12:00:00Z"));

    assertThat(clock(fixedTimes).now(LIVE_RUN)).isEqualTo(HOST_NOW);
  }

  @Test
  void releasing_a_pinned_time_returns_the_test_run_to_the_host_clock() {
    FixedTimes fixedTimes = new InMemoryFixedTimes();
    fixedTimes.pin(PINNED_RUN, Instant.parse("2026-09-07T12:00:00Z"));

    fixedTimes.release(PINNED_RUN);

    assertThat(clock(fixedTimes).now(PINNED_RUN)).isEqualTo(HOST_NOW);
  }

  @Test
  void releasing_a_test_run_that_pinned_nothing_changes_nothing() {
    FixedTimes fixedTimes = new InMemoryFixedTimes();

    fixedTimes.release(LIVE_RUN);

    assertThat(clock(fixedTimes).now(LIVE_RUN)).isEqualTo(HOST_NOW);
  }

  @ParameterizedTest(name = "produces {0} hexadecimal characters")
  @ValueSource(ints = {1, 24, 32})
  void produces_lowercase_hexadecimal_of_the_requested_length(int length) {
    String id = new SecureRandomIdGenerator().hex(length);

    assertThat(id).hasSize(length).matches("[0-9a-f]+");
  }

  @ParameterizedTest(name = "produces {0} decimal digits")
  @ValueSource(ints = {1, 6, 8})
  void produces_decimal_digits_of_the_requested_length(int length) {
    String id = new SecureRandomIdGenerator().digits(length);

    assertThat(id).hasSize(length).matches("[1-9][0-9]*");
  }

  @Test
  void never_starts_a_numeric_identifier_with_a_zero() {
    IdGenerator ids = new SecureRandomIdGenerator();

    for (int i = 0; i < 200; i++) {
      assertThat(ids.digits(8)).doesNotStartWith("0");
    }
  }

  @Test
  void does_not_repeat_itself() {
    IdGenerator ids = new SecureRandomIdGenerator();
    Set<String> issued = new HashSet<>();

    for (int i = 0; i < 500; i++) {
      issued.add(ids.hex(32));
    }

    assertThat(issued).hasSize(500);
  }

  @ParameterizedTest(name = "refuses to produce an identifier of length {0}")
  @ValueSource(ints = {0, -1})
  void refuses_an_identifier_with_no_length(int length) {
    IdGenerator ids = new SecureRandomIdGenerator();

    assertThatThrownBy(() -> ids.hex(length)).isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> ids.digits(length)).isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void finds_a_provider_by_the_path_segment_the_control_api_uses() {
    ProviderRegistry registry = new ProviderRegistry(List.of(provider("grow"), provider("stripe")));

    assertThat(registry.find("grow")).get().extracting(p -> p.id().value()).isEqualTo("grow");
    assertThat(registry.names()).containsExactly("grow", "stripe");
  }

  @ParameterizedTest(name = "does not find the provider named {0}")
  @ValueSource(strings = {"unknown", "GROW", "grow ", "", "../etc/passwd"})
  void does_not_find_a_provider_it_was_not_given(String name) {
    ProviderRegistry registry = new ProviderRegistry(List.of(provider("grow")));

    assertThat(registry.find(name)).isEmpty();
  }

  @Test
  void refuses_two_modules_claiming_the_same_provider_name() {
    List<SimulatorProvider> clashing = List.of(provider("grow"), provider("grow"));

    assertThatThrownBy(() -> new ProviderRegistry(clashing))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("grow");
  }

  private static SimulatorClock clock(FixedTimes fixedTimes) {
    return new HostBackedSimulatorClock(fixedTimes, Clock.fixed(HOST_NOW, ZoneOffset.UTC));
  }

  private static SimulatorProvider provider(String name) {
    return new SimulatorProvider() {
      @Override
      public ProviderId id() {
        return ProviderId.of(name);
      }

      @Override
      public ProviderWireRoutes wireRoutes() {
        return new ProviderWireRoutes(id(), List.of("/" + id().value()));
      }

      @Override
      public ProviderWireAnswer theAnswerOffItsRoutes(WireFault fault, String address) {
        return new ProviderWireAnswer.NothingThisContractPublishes(
            "this double stands for a provider module with no published answer for a request off"
                + " its own routes");
      }

      @Override
      public HostedCompletionOffer hostedCompletion(TestRunId testRunId, String providerObjectId) {
        return new HostedCompletionOffer.NotOffered(
            id(), "this double stands for a provider module with no payer-facing page of its own");
      }

      @Override
      public HostedCompletionOutcome complete(HostedCompletionRequest request) {
        return new HostedCompletionOutcome.NoSuchObject();
      }

      @Override
      public ProviderOriginationFields originationFields() {
        return new ProviderOriginationFields(
            ProviderId.of(name),
            List.of(
                new DeclaredOrigination(
                    "a sale",
                    "Take the money in one call",
                    "a payment this double reports and never stores",
                    new ThePayerStep.NoPageThisSimulatorServes(
                        "this double serves no page a payer finishes."),
                    List.of(
                        AFieldATesterFillsIn.typed(
                            "amount",
                            "amount",
                            true,
                            "the amount this double is asked for and does nothing with.",
                            "1000")))),
            new LinkedHashMap<>());
      }

      @Override
      public OriginationOutcome originate(OriginationRequest request) {
        return new OriginationOutcome.NoSuchAccount();
      }

      @Override
      public ProviderConfigurationPlan parse(ProviderConfiguration configuration) {
        return new ProviderConfigurationPlan(Set.of(), List.of(), () -> {});
      }

      @Override
      public ProviderKeyMechanisms keyMechanisms() {
        return new ProviderKeyMechanisms(
            ProviderId.of(name),
            List.of(
                new DeclaredKey(
                    "Idempotency-Key",
                    KeyRole.TRUE_IDEMPOTENCY_KEY,
                    KeyCarriage.HEADER,
                    "the account this key was sent under",
                    TestKeyClaims.NEVER_HELD)),
            Map.of(
                KeyRole.DUPLICATE_DETECTION_KEY,
                "this double refuses no repeat",
                KeyRole.CORRELATION_KEY,
                "this double correlates nothing"));
      }

      @Override
      public ProviderPairing pairing() {
        return new ProviderPairing(
            ProviderId.of(name),
            "/double",
            "the double authenticates nothing",
            List.of(new PairingSetting("BASE_URL", new PairingValue.TheProviderBaseUrl())));
      }

      @Override
      public KeyedExchangePage keyedExchanges(KeyedExchangeQuery query) {
        return KeyedExchangePage.empty();
      }

      @Override
      public ProviderVocabulary vocabulary() {
        return new ProviderVocabulary(
            ProviderId.of(name),
            List.of(
                new ProviderOperation(
                    "theOnlyOperation",
                    Set.of(),
                    "the object it addresses",
                    StoryKeyLifetime.AN_IDENTIFIER_THE_OBJECT_KEEPS_FOR_LIFE)),
            Map.of(),
            new FailureCodes("[0-9]+", "this stub provider declines with a number"),
            ProcessingStates.noneOfThem("this provider names no processing state"));
      }

      @Override
      public AccountRemovalPlan forget(TestRunId testRunId, String accountId) {
        return new AccountRemovalPlan(() -> {});
      }

      @Override
      public ProviderAccountFields accountFields() {
        return new ProviderAccountFields(
            ProviderId.of(name),
            List.of(
                new ConfigurableField(
                    "theOnlyField",
                    "The only field",
                    "a sentence a test does not read",
                    true,
                    false,
                    List.of(),
                    "a value a test does not read")));
      }

      @Override
      public ProviderPaymentMethodFields paymentMethodFields() {
        return new ProviderPaymentMethodFields(
            ProviderId.of(name),
            List.of(),
            Map.of(
                ProviderPaymentMethodFields.CUSTOMER_ID,
                "this stub provider names no customer",
                ProviderPaymentMethodFields.PAYMENT_METHOD_ID,
                "this stub provider names no payment method",
                ProviderPaymentMethodFields.TOKEN,
                "this stub provider mints no token"));
      }

      @Override
      public PaymentMethodFixturePlan seed(NewPaymentMethodFixture fixture) {
        return PaymentMethodFixturePlan.storingNothingOfItsOwn();
      }

      @Override
      public PaymentMethodFixtureRemoval forgetFixture(PaymentMethodFixture fixture) {
        return new PaymentMethodFixtureRemoval(() -> {});
      }

      @Override
      public PresetExpansion expand(PresetRequest request) {
        throw new UnsupportedOperationException("this stub provider expands no preset");
      }

      @Override
      public ResetOutcome reset(TestRunId testRunId, ResetScope scope) {
        return new ResetOutcome(0, 0);
      }

      @Override
      public List<ScheduledWebhook> schedule(WebhookScheduleRequest request) {
        throw new UnsupportedOperationException("this stub provider sends no callbacks");
      }

      @Override
      public Optional<ProviderObjectState> state(TestRunId testRunId, String providerObjectId) {
        return Optional.empty();
      }

      @Override
      public ProviderObjectPage objects(ProviderObjectQuery query) {
        return new ProviderObjectPage.Listed(List.of());
      }

      @Override
      public HostedPageContract hostedPages() {
        return new HostedPageContract.NoPageAPayerFinishes(
            "this stand-in for a provider module keeps no object waiting for a payer");
      }

      @Override
      public ObjectActions objectActions() {
        return new ObjectActions(ProviderId.of(name), Map.of());
      }

      @Override
      public ObjectActionOutcome act(ObjectActionRequest request) {
        return new ObjectActionOutcome.NoSuchObject();
      }

      @Override
      public Optional<DisclosedAccount> disclosureOf(TestRunId testRunId, String accountId) {
        return Optional.empty();
      }
    };
  }

  private static final class InMemoryFixedTimes implements FixedTimes {

    private final Map<TestRunId, Instant> pinned = new HashMap<>();

    @Override
    public Optional<Instant> of(TestRunId testRunId) {
      return Optional.ofNullable(pinned.get(testRunId));
    }

    @Override
    public void pin(TestRunId testRunId, Instant fixedNow) {
      pinned.put(testRunId, fixedNow);
    }

    @Override
    public void release(TestRunId testRunId) {
      pinned.remove(testRunId);
    }
  }
}
