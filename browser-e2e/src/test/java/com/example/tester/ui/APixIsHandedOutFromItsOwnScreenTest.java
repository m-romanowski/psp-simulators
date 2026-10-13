package com.example.tester.ui;

import static org.assertj.core.api.Assertions.assertThat;

import com.microsoft.playwright.Page;
import com.microsoft.playwright.assertions.PlaywrightAssertions;
import com.microsoft.playwright.options.AriaRole;
import java.nio.file.Path;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class APixIsHandedOutFromItsOwnScreenTest extends AJourneyInARealBrowser {

  private static final String AUTRA = "autra";

  private static final String MERCHANT = "merchant-pix";

  private static final String THE_TAB = "Pix";

  private static final String THE_PAYER_STEP_FORM = "form[action^='/ui/objects/complete']";

  private static final String THE_PAYER_PAID_IT =
      "the payer pasted the payload into their own bank app and paid it";

  @Test
  @DisplayName("a tester hands out a Pix and takes it to SETTLED without leaving the Pix screen")
  void aPixIsStartedPaidAndSettledFromTheScreenThatIsAboutPix() {
    setup()
        .configureAnAutraMerchant(testRunId(), MERCHANT, "a-client", "a-secret", "12345678000199");

    page().navigate(at("/ui/objects?provider=" + AUTRA + "&testRunId=" + testRunId()));
    page()
        .getByRole(AriaRole.LINK, new Page.GetByRoleOptions().setName(THE_TAB).setExact(true))
        .click();

    PlaywrightAssertions.assertThat(
            page()
                .getByRole(
                    AriaRole.HEADING,
                    new Page.GetByRoleOptions().setName("Hand out a Pix payload")))
        .isVisible();
    assertThat(page().url())
        .as("the tab of a contributed screen goes straight to that screen and to no wizard")
        .contains("/ui/x/autra/pix");

    page().getByLabel("amount to collect").fill("99.90");
    page().getByLabel("currency").fill("BRL");
    page().getByLabel("what the payer sees in their bank app").fill("Pedido do teste");
    page()
        .getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Hand out a Pix payload"))
        .click();

    assertThat(page().url())
        .as("a form of a contributed screen comes back to the screen it was submitted from")
        .contains("/ui/x/autra/pix");
    PlaywrightAssertions.assertThat(page().getByText("This payment exists now")).isVisible();
    PlaywrightAssertions.assertThat(page().getByText("PENDING").first()).isVisible();
    assertThat(page().innerText("body"))
        .as("a deadline printed as a bare UTC instant is a deadline a tester has to work out")
        .containsPattern("in \\d+ (second|minute)");
    assertThat(theCopyAndPastePayloadOnTheScreen())
        .as(
            "a Pix is paid by the payer copying this payload into their own bank app, so the"
                + " screen a tester opens for a Pix is the screen that has to carry it")
        .startsWith("0002")
        .contains("BR.GOV.BCB.PIX");

    theWholeScreenIsReadAt(TheBrowser.A_PHONE_IS_THIS_WIDE, TheBrowser.A_PHONE_IS_THIS_TALL);
    theWholeScreenIsReadAt(1280, 700);

    assertThat(page().innerText(THE_PAYER_STEP_FORM))
        .as("a screen that says a Pix has no page must not offer the payer step in words about one")
        .doesNotContain("on the page")
        .doesNotContain("left the page without paying");
    assertThat(page().getByLabel("the payer met a decline on the page").count())
        .as(
            "autra gives a Pix no declined state, so offering that choice offers one that must"
                + " fail and tells the tester only after the fact")
        .isZero();
    assertThat(page().locator("input[name='outcome']").count())
        .as("a Pix admits exactly the two steps autra publishes for one")
        .isEqualTo(2);
    assertThat(page().innerText("body")).contains(THE_PAYER_PAID_IT);
    assertThat(page().innerText(THE_PAYER_STEP_FORM))
        .as(
            "the caution a tester ticks confirm against belongs beside the choices they tick it"
                + " for, and not only in the refusal they read when they forget to")
        .contains("tick confirm before taking this payer step")
        .contains("a Pix collects no card, so none is named here and nothing is charged")
        .contains("none of it can be undone by leaving this screen")
        .doesNotContain("charges the card");
    assertThat(page().innerText("body"))
        .as(
            "the reason autra offers no decline on a Pix went missing with the choice itself, and"
                + " a tester could reach it only by forging a request for a step no form draws")
        .contains("Autra offers no payer step on this one where the payer met a decline")
        .contains("it has no declined state at all")
        .contains("leaves it to expire, so name that outcome instead of a decline autra never");

    page().getByLabel(THE_PAYER_PAID_IT).check();
    page().getByLabel("confirm").first().check();
    page()
        .getByRole(
            AriaRole.BUTTON,
            new Page.GetByRoleOptions().setName("Stand in for the payer's bank app"))
        .click();

    assertThat(page().url()).contains("/ui/x/autra/pix");
    PlaywrightAssertions.assertThat(page().getByText("AUTHORIZED").first()).isVisible();
    assertThat(page().innerText("div.done"))
        .as("the banner after a payer step says what was done to the object it was applied to")
        .isEqualTo("This payer step is taken: " + THE_PAYER_PAID_IT + ".");

    page().getByLabel("confirm").first().check();
    page()
        .getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Ask autra to settle"))
        .click();

    assertThat(page().url()).contains("/ui/x/autra/pix");
    PlaywrightAssertions.assertThat(page().getByText("SETTLED").first()).isVisible();
    assertThat(page().innerText("body"))
        .as(
            "a settled Pix stopped working when it settled, and the row counted down to its"
                + " expiresAt as though a payer could still pay it")
        .doesNotContain("when the payload stops working")
        .contains("the payload stopped working whether or not");
    theWholeScreenIsReadAt(1280, 700, "pix-settled");
    assertThat(page().innerText("body"))
        .as("the sentence under an id says what a Pix is, so it is still true of a settled one")
        .contains("What one of these is, in the words of autra:")
        .contains("A Pix is the Brazilian instant transfer")
        .doesNotContain("A Pix waiting for its payer");

    theBrowserSawNothingGoWrong();
    setup().forgetTheTestRunAndItsAccount(AUTRA, testRunId(), MERCHANT);
  }

  @Test
  @DisplayName("a submit this simulator refuses is answered on the Pix screen and on no other")
  void aRefusedSubmitIsAnsweredOnTheScreenTheFormWasOn() {
    setup()
        .configureAnAutraMerchant(testRunId(), MERCHANT, "a-client", "a-secret", "12345678000199");

    page().navigate(at("/ui/x/autra/pix?testRunId=" + testRunId()));
    assertThat(page().locator("#provider").count())
        .as("a provider switcher that discarded the choice it was given is worse than none")
        .isZero();
    PlaywrightAssertions.assertThat(
            page()
                .getByRole(
                    AriaRole.LINK, new Page.GetByRoleOptions().setName("choose another provider")))
        .isVisible();

    page().getByLabel("currency").fill("BRL");
    page().getByLabel("what the payer sees in their bank app").fill("Pedido 7");
    page()
        .getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Hand out a Pix payload"))
        .click();

    PlaywrightAssertions.assertThat(page().locator("div.refusal")).isVisible();
    assertThat(page().innerText("div.refusal"))
        .as(
            "the amount box carried required, so no browser could submit it empty and the"
                + " refusal naming what this way cannot start without reached no clicking tester")
        .contains("cannot start without")
        .contains("amount to collect");

    page().getByLabel("amount to collect").fill("abc");
    page().getByLabel("currency").fill("BRL");
    page().getByLabel("what the payer sees in their bank app").fill("Pedido 7");
    page()
        .getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Hand out a Pix payload"))
        .click();

    PlaywrightAssertions.assertThat(page().locator("div.refusal")).isVisible();
    assertThat(page().innerText("div.refusal"))
        .as(
            "a number this provider cannot read is refused by the provider, and it read"
                + " INVALID_JSON with \"the request body is not a JSON object this endpoint can"
                + " read\" about a body no tester sent and no JSON mistake")
        .contains("INVALID_AMOUNT")
        .contains("amount is required and must be a number above zero")
        .doesNotContain("INVALID_JSON")
        .doesNotContain("not a JSON object");
    PlaywrightAssertions.assertThat(page().getByLabel("amount to collect")).hasValue("abc");
    PlaywrightAssertions.assertThat(page().getByLabel("currency")).hasValue("BRL");
    PlaywrightAssertions.assertThat(page().getByLabel("what the payer sees in their bank app"))
        .hasValue("Pedido 7");
    theWholeScreenIsReadAt(1280, 700, "pix-refused-start");

    page().getByLabel("amount to collect").fill("12.34");
    page().getByLabel("currency").fill("BRL");
    page()
        .getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Hand out a Pix payload"))
        .click();

    page().getByLabel(THE_PAYER_PAID_IT).check();
    page()
        .getByRole(
            AriaRole.BUTTON,
            new Page.GetByRoleOptions().setName("Stand in for the payer's bank app"))
        .click();

    assertThat(page().url())
        .as("a refusal belongs on the screen the tester was on, beside the form they filled")
        .contains("/ui/x/autra/pix");
    PlaywrightAssertions.assertThat(page().locator("div.refusal")).isVisible();
    assertThat(page().innerText("div.refusal"))
        .as(
            "the caution a tester ticks confirm against has to be true of a Pix, which collects no"
                + " card and has no page to finish or to close")
        .contains("tick confirm before taking this payer step")
        .contains("a Pix collects no card, so none is named here and nothing is charged")
        .doesNotContain("charges the card")
        .doesNotContain("finishing this page")
        .doesNotContain("closing the page");
    assertThat(theTabThisScreenMarksAsItsOwn())
        .as("a refusal that marked another tab as current is the navigation jumping around")
        .isEqualTo(THE_TAB);
    assertThat(page().getByLabel(THE_PAYER_PAID_IT).isChecked())
        .as(
            "a payer step that came back unticked left the tester to work out again which outcome"
                + " they had picked before they could confirm it")
        .isTrue();
    theWholeScreenIsReadAt(1280, 700, "pix-refused-payer-step");

    theBrowserSawNothingGoWrong();
    setup().forgetTheTestRunAndItsAccount(AUTRA, testRunId(), MERCHANT);
  }

  private String theTabThisScreenMarksAsItsOwn() {
    return String.valueOf(
        page()
            .evaluate(
                "() => {"
                    + " const current ="
                    + " document.querySelector('nav.rail a[aria-current=\"page\"]');"
                    + " return current == null ? '' : current.textContent.trim(); }"));
  }

  private String theCopyAndPastePayloadOnTheScreen() {
    return String.valueOf(
        page()
            .evaluate(
                "() => [...document.querySelectorAll('main p.id')]"
                    + ".map((where) => where.textContent.trim())"
                    + ".find((text) => text.startsWith('0002'))"));
  }

  private void theWholeScreenIsReadAt(int wide, int tall) {
    theWholeScreenIsReadAt(wide, tall, "pix");
  }

  private void theWholeScreenIsReadAt(int wide, int tall, String named) {
    page().setViewportSize(wide, tall);
    page()
        .screenshot(
            new Page.ScreenshotOptions()
                .setFullPage(true)
                .setPath(
                    Path.of(System.getProperty("shots", "build/shots"))
                        .resolve(named + "-" + wide + "x" + tall + ".png")));
    assertThat(browser().howWideTheDocumentIs())
        .as("the Pix screen must not push the page sideways at " + wide + "x" + tall)
        .isLessThanOrEqualTo(browser().howWideTheViewportIs());
  }
}
