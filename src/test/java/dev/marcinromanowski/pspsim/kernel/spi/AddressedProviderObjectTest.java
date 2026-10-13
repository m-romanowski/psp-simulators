package dev.marcinromanowski.pspsim.kernel.spi;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.marcinromanowski.pspsim.kernel.NormalizedStatus;
import dev.marcinromanowski.pspsim.kernel.ObjectOrigin;
import dev.marcinromanowski.pspsim.kernel.money.Ledger;
import dev.marcinromanowski.pspsim.kernel.testrun.TestRunId;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class AddressedProviderObjectTest {

  private static final TestRunId RUN = TestRunId.of("addressing");

  private static final String CREATED_UNDER = "pay_1";

  private static final String MINTED_BY_A_CAPTURE = "pay_2";

  private static final String MINTED_BY_A_REFUND = "pay_3";

  private static final String A_SECOND_OBJECT = "pay_9";

  @Test
  void a_read_by_the_canonical_member_answers_the_object_and_names_that_member() {
    ProviderDouble module = new ProviderDouble().holding(CREATED_UNDER);

    AddressedProviderObject addressed =
        AddressedProviderObject.read(module, RUN, CREATED_UNDER).orElseThrow();

    assertThat(addressed.addressedBy()).isEqualTo(CREATED_UNDER);
    assertThat(addressed.canonical()).isEqualTo(CREATED_UNDER);
    assertThat(addressed.answersTo()).containsExactly(CREATED_UNDER);
    assertThat(addressed.addressedByItsCanonicalMember()).isTrue();
  }

  @Test
  void a_read_by_an_identifier_a_later_operation_minted_answers_the_same_object() {
    ProviderDouble module =
        new ProviderDouble()
            .holding(CREATED_UNDER)
            .mintingAFurtherIdentifierOn(CREATED_UNDER, MINTED_BY_A_CAPTURE);

    AddressedProviderObject addressed =
        AddressedProviderObject.read(module, RUN, MINTED_BY_A_CAPTURE).orElseThrow();

    assertThat(addressed.canonical()).isEqualTo(CREATED_UNDER);
    assertThat(addressed.object().providerObjectId()).isEqualTo(CREATED_UNDER);
    assertThat(addressed.answersTo()).containsExactly(CREATED_UNDER, MINTED_BY_A_CAPTURE);
  }

  @Test
  void a_read_by_a_later_member_says_it_was_addressed_by_that_member_and_not_by_the_canonical() {
    ProviderDouble module =
        new ProviderDouble()
            .holding(CREATED_UNDER)
            .mintingAFurtherIdentifierOn(CREATED_UNDER, MINTED_BY_A_CAPTURE)
            .mintingAFurtherIdentifierOn(MINTED_BY_A_CAPTURE, MINTED_BY_A_REFUND);

    AddressedProviderObject addressed =
        AddressedProviderObject.read(module, RUN, MINTED_BY_A_REFUND).orElseThrow();

    assertThat(addressed.addressedBy()).isEqualTo(MINTED_BY_A_REFUND);
    assertThat(addressed.addressedByItsCanonicalMember()).isFalse();
    assertThat(addressed.canonical()).isEqualTo(CREATED_UNDER);
  }

  @Test
  void an_identifier_no_object_of_the_run_answers_to_is_still_refused() {
    ProviderDouble module =
        new ProviderDouble()
            .holding(CREATED_UNDER)
            .mintingAFurtherIdentifierOn(CREATED_UNDER, MINTED_BY_A_CAPTURE);

    assertThat(AddressedProviderObject.read(module, RUN, MINTED_BY_A_REFUND))
        .isEqualTo(Optional.empty());
  }

  @Test
  void one_identifier_never_reaches_two_objects_of_one_run() {
    ProviderDouble module =
        new ProviderDouble()
            .holding(CREATED_UNDER)
            .mintingAFurtherIdentifierOn(CREATED_UNDER, MINTED_BY_A_CAPTURE)
            .holding(A_SECOND_OBJECT);

    assertThat(
            AddressedProviderObject.read(module, RUN, MINTED_BY_A_CAPTURE)
                .orElseThrow()
                .canonical())
        .isEqualTo(CREATED_UNDER);
    assertThat(AddressedProviderObject.read(module, RUN, A_SECOND_OBJECT).orElseThrow().canonical())
        .isEqualTo(A_SECOND_OBJECT);
    ProviderDouble alreadyAnsweringToTheMintedIdentifier =
        new ProviderDouble()
            .holding(CREATED_UNDER)
            .mintingAFurtherIdentifierOn(CREATED_UNDER, MINTED_BY_A_CAPTURE);

    assertThatThrownBy(() -> alreadyAnsweringToTheMintedIdentifier.holding(MINTED_BY_A_CAPTURE))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("one identifier to two objects");
  }

  @Test
  void a_listing_addresses_every_object_it_lists_by_the_identifier_it_was_created_under() {
    AddressedProviderObject addressed =
        AddressedProviderObject.canonically(
            anObject(
                ProviderObjectIdentity.of(CREATED_UNDER).answeringAlsoTo(MINTED_BY_A_CAPTURE)));

    assertThat(addressed.addressedBy()).isEqualTo(CREATED_UNDER);
    assertThat(addressed.addressedByItsCanonicalMember()).isTrue();
  }

  @Test
  void refuses_an_answer_addressed_by_an_identifier_the_object_does_not_admit_answering_to() {
    ProviderObjectState object = anObject(ProviderObjectIdentity.of(CREATED_UNDER));

    assertThatThrownBy(() -> new AddressedProviderObject(MINTED_BY_A_CAPTURE, object))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("does not list that identifier");
  }

  private static ProviderObjectState anObject(ProviderObjectIdentity identity) {
    return new ProviderObjectState(
        identity,
        AKindOfObjectATestNames.ONE,
        "1",
        NormalizedStatus.SUCCEEDED,
        null,
        null,
        Ledger.empty(),
        false,
        false,
        false,
        Map.of(),
        ObjectOrigin.PROVIDER_API,
        Instant.EPOCH);
  }
}
