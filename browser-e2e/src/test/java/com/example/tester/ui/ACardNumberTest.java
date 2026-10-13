package com.example.tester.ui;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ACardNumberTest {

  private static final String TYPED = "4127208104942601";

  private static final Set<String> THE_PAYER_HOLDS = Set.of(TYPED);

  @Test
  @DisplayName("a card number is found written in groups of four with spaces")
  void aCardNumberWrittenWithSpacesIsFound() {
    assertThat(ACardNumber.leakedIn("a page", "card 4127 2081 0494 2601 ok", Set.of()))
        .containsExactly("a page carries a whole card number ending 2601");
  }

  @Test
  @DisplayName("a card number is found written in groups of four with dashes")
  void aCardNumberWrittenWithDashesIsFound() {
    assertThat(ACardNumber.leakedIn("a page", "<td>4127-2081-0494-2601</td>", Set.of()))
        .containsExactly("a page carries a whole card number ending 2601");
  }

  @Test
  @DisplayName("a card number is found unquoted in prose, in json and in a query string")
  void aCardNumberWrittenAsOneRunIsFound() {
    assertThat(ACardNumber.leakedIn("a", "the card 4127208104942601 was used", Set.of()))
        .hasSize(1);
    assertThat(ACardNumber.leakedIn("b", "{\"pan\":4127208104942601}", Set.of())).hasSize(1);
    assertThat(ACardNumber.leakedIn("c", "http://shop/paid?pan=4127208104942601&x=1", Set.of()))
        .hasSize(1);
  }

  @Test
  @DisplayName("a card number followed by an unrelated number is still found")
  void aCardNumberBesideAnotherNumberIsFound() {
    assertThat(ACardNumber.leakedIn("a page", "4127208104942601 5 AZN", Set.of())).hasSize(1);
  }

  @Test
  @DisplayName("a card the payer typed is found even when it fails the Luhn check")
  void theTypedCardIsFoundWithoutLuhn() {
    assertThat(ACardNumber.passesLuhn("4127208104942602")).isFalse();
    assertThat(ACardNumber.leakedIn("a", "x=4127208104942602", Set.of("4127208104942602")))
        .hasSize(1);
  }

  @Test
  @DisplayName("the typed card's first six beside its last four is found, however it is masked")
  void theFirstSixBesideTheLastFourIsFound() {
    for (String masked :
        List.of("412720******2601", "4127 20** **** 2601", "412720-XXXX-2601", "412720...2601")) {
      assertThat(ACardNumber.leakedIn("a callback", masked, THE_PAYER_HOLDS))
          .as(masked)
          .containsExactly(
              "a callback carries the first six digits of the card ending 2601 beside its last"
                  + " four");
    }
  }

  @Test
  @DisplayName("a card masked but for its last four, and the numbers a payment carries, are not")
  void whatAPaymentLegitimatelyCarriesIsNotACardNumber() {
    String legitimate =
        "CARD=************2601&ORDER=20260929195000123&TIMESTAMP=20260929195000&RRN=123456789012"
            + "&TERMINAL=17204567&INT_REF=A1B2C3D4E5F60718&TOKEN=0123456789abcdef0123456789abcdef"
            + "&AMOUNT=10.00&cardSuffix=4580&maskedCard=411111******1111";
    assertThat(ACardNumber.leakedIn("a callback", legitimate, THE_PAYER_HOLDS)).isEmpty();
  }

  @Test
  @DisplayName("a run of digits no issuer draws from, or failing Luhn, is not a card number")
  void aRunOfDigitsIsACardNumberOnlyWithAnIssuerPrefixAndLuhn() {
    assertThat(ACardNumber.looksIssued("4127208104942601")).isTrue();
    assertThat(ACardNumber.looksIssued("4127208104942602")).isFalse();
    assertThat(ACardNumber.looksIssued("1000000000000007")).isFalse();
  }
}
