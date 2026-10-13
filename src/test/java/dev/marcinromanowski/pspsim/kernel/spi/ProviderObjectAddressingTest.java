package dev.marcinromanowski.pspsim.kernel.spi;

import static org.assertj.core.api.Assertions.assertThat;

import dev.marcinromanowski.pspsim.kernel.ObjectOrigin;
import dev.marcinromanowski.pspsim.kernel.audit.AuditDirection;
import dev.marcinromanowski.pspsim.kernel.audit.AuditQuery;
import dev.marcinromanowski.pspsim.kernel.testrun.TestRunId;
import java.time.Instant;
import java.util.List;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

class ProviderObjectAddressingTest {

  private static final TestRunId RUN = TestRunId.of("addressing");

  private static final String CREATED_UNDER = "pay_1";

  private static final String MINTED_BY_A_CAPTURE = "pay_2";

  private static final String NO_OBJECT_ANSWERS_TO_IT = "pay_404";

  private static final String NARROWED_TO_ONE_EXCHANGE = "aud_one_exchange";

  @Test
  void an_object_that_kept_one_identifier_narrows_the_trail_to_that_one_identifier() {
    ProviderDouble module = new ProviderDouble().holding(CREATED_UNDER);

    assertThat(
            ProviderObjectAddressing.everyIdentifierRowsOfThatObjectCarry(
                module, RUN, CREATED_UNDER))
        .containsExactly(CREATED_UNDER);
  }

  @Test
  void an_object_addressed_by_a_later_identifier_narrows_the_trail_to_every_one_it_answers_to() {
    ProviderDouble module =
        new ProviderDouble()
            .holding(CREATED_UNDER)
            .mintingAFurtherIdentifierOn(CREATED_UNDER, MINTED_BY_A_CAPTURE);

    assertThat(
            ProviderObjectAddressing.everyIdentifierRowsOfThatObjectCarry(
                module, RUN, MINTED_BY_A_CAPTURE))
        .containsExactly(CREATED_UNDER, MINTED_BY_A_CAPTURE);
  }

  @Test
  void an_identifier_this_run_holds_no_object_under_still_narrows_the_trail_to_itself() {
    ProviderDouble module = new ProviderDouble().holding(CREATED_UNDER);

    assertThat(
            ProviderObjectAddressing.everyIdentifierRowsOfThatObjectCarry(
                module, RUN, NO_OBJECT_ANSWERS_TO_IT))
        .containsExactly(NO_OBJECT_ANSWERS_TO_IT);
  }

  @Test
  void an_identifier_given_with_surrounding_whitespace_narrows_by_what_the_rows_carry() {
    ProviderDouble module = new ProviderDouble().holding(CREATED_UNDER);

    assertThat(
            ProviderObjectAddressing.everyIdentifierRowsOfThatObjectCarry(
                module, RUN, " " + CREATED_UNDER + " "))
        .containsExactly(CREATED_UNDER);
  }

  @Test
  void no_identifier_at_all_narrows_the_trail_by_no_object() {
    ProviderDouble module = new ProviderDouble().holding(CREATED_UNDER);

    assertThat(ProviderObjectAddressing.everyIdentifierRowsOfThatObjectCarry(module, RUN, null))
        .isEmpty();
    assertThat(ProviderObjectAddressing.everyIdentifierRowsOfThatObjectCarry(module, RUN, " "))
        .isEmpty();
  }

  @Test
  void a_trail_asked_for_one_object_is_read_over_every_identifier_that_object_answers_to() {
    ProviderDouble module =
        new ProviderDouble()
            .holding(CREATED_UNDER)
            .mintingAFurtherIdentifierOn(CREATED_UNDER, MINTED_BY_A_CAPTURE);

    AuditQuery widened =
        ProviderObjectAddressing.overEveryIdentifierOfTheSameObject(
            module, queryNarrowedTo(List.of(CREATED_UNDER)));

    assertThat(widened.providerObjectIds()).containsExactly(CREATED_UNDER, MINTED_BY_A_CAPTURE);
  }

  @Test
  void a_trail_asked_for_an_object_that_kept_one_identifier_is_read_exactly_as_it_was_asked() {
    ProviderDouble module = new ProviderDouble().holding(CREATED_UNDER);
    AuditQuery asked = queryNarrowedTo(List.of(CREATED_UNDER));

    assertThat(ProviderObjectAddressing.overEveryIdentifierOfTheSameObject(module, asked))
        .isSameAs(asked);
  }

  @Test
  void a_trail_narrowed_by_no_object_is_read_exactly_as_it_was_asked() {
    ProviderDouble module =
        new ProviderDouble()
            .holding(CREATED_UNDER)
            .mintingAFurtherIdentifierOn(CREATED_UNDER, MINTED_BY_A_CAPTURE);
    AuditQuery asked = queryNarrowedTo(List.of());

    assertThat(ProviderObjectAddressing.overEveryIdentifierOfTheSameObject(module, asked))
        .isSameAs(asked);
  }

  @Test
  void a_trail_already_narrowed_over_a_set_of_identifiers_is_not_widened_again() {
    ProviderDouble module =
        new ProviderDouble()
            .holding(CREATED_UNDER)
            .mintingAFurtherIdentifierOn(CREATED_UNDER, MINTED_BY_A_CAPTURE);
    AuditQuery asked = queryNarrowedTo(List.of(CREATED_UNDER, NO_OBJECT_ANSWERS_TO_IT));

    assertThat(ProviderObjectAddressing.overEveryIdentifierOfTheSameObject(module, asked))
        .isSameAs(asked);
  }

  @Test
  void widening_a_trail_to_a_whole_object_keeps_every_other_narrowing_it_was_asked_with() {
    ProviderDouble module =
        new ProviderDouble()
            .holding(CREATED_UNDER)
            .mintingAFurtherIdentifierOn(CREATED_UNDER, MINTED_BY_A_CAPTURE);

    AuditQuery widened =
        ProviderObjectAddressing.overEveryIdentifierOfTheSameObject(
            module, queryNarrowedTo(List.of(CREATED_UNDER)));

    assertThat(widened.testRunId()).isEqualTo(RUN);
    assertThat(widened.auditId()).isEqualTo(NARROWED_TO_ONE_EXCHANGE);
    assertThat(widened.origin()).isEqualTo(ObjectOrigin.PROVIDER_API);
    assertThat(widened.direction()).isEqualTo(AuditDirection.INBOUND_REQUEST);
    assertThat(widened.method()).isEqualTo("POST");
    assertThat(widened.httpStatus()).isEqualTo(200);
    assertThat(widened.scenarioId()).isEqualTo("scn_1");
    assertThat(widened.from()).isEqualTo(Instant.EPOCH);
    assertThat(widened.until()).isEqualTo(Instant.EPOCH.plusSeconds(60));
    assertThat(widened.after()).isEqualTo("aud_1");
    assertThat(widened.limit()).isEqualTo(7);
  }

  private static AuditQuery queryNarrowedTo(@Nullable List<String> providerObjectIds) {
    return new AuditQuery(
        RUN,
        NARROWED_TO_ONE_EXCHANGE,
        null,
        ObjectOrigin.PROVIDER_API,
        AuditDirection.INBOUND_REQUEST,
        "POST",
        200,
        providerObjectIds == null ? List.of() : providerObjectIds,
        "scn_1",
        Instant.EPOCH,
        Instant.EPOCH.plusSeconds(60),
        "aud_1",
        7);
  }
}
