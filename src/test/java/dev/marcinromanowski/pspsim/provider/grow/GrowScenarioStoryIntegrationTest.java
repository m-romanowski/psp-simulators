package dev.marcinromanowski.pspsim.provider.grow;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;

import dev.marcinromanowski.pspsim.PostgresBackedTest;
import dev.marcinromanowski.pspsim.kernel.audit.TheCausesOfAFailure;
import dev.marcinromanowski.pspsim.kernel.credentials.Redaction;
import dev.marcinromanowski.pspsim.kernel.scenario.ScenarioStory;
import dev.marcinromanowski.pspsim.kernel.testrun.TestRunId;
import dev.marcinromanowski.pspsim.kernel.webhook.LocalMerchantServer;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.assertj.core.groups.Tuple;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

class GrowScenarioStoryIntegrationTest extends PostgresBackedTest {

  private static final TestRunId RUN = TestRunId.of("grow-story-it");
  private static final String USER_ID = "grow-story-user";
  private static final String PAGE_CODE = "grow-story-page";
  private static final String CREATE = "/api/light/server/1.0/createPaymentProcess";
  private static final String PROCESS_INFO = "/api/light/server/1.0/getPaymentProcessInfo";
  private static final String SETTLE = "/api/light/server/1.0/settleSuspendedTransaction";
  private static final String HELD = "120.50";
  private static final String SPAN = "preauth-then-capture";
  private static final String PREAUTHORIZED = "preauthorized";
  private static final String CAPTURED = "captured";
  private static final String STORY = "lookup-settles-late";
  private static final String LOOKED_UP_ONCE = "looked-up-once";
  private static final String REPLAYED = "lookup-replays";

  private record HostedProcess(String processId, String processToken) {}

  private record Held(String processId, String transactionId, String transactionToken) {}

  @LocalServerPort private int port;

  @Autowired private ObjectMapper json;

  @Autowired private JdbcClient jdbc;

  private RestClient http;

  private LocalMerchantServer merchant;

  @BeforeEach
  void configureTheMerchantAccount() {
    http = RestClient.create("http://localhost:" + port);
    merchant = new LocalMerchantServer();
    reset();
    http.put()
        .uri("/__simulator/grow/config")
        .contentType(MediaType.APPLICATION_JSON)
        .body(
            Map.of(
                "testRunId", RUN.value(),
                "accounts",
                    List.of(
                        Map.of(
                            "accountId",
                            "merchant-1",
                            "configuration",
                            Map.of("userId", USER_ID, "pageCode", PAGE_CODE))),
                "webhooks", List.of(Map.of("accountId", "merchant-1", "url", merchant.url()))))
        .retrieve()
        .toBodilessEntity();
  }

  @AfterEach
  void unconfigureTheMerchantAccount() {
    merchant.close();
    reset();
    http.put()
        .uri("/__simulator/grow/config")
        .contentType(MediaType.APPLICATION_JSON)
        .body(
            Map.of(
                "testRunId", RUN.value(),
                "clock", Map.of(),
                "accounts", List.of(),
                "webhooks", List.of()))
        .retrieve()
        .toBodilessEntity();
  }

  @Test
  void a_story_that_begins_at_the_creation_of_a_payment_decides_the_capture_of_that_payment() {
    theSpanningStory();

    Held held = aHeldPayment();
    Map<String, Object> captured = parse(post(SETTLE, settlement(held)));

    assertThat(captured).containsEntry("status", 0);
    assertThat(errorOf(captured)).containsEntry("id", 809);
    assertThat(stateOf(held.transactionId()))
        .containsEntry("normalizedStatus", "READY_FOR_CAPTURE");
  }

