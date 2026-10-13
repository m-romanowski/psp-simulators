package com.example.tester.ui;

import static org.assertj.core.api.Assertions.assertThat;

import com.microsoft.playwright.Page;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class EveryDestinationIsOneClickAwayTest extends AJourneyInARealBrowser {

  private static final int A_LAPTOP_IS_THIS_WIDE = 1280;

  private static final int A_LAPTOP_IS_THIS_TALL = 700;

  private static final int A_SHORT_LAPTOP_IS_THIS_TALL = 600;

  private static final int THE_RAIL_HOLDS_AT_LEAST_THIS_MANY = 19;

  private static final String THE_PROVIDERS_ON_OFFER =
      """
      () => [...document.querySelectorAll('#provider option')]
        .map((option) => option.value)
        .filter((value) => value !== '')
      """;

  private static final double READABLE_SMALL_TEXT_CONTRASTS_AT_LEAST = 4.5;

  private static final String THE_RAIL = "nav.rail a";

  private static final String TEXT_NO_SMALLER_THAN_14PX =
      "nav.rail a, nav.rail .groupName { font-size: 14px !important; }";

  private static final String THE_LONGEST_LABEL = "Checks against the real PSP";

  private static final String A_LABEL_TWO_LINES_LONG =
      "Checks against the real PSP of every provider this build carries";

  private static final String THE_LINKS_WHOSE_TITLE_IS_NOT_THEIR_LABEL =
      """
      () => [...document.querySelectorAll('nav.rail a')]
        .filter((link) => link.title !== link.textContent.trim())
        .map((link) => link.textContent.trim() + ' is titled "' + link.title + '"')
      """;

  private static final String THE_LABEL_GROWS_AND_REPORTS_ITS_LINES =
      """
      ([was, now]) => {
        const link = [...document.querySelectorAll('nav.rail a')]
          .find((candidate) => candidate.textContent.trim() === was);
        const before = link.getBoundingClientRect().height;
        link.textContent = now;
        const style = getComputedStyle(link);
        return {
          taller: link.getBoundingClientRect().height > before,
          cutShort: link.scrollWidth > link.clientWidth && style.overflow === 'hidden'
            && style.textOverflow === 'ellipsis',
        };
      }
      """;

  private static final String THE_ONES_A_TESTER_CANNOT_CLICK =
      """
      () => {
        const hits = (link, x, y) => {
          const found = document.elementFromPoint(x, y);
          return found != null && (found === link || link.contains(found));
        };
        const beyondReach = [];
        for (const link of document.querySelectorAll('nav.rail a')) {
          const box = link.getBoundingClientRect();
          const middle = box.left + box.width / 2;
          const clickable = box.width > 0 && box.height > 0
            && box.top >= 0 && box.bottom <= window.innerHeight
            && box.left >= 0 && box.right <= window.innerWidth
            && hits(link, middle, box.top + box.height / 2)
            && hits(link, middle, box.top + 1)
            && hits(link, middle, box.bottom - 1);
          if (!clickable) {
            beyondReach.push(link.textContent.trim() + ' spans ' + Math.round(box.top) + '-'
              + Math.round(box.bottom) + ' down and ' + Math.round(box.left) + '-'
              + Math.round(box.right) + ' across');
          }
        }
        return beyondReach;
      }
      """;

  private static final String THE_WEAKEST_GROUP_NAME_CONTRAST =
      """
      () => {
        const channels = (colour) => colour.match(/[\\d.]+/g).slice(0, 3).map(Number);
        const luminance = (colour) => {
          const [r, g, b] = channels(colour).map((channel) => {
            const c = channel / 255;
            return c <= 0.04045 ? c / 12.92 : Math.pow((c + 0.055) / 1.055, 2.4);
          });
          return 0.2126 * r + 0.7152 * g + 0.0722 * b;
        };
        const groundOf = (element) => {
          for (let at = element; at != null; at = at.parentElement) {
            const ground = getComputedStyle(at).backgroundColor;
            if (ground !== 'rgba(0, 0, 0, 0)' && ground !== 'transparent') {
              return ground;
            }
          }
          return 'rgb(255, 255, 255)';
        };
        let weakest = Infinity;
        for (const name of document.querySelectorAll('nav.rail .groupName')) {
          const ink = luminance(getComputedStyle(name).color);
          const ground = luminance(groundOf(name));
          const ratio = (Math.max(ink, ground) + 0.05) / (Math.min(ink, ground) + 0.05);
          weakest = Math.min(weakest, ratio);
        }
        return weakest;
      }
      """;

  @Test
  @DisplayName("every screen the rail offers can be clicked without scrolling the page")
  void noScreenOfTheRailSitsOutsideAWindowThisSmall() {
    int longestRail = 0;
    for (String provider : theProvidersThisSimulatorCarries()) {
      page().navigate(at("/ui/objects?provider=" + provider + "&testRunId=" + testRunId()));

      everyDestinationIsClickableAt(provider, A_LAPTOP_IS_THIS_WIDE, A_LAPTOP_IS_THIS_TALL);
      everyDestinationIsClickableAt(provider, A_LAPTOP_IS_THIS_WIDE, A_SHORT_LAPTOP_IS_THIS_TALL);
      everyDestinationIsClickableAt(
          provider, TheBrowser.A_PHONE_IS_THIS_WIDE, TheBrowser.A_PHONE_IS_THIS_TALL);
      longestRail = Math.max(longestRail, page().locator(THE_RAIL).count());
    }

    assertThat(longestRail)
        .as(
            "the rail is no longer written out screen by screen: a provider contributes a tab by"
                + " dropping a page in, and if no provider of this build carries one then every"
                + " rail measured above was the same rail this test already measured before"
                + " anything could be contributed to it")
        .isGreaterThan(THE_RAIL_HOLDS_AT_LEAST_THIS_MANY);

    theBrowserSawNothingGoWrong();
  }

  private List<String> theProvidersThisSimulatorCarries() {
    page().navigate(at("/ui/objects?provider=" + GROW + "&testRunId=" + testRunId()));
    List<String> providers = new ArrayList<>();
    for (Object each : (List<?>) page().evaluate(THE_PROVIDERS_ON_OFFER)) {
      providers.add(String.valueOf(each));
    }
    assertThat(providers)
        .as("a rail is measured for every provider this build carries, and this build carries none")
        .isNotEmpty();
    return List.copyOf(providers);
  }

  @Test
  @DisplayName("at 1280x600, with text at a 14px minimum, a label too long for the rail still fits")
  void aLabelTooLongForTheRailPushesNoScreenOutOfIt() {
    page().navigate(at("/ui/objects?provider=" + GROW + "&testRunId=" + testRunId()));
    page().setViewportSize(A_LAPTOP_IS_THIS_WIDE, A_SHORT_LAPTOP_IS_THIS_TALL);

    assertThat((List<?>) page().evaluate(THE_LINKS_WHOSE_TITLE_IS_NOT_THEIR_LABEL))
        .as(
            "a label the rail cuts short must still be readable whole, so every link of the rail"
                + " carries its own label as its title")
        .isEmpty();

    page().addStyleTag(new Page.AddStyleTagOptions().setContent(TEXT_NO_SMALLER_THAN_14PX));
    Object grown =
        page()
            .evaluate(
                THE_LABEL_GROWS_AND_REPORTS_ITS_LINES,
                List.of(THE_LONGEST_LABEL, A_LABEL_TWO_LINES_LONG));
    assertThat(grown)
        .as(
            "a label longer than the rail is wide must stay one line high and be cut short,"
                + " because the rail at this height has no spare line for it")
        .isEqualTo(Map.of("taller", false, "cutShort", true));

    everyDestinationIsClickableAt(GROW, A_LAPTOP_IS_THIS_WIDE, A_SHORT_LAPTOP_IS_THIS_TALL);

    theBrowserSawNothingGoWrong();
  }

  @Test
  @DisplayName("the name of every group of the rail reads at the contrast small text needs")
  void everyGroupNameOfTheRailIsReadable() {
    page().navigate(at("/ui/objects?provider=" + GROW + "&testRunId=" + testRunId()));

    for (int[] window :
        List.of(
            new int[] {A_LAPTOP_IS_THIS_WIDE, A_LAPTOP_IS_THIS_TALL},
            new int[] {TheBrowser.A_PHONE_IS_THIS_WIDE, TheBrowser.A_PHONE_IS_THIS_TALL})) {
      page().setViewportSize(window[0], window[1]);
      double weakest = ((Number) page().evaluate(THE_WEAKEST_GROUP_NAME_CONTRAST)).doubleValue();
      assertThat(weakest)
          .as(
              "a group name of the rail is 11px text, so WCAG AA asks 4.5:1 of it against the"
                  + " ground it is painted on; at "
                  + window[0]
                  + "x"
                  + window[1]
                  + " the weakest one reads at "
                  + weakest)
          .isGreaterThanOrEqualTo(READABLE_SMALL_TEXT_CONTRASTS_AT_LEAST);
    }

    theBrowserSawNothingGoWrong();
  }

  private void everyDestinationIsClickableAt(String provider, int wide, int tall) {
    page().setViewportSize(wide, tall);
    String window = provider + " at " + wide + "x" + tall;

    assertThat(page().locator(THE_RAIL).count())
        .as(
            "the rail of "
                + window
                + " must still offer every screen this simulator carries, whatever a provider has"
                + " contributed to it; a rail that fits because destinations were dropped out of"
                + " it is not a rail that fits")
        .isGreaterThanOrEqualTo(THE_RAIL_HOLDS_AT_LEAST_THIS_MANY);

    List<?> beyondReach = (List<?>) page().evaluate(THE_ONES_A_TESTER_CANNOT_CLICK);
    assertThat(beyondReach)
        .as(
            "at "
                + window
                + ", with the page unscrolled, each of these links of the rail is either partly"
                + " off the viewport or covered at its middle, its top edge or its bottom edge,"
                + " so a tester cannot rely on clicking it")
        .isEmpty();

    assertThat(browser().howWideTheDocumentIs())
        .as("no screen may push the page sideways at " + window)
        .isLessThanOrEqualTo(browser().howWideTheViewportIs());
  }
}
