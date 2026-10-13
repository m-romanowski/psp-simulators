package dev.marcinromanowski.pspsim.kernel.spi;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.junit.jupiter.api.Test;

class HostedPageContractTest {

  private static final HostedPageContract.AnObjectItKeepsWaiting A_CARD_SESSION =
      new HostedPageContract.AnObjectItKeepsWaiting("a card session", true);

  private static final HostedPageContract.AnObjectItKeepsWaiting A_PIX =
      new HostedPageContract.AnObjectItKeepsWaiting("a Pix", false);

  @Test
  void a_module_that_keeps_an_object_waiting_and_names_it_with_nothing_is_refused_at_its_word() {
    assertThatThrownBy(() -> new HostedPageContract.AnObjectItKeepsWaiting(" ", true))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("must name that object in its own words");
  }

  @Test
  void a_module_that_keeps_no_object_waiting_and_says_nothing_instead_is_refused_at_its_word() {
    assertThatThrownBy(() -> new HostedPageContract.NoPageAPayerFinishes(""))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("must say what it has instead");
  }

  @Test
  void
      a_module_that_hosts_a_page_and_names_no_object_on_it_is_refused_rather_than_counted_as_one() {
    assertThatThrownBy(() -> new HostedPageContract.ObjectsAPayerFinishes(List.of()))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("must name at least one of them");
  }

  @Test
  void a_module_that_names_one_object_twice_is_refused_so_no_count_of_what_waits_is_one_too_many() {
    assertThatThrownBy(
            () ->
                new HostedPageContract.ObjectsAPayerFinishes(
                    List.of(
                        A_CARD_SESSION,
                        new HostedPageContract.AnObjectItKeepsWaiting("a card session", false))))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("names the object a card session twice");
  }

  @Test
  void a_module_that_keeps_two_objects_names_them_both_in_the_order_it_declared_them() {
    HostedPageContract.ObjectsAPayerFinishes keeping =
        new HostedPageContract.ObjectsAPayerFinishes(List.of(A_CARD_SESSION, A_PIX));

    assertThat(keeping.asThisProviderNamesThem()).containsExactly("a card session", "a Pix");
  }

  @Test
  void a_module_that_keeps_two_objects_counts_only_the_ones_a_payer_names_a_card_on() {
    assertThat(
            new HostedPageContract.ObjectsAPayerFinishes(List.of(A_CARD_SESSION, A_PIX))
                .howManyOfThemAPayerNamesACardOn())
        .isEqualTo(1);
    assertThat(
            new HostedPageContract.ObjectsAPayerFinishes(List.of(A_PIX))
                .howManyOfThemAPayerNamesACardOn())
        .isZero();
    assertThat(
            new HostedPageContract.ObjectsAPayerFinishes(List.of(A_CARD_SESSION))
                .howManyOfThemAPayerNamesACardOn())
        .isEqualTo(1);
  }
}
