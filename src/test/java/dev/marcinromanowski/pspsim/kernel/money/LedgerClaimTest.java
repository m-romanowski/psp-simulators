package dev.marcinromanowski.pspsim.kernel.money;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class LedgerClaimTest {

  private final List<String> whatTheStoreWasAskedToDo = new ArrayList<>();

  @Test
  void records_the_change_against_the_object_once_the_claim_is_won() {
    LedgerClaim claimed =
        LedgerClaim.of(() -> stored(true), () -> claimAffecting(1), this::noteTheChange);

    assertThat(claimed).isEqualTo(LedgerClaim.APPLIED);
    assertThat(whatTheStoreWasAskedToDo).containsExactly("stored", "claim", "noted");
  }

  @Test
  void leaves_the_object_as_it_found_it_when_another_request_took_the_claim_first() {
    LedgerClaim claimed =
        LedgerClaim.of(() -> stored(true), () -> claimAffecting(0), this::noteTheChange);

    assertThat(claimed).isEqualTo(LedgerClaim.CLAIM_TAKEN);
    assertThat(whatTheStoreWasAskedToDo).containsExactly("stored", "claim");
  }

  @Test
  void refuses_to_claim_anything_against_an_object_the_store_no_longer_holds() {
    LedgerClaim claimed =
        LedgerClaim.of(() -> stored(false), () -> claimAffecting(1), this::noteTheChange);

    assertThat(claimed).isEqualTo(LedgerClaim.OBJECT_GONE);
    assertThat(whatTheStoreWasAskedToDo).containsExactly("stored");
  }

  private boolean stored(boolean answer) {
    whatTheStoreWasAskedToDo.add("stored");
    return answer;
  }

  private int claimAffecting(int rows) {
    whatTheStoreWasAskedToDo.add("claim");
    return rows;
  }

  private void noteTheChange() {
    whatTheStoreWasAskedToDo.add("noted");
  }
}
