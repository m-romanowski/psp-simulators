package dev.marcinromanowski.pspsim.provider.autra;

import static org.assertj.core.api.Assertions.assertThat;

import dev.marcinromanowski.pspsim.kernel.spi.ProviderWireAnswer;
import dev.marcinromanowski.pspsim.kernel.spi.WireFault;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.Test;

class AutraWireFaultsTest {

  @Test
  void every_address_this_build_serves_for_autra_is_inside_a_namespace_autra_claims() {
    assertThat(AutraWireFaults.ROUTES.covers(AutraApiController.OAUTH_TOKEN)).isTrue();
    assertThat(AutraWireFaults.ROUTES.covers(AutraApiController.PIX_CHECKOUT)).isTrue();
    assertThat(AutraWireFaults.ROUTES.covers(AutraApiController.PRE_AUTHORIZATION)).isTrue();
    assertThat(AutraWireFaults.ROUTES.covers(AutraApiController.CARD_SESSIONS)).isTrue();
    assertThat(AutraWireFaults.ROUTES.covers(AutraCardPage.SESSIONS + "abc")).isTrue();
    assertThat(AutraWireFaults.ROUTES.provider()).isEqualTo(AutraApi.AUTRA);
  }

  @Test
  void no_address_of_another_provider_or_of_the_screens_is_claimed_for_autra() {
    assertThat(AutraWireFaults.ROUTES.covers("/v1/payment_intents"))
        .as(
            "two providers of this build answer under /v1, so autra claims the two second"
                + " segments its own contract publishes and never /v1 itself")
        .isFalse();
    assertThat(AutraWireFaults.ROUTES.covers("/v1/refunds")).isFalse();
    assertThat(AutraWireFaults.ROUTES.covers("/token/cgi_link"))
        .as("azericard's token gateway only begins like autra's hosted card page")
        .isFalse();
    assertThat(AutraWireFaults.ROUTES.covers("/api/light/server/1.0/createPaymentProcess"))
        .isFalse();
    assertThat(AutraWireFaults.ROUTES.covers("/ui/x/autra/pix")).isFalse();
    assertThat(AutraWireFaults.ROUTES.covers("/__simulator/autra/state")).isFalse();
  }

  @Test
  void autra_publishes_no_code_for_a_request_it_never_read_and_says_what_it_publishes_instead() {
    for (WireFault fault :
        List.of(
            WireFault.AN_ADDRESS_THIS_PROVIDER_DOES_NOT_SERVE,
            WireFault.A_METHOD_THIS_ADDRESS_DOES_NOT_ANSWER)) {
      ProviderWireAnswer answered = AutraWireFaults.theAnswerTo(fault, AutraWireFaults.ACQUIRING);

      assertThat(answered)
          .as(
              "every code in autra's catalogue is about the call it read, so INVALID_JSON for an"
                  + " address autra does not serve would name a body nobody sent")
          .isInstanceOf(ProviderWireAnswer.NothingThisContractPublishes.class);
      assertThat(
              ((ProviderWireAnswer.NothingThisContractPublishes) answered)
                  .whatThisContractPublishesInstead())
          .isEqualTo(AutraWireFaults.WHAT_THIS_CONTRACT_PUBLISHES_INSTEAD);
    }
  }

  @Test
  void a_failure_inside_autra_on_its_acquiring_namespace_answers_the_code_autra_publishes() {
    ProviderWireAnswer answered =
        AutraWireFaults.theAnswerTo(
            WireFault.A_FAILURE_NOTHING_ELSE_ANSWERED, AutraApiController.PIX_CHECKOUT);

    assertThat(answered).isInstanceOf(ProviderWireAnswer.InThisProvidersOwnWords.class);
    ProviderWireAnswer.InThisProvidersOwnWords own =
        (ProviderWireAnswer.InThisProvidersOwnWords) answered;
    assertThat(own.httpStatus()).isEqualTo(500);
    assertThat(own.contentType()).isEqualTo("application/json");
    assertThat(new String(own.body(), StandardCharsets.UTF_8))
        .as(
            "autra's openapi binds 500 to its errors[] of code and msg, and its own error"
                + " catalogue on the acquiring namespace pairs INTERNAL_ERROR with 500 and"
                + " glosses it Falha interna., so this envelope invents no field name, no code"
                + " and no text; an expectation built out of the constant under test could not"
                + " fail when that text changed")
        .isEqualTo("{\"errors\":[{\"code\":\"INTERNAL_ERROR\",\"msg\":\"Falha interna.\"}]}");
    assertThat(new String(own.body(), StandardCharsets.UTF_8))
        .as("a real acquirer does not hand a client this simulator's insides")
        .doesNotContain("Exception")
        .doesNotContain("dev.marcinromanowski.pspsim")
        .doesNotContain("org.springframework")
        .doesNotContain("select ")
        .doesNotContain("src/main");
  }

  @Test
  void a_failure_outside_the_acquiring_namespace_is_answered_by_nothing_of_autras() {
    for (String address :
        List.of(
            AutraApiController.OAUTH_TOKEN,
            AutraWireFaults.OAUTH,
            AutraWireFaults.CARD_PAGE,
            AutraCardPage.SESSIONS + "abc",
            "/v1/acquiringx/payments")) {
      assertThat(AutraWireFaults.theAnswerTo(WireFault.A_FAILURE_NOTHING_ELSE_ANSWERED, address))
          .as(
              "%s is not under autra's acquiring namespace: the token route publishes one error"
                  + " string and no 5xx at all, and the hosted card page answers html, so an"
                  + " acquiring envelope there is a shape autra never sends",
              address)
          .isInstanceOf(ProviderWireAnswer.NothingThisContractPublishes.class);
    }
    assertThat(AutraWireFaults.underTheAcquiringNamespace(AutraWireFaults.ACQUIRING)).isTrue();
  }
}
