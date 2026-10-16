package dev.marcinromanowski.pspsim.provider.grow;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.springframework.dao.DuplicateKeyException;

class GrowGroupChargesTest {

  @Test
  void a_group_another_request_charged_first_is_refused_as_already_charged() {
    assertThat(
            GrowGroupCharges.theGroupOrRethrown(
                new DuplicateKeyException(
                    "ERROR: duplicate key value violates unique constraint"
                        + " \"grow_stored_card_charge_one_group_per_charged_payment\"")))
        .isEqualTo(GrowTransactions.Charged.GROUP_ALREADY_CHARGED);
  }

  @Test
  void any_other_unique_violation_is_thrown_as_it_was_raised() {
    DuplicateKeyException another =
        new DuplicateKeyException(
            "ERROR: duplicate key value violates unique constraint"
                + " \"grow_stored_card_charge_one_submission_per_transaction\"");

    assertThatThrownBy(() -> GrowGroupCharges.theGroupOrRethrown(another)).isSameAs(another);
  }

  @Test
  void a_constraint_that_merely_begins_with_the_group_name_is_not_the_group() {
    DuplicateKeyException longer =
        new DuplicateKeyException(
            "violates unique constraint"
                + " \"grow_stored_card_charge_one_group_per_charged_payment_and_more\"");

    assertThatThrownBy(() -> GrowGroupCharges.theGroupOrRethrown(longer)).isSameAs(longer);
  }
}
