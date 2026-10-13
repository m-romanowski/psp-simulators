package dev.marcinromanowski.pspsim.kernel.spi;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import org.junit.jupiter.api.Test;

class HostedSessionFactsTest {

  private static final Instant CREATED = Instant.parse("2026-09-15T10:00:00Z");

  private static final Instant EXPIRES = Instant.parse("2026-09-15T13:00:00Z");

  private static final String NO_PARTY =
      "provider worldline publishes no customer object on the hosted tokenization routes";

  private static final String NO_DEADLINE =
      "provider grow publishes no deadline on a payment process at all";

  @Test
  void a_party_this_provider_names_reads_as_the_value_it_published() {
    HostedSessionFacts.AnObjectAPayerFinishes session =
        aSessionFor(new HostedSessionFacts.Party.NamedBy("Dana Levi"), anExpiryAt(EXPIRES));

    assertThat(session.thePartyAsItReads()).isEqualTo("Dana Levi");
  }

  @Test
  void a_party_this_provider_names_is_reported_as_named() {
    HostedSessionFacts.AnObjectAPayerFinishes session =
        aSessionFor(new HostedSessionFacts.Party.NamedBy("Dana Levi"), anExpiryAt(EXPIRES));

    assertThat(session.aPartyIsNamed()).isTrue();
  }

  @Test
  void a_provider_that_names_no_party_reads_as_its_own_words_and_not_as_a_blank() {
    HostedSessionFacts.AnObjectAPayerFinishes session = aSessionWithNoParty();

    assertThat(session.thePartyAsItReads()).isEqualTo(NO_PARTY);
  }

  @Test
  void a_provider_that_names_no_party_is_reported_as_naming_none() {
    HostedSessionFacts.AnObjectAPayerFinishes session = aSessionWithNoParty();

    assertThat(session.aPartyIsNamed()).isFalse();
  }

  @Test
  void a_deadline_this_provider_publishes_reads_as_the_instant_it_expires_at() {
    HostedSessionFacts.AnObjectAPayerFinishes session = aSessionExpiringAt(EXPIRES);

    assertThat(session.theDeadlineAsItReads()).isEqualTo("2026-09-15T13:00:00Z");
  }

  @Test
  void a_deadline_this_provider_publishes_is_reported_as_a_known_time() {
    HostedSessionFacts.AnObjectAPayerFinishes session = aSessionExpiringAt(EXPIRES);

    assertThat(session.itExpiresAtAKnownTime()).isTrue();
  }

  @Test
  void a_provider_that_publishes_no_deadline_reads_as_its_own_words_and_not_as_a_blank() {
    HostedSessionFacts.AnObjectAPayerFinishes session = aSessionWithNoDeadline();

    assertThat(session.theDeadlineAsItReads()).isEqualTo(NO_DEADLINE);
  }

  @Test
  void a_provider_that_publishes_no_deadline_is_reported_as_naming_no_known_time() {
    HostedSessionFacts.AnObjectAPayerFinishes session = aSessionWithNoDeadline();

    assertThat(session.itExpiresAtAKnownTime()).isFalse();
  }

  @Test
  void a_deadline_the_clock_of_this_run_has_not_reached_has_not_passed() {
    HostedSessionFacts.AnObjectAPayerFinishes session = aSessionExpiringAt(EXPIRES);

    assertThat(session.itsDeadlineHasPassedBy(EXPIRES.minusMillis(1))).isFalse();
  }

  @Test
  void a_deadline_the_clock_of_this_run_stands_exactly_on_has_passed() {
    HostedSessionFacts.AnObjectAPayerFinishes session = aSessionExpiringAt(EXPIRES);

    assertThat(session.itsDeadlineHasPassedBy(EXPIRES)).isTrue();
  }

  @Test
  void a_deadline_the_clock_of_this_run_has_gone_beyond_has_passed() {
    HostedSessionFacts.AnObjectAPayerFinishes session = aSessionExpiringAt(EXPIRES);

    assertThat(session.itsDeadlineHasPassedBy(EXPIRES.plusSeconds(1))).isTrue();
  }

  @Test
  void a_provider_that_publishes_no_deadline_has_none_the_clock_of_this_run_can_pass() {
    HostedSessionFacts.AnObjectAPayerFinishes session = aSessionWithNoDeadline();

    assertThat(session.itsDeadlineHasPassedBy(EXPIRES.plusSeconds(1))).isFalse();
  }

  @Test
  void an_object_a_payer_finishes_that_names_no_account_is_refused() {
    assertThatThrownBy(
            () ->
                new HostedSessionFacts.AnObjectAPayerFinishes(
                    " ",
                    new HostedSessionFacts.Party.NamedBy("Dana Levi"),
                    "createPaymentProcess:chargeType=2",
                    CREATED,
                    anExpiryAt(EXPIRES)))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("belongs to one provider account");
  }

  @Test
  void an_object_a_payer_finishes_that_names_no_operation_is_refused() {
    assertThatThrownBy(
            () ->
                new HostedSessionFacts.AnObjectAPayerFinishes(
                    "merchant-1",
                    new HostedSessionFacts.Party.NamedBy("Dana Levi"),
                    " ",
                    CREATED,
                    anExpiryAt(EXPIRES)))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("was started for one operation");
  }

  @Test
  void a_party_named_by_a_provider_with_nothing_in_it_is_refused() {
    assertThatThrownBy(() -> new HostedSessionFacts.Party.NamedBy(" "))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("would print an empty cell");
  }

  @Test
  void a_provider_that_names_no_party_and_says_nothing_instead_is_refused() {
    assertThatThrownBy(() -> new HostedSessionFacts.Party.NoPartyThisProviderPublishes(" "))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("a decision this simulator did not make");
  }

  @Test
  void a_provider_that_publishes_no_deadline_and_says_nothing_instead_is_refused() {
    assertThatThrownBy(() -> new HostedSessionFacts.Deadline.NoDeadlineThisProviderPublishes(" "))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("a decision this simulator did not make");
  }

  private static HostedSessionFacts.Deadline anExpiryAt(Instant at) {
    return new HostedSessionFacts.Deadline.ItExpiresAt(at);
  }

  private static HostedSessionFacts.AnObjectAPayerFinishes aSessionWithNoParty() {
    return aSessionFor(
        new HostedSessionFacts.Party.NoPartyThisProviderPublishes(NO_PARTY), anExpiryAt(EXPIRES));
  }

  private static HostedSessionFacts.AnObjectAPayerFinishes aSessionWithNoDeadline() {
    return aSessionFor(
        new HostedSessionFacts.Party.NamedBy("Dana Levi"),
        new HostedSessionFacts.Deadline.NoDeadlineThisProviderPublishes(NO_DEADLINE));
  }

  private static HostedSessionFacts.AnObjectAPayerFinishes aSessionExpiringAt(Instant at) {
    return aSessionFor(new HostedSessionFacts.Party.NamedBy("Dana Levi"), anExpiryAt(at));
  }

  private static HostedSessionFacts.AnObjectAPayerFinishes aSessionFor(
      HostedSessionFacts.Party party, HostedSessionFacts.Deadline deadline) {
    return new HostedSessionFacts.AnObjectAPayerFinishes(
        "merchant-1", party, "createPaymentProcess:chargeType=2", CREATED, deadline);
  }
}
