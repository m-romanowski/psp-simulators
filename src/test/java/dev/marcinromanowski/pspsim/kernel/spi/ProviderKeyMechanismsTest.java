package dev.marcinromanowski.pspsim.kernel.spi;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.marcinromanowski.pspsim.kernel.ProviderId;
import dev.marcinromanowski.pspsim.kernel.idempotency.KeyRole;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ProviderKeyMechanismsTest {

  private static final ProviderId GROW = ProviderId.of("grow");

  private static final String UNIQUE = "transactionUniqueIdentifier";

  private static final String GROUP = "transactionGroupIdentifier";

  private static final String NO_REPLAY = "provider grow replays nothing anywhere";

  @Test
  void a_provider_with_no_idempotency_key_says_what_it_has_instead_rather_than_leaving_it_blank() {
    ProviderKeyMechanisms mechanisms =
        new ProviderKeyMechanisms(
            GROW,
            List.of(
                new DeclaredKey(
                    UNIQUE,
                    KeyRole.DUPLICATE_DETECTION_KEY,
                    KeyCarriage.REQUEST_FIELD,
                    "every submission of this business",
                    TestKeyClaims.NEVER_HELD)),
            Map.of(
                KeyRole.TRUE_IDEMPOTENCY_KEY,
                NO_REPLAY,
                KeyRole.CORRELATION_KEY,
                "provider grow correlates nothing on this contract"),
            answered -> answered);

    assertThat(mechanisms.has(KeyRole.TRUE_IDEMPOTENCY_KEY)).isFalse();
    assertThat(mechanisms.theAbsenceOf(KeyRole.TRUE_IDEMPOTENCY_KEY)).isEqualTo(NO_REPLAY);
    assertThat(mechanisms.has(KeyRole.DUPLICATE_DETECTION_KEY)).isTrue();
    assertThat(mechanisms.inTheRoleOf(KeyRole.DUPLICATE_DETECTION_KEY))
        .singleElement()
        .extracting(DeclaredKey::name)
        .isEqualTo(UNIQUE);
    assertThat(mechanisms.inTheRoleOf(KeyRole.TRUE_IDEMPOTENCY_KEY)).isEmpty();
  }

  @Test
  void a_provider_that_accounts_for_neither_having_nor_lacking_a_role_is_refused() {
    List<DeclaredKey> oneDuplicateDetectionKey =
        List.of(
            new DeclaredKey(
                UNIQUE,
                KeyRole.DUPLICATE_DETECTION_KEY,
                KeyCarriage.REQUEST_FIELD,
                "every submission of this business",
                TestKeyClaims.NEVER_HELD));
    Map<KeyRole, String> sayingNothingOfCorrelation =
        Map.of(KeyRole.TRUE_IDEMPOTENCY_KEY, NO_REPLAY);

    assertThatThrownBy(
            () ->
                new ProviderKeyMechanisms(
                    GROW,
                    oneDuplicateDetectionKey,
                    sayingNothingOfCorrelation,
                    answered -> answered))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("correlation key")
        .hasMessageContaining("neither having nor lacking");
  }

  @Test
  void a_provider_that_both_declares_a_role_and_disclaims_it_is_refused() {
    List<DeclaredKey> oneDuplicateDetectionKey =
        List.of(
            new DeclaredKey(
                UNIQUE,
                KeyRole.DUPLICATE_DETECTION_KEY,
                KeyCarriage.REQUEST_FIELD,
                "every submission of this business",
                TestKeyClaims.NEVER_HELD));
    Map<KeyRole, String> denyingTheRoleItDeclared =
        Map.of(
            KeyRole.TRUE_IDEMPOTENCY_KEY,
            NO_REPLAY,
            KeyRole.DUPLICATE_DETECTION_KEY,
            "provider grow refuses no repeat",
            KeyRole.CORRELATION_KEY,
            "provider grow correlates nothing");

    assertThatThrownBy(
            () ->
                new ProviderKeyMechanisms(
                    GROW, oneDuplicateDetectionKey, denyingTheRoleItDeclared, answered -> answered))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("at the same time declares that it has none");
  }

  @Test
  void a_provider_that_declares_one_key_twice_is_refused() {
    List<DeclaredKey> oneNameTwice =
        List.of(
            new DeclaredKey(
                UNIQUE,
                KeyRole.DUPLICATE_DETECTION_KEY,
                KeyCarriage.REQUEST_FIELD,
                "every submission of this business",
                TestKeyClaims.NEVER_HELD),
            new DeclaredKey(
                UNIQUE,
                KeyRole.CORRELATION_KEY,
                KeyCarriage.REQUEST_FIELD,
                "the transaction it names",
                TestKeyClaims.NEVER_HELD));
    Map<KeyRole, String> withoutATrueIdempotencyKey =
        Map.of(KeyRole.TRUE_IDEMPOTENCY_KEY, NO_REPLAY);

    assertThatThrownBy(
            () ->
                new ProviderKeyMechanisms(
                    GROW, oneNameTwice, withoutATrueIdempotencyKey, answered -> answered))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("declared twice");
  }

  @Test
  void a_provider_that_leaves_the_absence_of_a_role_blank_is_refused() {
    List<DeclaredKey> twoNamedKeys =
        List.of(
            new DeclaredKey(
                UNIQUE,
                KeyRole.DUPLICATE_DETECTION_KEY,
                KeyCarriage.REQUEST_FIELD,
                "every submission of this business",
                TestKeyClaims.NEVER_HELD),
            new DeclaredKey(
                GROUP,
                KeyRole.CORRELATION_KEY,
                KeyCarriage.REQUEST_FIELD,
                "the group it names",
                TestKeyClaims.NEVER_HELD));
    Map<KeyRole, String> sayingNothingInItsOwnWords = Map.of(KeyRole.TRUE_IDEMPOTENCY_KEY, "  ");

    assertThatThrownBy(
            () ->
                new ProviderKeyMechanisms(
                    GROW, twoNamedKeys, sayingNothingInItsOwnWords, answered -> answered))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("must say, in its own words");
  }

  @Test
  void a_key_with_no_scope_of_its_own_is_refused_because_the_core_owns_only_the_role() {
    assertThatThrownBy(
            () ->
                new DeclaredKey(
                    UNIQUE,
                    KeyRole.DUPLICATE_DETECTION_KEY,
                    KeyCarriage.REQUEST_FIELD,
                    "",
                    TestKeyClaims.NEVER_HELD))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("what its repeat is compared against");
  }

  @Test
  void a_key_with_no_name_is_refused_because_no_request_could_be_matched_to_it() {
    assertThatThrownBy(
            () ->
                new DeclaredKey(
                    "",
                    KeyRole.CORRELATION_KEY,
                    KeyCarriage.HEADER,
                    "the object",
                    TestKeyClaims.NEVER_HELD))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("names nothing a client sends");
  }

  @Test
  void a_request_recorded_under_a_key_the_provider_declares_nowhere_is_reported_and_not_guessed() {
    ProviderKeyMechanisms mechanisms = aGrowLikeProvider();

    assertThatThrownBy(() -> mechanisms.roleOf("somethingElse"))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("which it declares nowhere");
    assertThat(mechanisms.roleOf(UNIQUE)).isEqualTo(KeyRole.DUPLICATE_DETECTION_KEY);
    assertThat(mechanisms.key("somethingElse")).isEmpty();
  }

  @Test
  void asking_what_a_provider_has_instead_of_a_role_it_does_declare_is_a_programming_error() {
    ProviderKeyMechanisms mechanisms = aGrowLikeProvider();

    assertThatThrownBy(() -> mechanisms.theAbsenceOf(KeyRole.DUPLICATE_DETECTION_KEY))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("declares a duplicate-detection key of its own");
  }

  private static ProviderKeyMechanisms aGrowLikeProvider() {
    return new ProviderKeyMechanisms(
        GROW,
        List.of(
            new DeclaredKey(
                UNIQUE,
                KeyRole.DUPLICATE_DETECTION_KEY,
                KeyCarriage.REQUEST_FIELD,
                "every submission of this business",
                TestKeyClaims.NEVER_HELD),
            new DeclaredKey(
                GROUP,
                KeyRole.CORRELATION_KEY,
                KeyCarriage.REQUEST_FIELD,
                "the group it names",
                TestKeyClaims.NEVER_HELD)),
        Map.of(KeyRole.TRUE_IDEMPOTENCY_KEY, NO_REPLAY),
        answered -> answered);
  }
}
