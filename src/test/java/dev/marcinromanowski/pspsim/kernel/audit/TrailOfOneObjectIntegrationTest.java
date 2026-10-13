package dev.marcinromanowski.pspsim.kernel.audit;

import static org.assertj.core.api.Assertions.assertThat;

import dev.marcinromanowski.pspsim.PostgresBackedTest;
import dev.marcinromanowski.pspsim.kernel.ObjectOrigin;
import dev.marcinromanowski.pspsim.kernel.ProviderId;
import dev.marcinromanowski.pspsim.kernel.testrun.TestRunId;
import dev.marcinromanowski.pspsim.kernel.testrun.TestRunResolutionRule;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class TrailOfOneObjectIntegrationTest extends PostgresBackedTest {

  private static final ProviderId PROVIDER = ProviderId.of("grow");

  private static final TestRunId RUN = TestRunId.of("trail-of-one-object");

  private static final Instant AT = Instant.parse("2026-09-07T12:00:00Z");

  private static final String CREATED_UNDER = "pay_1";

  private static final String MINTED_BY_A_CAPTURE = "pay_2";

  private static final String ANOTHER_OBJECT = "pay_9";

  @Autowired private AuditTrail trail;

  @Autowired private AuditReads reads;

  @BeforeEach
  void aTrailWhoseRowsCarryThreeIdentifiers() {
    trail.clear(PROVIDER, RUN);
    trail.record(rowOf("aud_1", CREATED_UNDER));
    trail.record(rowOf("aud_2", MINTED_BY_A_CAPTURE));
    trail.record(rowOf("aud_3", ANOTHER_OBJECT));
    trail.record(rowOf("aud_4", null));
  }

  @Test
  void a_trail_read_over_a_set_of_identifiers_holds_the_rows_carrying_any_member_of_it() {
    assertThat(auditIdsOf(List.of(CREATED_UNDER, MINTED_BY_A_CAPTURE)))
        .containsExactly("aud_1", "aud_2");
  }

  @Test
  void a_trail_read_over_one_identifier_holds_only_the_rows_carrying_that_one() {
    assertThat(auditIdsOf(List.of(CREATED_UNDER))).containsExactly("aud_1");
    assertThat(auditIdsOf(List.of(MINTED_BY_A_CAPTURE))).containsExactly("aud_2");
  }

  @Test
  void a_trail_read_over_no_identifier_at_all_is_narrowed_by_no_object() {
    assertThat(auditIdsOf(List.of())).containsExactly("aud_1", "aud_2", "aud_3", "aud_4");
  }

  @Test
  void a_trail_read_over_an_identifier_no_row_carries_holds_nothing() {
    assertThat(auditIdsOf(List.of("pay_404"))).isEmpty();
  }

  @Test
  void a_trail_narrowed_to_one_audit_id_holds_that_exchange_and_not_the_others_of_its_object() {
    trail.record(rowOf("aud_5", CREATED_UNDER));

    assertThat(auditIdsOf(List.of(CREATED_UNDER))).containsExactly("aud_1", "aud_5");
    assertThat(auditIdsOfTheExchange("aud_5")).containsExactly("aud_5");
    assertThat(auditIdsOfTheExchange("aud_1")).containsExactly("aud_1");
  }

  @Test
  void a_trail_narrowed_to_the_audit_id_of_an_exchange_that_touched_no_object_still_holds_it() {
    assertThat(auditIdsOfTheExchange("aud_4")).containsExactly("aud_4");
  }

  @Test
  void a_trail_narrowed_to_an_audit_id_no_row_was_recorded_under_holds_nothing() {
    assertThat(auditIdsOfTheExchange("aud_404")).isEmpty();
  }

  private List<String> auditIdsOfTheExchange(String auditId) {
    AuditPage page =
        reads.pageOf(
            PROVIDER,
            new AuditQuery(
                RUN,
                auditId,
                null,
                null,
                null,
                null,
                null,
                List.of(),
                null,
                null,
                null,
                null,
                AuditQuery.DEFAULT_LIMIT));
    assertThat(page).isInstanceOf(AuditPage.Listed.class);
    return ((AuditPage.Listed) page).records().stream().map(AuditRecord::auditId).toList();
  }

  private List<String> auditIdsOf(List<String> providerObjectIds) {
    AuditPage page =
        reads.pageOf(
            PROVIDER,
            new AuditQuery(
                RUN,
                null,
                null,
                null,
                null,
                null,
                null,
                providerObjectIds,
                null,
                null,
                null,
                null,
                AuditQuery.DEFAULT_LIMIT));
    assertThat(page).isInstanceOf(AuditPage.Listed.class);
    return ((AuditPage.Listed) page).records().stream().map(AuditRecord::auditId).toList();
  }

  private static AuditRecord rowOf(String auditId, @Nullable String providerObjectId) {
    return new AuditRecord(
        auditId,
        RUN,
        TestRunResolutionRule.ACCOUNT_CREDENTIALS,
        PROVIDER,
        null,
        ObjectOrigin.PROVIDER_API,
        AuditDirection.OUTBOUND_RESPONSE,
        ProviderOutcome.SUCCESS,
        "POST",
        "/api/light",
        200,
        providerObjectId,
        null,
        null,
        1,
        AT,
        Map.of(),
        Map.of(),
        null);
  }
}
