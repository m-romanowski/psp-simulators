package dev.marcinromanowski.pspsim.ui;

import static org.assertj.core.api.Assertions.assertThat;

import dev.marcinromanowski.pspsim.kernel.spi.SimulatorProvider;
import java.util.List;
import org.junit.jupiter.api.Test;

class HostedPagesTest {

  @Test
  void a_module_that_declares_a_page_a_payer_finishes_is_split_from_one_that_declares_none() {
    HostedPages.Split split =
        HostedPages.of(
            List.of(
                new ProviderModuleDouble("grow").keepingWaiting("a payment process"),
                new ProviderModuleDouble("stripe")));

    assertThat(split.thatKeepAnObjectAPayerFinishes()).containsExactly("grow");
    assertThat(split.withNoPageAPayerFinishes()).containsExactly("stripe");
    assertThat(split.providers()).isEqualTo(2);
  }

  @Test
  void a_module_that_gains_a_page_a_payer_finishes_leaves_the_list_of_those_that_have_none() {
    SimulatorProvider before = new ProviderModuleDouble("azericard");
    SimulatorProvider after = new ProviderModuleDouble("azericard").keepingWaiting("a hold");

    assertThat(HostedPages.of(List.of(before)).withNoPageAPayerFinishes())
        .containsExactly("azericard");
    assertThat(HostedPages.of(List.of(before)).thatKeepAnObjectAPayerFinishes()).isEmpty();
    assertThat(HostedPages.of(List.of(after)).thatKeepAnObjectAPayerFinishes())
        .containsExactly("azericard");
    assertThat(HostedPages.of(List.of(after)).withNoPageAPayerFinishes()).isEmpty();
  }

  @Test
  void the_object_each_hosting_module_keeps_is_named_in_that_modules_own_words_after_its_name() {
    HostedPages.Split split =
        HostedPages.of(
            List.of(
                new ProviderModuleDouble("worldline")
                    .keepingWaiting("a hosted tokenization session"),
                new ProviderModuleDouble("autra").keepingWaiting("a card session")));

    assertThat(split.theObjectsTheyKeep())
        .isEqualTo("autra a card session; worldline a hosted tokenization session");
  }

  @Test
  void a_module_that_keeps_two_objects_has_both_read_after_its_name_joined_by_an_english_and() {
    HostedPages.Split split =
        HostedPages.of(
            List.of(
                new ProviderModuleDouble("grow").keepingWaiting("a payment process"),
                new ProviderModuleDouble("autra").keepingWaiting("a card session", "a Pix")));

    assertThat(split.theObjectsTheyKeep())
        .isEqualTo("autra a card session and a Pix; grow a payment process");
  }

  @Test
  void a_module_that_keeps_three_objects_reads_them_as_a_comma_list_ending_in_one_and() {
    assertThat(HostedPages.asOneEnglishPhrase(List.of("a session", "a Pix", "a boleto")))
        .isEqualTo("a session, a Pix and a boleto");
  }

  @Test
  void the_count_of_what_waits_is_the_objects_declared_and_not_the_providers_declaring_them() {
    HostedPages.Split split =
        HostedPages.of(
            List.of(
                new ProviderModuleDouble("autra").keepingWaiting("a card session", "a Pix"),
                new ProviderModuleDouble("grow").keepingWaiting("a payment process"),
                new ProviderModuleDouble("worldline")
                    .keepingWaiting("a hosted tokenization session"),
                new ProviderModuleDouble("azericard"),
                new ProviderModuleDouble("stripe")));

    assertThat(split.thatKeepAnObjectAPayerFinishes()).hasSize(3);
    assertThat(split.howManyObjectsWait()).isEqualTo(4);
    assertThat(split.theObjectsAsACount()).isEqualTo("4 objects");
  }

  @Test
  void a_build_keeping_one_object_counts_it_in_the_singular_so_the_sentence_reads_as_english() {
    HostedPages.Split split =
        HostedPages.of(List.of(new ProviderModuleDouble("grow").keepingWaiting("a process")));

    assertThat(split.howManyObjectsWait()).isEqualTo(1);
    assertThat(split.theObjectsAsACount()).isEqualTo("1 object");
  }

  @Test
  void the_order_a_split_reads_in_is_the_providers_own_names_and_not_the_order_they_registered() {
    HostedPages.Split split =
        HostedPages.of(
            List.of(
                new ProviderModuleDouble("stripe"),
                new ProviderModuleDouble("grow").keepingWaiting("a payment process"),
                new ProviderModuleDouble("azericard"),
                new ProviderModuleDouble("autra").keepingWaiting("a card session")));

    assertThat(split.thatKeepAnObjectAPayerFinishes()).containsExactly("autra", "grow");
    assertThat(split.withNoPageAPayerFinishes()).containsExactly("azericard", "stripe");
  }

  @Test
  void a_build_serving_no_provider_at_all_splits_into_two_empty_lists_rather_than_failing() {
    HostedPages.Split split = HostedPages.of(List.of());

    assertThat(split.thatKeepAnObjectAPayerFinishes()).isEmpty();
    assertThat(split.withNoPageAPayerFinishes()).isEmpty();
    assertThat(split.theObjectsTheyKeep()).isEmpty();
    assertThat(split.howManyObjectsWait()).isZero();
    assertThat(split.theObjectsAsACount()).isEqualTo("0 objects");
    assertThat(split.providers()).isZero();
  }
}
