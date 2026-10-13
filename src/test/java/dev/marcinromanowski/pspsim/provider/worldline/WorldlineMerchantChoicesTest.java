package dev.marcinromanowski.pspsim.provider.worldline;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;

import dev.marcinromanowski.pspsim.kernel.spi.AcceptedValue;
import dev.marcinromanowski.pspsim.kernel.spi.ConfigurableField;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BiFunction;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class WorldlineMerchantChoicesTest {

  @Test
  void the_two_path_versions_are_offered_as_the_string_a_client_has_to_sign() {
    assertThat(sentencesOf(WorldlineSignedPathVersion.SETTING))
        .as(
            "the label said \"which path version is signed\" and the two values spell the answer"
                + " out in enum names a tester has no way of reading")
        .anySatisfy(said -> assertThat(said).contains("/v2/"))
        .anySatisfy(said -> assertThat(said).contains("/v1/"));
  }

  @Test
  void the_three_landings_a_capture_comes_to_rest_on_name_the_status_category_and_code() {
    assertThat(WorldlineCaptureLanding.acceptedValues())
        .extracting(AcceptedValue::value)
        .containsExactly("CAPTURED_9", "CAPTURE_REQUESTED_91", "PENDING_CAPTURE_9");
    assertThat(sentencesOf(WorldlineCaptureLanding.SETTING))
        .anySatisfy(said -> assertThat(said).contains("CAPTURED").contains("COMPLETED"))
        .anySatisfy(
            said -> assertThat(said).contains("CAPTURE_REQUESTED").contains("statusCode 91"))
        .anySatisfy(
            said -> assertThat(said).contains("PENDING_CAPTURE").contains("PENDING_MERCHANT"));
  }

  @Test
  void what_a_call_is_authenticated_by_says_which_of_the_two_leaves_a_signature_unchecked() {
    assertThat(sentencesOf(WorldlineAuthentication.SETTING))
        .anySatisfy(said -> assertThat(said).contains("must carry an HMAC signature"))
        .anySatisfy(said -> assertThat(said).contains("go unchecked"));
  }

  @Test
  void the_values_offered_for_each_field_are_every_value_that_field_has() {
    assertThat(valuesOf(WorldlineSignedPathVersion.SETTING))
        .containsExactlyElementsOf(namesOf(WorldlineSignedPathVersion.values()));
    assertThat(valuesOf(WorldlineCaptureLanding.SETTING))
        .containsExactlyElementsOf(namesOf(WorldlineCaptureLanding.values()));
    assertThat(valuesOf(WorldlineAuthentication.SETTING))
        .containsExactlyElementsOf(namesOf(WorldlineAuthentication.values()));
  }

  @Test
  void every_value_a_field_offers_is_one_this_provider_takes() {
    takes(WorldlineSignedPathVersion.SETTING, WorldlineSignedPathVersion::of);
    takes(WorldlineCaptureLanding.SETTING, WorldlineCaptureLanding::of);
    takes(WorldlineAuthentication.SETTING, WorldlineAuthentication::of);
  }

  @ParameterizedTest
  @CsvSource({
    WorldlineMerchant.MERCHANT_ID_PROPERTY,
    WorldlineMerchant.API_KEY_PROPERTY,
    WorldlineMerchant.API_SECRET_PROPERTY
  })
  void a_field_whose_values_are_not_a_closed_set_offers_none_and_stays_a_box(String key) {
    assertThat(field(key).acceptedValues())
        .as("the PSPID and the key pair are issued to a merchant, not picked from a list")
        .isEmpty();
  }

  @Test
  void the_two_authentications_are_declared_with_what_each_one_still_checks() {
    assertThat(WorldlineAuthentication.acceptedValues())
        .containsExactly(
            new AcceptedValue(
                "THE_SIGNATURE_IS_VERIFIED",
                "every call must carry an HMAC signature this simulator can recompute, so a"
                    + " client that signs wrongly finds out here"),
            new AcceptedValue(
                "THE_SIGNATURE_IS_NOT_VERIFIED",
                "the apiKey must still name this account, but the signature and the Date go"
                    + " unchecked, so a test may drive the wire without signing"));
  }

  @Test
  void the_two_signed_path_versions_are_declared_with_the_worldline_page_each_one_comes_from() {
    assertThat(WorldlineSignedPathVersion.acceptedValues())
        .containsExactly(
            new AcceptedValue(
                "V2_AS_THE_SCHEMA_AND_THE_PROPERTY_TABLE_PUBLISH_IT",
                "your client must sign the path as /v2/..., the version worldline's schema and its"
                    + " own property table publish"),
            new AcceptedValue(
                "V1_AS_THE_WORKED_EXAMPLES_SIGN_IT",
                "your client must sign the path as /v1/..., the version worldline's worked"
                    + " signature examples use"));
  }

  private static void takes(String key, BiFunction<String, String, Object> parser) {
    for (String offered : valuesOf(key)) {
      assertThatNoException()
          .as(key + " offers " + offered)
          .isThrownBy(() -> parser.apply(key, offered));
    }
  }

  private static List<String> namesOf(Enum<?>[] values) {
    List<String> names = new ArrayList<>();
    for (Enum<?> value : values) {
      names.add(value.name());
    }
    return names;
  }

  private static List<String> valuesOf(String key) {
    List<String> values = new ArrayList<>();
    for (AcceptedValue accepted : field(key).acceptedValues()) {
      values.add(accepted.value());
    }
    return values;
  }

  private static List<String> sentencesOf(String key) {
    List<String> said = new ArrayList<>();
    for (AcceptedValue accepted : field(key).acceptedValues()) {
      said.add(accepted.whatItMeans());
    }
    return said;
  }

  private static ConfigurableField field(String key) {
    return WorldlineMerchant.ACCOUNT_FIELDS.field(key).orElseThrow();
  }
}
