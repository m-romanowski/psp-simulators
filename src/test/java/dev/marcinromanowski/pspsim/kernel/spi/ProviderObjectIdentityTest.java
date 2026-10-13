package dev.marcinromanowski.pspsim.kernel.spi;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class ProviderObjectIdentityTest {

  private static final String CREATED_UNDER = "pay_1";

  private static final String MINTED_BY_A_CAPTURE = "pay_2";

  @Test
  void an_object_created_under_one_identifier_answers_to_that_identifier_and_no_other() {
    ProviderObjectIdentity identity = ProviderObjectIdentity.of(CREATED_UNDER);

    assertThat(identity.canonical()).isEqualTo(CREATED_UNDER);
    assertThat(identity.members()).containsExactly(CREATED_UNDER);
    assertThat(identity.answersTo(CREATED_UNDER)).isTrue();
    assertThat(identity.answersTo(MINTED_BY_A_CAPTURE)).isFalse();
    assertThat(identity.keepsOneIdentifierForLife()).isTrue();
    assertThat(identity.isTheCanonicalMember(CREATED_UNDER)).isTrue();
  }

  @Test
  void an_object_that_took_a_second_identifier_answers_to_both_and_keeps_the_first_canonical() {
    ProviderObjectIdentity widened =
        ProviderObjectIdentity.of(CREATED_UNDER).answeringAlsoTo(MINTED_BY_A_CAPTURE);

    assertThat(widened.canonical()).isEqualTo(CREATED_UNDER);
    assertThat(widened.members()).containsExactly(CREATED_UNDER, MINTED_BY_A_CAPTURE);
    assertThat(widened.answersTo(CREATED_UNDER)).isTrue();
    assertThat(widened.answersTo(MINTED_BY_A_CAPTURE)).isTrue();
    assertThat(widened.keepsOneIdentifierForLife()).isFalse();
    assertThat(widened.isTheCanonicalMember(MINTED_BY_A_CAPTURE)).isFalse();
  }

  @Test
  void refuses_a_canonical_identifier_no_lookup_could_be_given() {
    assertThatThrownBy(() -> ProviderObjectIdentity.of(" "))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("canonical identifier addresses nothing");
  }

  @Test
  void refuses_an_identity_that_lists_no_identifier_at_all() {
    assertThatThrownBy(() -> new ProviderObjectIdentity(CREATED_UNDER, List.of()))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("lists none");
  }

  @Test
  void refuses_an_identity_whose_first_member_is_not_the_canonical_one() {
    List<String> theCanonicalOneSecond = List.of(MINTED_BY_A_CAPTURE, CREATED_UNDER);

    assertThatThrownBy(() -> new ProviderObjectIdentity(CREATED_UNDER, theCanonicalOneSecond))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("does not begin with its canonical identifier");
  }

  @Test
  void refuses_a_later_member_a_request_carrying_no_identifier_would_resolve_to() {
    List<String> oneBlankMember = List.of(CREATED_UNDER, " ");

    assertThatThrownBy(() -> new ProviderObjectIdentity(CREATED_UNDER, oneBlankMember))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("member 1 is blank");
  }

  @Test
  void refuses_an_identity_that_counts_one_identifier_twice() {
    List<String> theCanonicalOneTwice = List.of(CREATED_UNDER, MINTED_BY_A_CAPTURE, CREATED_UNDER);

    assertThatThrownBy(() -> new ProviderObjectIdentity(CREATED_UNDER, theCanonicalOneTwice))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("member 2 repeats an earlier member");
  }

  @Test
  void a_member_list_handed_over_and_then_changed_does_not_change_the_identity() {
    List<String> handedOver = new ArrayList<>(List.of(CREATED_UNDER));
    ProviderObjectIdentity identity = new ProviderObjectIdentity(CREATED_UNDER, handedOver);

    handedOver.add(MINTED_BY_A_CAPTURE);

    assertThat(identity.members()).containsExactly(CREATED_UNDER);
  }
}
