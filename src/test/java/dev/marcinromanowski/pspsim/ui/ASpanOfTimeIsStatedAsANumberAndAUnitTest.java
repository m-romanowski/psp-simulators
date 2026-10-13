package dev.marcinromanowski.pspsim.ui;

import static org.assertj.core.api.Assertions.assertThat;

import dev.marcinromanowski.pspsim.kernel.ProviderId;
import dev.marcinromanowski.pspsim.kernel.spi.ConfigurableField;
import dev.marcinromanowski.pspsim.kernel.spi.ProviderAccountFields;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.springframework.ui.ConcurrentModel;
import org.springframework.ui.Model;

class ASpanOfTimeIsStatedAsANumberAndAUnitTest {

  private static final String A_RUN = "a-run";

  private static final String PROVIDER = "aprovider";

  private static final ProviderAccountFields FIELDS =
      new ProviderAccountFields(
          ProviderId.of(PROVIDER),
          List.of(
              new ConfigurableField(
                  "terminalId",
                  "Terminal id",
                  "a sentence this test does not read",
                  true,
                  false,
                  List.of(),
                  "a value a test does not read"),
              ConfigurableField.aDuration(
                  "captureWindow",
                  "how long a hold is capturable",
                  "a sentence this test does not read",
                  false,
                  "P7D")));

  @Test
  void the_control_a_span_is_stated_on_is_a_number_beside_the_units_core_offers() {
    String drawn = theAccountFormOn(Map.of());

    assertThat(drawn)
        .as(
            "a tester typed P7D into a text box and read PT168H back; the box now hints the"
                + " number the provider publishes, and the unit beside it is picked")
        .contains("name=\"field.captureWindow\"")
        .contains("placeholder=\"7\"")
        .contains("inputmode=\"numeric\"")
        .contains("name=\"unitOf.captureWindow\"")
        .doesNotContain("placeholder=\"P7D\"");
    assertThat(theUnitsOfferedOn(drawn)).containsExactly("seconds", "minutes", "hours", "days");
    assertThat(theUnitSelectedOn(drawn))
        .as("the provider publishes P7D, so the form opens on the unit it counts that in")
        .isEqualTo("DAYS");
  }

  @Test
  void a_field_that_is_no_span_of_time_is_still_the_box_it_was() {
    String drawn = theAccountFormOn(Map.of());

    assertThat(drawn)
        .as(
            "the box keeps the one control it had, and hints the example the provider wrote"
                + " rather than the key the provider reads it under")
        .contains("name=\"field.terminalId\"")
        .contains("placeholder=\"a value a test does not read\"")
        .doesNotContain("placeholder=\"terminalId\"");
    assertThat(drawn)
        .as("only a span of time is drawn with a unit beside it")
        .doesNotContain("name=\"unitOf.terminalId\"");
  }

  @Test
  void a_span_a_refused_form_comes_back_with_is_drawn_as_the_number_and_unit_it_names() {
    String drawn = theAccountFormOn(Map.of("captureWindow", "PT90M"));

    assertThat(drawn).contains("name=\"field.captureWindow\" value=\"90\"");
    assertThat(theUnitSelectedOn(drawn)).isEqualTo("MINUTES");
  }

  @Test
  void the_control_pre_validates_nothing_the_provider_is_there_to_refuse() {
    String drawn = theAccountFormOn(Map.of());

    assertThat(theControlOn(drawn))
        .as(
            "the provider is the validator: a box this form blocks a submit on, empties a paste"
                + " into or bounds in the browser would take a refusal away from the provider"
                + " that was written for a tester to read, and every other box of this UI is a"
                + " plain text input for that reason")
        .doesNotContain("type=\"number\"")
        .doesNotContain("min=")
        .doesNotContain("max=")
        .doesNotContain("step=")
        .doesNotContain("pattern=")
        .doesNotContain("required");
    assertThat(drawn)
        .as("and nothing on the form turns constraint validation on for the page either")
        .doesNotContain("novalidate")
        .doesNotContain("type=\"number\"");
  }

  private static String theControlOn(String drawn) {
    Matcher control =
        Pattern.compile("<input[^>]*name=\"field\\.captureWindow\"[^>]*>").matcher(drawn);
    assertThat(control.find()).as("the form draws a box for the span").isTrue();
    return control.group();
  }

  private static List<String> theUnitsOfferedOn(String drawn) {
    List<String> offered = new ArrayList<>();
    Matcher unit = Pattern.compile("<option[^>]*>([a-z]+)</option>").matcher(theUnitListOn(drawn));
    while (unit.find()) {
      offered.add(unit.group(1));
    }
    return offered;
  }

  private static String theUnitSelectedOn(String drawn) {
    Matcher selected =
        Pattern.compile("<option[^>]*value=\"([A-Z]+)\"[^>]*selected", Pattern.DOTALL)
            .matcher(theUnitListOn(drawn));
    assertThat(selected.find()).as("one unit is selected when the form is drawn").isTrue();
    return selected.group(1);
  }

  private static String theUnitListOn(String drawn) {
    Matcher list =
        Pattern.compile("name=\"unitOf\\.captureWindow\"(.*?)</select>", Pattern.DOTALL)
            .matcher(drawn);
    assertThat(list.find()).as("the form draws a unit list for the span").isTrue();
    return list.group(1);
  }

  private static String theAccountFormOn(Map<String, String> submitted) {
    Model model = new ConcurrentModel();
    model.addAttribute("providers", List.of(PROVIDER));
    model.addAttribute("path", "/ui/configuration/account");
    model.addAttribute("screen", "configuration");
    model.addAttribute("provider", PROVIDER);
    model.addAttribute("testRunId", A_RUN);
    model.addAttribute("accountId", "merchant-1");
    model.addAttribute(
        "account",
        AccountForm.of(
            PROVIDER,
            A_RUN,
            "merchant-1",
            FIELDS,
            submitted,
            Map.of(),
            List.of(),
            null,
            List.of(),
            null));
    return ScreensThisTestDraws.drawn(
        "account", model, new ContributedScreens(List.of()), PROVIDER);
  }
}
