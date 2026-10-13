package dev.marcinromanowski.pspsim.kernel.spi;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class WhatAPayerPageMayLoadTest {

  private static final String A_POLICY = "default-src 'none'; style-src 'unsafe-inline'";

  @Test
  void a_document_declares_four_defences_in_one_order_whatever_the_process() {
    Map<String, String> declared = WhatAPayerPageMayLoad.onADocumentDeclaring(A_POLICY);

    assertThat(declared)
        .as(
            "a header order drawn from a salted map varies per process, and a client cannot tell"
                + " this simulator from the provider by a difference that moves")
        .containsExactly(
            Map.entry("Cache-Control", "no-store"),
            Map.entry("X-Content-Type-Options", "nosniff"),
            Map.entry("Referrer-Policy", "no-referrer"),
            Map.entry("Content-Security-Policy", A_POLICY));
  }

  @Test
  void the_policy_a_page_hands_in_is_the_policy_the_declaration_carries() {
    assertThat(WhatAPayerPageMayLoad.onADocumentDeclaring(A_POLICY + "; frame-ancestors 'none'"))
        .containsEntry("Content-Security-Policy", A_POLICY + "; frame-ancestors 'none'");
    assertThat(WhatAPayerPageMayLoad.NOTHING_BUT_THE_PAGES_OWN_STYLE).isEqualTo(A_POLICY);
  }

  @Test
  void a_declaration_read_one_value_at_a_time_carries_the_same_four_in_the_same_order() {
    Map<String, List<String>> each = WhatAPayerPageMayLoad.eachOnADocumentDeclaring(A_POLICY);

    assertThat(each)
        .containsExactly(
            Map.entry("Cache-Control", List.of("no-store")),
            Map.entry("X-Content-Type-Options", List.of("nosniff")),
            Map.entry("Referrer-Policy", List.of("no-referrer")),
            Map.entry("Content-Security-Policy", List.of(A_POLICY)));
  }

  @Test
  void a_caller_cannot_add_a_defence_to_a_declaration_it_was_handed() {
    Map<String, String> declared = WhatAPayerPageMayLoad.onADocumentDeclaring(A_POLICY);

    assertThatCode(() -> declared.put("X-Whatever", "yes"))
        .as("a page that could add a header to the declaration could also drop one")
        .isInstanceOf(UnsupportedOperationException.class);
    assertThatThrownBy(() -> WhatAPayerPageMayLoad.eachOnADocumentDeclaring(A_POLICY).clear())
        .isInstanceOf(UnsupportedOperationException.class);
  }
}
