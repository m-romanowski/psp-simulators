package com.example.tester.ui;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class ATesterRouteTest {

  @ParameterizedTest(name = "{0} reaches what only a tester may")
  @DisplayName("an address reaches a tester route however its path is spelled")
  @ValueSource(
      strings = {
        "http://127.0.0.1:8080/__simulator/grow/state/1",
        "http://127.0.0.1:8080/%5F%5Fsimulator/x",
        "http://127.0.0.1:8080/%255F%255Fsimulator/x",
        "http://127.0.0.1:8080//__simulator/x",
        "http://127.0.0.1:8080/ui;x=1/y",
        "http://127.0.0.1:8080/a/../__simulator",
        "http://127.0.0.1:8080/./ui",
        "http://127.0.0.1:8080/UI",
        "http://127.0.0.1:8080/__SIMULATOR/grow/reset",
        "/__simulator"
      })
  void aTesterRouteIsFoundHoweverItIsSpelled(String url) {
    assertThat(ATesterRoute.isNamedBy(url)).isTrue();
  }

  @ParameterizedTest(name = "{0} is the provider's own")
  @DisplayName("an address a provider serves is not a tester route")
  @ValueSource(
      strings = {
        "http://127.0.0.1:8080/uix",
        "http://127.0.0.1:8080/far?l=abc&q=/__simulator",
        "http://127.0.0.1:8080/far?q=/ui",
        "http://127.0.0.1:8080/far#/__simulator",
        "http://127.0.0.1:8080/token/cgi_link",
        "http://127.0.0.1:8080/v2/m/hostedtokenizations/abc",
        "http://127.0.0.1:8080/",
        "about:blank"
      })
  void aProvidersOwnAddressIsNotATesterRoute(String url) {
    assertThat(ATesterRoute.isNamedBy(url)).isFalse();
  }
}
