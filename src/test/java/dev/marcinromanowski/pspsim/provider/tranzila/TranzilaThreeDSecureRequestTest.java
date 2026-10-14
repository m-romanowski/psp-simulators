package dev.marcinromanowski.pspsim.provider.tranzila;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class TranzilaThreeDSecureRequestTest {

  @Test
  void a_request_naming_nothing_more_than_activate_3ds_asks_the_published_defaults() {
    TranzilaThreeDSecure read = read("{}");

    assertThat(read.forceTxnOnFail()).isFalse();
    assertThat(read.forceChallenge()).isFalse();
    assertThat(read.redirectUrl()).isNull();
    assertThat(read.redirectParams()).isEmpty();
  }

  @Test
  void the_settings_are_read_as_the_3ds_schema_names_them() {
    TranzilaThreeDSecure read =
        read(
            "{\"3ds_settings\":{\"browser\":{\"java_enabled\":1,\"language\":\"en-gb\","
                + "\"color_depth\":24,\"screen_height\":934,\"screen_width\":0,\"time_zone\":-120,"
                + "\"ip\":\"10.0.0.1\",\"user_agent\":\"ua\",\"accept_header\":\"*/*\","
                + "\"window_size\":\"04\"},\"force_txn_on_3ds_fail\":\"Y\",\"force_challenge\":1}}");

    assertThat(read.forceTxnOnFail()).isTrue();
    assertThat(read.forceChallenge()).isTrue();
  }

  @Test
  void force_txn_on_3ds_fail_n_and_force_challenge_zero_are_read_as_not_asked() {
    TranzilaThreeDSecure read =
        read("{\"3ds_settings\":{\"force_txn_on_3ds_fail\":\"N\",\"force_challenge\":0}}");

    assertThat(read.forceTxnOnFail()).isFalse();
    assertThat(read.forceChallenge()).isFalse();
  }

  @Test
  void the_complete_examples_redirect_supplies_the_address_and_its_params_in_order() {
    TranzilaThreeDSecure read =
        read(
            "{\"auth_3ds_redirect\":{\"url\":\"https://test.com\",\"params\":["
                + "{\"key\":\"test\",\"value\":\"test\"},{\"key\":\"ttr\",\"value\":\"322\"},"
                + "{\"key\":\"test\",\"value\":\"again\"}]}}");

    assertThat(read.redirectUrl()).isEqualTo("https://test.com");
    assertThat(read.redirectParams())
        .containsExactly(Map.entry("test", "test"), Map.entry("ttr", "322"));
  }

  @Test
  void a_redirect_without_params_supplies_its_address_alone() {
    TranzilaThreeDSecure read = read("{\"auth_3ds_redirect\":{\"url\":\"http://shop.test/back\"}}");

    assertThat(read.redirectUrl()).isEqualTo("http://shop.test/back");
    assertThat(read.redirectParams()).isEmpty();
  }

  @Test
  void every_member_outside_the_3ds_schema_is_refused_with_its_own_words() {
    assertThat(refusalOf("{\"3ds_settings\":[]}")).isEqualTo("3ds_settings must be an object");
    assertThat(refusalOf("{\"3ds_settings\":{\"sto\":\"N\"}}"))
        .isEqualTo("3ds_settings.sto is not a field of the 3DS schema");
    assertThat(refusalOf("{\"3ds_settings\":{\"force_txn_on_3ds_fail\":\"y\"}}"))
        .isEqualTo("3ds_settings.force_txn_on_3ds_fail is neither Y nor N");
    assertThat(refusalOf("{\"3ds_settings\":{\"force_txn_on_3ds_fail\":true}}"))
        .isEqualTo("3ds_settings.force_txn_on_3ds_fail is neither Y nor N");
    assertThat(refusalOf("{\"3ds_settings\":{\"force_challenge\":2}}"))
        .isEqualTo("3ds_settings.force_challenge is neither 0 nor 1");
    assertThat(refusalOf("{\"3ds_settings\":{\"force_challenge\":\"1\"}}"))
        .isEqualTo("force_challenge must be an integer");
    assertThat(refusalOf("{\"3ds_settings\":{\"browser\":\"x\"}}"))
        .isEqualTo("3ds_settings.browser must be an object");
    assertThat(refusalOf("{\"3ds_settings\":{\"browser\":{\"language\":1}}}"))
        .isEqualTo("3ds_settings.browser.language must be a string");
    assertThat(refusalOf("{\"3ds_settings\":{\"browser\":{\"screen_height\":-1}}}"))
        .isEqualTo("3ds_settings.browser.screen_height must not be negative");
    assertThat(refusalOf("{\"3ds_settings\":{\"browser\":{\"screen_width\":-1}}}"))
        .isEqualTo("3ds_settings.browser.screen_width must not be negative");
    assertThat(refusalOf("{\"3ds_settings\":{\"browser\":{\"java_enabled\":2}}}"))
        .isEqualTo("3ds_settings.browser.java_enabled is neither 0 nor 1");
    assertThat(refusalOf("{\"3ds_settings\":{\"browser\":{\"color_depth\":5}}}"))
        .isEqualTo("3ds_settings.browser.color_depth is none of 1, 4, 8, 15, 16, 24, 32 and 48");
    assertThat(refusalOf("{\"3ds_settings\":{\"browser\":{\"time_zone\":\"UTC\"}}}"))
        .isEqualTo("time_zone must be an integer");
    assertThat(refusalOf("{\"3ds_settings\":{\"browser\":{\"b_language\":\"en\"}}}"))
        .isEqualTo("3ds_settings.browser.b_language is not a field of the 3DS schema");
    assertThat(refusalOf("{\"3ds_settings\":{\"auth_3ds_redirect\":{\"url\":\"https://a.b\"}}}"))
        .isEqualTo("auth_3ds_redirect must be an array");
  }

  @Test
  void the_redirect_is_refused_unless_it_is_the_object_the_complete_example_carries() {
    assertThat(refusalOf("{\"auth_3ds_redirect\":[{\"key\":\"a\",\"value\":\"b\"}]}"))
        .isEqualTo(
            "auth_3ds_redirect at the top of the body is the object {url, params} the complete"
                + " example carries; as an array of {key, value} it belongs in 3ds_settings");
    assertThat(refusalOf("{\"auth_3ds_redirect\":{\"url\":\"https://a.b\",\"method\":\"GET\"}}"))
        .isEqualTo("auth_3ds_redirect.method is not a field of the complete example");
    assertThat(refusalOf("{\"auth_3ds_redirect\":{\"params\":[]}}"))
        .isEqualTo("auth_3ds_redirect.url is required");
    assertThat(refusalOf("{\"auth_3ds_redirect\":{\"url\":\"/back\"}}"))
        .isEqualTo(
            "auth_3ds_redirect.url is not an absolute http or https address a browser could be"
                + " sent to");
    assertThat(refusalOf("{\"auth_3ds_redirect\":{\"url\":\"https://a.b\",\"params\":{}}}"))
        .isEqualTo("params must be an array");
    assertThat(refusalOf("{\"auth_3ds_redirect\":{\"url\":\"https://a.b\",\"params\":[1]}}"))
        .isEqualTo("every element of params is an object");
    assertThat(
            refusalOf(
                "{\"auth_3ds_redirect\":{\"url\":\"https://a.b\",\"params\":"
                    + "[{\"key\":\"a\",\"value\":\"b\",\"c\":\"d\"}]}}"))
        .isEqualTo("params.c is not a field of {key, value}");
    assertThat(
            refusalOf(
                "{\"auth_3ds_redirect\":{\"url\":\"https://a.b\",\"params\":"
                    + "[{\"key\":1,\"value\":\"b\"}]}}"))
        .isEqualTo("params.key must be a string");
    assertThat(
            refusalOf(
                "{\"auth_3ds_redirect\":{\"url\":\"https://a.b\",\"params\":"
                    + "[{\"key\":\"a\",\"value\":322}]}}"))
        .isEqualTo("params.value must be a string");
  }

  @Test
  void a_request_not_asking_for_3ds_still_has_its_3ds_members_judged_and_kept_out_of_the_charge() {
    String body =
        TranzilaWire.aDebit()
            .replace(
                "{\"terminal",
                "{\"auth_3ds_redirect\":{\"url\":\"https://a.b\"},"
                    + "\"3ds_settings\":{\"force_challenge\":1},\"terminal");

    TranzilaCreateRequest read = TranzilaCreateRequest.read(json(body));

    assertThat(read.threeDSecure()).isNull();
    assertThatThrownBy(
            () ->
                TranzilaCreateRequest.read(
                    json(
                        TranzilaWire.aDebit()
                            .replace("{\"terminal", "{\"3ds_settings\":1,\"terminal"))))
        .isInstanceOf(TranzilaSchemaMismatch.class)
        .hasMessage("3ds_settings must be an object");
  }

  @Test
  void the_browser_admits_each_integer_as_its_own_enum_or_bound() {
    assertThat(TranzilaThreeDSecure.theBrowserAdmits("java_enabled", 0)).isTrue();
    assertThat(TranzilaThreeDSecure.theBrowserAdmits("java_enabled", 1)).isTrue();
    assertThat(TranzilaThreeDSecure.theBrowserAdmits("java_enabled", 2)).isFalse();
    assertThat(TranzilaThreeDSecure.theBrowserAdmits("color_depth", 48)).isTrue();
    assertThat(TranzilaThreeDSecure.theBrowserAdmits("color_depth", 2)).isFalse();
    assertThat(TranzilaThreeDSecure.theBrowserAdmits("time_zone", -720)).isTrue();
    assertThat(TranzilaThreeDSecure.theBrowserAdmits("screen_height", 0)).isTrue();
    assertThat(TranzilaThreeDSecure.theBrowserAdmits("screen_height", -1)).isFalse();
  }

  @Test
  void the_hosted_fields_force_the_transaction_unless_told_n_and_challenge_only_on_one() {
    assertThat(TranzilaHostedFieldsRequest.theThreeDSecureIn(Map.of()))
        .isEqualTo(TranzilaThreeDSecure.asTheHostedFieldsAsk(true, false));
    assertThat(
            TranzilaHostedFieldsRequest.theThreeDSecureIn(
                Map.of("force_txn_on_3ds_fail", List.of("N"), "force_challenge", List.of("1"))))
        .isEqualTo(TranzilaThreeDSecure.asTheHostedFieldsAsk(false, true));
    assertThat(
            TranzilaHostedFieldsRequest.theThreeDSecureIn(
                Map.of("force_txn_on_3ds_fail", List.of("Y"), "force_challenge", List.of("0"))))
        .isEqualTo(TranzilaThreeDSecure.asTheHostedFieldsAsk(true, false));
  }

  private static TranzilaThreeDSecure read(String body) {
    TranzilaJson.JsonObject parsed = json(body);
    TranzilaThreeDSecure.refuseWhatTheSchemaDoesNotAdmit(parsed);
    return TranzilaThreeDSecure.read(parsed);
  }

  private static String refusalOf(String body) {
    try {
      read(body);
    } catch (TranzilaSchemaMismatch refused) {
      return refused.why();
    }
    throw new AssertionError(body + " was admitted");
  }

  private static TranzilaJson.JsonObject json(String body) {
    return TranzilaJsonReader.objectIn(body).orElseThrow();
  }
}