  @Test
  void the_capture_of_a_second_payment_is_not_decided_by_the_story_the_first_payment_moved() {
    theSpanningStory();

    Held first = aHeldPayment();
    Held second = aHeldPayment();

    assertThat(parse(post(SETTLE, settlement(first)))).containsEntry("status", 0);
    assertThat(theSpanningStoryStands())
        .containsOnly(
            entry(GrowStoryKey.ofProcess(first.processId()), CAPTURED),
            entry(GrowStoryKey.ofProcess(second.processId()), PREAUTHORIZED));

    assertThat(parse(post(SETTLE, settlement(second)))).containsEntry("status", 0);
    assertThat(theSpanningStoryStands())
        .containsOnly(
            entry(GrowStoryKey.ofProcess(first.processId()), CAPTURED),
            entry(GrowStoryKey.ofProcess(second.processId()), CAPTURED));
    assertThat(stateOf(first.transactionId()))
        .containsEntry("normalizedStatus", "READY_FOR_CAPTURE");
    assertThat(stateOf(second.transactionId()))
        .containsEntry("normalizedStatus", "READY_FOR_CAPTURE");
  }

  private Map<String, String> theSpanningStoryStands() {
    Map<String, String> standing = new LinkedHashMap<>();
    jdbc.sql(
            """
            select story_key, state from simulator_scenario_story_state
            where provider = 'grow' and test_run_id = ? and story = ?
            """)
        .params(RUN.value(), SPAN)
        .query()
        .listOfRows()
        .forEach(row -> standing.put((String) row.get("story_key"), (String) row.get("state")));
    return standing;
  }

  @Test
  void a_capture_of_a_payment_whose_creation_never_met_the_story_is_left_alone() {
    Held before = aHeldPayment();
    theSpanningStory();

    assertThat(parse(post(SETTLE, settlement(before)))).containsEntry("status", 1);
    assertThat(stateOf(before.transactionId())).containsEntry("normalizedStatus", "SUCCEEDED");
  }

  @Test
  void a_lookup_answers_differently_on_the_second_call_because_the_story_moved_between_them() {
    HostedProcess process = aHold();
    theLookupStory();

    Map<String, Object> first = parse(lookup(process));
    Map<String, Object> second = parse(lookup(process));

    assertThat(first).containsEntry("status", 0);
    assertThat(errorOf(first)).containsEntry("id", 813);
    assertThat(second).containsEntry("status", 1);
    assertThat(dataOf(second)).containsEntry("processId", process.processId());
  }

  @Test
  void two_payments_of_one_test_run_never_take_each_others_step_of_one_story() {
    HostedProcess first = aHold();
    HostedProcess second = aHold();
    theLookupStory();

    assertThat(parse(lookup(first))).containsEntry("status", 0);
    assertThat(parse(lookup(second))).containsEntry("status", 0);
    assertThat(parse(lookup(first))).containsEntry("status", 1);
    assertThat(parse(lookup(second))).containsEntry("status", 1);
  }

  @Test
  void concurrent_lookups_of_one_payment_take_the_declining_step_exactly_once()
      throws InterruptedException {
    HostedProcess process = aHold();
    theLookupStory();
    int callers = 8;
    CyclicBarrier together = new CyclicBarrier(callers);
    List<Callable<Integer>> attempts = new ArrayList<>();
    for (int caller = 0; caller < callers; caller++) {
      attempts.add(
          () -> {
            together.await();
            return (Integer) parse(lookup(process)).get("status");
          });
    }

    List<Future<Integer>> answers;
    try (ExecutorService callersPool = Executors.newFixedThreadPool(callers)) {
      answers = callersPool.invokeAll(attempts);
    }

    assertThat(answers.stream().map(GrowScenarioStoryIntegrationTest::valueOf))
        .filteredOn(status -> status == 0)
        .hasSize(1);
  }

  @Test
  void concurrent_lookups_of_one_payment_under_a_cyclic_story_all_answer_and_alternate()
      throws InterruptedException {
    HostedProcess process = aHold();
    theReplayingLookupStory();
    int callers = 8;
    CyclicBarrier together = new CyclicBarrier(callers);
    List<Callable<Integer>> attempts = new ArrayList<>();
    for (int caller = 0; caller < callers; caller++) {
      attempts.add(
          () -> {
            together.await();
            return (Integer) parse(lookup(process)).get("status");
          });
    }

    ExecutorService callersPool = Executors.newFixedThreadPool(callers);
    List<Future<Integer>> answers;
    try {
      answers = callersPool.invokeAll(attempts, 2, TimeUnit.MINUTES);
    } finally {
      callersPool.shutdownNow();
    }

    assertThat(answers.stream().map(GrowScenarioStoryIntegrationTest::valueOf))
        .hasSize(callers)
        .filteredOn(status -> status == 0)
        .hasSize(callers / 2);
  }

