package dev.marcinromanowski.pspsim.control;

import static org.assertj.core.api.Assertions.assertThat;

import dev.marcinromanowski.pspsim.kernel.ObjectOrigin;
import dev.marcinromanowski.pspsim.kernel.ProviderId;
import dev.marcinromanowski.pspsim.kernel.spi.ProviderDouble;
import dev.marcinromanowski.pspsim.kernel.spi.ProviderRegistry;
import dev.marcinromanowski.pspsim.kernel.spi.WireAnswer;
import dev.marcinromanowski.pspsim.kernel.spi.WireFault;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

class TheControlApiInItsOwnWordsTest {

  private final TheControlApiInItsOwnWords controlApi =
      new TheControlApiInItsOwnWords(
          new ProviderRegistry(List.of(new ProviderDouble("gateway"))), new ObjectMapper());

  @Test
  void every_address_of_the_control_api_and_nothing_above_or_beside_it_belongs_to_it() {
    assertThat(controlApi.owns("/__simulator")).isTrue();
    assertThat(controlApi.owns("/__simulator/gateway/config")).isTrue();
    assertThat(controlApi.owns("/__simulator/nothingHere")).isTrue();
    assertThat(controlApi.owns("/__simulatorx/gateway")).isFalse();
    assertThat(controlApi.owns("/ui/objects"))
        .as(
            "a screen's failure is drawn on this simulator's own page with the reasons on it, and"
                + " claiming the screens here would replace that page with one json field")
        .isFalse();
    assertThat(controlApi.owns("/cgi-bin/cgi_link")).isFalse();
    assertThat(controlApi.owns("/")).isFalse();
  }

  @Test
  void what_reaches_the_control_api_is_recorded_as_having_come_through_it() {
    assertThat(controlApi.whatReachesIt()).isEqualTo(ObjectOrigin.CONTROL_API);
  }

  @Test
  void an_address_of_the_control_api_names_the_provider_whose_trail_the_failure_belongs_in() {
    assertThat(controlApi.theProviderItsAddressNames("/__simulator/gateway/config"))
        .contains(ProviderId.of("gateway"));
    assertThat(controlApi.theProviderItsAddressNames("/__simulator/gateway"))
        .contains(ProviderId.of("gateway"));
  }

  @Test
  void an_address_that_names_no_provider_this_build_knows_names_none() {
    assertThat(controlApi.theProviderItsAddressNames("/__simulator/nothingHere")).isEmpty();
    assertThat(controlApi.theProviderItsAddressNames("/__simulator")).isEmpty();
    assertThat(controlApi.theProviderItsAddressNames("/__simulator/")).isEmpty();
    assertThat(controlApi.theProviderItsAddressNames("/cgi-bin/cgi_link")).isEmpty();
  }

  @Test
  void the_segment_that_names_a_provider_is_read_off_the_namespace_and_not_out_of_the_rest() {
    assertThat(TheControlApiInItsOwnWords.theSegmentAfterTheNamespace("/__simulator/grow/config"))
        .isEqualTo("grow");
    assertThat(TheControlApiInItsOwnWords.theSegmentAfterTheNamespace("/__simulator/grow"))
        .isEqualTo("grow");
    assertThat(TheControlApiInItsOwnWords.theSegmentAfterTheNamespace("/__simulator/"))
        .isEqualTo("");
    assertThat(TheControlApiInItsOwnWords.theSegmentAfterTheNamespace("/__simulator"))
        .isEqualTo("");
    assertThat(TheControlApiInItsOwnWords.theSegmentAfterTheNamespace("/__simulator//config"))
        .as(
            "the segment below the namespace is empty here, and reading the rest of the address"
                + " as a provider name would file a failure under whatever a doubled slash left"
                + " behind")
        .isEqualTo("");
    assertThat(TheControlApiInItsOwnWords.theSegmentAfterTheNamespace("/cgi-bin/grow/config"))
        .as(
            "an address outside the namespace has no provider segment at a fixed depth, and"
                + " reading one out of it would file a failure of a provider route under whatever"
                + " its second segment happened to spell")
        .isEqualTo("");
  }

  @Test
  void a_failure_nothing_else_answered_is_answered_in_the_one_field_the_control_api_publishes() {
    Optional<WireAnswer> answered =
        controlApi.theAnswerTo(WireFault.A_FAILURE_NOTHING_ELSE_ANSWERED);

    assertThat(answered).isPresent();
    assertThat(answered.get().httpStatus()).isEqualTo(500);
    assertThat(answered.get().contentType()).isEqualTo("application/json");
    String body = new String(answered.get().body(), StandardCharsets.UTF_8);
    assertThat(body)
        .as(
            "every other refusal of this control api is one message field, and a second shape"
                + " for the same situation is a client reading two contracts off one route")
        .startsWith("{\"message\":\"")
        .endsWith("\"}");
    assertThat(body).contains("the audit trail of this provider");
    assertThat(body)
        .as(
            "a real provider does not leak the words of the gateway in front of it, and this"
                + " simulator must not leak its own")
        .doesNotContain("timestamp")
        .doesNotContain("Internal Server Error")
        .doesNotContain("Exception")
        .doesNotContain("dev.marcinromanowski.pspsim")
        .doesNotContain("org.springframework")
        .doesNotContain("select ")
        .doesNotContain("src/main");
  }

  @Test
  void a_fault_the_control_api_publishes_no_answer_for_is_answered_by_nothing_of_it() {
    assertThat(controlApi.theAnswerTo(WireFault.AN_ADDRESS_THIS_PROVIDER_DOES_NOT_SERVE))
        .as(
            "what the control api answers for an address it does not serve is a separate"
                + " question, and answering it here would change a published 404 that nothing"
                + " measured")
        .isEmpty();
    assertThat(controlApi.theAnswerTo(WireFault.A_METHOD_THIS_ADDRESS_DOES_NOT_ANSWER)).isEmpty();
  }
}
