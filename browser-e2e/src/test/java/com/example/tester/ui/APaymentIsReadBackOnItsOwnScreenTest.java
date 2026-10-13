package com.example.tester.ui;

import static org.assertj.core.api.Assertions.assertThat;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.assertions.PlaywrightAssertions;
import com.microsoft.playwright.options.AriaRole;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class APaymentIsReadBackOnItsOwnScreenTest extends AJourneyInARealBrowser {

  private static final String AUTRA = "autra";

  private static final String MERCHANT = "merchant-read-back";

  private static final String DOCUMENT = "12345678000177";

  private static final String THE_TAB = "Read back";

  private static final String A_HOLD = "Reserve the amount on the card and capture it later";

  private static final String RESERVED = "640.00";

  private static final Pattern THE_OBJECT_THE_WIZARD_STARTED =
      Pattern.compile("[?&]started=([^&]+)");

  @Test
  @DisplayName(
      "a tester reserves an amount and reads the payment and its timeline back by clicking")
  void aPaymentIsListedAndReadBackOnTheScreenThatIsAboutReadingThemBack() {
    setup().configureAnAutraMerchant(testRunId(), MERCHANT, "a-client", "a-secret", DOCUMENT);

    String paymentId = aHoldIsReservedFromTheWizard();

    page().navigate(at("/ui/objects?provider=" + AUTRA + "&testRunId=" + testRunId()));
    page()
        .getByRole(AriaRole.LINK, new Page.GetByRoleOptions().setName(THE_TAB).setExact(true))
        .click();

    assertThat(page().url())
        .as("the tab of a contributed screen goes straight to that screen and to no wizard")
        .contains("/ui/x/autra/read-back");
    PlaywrightAssertions.assertThat(
            page()
                .getByRole(
                    AriaRole.HEADING,
                    new Page.GetByRoleOptions()
                        .setName("What a merchant's own app can ask autra about a payment")))
        .isVisible();

    String read = page().innerText("body");
    assertThat(read)
        .as(
            "a tester must be able to read a payment back by clicking, so every property the"
                + " single read answers and has a value for is on this screen under the name"
                + " autra answers it under, bar errors, whose refusals are printed on the steps"
                + " of the timeline they happened on")
        .contains("paymentID")
        .contains(paymentId)
        .contains("installments")
        .contains("createdAt")
        .contains("currency")
        .contains("PREAUTH")
        .contains("ACCEPTED")
        .contains(RESERVED)
        .contains("voidRetryable")
        .contains("refundedAmount")
        .contains("transactionMethod")
        .contains("authorizationCode")
        .contains("rrn")
        .contains("documentId")
        .contains(DOCUMENT);
    assertThat(read)
        .as("the timeline is the half of the single read that no other screen of this build shows")
        .contains("The timeline autra answers under")
        .contains("PREAUTH ACCEPTED " + RESERVED);
    assertThat(read)
        .as(
            "the one property autra publishes that this payment answers with nothing is named on"
                + " the screen, so a tester does not read its absence as a bug")
        .contains("maskedCard")
        .contains("brandReferenceId")
        .contains("errors");
    assertThat(read)
        .as("the listing's published use case is finding the reservations still open")
        .contains("Pre-authorizations still open");

    assertThat(theOpenReservationsPanel())
        .as("an open reservation is one a void would still release")
        .contains(paymentId);

    thePageDoesNotScrollSidewaysAtPhoneWidth("the Read back screen");

    theReservationIsCapturedOnThePaymentsScreen();
    page().navigate(at("/ui/x/autra/read-back?provider=" + AUTRA + "&testRunId=" + testRunId()));

    assertThat(page().innerText("body"))
        .as("a captured hold is still a PREAUTH and autra says so after the capture")
        .contains("PREAUTH")
        .contains("CAPTURED");
    assertThat(theOpenReservationsPanel())
        .as(
            "the panel's own prose calls these reservations still holding a cardholder's limit,"
                + " and a captured hold holds none; the API equivalent the panel names,"
                + " ?status=ACCEPTED&type=PREAUTH, excludes it, so a screen that still listed it"
                + " would disagree with the route it stands for")
        .doesNotContain(paymentId);

    page()
        .getByRole(
            AriaRole.LINK,
            new Page.GetByRoleOptions()
                .setName("Read everything this simulator holds about this payment"))
        .first()
        .click();

    assertThat(page().url())
        .as("the screen hands a tester on to the paged read rather than paging itself")
        .contains("/ui/objects")
        .contains(paymentId);

    theBrowserSawNothingGoWrong();
  }

  private String theOpenReservationsPanel() {
    return page()
        .locator("section.panel")
        .filter(new Locator.FilterOptions().setHasText("Pre-authorizations still open"))
        .innerText();
  }

  private void theReservationIsCapturedOnThePaymentsScreen() {
    page()
        .getByRole(
            AriaRole.LINK,
            new Page.GetByRoleOptions()
                .setName("Read everything this simulator holds about this payment"))
        .first()
        .click();
    Locator capture =
        page()
            .locator("form[action^='/ui/objects/act']")
            .filter(
                new Locator.FilterOptions()
                    .setHas(page().locator("input[name='action'][value='CAPTURE']")));
    capture.locator("input[name='confirm']").check();
    capture.locator("button[type='submit']").click();
  }

  private String aHoldIsReservedFromTheWizard() {
    page().navigate(at("/ui/objects?provider=" + AUTRA + "&testRunId=" + testRunId()));
    page()
        .getByRole(
            AriaRole.LINK, new Page.GetByRoleOptions().setName("Start a payment").setExact(true))
        .click();
    page().getByLabel("the account this payment is started on").selectOption(MERCHANT);
    page().getByLabel(A_HOLD).check();
    page()
        .getByRole(
            AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Next: what this payment says"))
        .click();
    page().getByLabel("amount to reserve").fill(RESERVED);
    page().getByLabel("currency").fill("BRL");
    page()
        .getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Start this payment"))
        .click();

    PlaywrightAssertions.assertThat(
            page()
                .getByRole(
                    AriaRole.HEADING,
                    new Page.GetByRoleOptions().setName("This payment exists now")))
        .isVisible();
    Matcher started = THE_OBJECT_THE_WIZARD_STARTED.matcher(page().url());
    assertThat(started.find())
        .as("the wizard names the object it started in the address it sends the browser to")
        .isTrue();
    return started.group(1);
  }
}