  @Test
  void a_cyclic_story_answers_one_payment_by_alternating_steps_however_often_it_is_called() {
    HostedProcess process = aHold();
    theReplayingLookupStory();

    List<Integer> answers = new ArrayList<>();
    for (int call = 0; call < 6; call++) {
      answers.add((Integer) parse(lookup(process)).get("status"));
    }

    assertThat(answers).containsExactly(0, 1, 0, 1, 0, 1);
  }

  @Test
  void a_rule_row_this_simulator_cannot_read_answers_no_body_at_all_and_never_a_json_error_body() {
    HostedProcess process = aHold();
    theLookupStory();
    jdbc.sql("update simulator_scenario_rule set match_header = ? where test_run_id = ?")
        .params("not json at all", RUN.value())
        .update();

    ResponseEntity<byte[]> answered = whateverAnswers(PROCESS_INFO, lookupForm(process));

    assertThat(answered.getStatusCode().value()).isEqualTo(500);
    assertThat(answered.getHeaders().getContentType())
        .as(
            "grow publishes no 5xx anywhere and every documented answer is HTTP 200, so the one"
                + " shape no client of grow can misread is none at all; this simulator's own"
                + " english prose under text/plain was a content type grow's platform never sends")
        .isNull();
    assertThat(answered.getBody()).isNull();

    ResponseEntity<List<Map<String, Object>>> trail =
        http.get()
            .uri("/__simulator/grow/audit?testRunId=" + TestRunId.UNKNOWN.value())
            .accept(MediaType.APPLICATION_JSON)
            .retrieve()
            .onStatus(status -> true, (request, ignored) -> {})
            .toEntity(new ParameterizedTypeReference<List<Map<String, Object>>>() {});

    assertThat(theFailureRowIn(trail.getBody()))
        .as(
            "the wire says nothing at all here, so everything the prose used to explain has to be"
                + " on the trail, and already there when the client reads it back; an error"
                + " dispatch reads no run out of a body nothing parsed, so the row is filed under"
                + " the reserved run")
        .isNotNull()
        .containsEntry("method", "POST")
        .containsEntry("status", 500)
        .containsEntry("origin", "PROVIDER_API");
    assertThat(theFailureRowIn(trail.getBody()).get("sanitizedBody").toString())
        .as(
            "the chain names what broke, and the span a reader quoted the stored value back in is"
                + " withheld before the row is stored: the trail travels in bundles and a"
                + " diagnostic of this build names the field and never the value")
        .contains(TheCausesOfAFailure.THE_CAUSES)
        .contains("StreamReadException")
        .contains(Redaction.REDACTED)
        .doesNotContain("not json at all")
        .doesNotContain("com.fasterxml")
        .doesNotContain("tools.jackson");
  }

  private static @Nullable Map<String, Object> theFailureRowIn(
      @Nullable List<Map<String, Object>> rows) {
    for (Map<String, Object> row : rows == null ? List.<Map<String, Object>>of() : rows) {
      if (PROCESS_INFO.equals(row.get("path")) && Integer.valueOf(500).equals(row.get("status"))) {
        return row;
      }
    }
    return null;
  }

  @Test
  void resetting_the_test_run_sends_the_story_back_to_its_start() {
    HostedProcess process = aHold();
    theLookupStory();
    assertThat(parse(lookup(process))).containsEntry("status", 0);
    assertThat(parse(lookup(process))).containsEntry("status", 1);

    reset();
    configureTheMerchantAccount();
    HostedProcess afterReset = aHold();
    theLookupStory();

    assertThat(parse(lookup(afterReset))).containsEntry("status", 0);
  }

