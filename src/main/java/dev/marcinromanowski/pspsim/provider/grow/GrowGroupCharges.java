package dev.marcinromanowski.pspsim.provider.grow;

import org.springframework.dao.DuplicateKeyException;

final class GrowGroupCharges {

  static final String ONE_GROUP_PER_CHARGED_PAYMENT =
      "grow_stored_card_charge_one_group_per_charged_payment";

  private GrowGroupCharges() {}

  static GrowTransactions.Charged theGroupOrRethrown(DuplicateKeyException conflict) {
    if (String.valueOf(conflict.getMessage())
        .contains("\"" + ONE_GROUP_PER_CHARGED_PAYMENT + "\"")) {
      return GrowTransactions.Charged.GROUP_ALREADY_CHARGED;
    }
    throw conflict;
  }
}
