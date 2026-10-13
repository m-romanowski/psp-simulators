package dev.marcinromanowski.pspsim.kernel.smoke;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class VendorSamplesTest {

  private static final String MANIFEST = "vendor-samples/acme/samples.json";

  private static final String A_DECLINE =
      """
      {
        "declined": {
          "evidence": "live probe",
          "probed": "2026-09-10",
          "excerpt": true,
          "note": "keeps the error of the answer",
          "source": "https://acme.example/pay",
          "reference": "acme.md, Declines",
          "status": 402,
          "headers": { "Content-Type": "application/json; charset=UTF-8" },
          "body": "declined.json"
        }
      }
      """;

  private static final String AN_EXAMPLE =
      "\"evidence\":\"published example\",\"source\":\"s\",\"reference\":\"r\"";

  private static final String A_STATUS_IS_AN_INTEGER =
      "acme/a must give status an HTTP status, an integer from 100 to 599";

  private static final String THE_DECLINED_BODY =
      "{\"error\":{\"code\":\"card_declined\",\"amount\":10.50}}";

  @Test
  void a_sample_carries_its_provenance_status_headers_and_body_as_the_manifest_declares_them() {
    VendorSample declined =
        read(Map.of(MANIFEST, A_DECLINE, "vendor-samples/acme/declined.json", THE_DECLINED_BODY))
            .sample("declined");

    assertThat(declined.provenance())
        .isEqualTo(
            new Provenance(
                "acme/declined",
                Evidence.probedOn(LocalDate.of(2026, 9, 10)).excerpted(),
                "https://acme.example/pay",
                "acme.md, Declines",
                "keeps the error of the answer"));
    assertThat(declined.status().value()).isEqualTo("402");
    assertThat(declined.header("Content-Type").value())
        .isEqualTo("application/json; charset=UTF-8");
    assertThat(declined.body().value()).isEqualTo(THE_DECLINED_BODY);
    assertThat(declined.json("/error/code").value()).isEqualTo("card_declined");
    assertThat(declined.json("/error/amount").value()).isEqualTo("10.50");
    assertThat(declined.json("/error/code").provenance()).isEqualTo(declined.provenance());
  }

  @Test
  void a_header_is_found_whatever_the_case_a_check_spells_its_name_in() {
    VendorSample declined =
        read(Map.of(MANIFEST, A_DECLINE, "vendor-samples/acme/declined.json", THE_DECLINED_BODY))
            .sample("declined");

    assertThat(declined.header("content-type").value())
        .isEqualTo("application/json; charset=UTF-8");
  }

  @Test
  void a_part_the_sample_does_not_record_is_refused_rather_than_read_as_nothing() {
    VendorSample bare =
        read(Map.of(
                MANIFEST,
                "{\"bare\":{\"evidence\":\"published example\",\"source\":\"https://a\","
                    + "\"reference\":\"acme.md, A\"}}"))
            .sample("bare");

    assertThatThrownBy(bare::status)
        .isInstanceOf(MalformedVendorSampleException.class)
        .hasMessage("acme/bare records no HTTP status for a check to read");
    assertThatThrownBy(bare::body)
        .isInstanceOf(MalformedVendorSampleException.class)
        .hasMessage("acme/bare records no body for a check to read");
    assertThatThrownBy(() -> bare.header("Content-Type"))
        .isInstanceOf(MalformedVendorSampleException.class)
        .hasMessage("acme/bare carries no header Content-Type for a check to read");
  }

  @Test
  void a_pointer_that_reaches_nothing_in_the_body_is_refused() {
    VendorSample declined =
        read(Map.of(MANIFEST, A_DECLINE, "vendor-samples/acme/declined.json", THE_DECLINED_BODY))
            .sample("declined");

    assertThatThrownBy(() -> declined.json("/error/decline_code"))
        .isInstanceOf(MalformedVendorSampleException.class)
        .hasMessageContaining("acme/declined yields no value");
  }

  @Test
  void a_sample_the_manifest_does_not_declare_is_not_found() {
    VendorSamples samples =
        read(Map.of(MANIFEST, A_DECLINE, "vendor-samples/acme/declined.json", THE_DECLINED_BODY));

    assertThatThrownBy(() -> samples.sample("approved"))
        .isInstanceOf(VendorSampleNotFoundException.class)
        .hasMessage("vendor-samples/acme/samples.json declares no sample approved");
  }

  @Test
  void an_owner_with_no_manifest_and_a_body_file_that_is_missing_are_not_found() {
    assertThatThrownBy(() -> read(Map.of()))
        .isInstanceOf(VendorSampleNotFoundException.class)
        .hasMessage("vendor-samples/acme/samples.json does not exist");
    assertThatThrownBy(() -> read(Map.of(MANIFEST, A_DECLINE)))
        .isInstanceOf(VendorSampleNotFoundException.class)
        .hasMessage("vendor-samples/acme/declined.json does not exist");
  }

  @Test
  void a_manifest_that_is_not_an_object_of_samples_is_malformed() {
    assertThatThrownBy(() -> read(Map.of(MANIFEST, "{ not json")))
        .isInstanceOf(MalformedVendorSampleException.class)
        .hasMessageStartingWith("vendor-samples/acme/samples.json is not JSON");
    for (String notAnObjectOfSamples : new String[] {"{}", "[]", "null", "\"a\""}) {
      assertThatThrownBy(() -> read(Map.of(MANIFEST, notAnObjectOfSamples)))
          .isInstanceOf(MalformedVendorSampleException.class)
          .hasMessage(
              "vendor-samples/acme/samples.json must be a JSON object naming at least one sample");
    }
  }

  @Test
  void a_sample_declared_as_anything_but_an_object_of_known_fields_is_malformed() {
    assertThatThrownBy(() -> read(Map.of(MANIFEST, "{\"a\":\"text\"}")))
        .isInstanceOf(MalformedVendorSampleException.class)
        .hasMessage("acme/a must be declared as a JSON object");
    assertThatThrownBy(
            () ->
                read(
                    Map.of(
                        MANIFEST,
                        "{\"a\":{\"evidence\":\"published example\",\"source\":\"s\","
                            + "\"reference\":\"r\",\"quote\":\"q\"}}")))
        .isInstanceOf(MalformedVendorSampleException.class)
        .hasMessage("acme/a declares quote, which a published example does not carry");
  }

  @Test
  void a_sample_missing_its_provenance_is_malformed() {
    assertThatThrownBy(
            () -> read(Map.of(MANIFEST, "{\"a\":{\"source\":\"s\",\"reference\":\"r\"}}")))
        .isInstanceOf(MalformedVendorSampleException.class)
        .hasMessage("acme/a declares no evidence");
    assertThatThrownBy(
            () ->
                read(
                    Map.of(
                        MANIFEST,
                        "{\"a\":{\"evidence\":\"published example\",\"reference\":\"r\"}}")))
        .isInstanceOf(MalformedVendorSampleException.class)
        .hasMessage("acme/a declares no source");
    assertThatThrownBy(
            () ->
                read(
                    Map.of(
                        MANIFEST, "{\"a\":{\"evidence\":\"published example\",\"source\":\"s\"}}")))
        .isInstanceOf(MalformedVendorSampleException.class)
        .hasMessage("acme/a declares no reference");
  }

  @Test
  void a_field_or_a_header_of_the_wrong_shape_is_malformed() {
    String provenance = "\"evidence\":\"published example\",\"source\":\"s\",\"reference\":\"r\"";
    assertThatThrownBy(
            () -> read(Map.of(MANIFEST, "{\"a\":{" + provenance + ",\"status\":{\"x\":1}}}")))
        .isInstanceOf(MalformedVendorSampleException.class)
        .hasMessage(A_STATUS_IS_AN_INTEGER);
    assertThatThrownBy(
            () -> read(Map.of(MANIFEST, "{\"a\":{" + provenance + ",\"headers\":[\"x\"]}}")))
        .isInstanceOf(MalformedVendorSampleException.class)
        .hasMessage("acme/a must declare its headers as an object");
    assertThatThrownBy(() -> read(Map.of(MANIFEST, "{\"a\":{" + provenance + ",\"status\":true}}")))
        .isInstanceOf(MalformedVendorSampleException.class)
        .hasMessage(A_STATUS_IS_AN_INTEGER);
    assertThatThrownBy(
            () -> read(Map.of(MANIFEST, "{\"a\":{" + provenance + ",\"excerpt\":\"yes\"}}")))
        .isInstanceOf(MalformedVendorSampleException.class)
        .hasMessage("acme/a must give excerpt true or false");
    assertThatThrownBy(
            () -> read(Map.of(MANIFEST, "{\"a\":{" + provenance + ",\"headers\":{\"X\":{}}}}")))
        .isInstanceOf(MalformedVendorSampleException.class)
        .hasMessage("acme/a must give X in its headers a text value");
  }

  @Test
  void a_text_field_given_a_non_scalar_value_is_refused_when_the_sample_loads() {
    String provenance = "\"evidence\":\"published example\",\"source\":\"s\",\"reference\":\"r\"";
    assertThatThrownBy(() -> read(Map.of(MANIFEST, "{\"a\":{" + provenance + ",\"note\":{}}}")))
        .isInstanceOf(MalformedVendorSampleException.class)
        .hasMessage("acme/a must give note a scalar value");
    assertThatThrownBy(
            () ->
                read(
                    Map.of(
                        MANIFEST,
                        "{\"a\":{\"evidence\":\"published example\",\"source\":true,"
                            + "\"reference\":\"r\"}}")))
        .isInstanceOf(MalformedVendorSampleException.class)
        .hasMessage("acme/a must give source a scalar value");
    assertThatThrownBy(
            () ->
                read(
                    Map.of(
                        MANIFEST,
                        "{\"a\":{\"evidence\":\"published example\",\"source\":[\"s\"],"
                            + "\"reference\":\"r\"}}")))
        .isInstanceOf(MalformedVendorSampleException.class)
        .hasMessage("acme/a must give source a scalar value");
  }

  @Test
  void the_samples_of_an_owner_are_read_from_the_classpath_by_its_name() {
    VendorSamples grow = VendorSamples.of("grow");

    assertThat(grow.sample("every-answer-is-http-200").rule("httpStatus").value()).isEqualTo("200");
    assertThat(grow.sample("create-payment-process-approved").json("/status").value())
        .isEqualTo("1");
    assertThatThrownBy(() -> VendorSamples.of("no-such-provider"))
        .isInstanceOf(VendorSampleNotFoundException.class)
        .hasMessage("vendor-samples/no-such-provider/samples.json does not exist");
  }

  @Test
  void a_published_rule_carries_the_values_it_publishes_and_a_check_reads_them_by_name() {
    VendorSample rule =
        read(Map.of(
                MANIFEST,
                "{\"resends\":{\"evidence\":\"published rule\",\"source\":\"s\","
                    + "\"reference\":\"r\",\"rules\":{\"additionalTimes\":\"5\"}}}"))
            .sample("resends");

    assertThat(rule.rule("additionalTimes").value()).isEqualTo("5");
    assertThat(rule.provenance().evidence()).isEqualTo(Evidence.of(EvidenceClass.PUBLISHED_RULE));
    assertThatThrownBy(() -> rule.rule("attempts"))
        .isInstanceOf(MalformedVendorSampleException.class)
        .hasMessage("acme/resends records no rule value attempts for a check to read");
  }

  @Test
  void a_published_rule_is_never_stored_as_an_answer_and_publishes_at_least_one_value() {
    String rule = "\"evidence\":\"published rule\",\"source\":\"s\",\"reference\":\"r\"";
    for (String answer :
        new String[] {
          "\"body\":\"a.json\"", "\"status\":200", "\"headers\":{}", "\"excerpt\":true"
        }) {
      assertThatThrownBy(
              () ->
                  read(
                      Map.of(
                          MANIFEST,
                          "{\"a\":{" + rule + ",\"rules\":{\"x\":\"1\"}," + answer + "}}")))
          .isInstanceOf(MalformedVendorSampleException.class)
          .hasMessageStartingWith("acme/a declares ")
          .hasMessageEndingWith(", which a published rule does not carry");
    }
    assertThatThrownBy(() -> read(Map.of(MANIFEST, "{\"a\":{" + rule + "}}")))
        .isInstanceOf(MalformedVendorSampleException.class)
        .hasMessage("acme/a is a published rule and names no value the rule publishes");
    assertThatThrownBy(() -> read(Map.of(MANIFEST, "{\"a\":{" + rule + ",\"rules\":{}}}")))
        .isInstanceOf(MalformedVendorSampleException.class)
        .hasMessage("acme/a is a published rule and names no value the rule publishes");
    assertThatThrownBy(() -> read(Map.of(MANIFEST, "{\"a\":{" + rule + ",\"rules\":{\"x\":1}}}")))
        .isInstanceOf(MalformedVendorSampleException.class)
        .hasMessage("acme/a must give x in its rules a text value");
  }

  @Test
  void only_a_published_rule_carries_rule_values_and_only_a_live_probe_is_dated() {
    String example = "\"evidence\":\"published example\",\"source\":\"s\",\"reference\":\"r\"";
    assertThatThrownBy(
            () -> read(Map.of(MANIFEST, "{\"a\":{" + example + ",\"rules\":{\"x\":\"1\"}}}")))
        .isInstanceOf(MalformedVendorSampleException.class)
        .hasMessage("acme/a declares rules, which a published example does not carry");
    assertThatThrownBy(
            () -> read(Map.of(MANIFEST, "{\"a\":{" + example + ",\"probed\":\"2026-09-10\"}}")))
        .isInstanceOf(MalformedVendorSampleException.class)
        .hasMessage("acme/a declares probed, which a published example does not carry");
  }

  @Test
  void a_live_probe_names_the_day_it_was_made_as_a_day() {
    String probe = "\"evidence\":\"live probe\",\"source\":\"s\",\"reference\":\"r\"";
    assertThatThrownBy(() -> read(Map.of(MANIFEST, "{\"a\":{" + probe + "}}")))
        .isInstanceOf(MalformedVendorSampleException.class)
        .hasMessage("acme/a declares no probed");
    assertThatThrownBy(
            () -> read(Map.of(MANIFEST, "{\"a\":{" + probe + ",\"probed\":\"10.09.2026\"}}")))
        .isInstanceOf(MalformedVendorSampleException.class)
        .hasMessage("acme/a gives probed 10.09.2026, which is not a day written as 2026-09-10");
  }

  @Test
  void the_simulators_own_contract_is_its_own_class_and_an_unknown_class_is_refused() {
    VendorSample own =
        VendorSamples.read(
                VendorSamples.SIMULATOR,
                Map.of(
                        "vendor-samples/simulator/samples.json",
                        "{\"own\":{\"evidence\":\"simulator contract\",\"source\":\"s\","
                            + "\"reference\":\"r\",\"status\":201}}")
                    ::get)
            .sample("own");

    assertThat(own.provenance().evidence())
        .isEqualTo(Evidence.of(EvidenceClass.SIMULATOR_CONTRACT));
    assertThat(own.status().value()).isEqualTo("201");
    assertThatThrownBy(
            () ->
                read(
                    Map.of(
                        MANIFEST,
                        "{\"a\":{\"evidence\":\"published rule, and a guess\",\"source\":\"s\","
                            + "\"reference\":\"r\"}}")))
        .isInstanceOf(MalformedVendorSampleException.class)
        .hasMessageStartingWith(
            "acme/a names evidence class published rule, and a guess, which is none of ");
  }

  @Test
  void a_sample_the_manifest_declares_twice_is_refused_rather_than_won_by_the_last() {
    String once =
        "{\"evidence\":\"published example\",\"source\":\"s\",\"reference\":\"r\"," + "\"status\":";
    assertThatThrownBy(
            () -> read(Map.of(MANIFEST, "{\"a\":" + once + "200},\"a\":" + once + "402}}")))
        .isInstanceOf(MalformedVendorSampleException.class)
        .hasMessageStartingWith(
            "vendor-samples/acme/samples.json is not JSON that names each member once");
    assertThatThrownBy(
            () ->
                read(
                    Map.of(
                        MANIFEST,
                        "{\"a\":{\"evidence\":\"published example\",\"source\":\"s\","
                            + "\"reference\":\"r\",\"status\":200,\"status\":402}}")))
        .isInstanceOf(MalformedVendorSampleException.class)
        .hasMessageStartingWith(
            "vendor-samples/acme/samples.json is not JSON that names each member once");
  }

  @Test
  void a_body_that_is_not_json_is_refused_naming_the_sample_when_a_check_reads_it_as_json() {
    VendorSample page =
        read(Map.of(
                MANIFEST,
                "{\"page\":{\"evidence\":\"published example\",\"source\":\"s\","
                    + "\"reference\":\"r\",\"body\":\"page.txt\"}}",
                "vendor-samples/acme/page.txt",
                "Sorry, due to technical fault"))
            .sample("page");

    assertThat(page.body().value()).isEqualTo("Sorry, due to technical fault");
    assertThatThrownBy(() -> page.json("/error"))
        .isInstanceOf(MalformedVendorSampleException.class)
        .hasMessage("acme/page has a body that is not JSON for a check to read /error");
  }

  @Test
  void the_simulator_contract_is_refused_in_a_provider_manifest() {
    assertThatThrownBy(
            () ->
                read(
                    Map.of(
                        MANIFEST,
                        "{\"a\":{\"evidence\":\"simulator contract\",\"source\":\"s\","
                            + "\"reference\":\"r\",\"status\":201}}")))
        .isInstanceOf(MalformedVendorSampleException.class)
        .hasMessageStartingWith("acme/a names evidence class simulator contract, and only the");
  }

  @Test
  void the_simulator_manifest_refuses_every_class_but_the_simulator_contract() {
    for (String vendorEvidence :
        new String[] {
          "\"evidence\":\"published example\"",
          "\"evidence\":\"live probe\",\"probed\":\"2026-09-10\"",
          "\"evidence\":\"published rule\",\"rules\":{\"x\":\"1\"}"
        }) {
      assertThatThrownBy(
              () ->
                  VendorSamples.read(
                      VendorSamples.SIMULATOR,
                      Map.of(
                              "vendor-samples/simulator/samples.json",
                              "{\"a\":{"
                                  + vendorEvidence
                                  + ",\"source\":\"s\",\"reference\":\"r\"}}")
                          ::get))
          .isInstanceOf(MalformedVendorSampleException.class)
          .hasMessageStartingWith("simulator/a names evidence class ")
          .hasMessageEndingWith(
              ", and only the simulator/ manifest carries the simulator contract, which is the"
                  + " only class it carries");
    }
  }

  @Test
  void two_headers_whose_names_differ_only_in_case_are_refused_when_the_sample_loads() {
    assertThatThrownBy(
            () ->
                read(
                    Map.of(
                        MANIFEST,
                        "{\"a\":{"
                            + AN_EXAMPLE
                            + ",\"headers\":{\"Content-Type\":\"text/html\","
                            + "\"content-type\":\"application/json\"}}}")))
        .isInstanceOf(MalformedVendorSampleException.class)
        .hasMessage("acme/a declares the header content-type twice, in two cases");
  }

  @Test
  void a_header_is_found_whatever_case_the_manifest_spelled_it_in() {
    VendorSample probed =
        read(Map.of(
                MANIFEST,
                "{\"a\":{" + AN_EXAMPLE + ",\"headers\":{\"www-authenticate\":\"Basic\"}}}"))
            .sample("a");

    assertThat(probed.header("WWW-Authenticate").value()).isEqualTo("Basic");
  }

  @Test
  void a_blank_header_value_or_rule_value_is_refused_when_the_sample_loads() {
    assertThatThrownBy(
            () -> read(Map.of(MANIFEST, "{\"a\":{" + AN_EXAMPLE + ",\"headers\":{\"X\":\" \"}}}")))
        .isInstanceOf(MalformedVendorSampleException.class)
        .hasMessage("acme/a gives X in its headers no text");
    assertThatThrownBy(
            () ->
                read(
                    Map.of(
                        MANIFEST,
                        "{\"a\":{\"evidence\":\"published rule\",\"source\":\"s\","
                            + "\"reference\":\"r\",\"rules\":{\"x\":\"\"}}}")))
        .isInstanceOf(MalformedVendorSampleException.class)
        .hasMessage("acme/a gives x in its rules no text");
  }

  @Test
  void a_status_that_is_not_an_integer_http_status_is_refused_when_the_sample_loads() {
    for (String notAStatus : new String[] {"200.5", "\"200\"", "99", "600", "-401", "1e3"}) {
      assertThatThrownBy(
              () ->
                  read(
                      Map.of(
                          MANIFEST, "{\"a\":{" + AN_EXAMPLE + ",\"status\":" + notAStatus + "}}")))
          .describedAs(notAStatus)
          .isInstanceOf(MalformedVendorSampleException.class)
          .hasMessage(A_STATUS_IS_AN_INTEGER);
    }
    for (String edge : new String[] {"100", "599"}) {
      assertThat(
              read(Map.of(MANIFEST, "{\"a\":{" + AN_EXAMPLE + ",\"status\":" + edge + "}}"))
                  .sample("a")
                  .status()
                  .value())
          .isEqualTo(edge);
    }
  }

  @Test
  void a_body_named_outside_the_providers_own_directory_is_refused_when_the_sample_loads() {
    for (String outside :
        new String[] {"../grow/a.json", "/etc/a.json", "..", "sub/a.json", "..\\\\a.json"}) {
      Map<String, String> files = new HashMap<>();
      files.put(MANIFEST, "{\"a\":{" + AN_EXAMPLE + ",\"body\":\"" + outside + "\"}}");
      files.put("vendor-samples/grow/a.json", "{}");
      files.put("vendor-samples/acme/sub/a.json", "{}");
      assertThatThrownBy(() -> read(files))
          .describedAs(outside)
          .isInstanceOf(MalformedVendorSampleException.class)
          .hasMessageStartingWith("acme/a names the body ")
          .hasMessageEndingWith(", which is not a file of its own directory");
    }
  }

  @Test
  void a_json_body_that_is_not_json_or_names_a_member_twice_is_refused_when_the_sample_loads() {
    String manifest = "{\"a\":{" + AN_EXAMPLE + ",\"body\":\"a.json\"}}";
    for (String unreadable : new String[] {"{\"error\":", "{\"id\":1,\"id\":2}", "{} {}"}) {
      assertThatThrownBy(
              () -> read(Map.of(MANIFEST, manifest, "vendor-samples/acme/a.json", unreadable)))
          .describedAs(unreadable)
          .isInstanceOf(MalformedVendorSampleException.class)
          .hasMessageStartingWith(
              "vendor-samples/acme/a.json is not JSON that names each member once");
    }
  }

  private static VendorSamples read(Map<String, String> files) {
    Map<String, String> resources = new HashMap<>(files);
    return VendorSamples.read("acme", resources::get);
  }
}
