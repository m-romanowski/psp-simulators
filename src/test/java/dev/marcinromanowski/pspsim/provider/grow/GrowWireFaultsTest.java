package dev.marcinromanowski.pspsim.provider.grow;

import static org.assertj.core.api.Assertions.assertThat;

import dev.marcinromanowski.pspsim.kernel.spi.ProviderWireAnswer;
import org.junit.jupiter.api.Test;

class GrowWireFaultsTest {

  @Test
  void every_address_this_build_serves_for_grow_is_inside_a_namespace_grow_claims() {
    assertThat(GrowWireFaults.ROUTES.covers("/api/light/server/1.0/createPaymentProcess")).isTrue();
    assertThat(GrowWireFaults.ROUTES.covers("/api/light/server/1.0/refundTransaction")).isTrue();
    assertThat(GrowWireFaults.ROUTES.covers(GrowHostedPage.PATH)).isTrue();
    assertThat(GrowWireFaults.ROUTES.covers("/api/light/andriod/1.0/cancelBitTransaction"))
        .as(
            "the portal publishes a second path family under /api/light that this build does not"
                + " serve, and a client that calls it is calling grow and not this application")
        .isTrue();
    assertThat(GrowWireFaults.ROUTES.provider()).isEqualTo(GrowLightApi.GROW);
  }

  @Test
  void no_address_of_another_provider_or_of_the_screens_is_claimed_for_grow() {
    assertThat(GrowWireFaults.ROUTES.covers("/v1/acquiring/pix/checkout")).isFalse();
    assertThat(GrowWireFaults.ROUTES.covers("/v1/payment_intents")).isFalse();
    assertThat(GrowWireFaults.ROUTES.covers("/v2/M1/payments")).isFalse();
    assertThat(GrowWireFaults.ROUTES.covers("/cgi-bin/cgi_link")).isFalse();
    assertThat(GrowWireFaults.ROUTES.covers("/farther")).isFalse();
    assertThat(GrowWireFaults.ROUTES.covers("/ui/objects")).isFalse();
    assertThat(GrowWireFaults.ROUTES.covers("/__simulator/grow/state")).isFalse();
  }

  @Test
  void grow_publishes_no_envelope_for_a_request_it_never_read_and_says_what_it_publishes_instead() {
    assertThat(GrowWireFaults.THE_ANSWER)
        .as(
            "grow's own envelope rides at HTTP 200 and means the operation ran, so sending it for"
                + " a request grow never saw would report an outcome that never happened")
        .isInstanceOf(ProviderWireAnswer.NothingThisContractPublishes.class);
    assertThat(
            ((ProviderWireAnswer.NothingThisContractPublishes) GrowWireFaults.THE_ANSWER)
                .whatThisContractPublishesInstead())
        .isEqualTo(GrowWireFaults.WHAT_THIS_CONTRACT_PUBLISHES_INSTEAD);
  }
}
