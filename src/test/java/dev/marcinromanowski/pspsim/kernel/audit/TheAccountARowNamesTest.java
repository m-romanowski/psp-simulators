package dev.marcinromanowski.pspsim.kernel.audit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.junit.jupiter.api.Test;

class TheAccountARowNamesTest {

  private static final String MERCHANT = "merchant-1";

  @Test
  void an_exchange_whose_credentials_reached_one_account_names_that_account() {
    assertThat(TheAccountARowNames.amongTheAccountsTheCredentialsMatched(List.of(MERCHANT)))
        .isEqualTo(MERCHANT);
  }

  @Test
  void an_exchange_whose_credentials_reached_no_account_names_none_rather_than_a_placeholder() {
    assertThat(TheAccountARowNames.amongTheAccountsTheCredentialsMatched(List.of())).isNull();
  }

  @Test
  void an_exchange_whose_credentials_reached_several_accounts_names_none_rather_than_the_first() {
    assertThat(TheAccountARowNames.amongTheAccountsTheCredentialsMatched(List.of(MERCHANT, "b")))
        .isNull();
  }

  @Test
  void a_blank_identity_names_no_account_so_no_row_can_carry_one_a_filter_could_never_match() {
    assertThat(TheAccountARowNames.theIdentityOrNone(null)).isNull();
    assertThat(TheAccountARowNames.theIdentityOrNone("")).isNull();
    assertThat(TheAccountARowNames.theIdentityOrNone("   ")).isNull();
  }

  @Test
  void an_identity_a_tester_typed_is_stored_without_the_spaces_around_it() {
    assertThat(TheAccountARowNames.theIdentityOrNone("  " + MERCHANT + " ")).isEqualTo(MERCHANT);
  }

  @Test
  void a_blank_narrowing_is_refused_because_it_would_keep_the_rows_naming_no_account_at_all() {
    assertThatThrownBy(() -> TheAccountARowNames.refuseAnIdentityNoRowCouldCarry("accountId", " "))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage(
            "accountId is blank, and a trail narrowed to a blank account would keep the rows that"
                + " name no account at all rather than the rows of one account");
  }

  @Test
  void an_identity_wider_than_a_row_holds_names_an_account_no_row_could_carry() {
    assertThatThrownBy(
            () -> TheAccountARowNames.refuseAnIdentityNoRowCouldCarry("accountId", "a".repeat(129)))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage(
            "accountId must be at most 128 characters, which is the widest identity a row holds,"
                + " so a longer one names an account no row could carry");
  }

  @Test
  void the_widest_identity_a_row_holds_is_itself_a_narrowing_the_trail_accepts() {
    assertThatCode(
            () -> TheAccountARowNames.refuseAnIdentityNoRowCouldCarry("accountId", "a".repeat(128)))
        .doesNotThrowAnyException();
    assertThat(TheAccountARowNames.LONGEST_IDENTITY_A_ROW_HOLDS).isEqualTo(128);
  }
}
