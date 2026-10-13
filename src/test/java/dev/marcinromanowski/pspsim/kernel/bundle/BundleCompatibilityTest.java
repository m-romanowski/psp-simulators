package dev.marcinromanowski.pspsim.kernel.bundle;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class BundleCompatibilityTest {

  private static final BundleCompatibility DECLARED_HERE =
      new BundleCompatibility(
          List.of("secretKey", "capture.window"),
          List.of("payment_method"),
          List.of("webhook.retryPlan"),
          List.of("FIXED_SUCCESS", "DELIVERY_DELAYED"));

  @Test
  void a_bundle_written_against_exactly_what_this_build_declares_is_refused_over_nothing() {
    assertThat(DECLARED_HERE.theTermsThisBuildNoLongerDeclares(DECLARED_HERE)).isEmpty();
  }

  @Test
  void an_account_field_this_build_no_longer_declares_is_named_with_the_group_it_sat_in() {
    BundleCompatibility written =
        new BundleCompatibility(
            List.of("secretKey", "apiSecret"),
            List.of("payment_method"),
            List.of("webhook.retryPlan"),
            List.of("FIXED_SUCCESS"));

    assertThat(written.theTermsThisBuildNoLongerDeclares(DECLARED_HERE))
        .extracting(BundleRefusal::property)
        .containsExactly("compatibility.accountFields.apiSecret");
  }

  @Test
  void a_payment_method_field_a_setting_and_an_action_type_are_each_named_in_their_own_group() {
    BundleCompatibility written =
        new BundleCompatibility(
            List.of("secretKey"),
            List.of("token"),
            List.of("webhook.retryPlan", "webhook.ceiling"),
            List.of("PROCESSING_THEN_TERMINAL"));

    assertThat(written.theTermsThisBuildNoLongerDeclares(DECLARED_HERE))
        .extracting(BundleRefusal::property)
        .containsExactly(
            "compatibility.paymentMethodFields.token",
            "compatibility.settings.webhook.ceiling",
            "compatibility.actionTypes.PROCESSING_THEN_TERMINAL");
  }

  @Test
  void a_term_this_build_has_added_since_the_bundle_was_written_refuses_the_bundle_over_nothing() {
    BundleCompatibility written =
        new BundleCompatibility(List.of("secretKey"), List.of(), List.of(), List.of());

    assertThat(written.theTermsThisBuildNoLongerDeclares(DECLARED_HERE)).isEmpty();
  }

  @Test
  void a_compatibility_carrying_no_list_at_all_names_no_term_and_is_refused_over_nothing() {
    BundleCompatibility written = new BundleCompatibility(null, null, null, null);

    assertThat(written.carriedAccountFields()).isEmpty();
    assertThat(written.carriedPaymentMethodFields()).isEmpty();
    assertThat(written.carriedSettings()).isEmpty();
    assertThat(written.carriedActionTypes()).isEmpty();
    assertThat(written.theTermsThisBuildNoLongerDeclares(DECLARED_HERE)).isEmpty();
  }

  @Test
  void a_build_that_declares_nothing_refuses_every_term_the_bundle_was_written_against() {
    BundleCompatibility nothingHere =
        new BundleCompatibility(List.of(), List.of(), List.of(), List.of());

    assertThat(DECLARED_HERE.theTermsThisBuildNoLongerDeclares(nothingHere))
        .extracting(BundleRefusal::property)
        .containsExactly(
            "compatibility.accountFields.secretKey",
            "compatibility.accountFields.capture.window",
            "compatibility.paymentMethodFields.payment_method",
            "compatibility.settings.webhook.retryPlan",
            "compatibility.actionTypes.FIXED_SUCCESS",
            "compatibility.actionTypes.DELIVERY_DELAYED");
  }

  @Test
  void a_refusal_over_a_moved_term_says_why_the_bundle_could_not_be_read_rather_than_nothing() {
    BundleCompatibility written =
        new BundleCompatibility(List.of("apiSecret"), List.of(), List.of(), List.of());

    assertThat(written.theTermsThisBuildNoLongerDeclares(DECLARED_HERE))
        .singleElement()
        .extracting(BundleRefusal::reason)
        .isEqualTo(BundleCompatibility.A_TERM_THIS_BUILD_NO_LONGER_DECLARES);
  }

  @Test
  void the_lists_a_compatibility_was_built_from_do_not_change_it_afterwards() {
    List<String> fields = new ArrayList<>(List.of("secretKey"));
    BundleCompatibility written = new BundleCompatibility(fields, List.of(), List.of(), List.of());
    fields.add("apiSecret");

    assertThat(written.carriedAccountFields()).containsExactly("secretKey");
  }
}