  @Test
  void a_created_step_reads_back_carrying_the_story_and_the_two_states_it_was_written_with() {
    theLookupStory();

    ResponseEntity<List<Map<String, Object>>> listed =
        http.get()
            .uri("/__simulator/grow/scenarios?testRunId=" + RUN.value())
            .retrieve()
            .toEntity(new ParameterizedTypeReference<List<Map<String, Object>>>() {});

    assertThat(listed.getBody())
        .extracting("story", "whenState", "thenState")
        .containsExactly(
            Tuple.tuple(STORY, LOOKED_UP_ONCE, null),
            Tuple.tuple(STORY, ScenarioStory.INITIAL_STATE, LOOKED_UP_ONCE));
  }

  private void theSpanningStory() {
    createScenario(
        Map.of(
            "testRunId",
            RUN.value(),
            "story",
            SPAN,
            "whenState",
            ScenarioStory.INITIAL_STATE,
            "thenState",
            PREAUTHORIZED,
            "match",
            Map.of("operation", "createPaymentProcess"),
            "action",
            Map.of("type", "FIXED_SUCCESS")));
    createScenario(
        Map.of(
            "testRunId",
            RUN.value(),
            "story",
            SPAN,
            "whenState",
            PREAUTHORIZED,
            "thenState",
            CAPTURED,
            "match",
            Map.of("operation", "settleSuspendedTransaction"),
            "action",
            Map.of("type", "TERMINAL_FAILURE", "providerCode", "809")));
  }

  private Held aHeldPayment() {
    HostedProcess process = aHold();
    http.post()
        .uri("/__simulator/grow/webhooks")
        .contentType(MediaType.APPLICATION_JSON)
        .body(Map.of("testRunId", RUN.value(), "providerObjectId", process.processId()))
        .retrieve()
        .toBodilessEntity();
    Map<String, Object> looked = dataOf(parse(lookup(process)));
    Map<String, Object> transaction = firstTransactionOf(looked);
    return new Held(
        process.processId(),
        String.valueOf(transaction.get("transactionId")),
        String.valueOf(transaction.get("transactionToken")));
  }

  @SuppressWarnings("unchecked")
  private static Map<String, Object> firstTransactionOf(Map<String, Object> processData) {
    return ((List<Map<String, Object>>) processData.get("transactions")).get(0);
  }

  private static MultiValueMap<String, Object> settlement(Held held) {
    MultiValueMap<String, Object> form = new LinkedMultiValueMap<>();
    form.add("userId", USER_ID);
    form.add("transactionId", held.transactionId());
    form.add("transactionToken", held.transactionToken());
    form.add("sum", HELD);
    return form;
  }

  private Map<String, Object> stateOf(String transactionId) {
    return http.get()
        .uri("/__simulator/grow/state/" + transactionId + "?testRunId=" + RUN.value())
        .retrieve()
        .toEntity(new ParameterizedTypeReference<Map<String, Object>>() {})
        .getBody();
  }

  private void theLookupStory() {
    createScenario(
        Map.of(
            "testRunId",
            RUN.value(),
            "story",
            STORY,
            "whenState",
            ScenarioStory.INITIAL_STATE,
            "thenState",
            LOOKED_UP_ONCE,
            "match",
            Map.of("operation", "getPaymentProcessInfo"),
            "action",
            Map.of("type", "TERMINAL_FAILURE", "providerCode", "813")));
    createScenario(
        Map.of(
            "testRunId", RUN.value(),
            "story", STORY,
            "whenState", LOOKED_UP_ONCE,
            "match", Map.of("operation", "getPaymentProcessInfo"),
            "action", Map.of("type", "FIXED_SUCCESS")));
  }

