package dev.marcinromanowski.pspsim.provider.worldline;

import static org.assertj.core.api.Assertions.assertThat;

import dev.marcinromanowski.pspsim.kernel.spi.ProviderWireAnswer;
import dev.marcinromanowski.pspsim.kernel.spi.WireFault;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class WorldlineWireFaultsTest {

  private final WorldlineWireFaults faults =
      new WorldlineWireFaults(new WorldlineDoubles.CountingIds());

  @Test
  void every_address_this_build_serves_for_worldline_is_inside_a_namespace_it_claims() {
    assertThat(WorldlineWireFaults.ROUTES.covers("/v2/M1/payments")).isTrue();
    assertThat(WorldlineWireFaults.ROUTES.covers("/v2/M1/hostedtokenizations/abc")).isTrue();
    assertThat(
            WorldlineWireFaults.ROUTES.covers(
                CreateHostedTokenization.THE_PUBLISHED_FORM_PATH + "abc"))
        .isTrue();
    assertThat(WorldlineWireFaults.ROUTES.provider()).isEqualTo(WorldlineApi.WORLDLINE);
  }

  @Test
  void no_address_of_another_provider_or_of_the_screens_is_claimed_for_worldline() {
    assertThat(WorldlineWireFaults.ROUTES.covers("/v1/payment_intents")).isFalse();
    assertThat(WorldlineWireFaults.ROUTES.covers("/v20/M1/payments")).isFalse();
    assertThat(WorldlineWireFaults.ROUTES.covers("/ui/objects")).isFalse();
    assertThat(WorldlineWireFaults.ROUTES.covers("/__simulator/worldline/state")).isFalse();
  }

  @Test
  void an_address_worldline_does_not_answer_is_404_in_its_own_envelope_with_no_id_and_no_code() {
    ProviderWireAnswer.InThisProvidersOwnWords answered =
        (ProviderWireAnswer.InThisProvidersOwnWords)
            faults.theAnswerTo(WireFault.AN_ADDRESS_THIS_PROVIDER_DOES_NOT_SERVE);

    assertThat(answered.httpStatus()).isEqualTo(WorldlineAnswers.NOT_FOUND);
    assertThat(answered.contentType()).isEqualTo(WorldlineFields.JSON);
    String body = new String(answered.body(), StandardCharsets.UTF_8);
    assertThat(body)
        .contains("\"" + WorldlineFields.ERROR_ID + "\"")
        .contains(
            "\""
                + WorldlineFields.CATEGORY
                + "\":\""
                + WorldlineErrors.DIRECT_PLATFORM_ERROR
                + "\"")
        .contains("\"" + WorldlineFields.HTTP_STATUS_CODE + "\":" + WorldlineAnswers.NOT_FOUND)
        .contains(WorldlineWireFaults.NO_ROUTE_ANSWERS_THIS_ADDRESS)
        .contains("\"" + WorldlineFields.RETRIABLE + "\":false")
        .as(
            "the four published identifiers for something unknown name a payment, a refund, a"
                + " payout and a capture, and borrowing one for an address would name a resource"
                + " nobody asked about")
        .doesNotContain("\"" + WorldlineFields.ID + "\"")
        .doesNotContain("\"" + WorldlineFields.ERROR_CODE + "\"");
  }

  @Test
  void a_method_an_address_does_not_answer_is_405_which_the_vendor_tells_a_caller_to_expect() {
    ProviderWireAnswer.InThisProvidersOwnWords answered =
        (ProviderWireAnswer.InThisProvidersOwnWords)
            faults.theAnswerTo(WireFault.A_METHOD_THIS_ADDRESS_DOES_NOT_ANSWER);

    assertThat(answered.httpStatus()).isEqualTo(WorldlineWireFaults.METHOD_NOT_ALLOWED);
    assertThat(new String(answered.body(), StandardCharsets.UTF_8))
        .contains(
            "\""
                + WorldlineFields.HTTP_STATUS_CODE
                + "\":"
                + WorldlineWireFaults.METHOD_NOT_ALLOWED)
        .contains("405 is outside the published status list");
  }

  @Test
  void every_envelope_carries_an_error_id_of_its_own_rather_than_one_shared_between_answers() {
    String first =
        new String(
            ((ProviderWireAnswer.InThisProvidersOwnWords)
                    faults.theAnswerTo(WireFault.AN_ADDRESS_THIS_PROVIDER_DOES_NOT_SERVE))
                .body(),
            StandardCharsets.UTF_8);
    String second =
        new String(
            ((ProviderWireAnswer.InThisProvidersOwnWords)
                    faults.theAnswerTo(WireFault.AN_ADDRESS_THIS_PROVIDER_DOES_NOT_SERVE))
                .body(),
            StandardCharsets.UTF_8);

    assertThat(first).isNotEqualTo(second);
  }

  @Test
  void a_failure_inside_this_simulator_is_not_dressed_as_an_answer_worldline_gave() {
    ProviderWireAnswer answered = faults.theAnswerTo(WireFault.A_FAILURE_NOTHING_ELSE_ANSWERED);

    assertThat(answered).isInstanceOf(ProviderWireAnswer.NothingThisContractPublishes.class);
    assertThat(
            ((ProviderWireAnswer.NothingThisContractPublishes) answered)
                .whatThisContractPublishesInstead())
        .isEqualTo(WorldlineWireFaults.WHAT_THIS_CONTRACT_PUBLISHES_FOR_A_FAILURE_INSIDE_IT);
  }
}
