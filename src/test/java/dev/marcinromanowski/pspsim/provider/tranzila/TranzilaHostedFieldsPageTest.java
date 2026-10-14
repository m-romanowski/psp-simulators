package dev.marcinromanowski.pspsim.provider.tranzila;

import static org.assertj.core.api.Assertions.assertThat;

import dev.marcinromanowski.pspsim.kernel.spi.ProviderWireAnswer;
import dev.marcinromanowski.pspsim.kernel.spi.ThePayerStep;
import dev.marcinromanowski.pspsim.kernel.spi.WireFault;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class TranzilaHostedFieldsPageTest {

  @Test
  void the_hosted_fields_are_served_at_the_vendors_paths_under_their_own_host() {
    assertThat(TranzilaHostedFieldsPage.HOST).isEqualTo("/tranzila/hf");
    assertThat(TranzilaHostedFieldsPage.SCRIPT_ROUTE)
        .isEqualTo("/tranzila/hf/assets/js/thostedf.js");
    assertThat(TranzilaHostedFieldsPage.FIELD_ROUTE)
        .isEqualTo("/tranzila/hf/assets/js/hfields_depend/genfield.php");
    assertThat(TranzilaHostedFieldsPage.CONTROL_ROUTE)
        .isEqualTo("/tranzila/hf/assets/js/hfields_depend/controlfields.html");
    assertThat(TranzilaHostedFieldsPage.PROCESS_ROUTE)
        .isEqualTo("/tranzila/hf/process/ajax_process_transaction");
    assertThat(TranzilaHostedFieldsPage.THE_FIELD_A_PAYER_TYPES_INTO)
        .isEqualTo(
            new ThePayerStep.APageThisSimulatorServes(
                "/tranzila/hf/assets/js/hfields_depend/genfield.php"));
    assertThat(TranzilaHostedFieldsPage.THE_FRAME_THAT_CHARGES)
        .isEqualTo(
            new ThePayerStep.APageThisSimulatorServes(
                "/tranzila/hf/assets/js/hfields_depend/controlfields.html"));
    assertThat(TranzilaHostedFieldsPage.THE_ROUTE_THE_CARD_IS_POSTED_TO)
        .isEqualTo(
            new ThePayerStep.APageThisSimulatorServes(
                "/tranzila/hf/process/ajax_process_transaction"));
  }

  @Test
  void a_field_frame_runs_its_one_script_and_loads_nothing() {
    assertThat(TranzilaHostedFieldsPage.THE_DOCUMENT)
        .startsWith("<!DOCTYPE html><html lang=\"en\">")
        .contains("<title>Tranzila hosted field</title>")
        .contains("<div id=\"field\"></div><script>" + TranzilaHostedFieldsPage.THE_SCRIPT)
        .endsWith("</script></body></html>")
        .doesNotContain(" src=")
        .doesNotContain("<iframe");
    assertThat(TranzilaHostedFieldsPage.WHAT_A_FIELD_PERMITS)
        .isEqualTo(
            "default-src 'none'; style-src 'unsafe-inline'; script-src 'sha256-"
                + PayerScriptHashes.of(TranzilaHostedFieldsPage.THE_SCRIPT)
                + "'");
    assertThat(TranzilaHostedFieldsPage.THE_SCRIPT)
        .contains("parent.frames[at].postMessage(update, location.origin)")
        .contains("parent.postMessage(JSON.stringify(message), \"*\")")
        .doesNotContain("innerHTML");
  }

  @Test
  void the_hidden_frame_posts_only_to_its_own_origin_and_answers_only_the_page_that_asked() {
    assertThat(TranzilaHostedControlPage.THE_DOCUMENT)
        .startsWith("<!DOCTYPE html><html lang=\"en\"><head><meta charset=\"utf-8\">")
        .contains("<script>" + TranzilaHostedControlPage.THE_SCRIPT + "</script>")
        .doesNotContain("<style");
    assertThat(TranzilaHostedControlPage.WHAT_IT_PERMITS)
        .isEqualTo(
            "default-src 'none'; style-src 'unsafe-inline'; script-src 'sha256-"
                + PayerScriptHashes.of(TranzilaHostedControlPage.THE_SCRIPT)
                + "'; connect-src 'self'");
    assertThat(TranzilaHostedControlPage.THE_SCRIPT)
        .contains("fetch(base + \"/process/ajax_process_transaction\"")
        .contains("arrived.origin === location.origin && message.instance === instance")
        .contains("message.type === \"charge\" && arrived.source === parent");
  }

  @Test
  void an_address_or_a_method_the_hosted_fields_do_not_serve_is_a_page_of_their_own() {
    ProviderWireAnswer.InThisProvidersOwnWords unknown =
        (ProviderWireAnswer.InThisProvidersOwnWords)
            TranzilaWireFaults.theAnswerTo(
                WireFault.AN_ADDRESS_THIS_PROVIDER_DOES_NOT_SERVE, "/tranzila/hf/nothing");
    ProviderWireAnswer.InThisProvidersOwnWords method =
        (ProviderWireAnswer.InThisProvidersOwnWords)
            TranzilaWireFaults.theAnswerTo(
                WireFault.A_METHOD_THIS_ADDRESS_DOES_NOT_ANSWER,
                "/tranzila/hf/process/ajax_process_transaction");

    assertThat(unknown.httpStatus()).isEqualTo(404);
    assertThat(unknown.contentType()).isEqualTo("text/html; charset=utf-8");
    assertThat(new String(unknown.body(), StandardCharsets.UTF_8))
        .isEqualTo(
            "<!DOCTYPE html><html lang=\"en\"><head><meta charset=\"utf-8\"><meta"
                + " name=\"viewport\" content=\"width=device-width, initial-scale=1\"><title>Tranzila"
                + " hosted fields</title><style>"
                + TranzilaHostedFieldsRefusalPage.STYLE
                + "</style></head><body><main><h1>Tranzila hosted fields</h1><p>No page of"
                + " provider tranzila&#39;s hosted fields is served at this address. The script is"
                + " /tranzila/hf/assets/js/thostedf.js and the charge is posted to"
                + " /tranzila/hf/process/ajax_process_transaction.</p></main></body></html>");
    assertThat(unknown.headers())
        .containsEntry("Content-Security-Policy", "default-src 'none'; style-src 'unsafe-inline'")
        .containsEntry("Cache-Control", "no-store");
    assertThat(method.httpStatus()).isEqualTo(405);
    assertThat(new String(method.body(), StandardCharsets.UTF_8))
        .contains(
            "This address of provider tranzila&#39;s hosted fields does not answer this HTTP"
                + " method. The script and the frames take GET, and the charge takes POST only.");
    assertThat(
            TranzilaWireFaults.theAnswerTo(
                WireFault.A_FAILURE_NOTHING_ELSE_ANSWERED, "/tranzila/hf/assets/js/thostedf.js"))
        .isEqualTo(
            new ProviderWireAnswer.NothingThisContractPublishes(
                TranzilaWireFaults.WHAT_THE_HOSTED_FIELDS_PUBLISH_FOR_A_FAILURE_INSIDE_THEM));
    assertThat(TranzilaWireFaults.ROUTES.namespaces()).contains("/tranzila/hf");
  }

  @Test
  void pairing_publishes_the_hosted_fields_host() {
    assertThat(TranzilaPairing.PAIRING.settings())
        .extracting(setting -> setting.name() + " " + setting.value())
        .contains("TRANZILA_HF_BASE_URL AnAddressOfThisSimulator[path=/tranzila/hf]");
  }
}