  private void theReplayingLookupStory() {
    createScenario(
        Map.of(
            "testRunId",
            RUN.value(),
            "story",
            REPLAYED,
            "whenState",
            ScenarioStory.INITIAL_STATE,
            "thenState",
            LOOKED_UP_ONCE,
            "match",
            Map.of("operation", "getPaymentProcessInfo"),
            "action",
            Map.of("type", "TERMINAL_FAILURE", "providerCode", "813")));
    createScenario(
        Map.of(
            "testRunId",
            RUN.value(),
            "story",
            REPLAYED,
            "whenState",
            LOOKED_UP_ONCE,
            "thenState",
            ScenarioStory.INITIAL_STATE,
            "match",
            Map.of("operation", "getPaymentProcessInfo"),
            "action",
            Map.of("type", "FIXED_SUCCESS")));
  }

  private void createScenario(Map<String, Object> body) {
    assertThat(
            http.post()
                .uri("/__simulator/grow/scenarios")
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .onStatus(status -> true, (request, ignored) -> {})
                .toBodilessEntity()
                .getStatusCode()
                .value())
        .isEqualTo(201);
  }

  private HostedProcess aHold() {
    Map<String, Object> created = parse(post(CREATE, hold()));
    assertThat(created).containsEntry("status", 1);
    Map<String, Object> data = dataOf(created);
    return new HostedProcess(
        String.valueOf(data.get("processId")), String.valueOf(data.get("processToken")));
  }

  private ResponseEntity<byte[]> lookup(HostedProcess process) {
    return post(PROCESS_INFO, lookupForm(process));
  }

  private static MultiValueMap<String, Object> lookupForm(HostedProcess process) {
    MultiValueMap<String, Object> form = new LinkedMultiValueMap<>();
    form.add("pageCode", PAGE_CODE);
    form.add("processId", process.processId());
    form.add("processToken", process.processToken());
    return form;
  }

  private ResponseEntity<byte[]> whateverAnswers(String path, MultiValueMap<String, Object> form) {
    return http.post()
        .uri(path)
        .contentType(MediaType.MULTIPART_FORM_DATA)
        .body(form)
        .retrieve()
        .onStatus(status -> true, (request, ignored) -> {})
        .toEntity(byte[].class);
  }

  private ResponseEntity<byte[]> post(String path, MultiValueMap<String, Object> form) {
    return http.post()
        .uri(path)
        .contentType(MediaType.MULTIPART_FORM_DATA)
        .body(form)
        .retrieve()
        .toEntity(byte[].class);
  }

  private void reset() {
    http.post()
        .uri("/__simulator/grow/reset")
        .contentType(MediaType.APPLICATION_JSON)
        .body(Map.of("testRunId", RUN.value(), "scope", "TEST_RUN"))
        .retrieve()
        .toBodilessEntity();
  }

  private Map<String, Object> parse(ResponseEntity<byte[]> response) {
    return json.readValue(
        GrowWireText.readable(new String(response.getBody(), UTF_8)),
        new TypeReference<Map<String, Object>>() {});
  }

  @SuppressWarnings("unchecked")
  private static Map<String, Object> dataOf(Map<String, Object> body) {
    return (Map<String, Object>) body.get("data");
  }

  @SuppressWarnings("unchecked")
  private static Map<String, Object> errorOf(Map<String, Object> body) {
    return (Map<String, Object>) body.get("err");
  }

  private static int valueOf(Future<Integer> answer) {
    try {
      return answer.get();
    } catch (InterruptedException | ExecutionException failed) {
      throw new IllegalStateException("a concurrent lookup did not answer", failed);
    }
  }

  private static MultiValueMap<String, Object> hold() {
    MultiValueMap<String, Object> form = new LinkedMultiValueMap<>();
    form.add("pageCode", PAGE_CODE);
    form.add("userId", USER_ID);
    form.add("chargeType", "2");
    form.add("sum", HELD);
    form.add("description", "A hold");
    form.add("successUrl", "https://merchant.example/thanks");
    form.add("cancelUrl", "https://merchant.example/cancel");
    form.add("pageField[fullName]", "Israel Israeli");
    form.add("pageField[phone]", "0505503144");
    form.add("cField2", "intent-99");
    return form;
  }
}
