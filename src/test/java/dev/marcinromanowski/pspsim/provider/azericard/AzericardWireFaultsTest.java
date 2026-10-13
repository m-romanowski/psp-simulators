package dev.marcinromanowski.pspsim.provider.azericard;

import static org.assertj.core.api.Assertions.assertThat;

import dev.marcinromanowski.pspsim.kernel.spi.ProviderWireAnswer;
import org.junit.jupiter.api.Test;

class AzericardWireFaultsTest {

  @Test
  void every_address_this_build_serves_for_azericard_is_inside_a_namespace_it_claims() {
    assertThat(AzericardWireFaults.ROUTES.covers(AzericardGatewayController.CGI_LINK)).isTrue();
    assertThat(AzericardWireFaults.ROUTES.covers(AzericardGatewayController.TOKEN_CGI_LINK))
        .isTrue();
    assertThat(AzericardWireFaults.ROUTES.covers(AzericardPayerPage.PATH)).isTrue();
    assertThat(AzericardWireFaults.ROUTES.provider()).isEqualTo(AzericardGateway.AZERICARD);
  }

  @Test
  void no_address_of_another_provider_or_of_the_screens_is_claimed_for_azericard() {
    assertThat(AzericardWireFaults.ROUTES.covers("/tokenize/sessions/abc"))
        .as("autra's hosted card page only begins like azericard's token gateway")
        .isFalse();
    assertThat(AzericardWireFaults.ROUTES.covers("/v1/acquiring/pix/checkout")).isFalse();
    assertThat(AzericardWireFaults.ROUTES.covers("/v2/M1/payments")).isFalse();
    assertThat(AzericardWireFaults.ROUTES.covers("/ui/objects")).isFalse();
    assertThat(AzericardWireFaults.ROUTES.covers("/__simulator/azericard/state")).isFalse();
  }

  @Test
  void the_page_azericard_serves_a_missing_field_cannot_stand_in_for_a_request_it_never_read() {
    assertThat(AzericardWireFaults.THE_ANSWER)
        .as(
            "the observed page carries HTTP 200 and an Error message line naming a request field,"
                + " and a request azericard never read names no field and reached no processing")
        .isInstanceOf(ProviderWireAnswer.NothingThisContractPublishes.class);
    assertThat(
            ((ProviderWireAnswer.NothingThisContractPublishes) AzericardWireFaults.THE_ANSWER)
                .whatThisContractPublishesInstead())
        .isEqualTo(AzericardWireFaults.WHAT_THIS_CONTRACT_PUBLISHES_INSTEAD)
        .contains(AzericardErrorPage.OPENING);
  }
}
