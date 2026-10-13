package com.example.tester.ui;

import static org.assertj.core.api.Assertions.assertThat;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.assertions.PlaywrightAssertions;
import com.microsoft.playwright.options.AriaRole;
import com.microsoft.playwright.options.ColorScheme;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class TheLayoutHidesNothingTest extends AJourneyInARealBrowser {

  private static final String THE_WASH_OF_AN_OPEN_ROW = "rgb(232, 240, 247)";

  private static final String THE_MARK_OF_AN_OPEN_ROW = "rgb(21, 80, 127) 3px 0px 0px 0px inset";

  private static final String THE_CARD_THE_PAYER_PICKS = "sandbox-4580";

  private static final String A_PANEL = "rgb(255, 255, 255)";

  private static final String THE_ACCENT = "rgb(21, 80, 127)";

  private static final String THE_STRIPE_OF_AN_EVEN_ENTRY = "rgb(244, 246, 249)";

  private static final String AN_UNSTRIPED_CELL = "rgba(0, 0, 0, 0)";

  private static final double TEXT_CONTRASTS_AT_LEAST = 4.5;

  private static final Pattern SWAGGERS_DARK_MODE = Pattern.compile("(^|\\s)dark-mode(\\s|$)");

  private static final int A_WIDE_SCREEN_IS_THIS_WIDE = 2400;

  private static final int A_BLOCK_OF_CONTROLS_STOPS_AT = 1200;

  private static final int A_SENTENCE_STOPS_AT = 800;

  private static final String WHAT_THE_TYPE_SCALE_MEASURES =
      """
      () => {
        const main = document.querySelector('body > main');
        const probe = document.createElement('section');
        probe.className = 'panel';
        probe.innerHTML = '<h2>a heading</h2><div class="body"><h3>a subheading</h3>'
          + '<p>' + 'a sentence that runs on '.repeat(40) + '</p>'
          + '<form class="form"><div><label>a label</label><input type="text"></div></form></div>';
        main.append(probe);
        const size = (selector) => parseFloat(getComputedStyle(probe.querySelector(selector)).fontSize);
        const body = parseFloat(getComputedStyle(document.body).fontSize);
        const label = getComputedStyle(probe.querySelector('label'));
        const paragraph = probe.querySelector('p');
        const ch = (() => {
          const unit = document.createElement('span');
          unit.style.cssText = 'display:inline-block;width:72ch';
          paragraph.append(unit);
          const width = unit.getBoundingClientRect().width;
          unit.remove();
          return width;
        })();
        const found = {
          h3AboveBody: size('h3') > body,
          h2AboveH3: size('h2') > size('h3'),
          labelInk: label.color,
          labelWeight: label.fontWeight,
          paragraphHeldAt72ch: Math.abs(paragraph.getBoundingClientRect().width - ch) < 1,
          mainStopsAt: getComputedStyle(main).maxWidth,
          formStopsAt: getComputedStyle(probe.querySelector('form.form')).maxWidth,
          mainTakesTheWindow: Math.abs(
            main.getBoundingClientRect().right - window.innerWidth) < 1,
        };
        probe.remove();
        return found;
      }
      """;

  private static final Map<String, Object> WHAT_THE_TYPE_SCALE_IS =
      Map.of(
          "h3AboveBody",
          true,
          "h2AboveH3",
          true,
          "labelInk",
          "rgb(47, 59, 71)",
          "labelWeight",
          "600",
          "paragraphHeldAt72ch",
          true,
          "mainStopsAt",
          "none",
          "formStopsAt",
          "1200px",
          "mainTakesTheWindow",
          true);

  private static final double A_FIELD_EDGE_CONTRASTS_AT_LEAST = 3.0;

  private static final String THE_HAND_WRITTEN_RULE_IS_BEHIND_THIS = "Show the fields of one rule";

  private static final List<String> THE_SCREENS_A_TESTER_READS =
      List.of(
          "/ui",
          "/ui/dashboard",
          "/ui/objects",
          "/ui/objects/new",
          "/ui/reads",
          "/ui/configuration",
          "/ui/configuration/account",
          "/ui/payment-methods",
          "/ui/scenarios",
          "/ui/webhooks",
          "/ui/audit",
          "/ui/timeline",
          "/ui/correlation",
          "/ui/idempotency",
          "/ui/pairing",
          "/ui/personas",
          "/ui/smoke",
          "/ui/run-bundle",
          "/ui/repro-bundle",
          "/ui/scenarios/diff");

  private static final String EVERY_DETAILS_IS_OPENED =
      "() => document.querySelectorAll('details').forEach((details) => { details.open = true; })";

  private static final String A_NOTICE_OF_EVERY_KIND_AND_A_BADGE_OF_EVERY_TONE =
      """
      () => {
        const probe = document.createElement('section');
        probe.className = 'panel';
        probe.innerHTML = '<div class="body">'
          + '<div class="done" role="status">a change that happened</div>'
          + '<div class="refusal">a change that was refused</div>'
          + '<div class="caution">a warning before a change</div>'
          + ['success', 'failure', 'pending', 'neutral', 'attention', 'fact']
            .map((tone) => '<span class="tag ' + tone + '">a ' + tone + ' badge</span>').join('')
          + '<span class="tag">a plain badge</span>'
          + '</div>';
        document.querySelector('body > main').prepend(probe);
      }
      """;

  private static final String CONTRAST =
      """
      const channels = (colour) => colour.match(/[\\d.]+/g).map(Number);
      const clear = (colour) => { const c = channels(colour); return c.length > 3 && c[3] === 0; };
      const opacityOf = (element) => {
        let opacity = 1;
        for (let at = element; at != null; at = at.parentElement) {
          opacity *= parseFloat(getComputedStyle(at).opacity);
        }
        return opacity;
      };
      const over = (colour, ground, opacity) => {
        const [r, g, b, a = 1] = channels(colour);
        const [gr, gg, gb] = channels(ground);
        const alpha = a * opacity;
        return 'rgb(' + [r * alpha + gr * (1 - alpha), g * alpha + gg * (1 - alpha),
          b * alpha + gb * (1 - alpha)].map(Math.round).join(', ') + ')';
      };
      const luminance = (colour) => {
        const [r, g, b] = channels(colour).slice(0, 3).map((channel) => {
          const c = channel / 255;
          return c <= 0.04045 ? c / 12.92 : Math.pow((c + 0.055) / 1.055, 2.4);
        });
        return 0.2126 * r + 0.7152 * g + 0.0722 * b;
      };
      const ratio = (one, other) => {
        const a = luminance(one);
        const b = luminance(other);
        return (Math.max(a, b) + 0.05) / (Math.min(a, b) + 0.05);
      };
      const groundOf = (element) => {
        const layers = [];
        for (let at = element; at != null; at = at.parentElement) {
          const style = getComputedStyle(at);
          const layered = style.backgroundImage.match(/rgba?\\([^)]*\\)/);
          const colour = layered != null && !clear(layered[0]) ? layered[0] : style.backgroundColor;
          if (!clear(colour)) {
            layers.push(colour);
            if ((channels(colour)[3] ?? 1) === 1) {
              break;
            }
          }
        }
        let ground = [255, 255, 255];
        for (const layer of layers.reverse()) {
          const [r, g, b, a = 1] = channels(layer);
          ground = [r * a + ground[0] * (1 - a), g * a + ground[1] * (1 - a),
            b * a + ground[2] * (1 - a)];
        }
        return 'rgb(' + ground.map(Math.round).join(', ') + ')';
      };
      const shown = (element) => {
        const box = element.getBoundingClientRect();
        return box.width > 0 && box.height > 0 && element.closest('.skip, option') == null
          && getComputedStyle(element).visibility === 'visible';
      };
      const name = (element) => element.tagName.toLowerCase()
        + (element.className && typeof element.className === 'string'
          ? '.' + element.className.trim().split(/\\s+/).join('.') : '');
      """;

  private static final String THE_TEXT_AND_FIELD_EDGES_BELOW_THEIR_MINIMUM =
      "([textMinimum, edgeMinimum]) => {"
          + CONTRAST
          + """
          const below = [];
          for (const element of document.body.querySelectorAll('*')) {
            const writes = [...element.childNodes].some((node) =>
              node.nodeType === Node.TEXT_NODE && node.textContent.trim() !== '');
            const field = element.matches(
              'input:not([type=hidden]):not([type=checkbox]):not([type=radio]), select, textarea');
            if (!(writes || field) || !shown(element)) {
              continue;
            }
            const style = getComputedStyle(element);
            const ground = groundOf(element);
            const opacity = opacityOf(element);
            const ink = over(style.color, ground, opacity);
            const text = ratio(ink, ground);
            if (text < textMinimum) {
              below.push(name(element) + ' "' + (element.textContent || '').trim().slice(0, 40)
                + '" text ' + style.color + ' at opacity ' + opacity + ' on ' + ground
                + ' reads ' + text.toFixed(2));
            }
            if (field) {
              const outside = groundOf(element.parentElement);
              const edge = Math.min(ratio(over(style.borderTopColor, ground, opacity), ground),
                ratio(over(style.borderTopColor, outside, opacity), outside));
              if (edge < edgeMinimum) {
                below.push(name(element) + '#' + element.id + ' edge ' + style.borderTopColor
                  + ' against ' + ground + ' and ' + outside + ' reads ' + edge.toFixed(2));
              }
            }
          }
          return below;
        }
        """;

  private static final String THE_WEAKEST_TEXT_OF_THE_API_PAGE =
      "(selectors) => {"
          + CONTRAST
          + """
          return selectors.map((selector) => {
            const element = document.querySelector(selector);
            const style = getComputedStyle(element);
            return selector + ' reads ' + ratio(style.color, groundOf(element)).toFixed(2);
          });
        }
        """;

  private static final String HOW_THE_FIRST_ROW_OF_EACH_ENTRY_IS_PAINTED =
      """
      () => [...document.querySelectorAll('main .scroll > table > tbody')].slice(0, 2)
        .map((entry, at) => [...entry.querySelector(':scope > tr').children].map((cell) => {
          const style = getComputedStyle(cell);
          const layered = style.backgroundImage.match(/rgba?\\([^)]*\\)/);
          const painted = layered != null && layered[0] !== 'rgba(0, 0, 0, 0)'
            ? layered[0]
            : style.position === 'sticky' ? 'rgba(0, 0, 0, 0)' : style.backgroundColor;
          return 'entry ' + (at + 1) + ': ' + painted;
        })).flat()
      """;

  private static final int A_ROW_OF_THE_CORRELATION_SCREEN_HAS_THIS_MANY_CELLS = 8;

  private static final int ROWS_ENOUGH_TO_SCROLL_A_TABLE_OF_A_LAPTOP = 60;

  private static final int THE_RAIL_TAKES_OVER_AT = 880;

  private static final int THE_FORM_FITS_TWO_COLUMNS_BESIDE_THE_RAIL_FROM = 840;

  private static final int A_CLASSIC_SCROLLBAR_IS_THIS_WIDE = 17;

  private static final String WHAT_THE_NAVIGATION_AND_THE_FORM_ARE =
      """
      (reserved) => {
        const main = document.querySelector('body > main');
        main.style.marginRight = reserved + 'px';
        const form = document.querySelector('form.form');
        const field = form.querySelector('input[type="text"]');
        const measured = {
          railIsAColumnOfItsOwn:
            getComputedStyle(document.querySelector('nav.rail')).position === 'fixed',
          formColumns: getComputedStyle(form).gridTemplateColumns.split(' ').length,
          theNarrowestColumn: Math.round(field.getBoundingClientRect().width),
          thePageScrollsSideways:
            Math.ceil(document.documentElement.scrollWidth) > window.innerWidth,
        };
        main.style.marginRight = '';
        return measured;
      }
      """;

  private static final String HOW_FAR_A_PINNED_BADGE_RUNS_PAST_ITS_CELL =
      """
      () => {
        const cells = [...document.querySelectorAll(
          'body > main .scroll table > tbody > tr > :first-child')];
        const past = cells.flatMap((cell) => [...cell.querySelectorAll('.tag')].map((badge) => ({
          text: badge.textContent.trim(),
          past: Math.round(badge.getBoundingClientRect().right - cell.getBoundingClientRect().right),
        })));
        return {
          badges: past.length,
          worst: past.length === 0 ? null : Math.max(...past.map((each) => each.past)),
          widest: past.length === 0 ? null : past.reduce(
            (a, b) => (b.past > a.past ? b : a)).text,
        };
      }
      """;

  private static final String WHAT_EVERY_BLOCK_OF_A_SCREEN_MEASURES =
      """
      () => {
        const widest = (selector) => {
          const found = [...document.querySelectorAll(selector)];
          return found.length === 0
            ? null
            : Math.round(Math.max(...found.map((each) => each.getBoundingClientRect().width)));
        };
        return {
          window: window.innerWidth,
          form: widest('body > main form.form'),
          filters: widest('body > main form.filters'),
          pairs: widest('body > main dl.pairs'),
          prose: widest('body > main p'),
          notice: widest('body > main .caution, body > main .refusal, body > main .done'),
          scroller: widest('body > main .scroll'),
          narrowTable: widest('body > main .scroll table'),
        };
      }
      """;

  private static final int A_LAPTOP_IS_THIS_WIDE = 1280;

  private static final int A_LAPTOP_IS_THIS_TALL = 700;

  private static final int THE_RAIL_IS_THIS_WIDE = 232;

  private static final int A_FIELD_SITS_THIS_FAR_DOWN_THE_WINDOW = 10;

  private static final String A_SENTENCE_LONGER_THAN_ANY_WINDOW =
      "This sentence stands in for a refusal, a lost response or a pairing note, and it runs"
          + " on for far longer than any window is wide, so a table that sized itself to it on"
          + " one line would push every column beside it out of reach and leave a tester"
          + " scrolling sideways to read one message that could simply have wrapped.";

  private static final String WHAT_EACH_CELL_OF_AN_OPEN_ROW_IS_PAINTED =
      """
      () => [...document.querySelectorAll('tr.open > td')].map((cell) => {
        const style = getComputedStyle(cell);
        const layered = style.backgroundImage.match(/rgba?\\([^)]*\\)/);
        const painted = layered != null && layered[0] !== 'rgba(0, 0, 0, 0)'
          ? layered[0]
          : style.backgroundColor;
        return (cell.parentElement.sectionRowIndex % 2 === 1 ? 'even row, ' : 'odd row, ')
          + (style.position === 'sticky' ? 'pinned cell: ' : 'cell: ') + painted;
      })
      """;

  private static final String HOW_THE_FIRST_CELL_OF_AN_OPEN_ROW_IS_MARKED =
      """
      () => [...document.querySelectorAll('tr.open > td:first-child')]
        .map((cell) => getComputedStyle(cell).boxShadow)
      """;

  private static final String WHAT_EACH_BODY_CELL_OF_A_TABLE_IN_A_CELL_IS_PAINTED =
      """
      () => [...document.querySelectorAll('td table > tbody > tr > td')].map((cell) =>
        (cell.parentElement.sectionRowIndex % 2 === 1 ? 'even row: ' : 'odd row: ')
          + getComputedStyle(cell).backgroundColor)
      """;

  private static final String WHAT_COVERS_THE_CORNERS_OF =
      """
      (element) => {
        const box = element.getBoundingClientRect();
        const at = (x, y) => {
          const found = document.elementFromPoint(x, y);
          if (found == null) {
            return 'nothing';
          }
          if (found === element || element.contains(found)) {
            return 'itself';
          }
          if (found.closest('thead') != null) {
            return 'the pinned header';
          }
          if (found.closest('tr > :first-child') != null) {
            return 'the pinned first column';
          }
          return found.tagName.toLowerCase();
        };
        return [
          at(box.left + 1, box.top + 1),
          at(box.right - 1, box.top + 1),
          at(box.left + 1, box.bottom - 1),
          at(box.right - 1, box.bottom - 1),
        ];
      }
      """;

  private static final String THE_TRAIL_TAKES_THIS_MANY_MORE_ROWS =
      """
      (copies) => {
        const table = document.querySelector('main .scroll > table');
        const first = table.querySelector(':scope > tbody');
        for (let made = 0; made < copies; made++) {
          table.append(first.cloneNode(true));
        }
        table.closest('.scroll').scrollIntoView({ block: 'center' });
      }
      """;

  private static final String THE_TABLE_SCROLLS_UNTIL_THIS_SITS_UNDER_ITS_PINS =
      """
      (element, across) => {
        const scroller = element.closest('.scroll');
        const edge = scroller.getBoundingClientRect();
        if (across) {
          scroller.scrollLeft += element.getBoundingClientRect().left - edge.left - 4;
        } else {
          scroller.scrollLeft = scroller.scrollWidth;
          scroller.scrollTop += element.getBoundingClientRect().top - edge.top - 4;
        }
      }
      """;

  private static final String HOW_WIDE_THE_FIRST_COLUMN_IS_BESIDE_ITS_SCROLLER =
      """
      () => {
        const cell = document.querySelector('main .scroll > table > tbody > tr > td');
        return {
          position: getComputedStyle(cell).position,
          cell: Math.round(cell.getBoundingClientRect().width),
          scroller: cell.closest('.scroll').clientWidth,
        };
      }
      """;

  private static final String WHAT_THE_SCREENS_RULES_DO_TO_A_TABLE_A_DETAILS_AND_A_BUTTON =
      """
      () => {
        const probe = document.createElement('div');
        probe.innerHTML = '<table><thead><tr><th>a header</th></tr></thead><tbody>'
          + '<tr><td>a row</td></tr><tr><td>a second row</td></tr></tbody></table>'
          + '<details><summary>more</summary></details><button type="button">a button</button>';
        document.querySelector('body > main').append(probe);
        const style = (selector) => getComputedStyle(probe.querySelector(selector));
        const found = {
          header: style('thead th').textTransform,
          secondRow: style('tbody tr:nth-child(2) td').backgroundColor,
          details: style('details').marginTop,
          button: style('button').whiteSpace,
        };
        probe.remove();
        return found;
      }
      """;

  private static final Map<String, String> WHAT_THE_SCREENS_RULES_DO =
      Map.of(
          "header", "uppercase",
          "secondRow", "rgb(244, 246, 249)",
          "details", "12px",
          "button", "nowrap");

  private static final Map<String, String> WHAT_A_BROWSER_DOES_UNSTYLED =
      Map.of(
          "header", "none",
          "secondRow", "rgba(0, 0, 0, 0)",
          "details", "0px",
          "button", "normal");

  private static final String HOW_SWAGGERS_OWN_OPERATION_BUTTON_IS_PAINTED =
      """
      () => {
        const button = document.querySelector('.opblock button');
        const style = getComputedStyle(button);
        return { background: style.backgroundColor, whiteSpace: style.whiteSpace };
      }
      """;

  private static final Map<String, String> HOW_SWAGGER_PAINTS_ITS_OWN_BUTTON_UNSTYLED =
      Map.of(
          "background", "rgba(0, 0, 0, 0)",
          "whiteSpace", "normal");

  private static final String HOW_THE_CELLS_OF_A_TABLE_IN_A_CELL_ARE_POSITIONED =
      """
      () => [...document.querySelectorAll('td table th, td table td:first-child')]
        .map((cell) => cell.tagName.toLowerCase() + ' ' + getComputedStyle(cell).position)
      """;

  private static final String THE_TABLE_BEFORE_AND_AFTER_A_LONG_SENTENCE =
      """
      (sentence) => {
        const cell = document.querySelector('tr.open > td[colspan]');
        const table = cell.closest('table');
        const before = table.getBoundingClientRect().width;
        cell.prepend(document.createTextNode(sentence));
        const pairs = document.createElement('dl');
        pairs.className = 'pairs';
        pairs.innerHTML = '<dt>what it refused</dt><dd class="wrap"></dd>';
        pairs.querySelector('dd').textContent = sentence;
        cell.append(pairs);
        const after = table.getBoundingClientRect().width;
        const box = cell.getBoundingClientRect();
        const dd = pairs.querySelector('dd').getBoundingClientRect();
        return {
          before: Math.round(before),
          after: Math.round(after),
          cellRight: Math.round(box.right),
          answerRight: Math.round(dd.right),
        };
      }
      """;

  @Test
  @DisplayName("an even open row is washed in every cell, not only its pinned first, and marked")
  void anOpenRowIsMarkedAboveTheStripe() {
    aPaymentProcessIsStartedOverGrowsOwnRoute("a-marked-grow-user", "a-marked-grow-page", 2);
    page().navigate(at("/ui/correlation?provider=" + GROW + "&testRunId=" + testRunId()));
    page()
        .getByRole(AriaRole.LINK, new Page.GetByRoleOptions().setName("Explain").setExact(true))
        .nth(1)
        .click();
    PlaywrightAssertions.assertThat(page().locator("tr.open").first()).isVisible();
    page().mouse().move(0, 0);

    List<String> painted = linesOf(WHAT_EACH_CELL_OF_AN_OPEN_ROW_IS_PAINTED);
    assertThat(painted)
        .as(
            "the second request of the run is the second, even, row of the correlation table,"
                + " so every one of its cells is where the stripe of an even row competes with"
                + " the wash of an open one; the pinned first cell is painted by one rule and"
                + " every other cell by another, and both must lose to the wash")
        .hasSize(A_ROW_OF_THE_CORRELATION_SCREEN_HAS_THIS_MANY_CELLS)
        .contains("even row, pinned cell: " + THE_WASH_OF_AN_OPEN_ROW)
        .contains("even row, cell: " + THE_WASH_OF_AN_OPEN_ROW)
        .allSatisfy(cell -> assertThat(cell).endsWith(THE_WASH_OF_AN_OPEN_ROW));
    assertThat(linesOf(HOW_THE_FIRST_CELL_OF_AN_OPEN_ROW_IS_MARKED))
        .as("the open row carries the accent bar down the left edge of its first cell")
        .singleElement()
        .satisfies(mark -> assertThat(mark).startsWith(THE_MARK_OF_AN_OPEN_ROW));

    theBrowserSawNothingGoWrong();
  }

  @Test
  @DisplayName("a table inside an open row pins neither its header nor its first column")
  void aTableInsideAnOpenRowStaysPut() {
    theAccountIsOpenOnTheConfigurationScreen("a-nested-grow-user", "a-nested-grow-page");

    List<String> nested = linesOf(HOW_THE_CELLS_OF_A_TABLE_IN_A_CELL_ARE_POSITIONED);
    assertThat(nested)
        .as(
            "the open account shows its configuration as a table inside the detail cell; its"
                + " header and first column must scroll with it rather than stick inside a box"
                + " that is itself inside a sticky grid")
        .isNotEmpty()
        .allSatisfy(cell -> assertThat(cell).endsWith(" static"));

    assertThat(linesOf(WHAT_EACH_BODY_CELL_OF_A_TABLE_IN_A_CELL_IS_PAINTED))
        .as(
            "the rows of the table inside the open account are the account's own, so the wash"
                + " of the open row must show through every one of them rather than be striped"
                + " over on every second row")
        .contains("even row: " + THE_WASH_OF_AN_OPEN_ROW)
        .allSatisfy(cell -> assertThat(cell).endsWith(THE_WASH_OF_AN_OPEN_ROW));

    theBrowserSawNothingGoWrong();
  }

  @Test
  @DisplayName("a control reached by shift-tab up a long table is not left under its pinned header")
  void aControlTabbedToUpATableIsNotUnderItsHeader() {
    aPaymentProcessIsStartedOverGrowsOwnRoute("an-upward-grow-user", "an-upward-grow-page", 1);
    page().setViewportSize(A_LAPTOP_IS_THIS_WIDE, A_LAPTOP_IS_THIS_TALL);
    page().navigate(at("/ui/audit?provider=" + GROW + "&testRunId=" + testRunId()));
    page().evaluate(THE_TRAIL_TAKES_THIS_MANY_MORE_ROWS, ROWS_ENOUGH_TO_SCROLL_A_TABLE_OF_A_LAPTOP);
    Locator opens =
        page().getByRole(AriaRole.LINK, new Page.GetByRoleOptions().setName("open").setExact(true));
    int middle = opens.count() / 2;

    aControlReachedByShiftTabIsUncovered(
        opens.nth(middle - 1), opens.nth(middle), false, "the pinned header");
  }

  @Test
  @DisplayName("a control reached by shift-tab left along a wide table is not left under its pin")
  void aControlTabbedToLeftAlongATableIsNotUnderItsPinnedColumn() {
    aCallbackIsQueuedForAPaymentThePayerFinished("a-leftward-grow-user", "a-leftward-grow-page");
    page().setViewportSize(A_LAPTOP_IS_THIS_WIDE, A_LAPTOP_IS_THIS_TALL);
    page().navigate(at("/ui/webhooks?provider=" + GROW + "&testRunId=" + testRunId()));
    Locator theDelivery =
        page()
            .locator("main .scroll > table > tbody > tr")
            .filter(
                new Locator.FilterOptions()
                    .setHas(
                        page()
                            .getByRole(
                                AriaRole.LINK,
                                new Page.GetByRoleOptions().setName("open").setExact(true))))
            .first();
    theDelivery.scrollIntoViewIfNeeded();

    aControlReachedByShiftTabIsUncovered(
        theDelivery.locator("td.id a").first(),
        theDelivery.getByRole(
            AriaRole.LINK, new Locator.GetByRoleOptions().setName("open").setExact(true)),
        true,
        "the pinned first column");
  }

  @Test
  @DisplayName("at phone width the first column leaves most of a table's scroller to the others")
  void aPhoneKeepsMostOfATableForTheColumnsAfterTheFirst() {
    aPaymentProcessIsStartedOverGrowsOwnRoute("a-narrow-grow-user", "a-narrow-grow-page", 1);
    page().setViewportSize(TheBrowser.A_PHONE_IS_THIS_WIDE, TheBrowser.A_PHONE_IS_THIS_TALL);
    page().navigate(at("/ui/audit?provider=" + GROW + "&testRunId=" + testRunId()));

    Map<?, ?> measured =
        (Map<?, ?>) page().evaluate(HOW_WIDE_THE_FIRST_COLUMN_IS_BESIDE_ITS_SCROLLER);
    boolean pinned = "sticky".equals(measured.get("position"));
    int cell = ((Number) measured.get("cell")).intValue();
    int scroller = ((Number) measured.get("scroller")).intValue();
    assertThat(pinned && cell * 2 > scroller)
        .as(
            "a first column pinned at "
                + TheBrowser.A_PHONE_IS_THIS_WIDE
                + "px must leave at least half of its table's scroller to the columns that"
                + " scroll under it, or be left unpinned; it is "
                + measured)
        .isFalse();

    theBrowserSawNothingGoWrong();
  }

  @Test
  @DisplayName("a long sentence in a detail row wraps inside the table instead of widening it")
  void aLongSentenceInADetailRowWrapsWithinTheTable() {
    theAccountIsOpenOnTheConfigurationScreen("a-prose-grow-user", "a-prose-grow-page");

    for (int[] window :
        List.of(
            new int[] {A_LAPTOP_IS_THIS_WIDE, A_LAPTOP_IS_THIS_TALL},
            new int[] {TheBrowser.A_PHONE_IS_THIS_WIDE, TheBrowser.A_PHONE_IS_THIS_TALL})) {
      page().setViewportSize(window[0], window[1]);
      page().reload();
      Map<?, ?> measured =
          (Map<?, ?>)
              page()
                  .evaluate(
                      THE_TABLE_BEFORE_AND_AFTER_A_LONG_SENTENCE,
                      A_SENTENCE_LONGER_THAN_ANY_WINDOW);
      String at = window[0] + "x" + window[1] + ": " + measured;
      assertThat(((Number) measured.get("after")).intValue())
          .as(
              "a sentence written straight into a detail cell, and the same sentence as the"
                  + " answer of a dl.pairs, must wrap at the width the table's own columns give"
                  + " it and leave the table exactly as wide as it was, at "
                  + at)
          .isEqualTo(((Number) measured.get("before")).intValue());
      assertThat(((Number) measured.get("answerRight")).intValue())
          .as("the answer of the pair ends inside its cell, at " + at)
          .isLessThanOrEqualTo(((Number) measured.get("cellRight")).intValue());
    }

    theBrowserSawNothingGoWrong();
  }

  private static final int A_GROUP_OF_FACTS_STARTS_AT_LEAST_THIS_FAR_IN = 12;

  private static final String A_GROUP_OF_FACTS_STANDS_THIS_FAR_BELOW_ITS_KEY = "4px";

  private static final double A_MARK_DOWN_A_LEFT_EDGE_IS_AT_LEAST_THIS_WIDE = 3;

  private static final int A_RECURSION_IS_PROVEN_FROM_THIS_MANY_LEVELS = 2;

  private static final String AN_ANSWER_OF_FACTS_WITH_FACTS_UNDER_THEM =
      """
      () => {
        const probe = document.createElement('section');
        probe.className = 'panel';
        probe.innerHTML = '<h2>what a provider answered</h2><div class="body">'
          + '<h3 class="id">100003</h3>'
          + '<dl class="pairs">'
          + '<dt class="id">paymentID</dt><dd class="id wrap">100003</dd>'
          + '<dt class="id">status</dt><dd class="id wrap">ACCEPTED</dd>'
          + '<dt class="id">acquirerTransaction</dt>'
          + '<dd><dl class="pairs">'
          + '<dt class="id">trackingNumber</dt><dd class="id wrap">884422</dd>'
          + '<dt class="id">acquirerName</dt><dd class="id wrap">an acquirer of some name</dd>'
          + '</dl></dd>'
          + '<dt class="id">events</dt>'
          + '<dd><dl class="pairs">'
          + '<dt>number 1</dt>'
          + '<dd><dl class="pairs">'
          + '<dt class="id">type</dt><dd class="id wrap">PREAUTH</dd>'
          + '<dt class="id">amount</dt><dd class="id wrap">640.00</dd>'
          + '<dt class="id">at</dt><dd class="id wrap">2026-09-22T10:00:00Z</dd>'
          + '</dl></dd>'
          + '<dt>number 2</dt>'
          + '<dd><dl class="pairs">'
          + '<dt class="id">type</dt><dd class="id wrap">CAPTURE</dd>'
          + '<dt class="id">amount</dt><dd class="id wrap">640.00</dd>'
          + '</dl></dd>'
          + '</dl></dd>'
          + '</dl></div>';
        document.querySelector('body > main').prepend(probe);
      }
      """;

  private static final String WHAT_A_GROUP_OF_FACTS_UNDER_A_FACT_IS_DRAWN_AS =
      """
      () => {
        const groups = [...document.querySelectorAll('body > main dl.pairs dl.pairs')];
        if (groups.length === 0) {
          return { groups: 0 };
        }
        const box = (element) => element.getBoundingClientRect();
        const levelsAbove = (group) => {
          let levels = 0;
          for (let above = group.parentElement.closest('dl.pairs'); above != null;
               above = above.parentElement.closest('dl.pairs')) {
            levels++;
          }
          return levels;
        };
        const measured = groups.map((group) => {
          const style = getComputedStyle(group);
          const cell = group.parentElement;
          const key = cell.previousElementSibling;
          const firstKeyUnderIt = group.querySelector(':scope > dt');
          return {
            theKeyItHangsUnder: key.textContent.trim(),
            levelsIn: levelsAbove(group),
            setInFromThatKey: Math.round(box(firstKeyUnderIt).left - box(key).left),
            theMarkDownItsLeftEdge: style.borderLeftStyle + ' ' + style.borderLeftWidth
              + ' ' + style.borderLeftColor,
            howWideThatMarkIs:
              style.borderLeftStyle === 'none' ? 0 : parseFloat(style.borderLeftWidth),
            thatMarkIsOpaque: style.borderLeftStyle !== 'none'
              && !/rgba\\([^)]*,\\s*0\\s*\\)/.test(style.borderLeftColor),
            pastTheCellItIsIn: Math.round(box(group).right - box(cell).right),
            theCapItCarries: style.maxWidth,
            theSpaceAboveIt: style.marginTop,
            theInkOfThatKey: getComputedStyle(key).color,
          };
        });
        const howBrightAnInkIs = (colour) => {
          const [r, g, b] = colour.match(/[0-9.]+/g).slice(0, 3).map((channel) => {
            const c = channel / 255;
            return c <= 0.04045 ? c / 12.92 : Math.pow((c + 0.055) / 1.055, 2.4);
          });
          return 0.2126 * r + 0.7152 * g + 0.0722 * b;
        };
        const aKeyCarryingAValue = [...document.querySelectorAll('body > main dl.pairs > dt')]
          .find((key) => key.nextElementSibling != null
            && key.nextElementSibling.querySelector('dl.pairs') == null);
        const each = (taken) => [...new Set(measured.map(taken))].sort();
        return {
          groups: measured.length,
          deepest: Math.max(...measured.map((group) => group.levelsIn)),
          theKeysTheyHangUnder: each((group) => group.theKeyItHangsUnder).slice(0, 6),
          theLeastOneStartsInFromItsKey:
            Math.min(...measured.map((group) => group.setInFromThatKey)),
          theThinnestMarkDownALeftEdge:
            Math.min(...measured.map((group) => group.howWideThatMarkIs)),
          everyMarkDownALeftEdgeIsOpaque: measured.every((group) => group.thatMarkIsOpaque),
          theMarksDownTheirLeftEdges: each((group) => group.theMarkDownItsLeftEdge),
          theFurthestOneRunsPastItsCell:
            Math.max(...measured.map((group) => group.pastTheCellItIsIn)),
          theCapsTheyCarry: each((group) => group.theCapItCarries),
          theSpaceAboveThem: each((group) => group.theSpaceAboveIt),
          theInkOfTheKeysTheyHangUnder: each((group) => group.theInkOfThatKey),
          theInkOfAKeyCarryingAValueItself: aKeyCarryingAValue == null
            ? 'this answer carries no key with a value of its own'
            : getComputedStyle(aKeyCarryingAValue).color,
          howBrightTheKeysTheyHangUnderAre:
            measured.map((group) => howBrightAnInkIs(group.theInkOfThatKey)),
          howBrightAKeyCarryingAValueItselfIs: aKeyCarryingAValue == null
            ? null
            : howBrightAnInkIs(getComputedStyle(aKeyCarryingAValue).color),
          thePageScrollsSideways:
            Math.ceil(document.documentElement.scrollWidth) > window.innerWidth,
        };
      }
      """;

  @Test
  @DisplayName("the facts under a fact are drawn as a group set in from the fact that carries it")
  void theFactsUnderAFactAreDrawnAsAGroupSetInFromTheFactThatCarriesThem() {
    List<String> theMarkEachSchemeDraws = new ArrayList<>();
    for (ColorScheme scheme : List.of(ColorScheme.LIGHT, ColorScheme.DARK)) {
      page().emulateMedia(new Page.EmulateMediaOptions().setColorScheme(scheme));
      Map<?, ?> drawn = Map.of();
      for (int[] window :
          List.of(
              new int[] {A_LAPTOP_IS_THIS_WIDE, A_LAPTOP_IS_THIS_TALL},
              new int[] {TheBrowser.A_PHONE_IS_THIS_WIDE, TheBrowser.A_PHONE_IS_THIS_TALL})) {
        page().setViewportSize(window[0], window[1]);
        page().navigate(at("/ui/reads?provider=" + GROW + "&testRunId=" + testRunId()));
        page().evaluate(AN_ANSWER_OF_FACTS_WITH_FACTS_UNDER_THEM);
        page().mouse().move(0, 0);
        drawn = (Map<?, ?>) page().evaluate(WHAT_A_GROUP_OF_FACTS_UNDER_A_FACT_IS_DRAWN_AS);
        String at = "in the " + scheme + " scheme at " + window[0] + "x" + window[1] + ": " + drawn;

        assertThat(((Number) drawn.get("groups")).intValue())
            .as(
                "the Look a payment up screen draws a provider's nested answer, a card inside a"
                    + " payment or the steps inside a charge, as a list of pairs inside the"
                    + " answer cell of another, and this is the markup that screen's own"
                    + " fragment writes; a rule about how a group of facts is drawn proves"
                    + " nothing on a screen carrying no group at all, "
                    + at)
            .isGreaterThan(0);
        assertThat(((Number) drawn.get("deepest")).intValue())
            .as(
                "that fragment replaces itself inside the cell it draws, so a provider"
                    + " answering a list of objects gets a group two levels inside the"
                    + " outermost list, three lists deep in all, and that is the shape this"
                    + " rule has to hold for, "
                    + at)
            .isGreaterThanOrEqualTo(A_RECURSION_IS_PROVEN_FROM_THIS_MANY_LEVELS);
        assertThat(((Number) drawn.get("theLeastOneStartsInFromItsKey")).intValue())
            .as(
                "every key of a group starts to the right of the key that carries the group, or"
                    + " a tester cannot tell which fact the inner keys belong to: under 880px a"
                    + " list of pairs is one column, where a group would otherwise begin at the"
                    + " very edge its own parent key begins at, "
                    + at)
            .isGreaterThanOrEqualTo(A_GROUP_OF_FACTS_STARTS_AT_LEAST_THIS_FAR_IN);
        assertThat(((Number) drawn.get("theThinnestMarkDownALeftEdge")).doubleValue())
            .as(
                "a group is ruled down its left edge the way an h3 is ruled down its own, which"
                    + " is how this stylesheet already says that what follows belongs to what"
                    + " names it, and the space it is set in by is the space that rule stands"
                    + " in, "
                    + at)
            .isGreaterThanOrEqualTo(A_MARK_DOWN_A_LEFT_EDGE_IS_AT_LEAST_THIS_WIDE);
        assertThat(drawn.get("everyMarkDownALeftEdgeIsOpaque"))
            .as("a rule drawn in a transparent colour marks nothing, " + at)
            .isEqualTo(true);
        assertThat(((Number) drawn.get("theFurthestOneRunsPastItsCell")).intValue())
            .as(
                "a group set in from its key still ends inside the answer it is part of rather"
                    + " than pushing the screen sideways, "
                    + at)
            .isLessThanOrEqualTo(0);
        assertThat(drawn.get("theCapsTheyCarry"))
            .as(
                "the cap that stops a block of pairs at %spx is the outermost list's own: a"
                    + " group of facts is drawn inside one cell of that list, and a cap of its"
                    + " own measures a width that cell never gives it, %s",
                A_BLOCK_OF_CONTROLS_STOPS_AT, at)
            .isEqualTo(List.of("none"));
        assertThat(
                ((List<?>) drawn.get("theInkOfTheKeysTheyHangUnder"))
                    .stream().map(String::valueOf).toList())
            .as(
                "a key carrying facts names the group under it and is written in an ink of its"
                    + " own, not the one every other key is written in, "
                    + at)
            .isNotEmpty()
            .doesNotContain(String.valueOf(drawn.get("theInkOfAKeyCarryingAValueItself")));
        double howBrightAKeyCarryingAValueIs =
            ((Number) drawn.get("howBrightAKeyCarryingAValueItselfIs")).doubleValue();
        for (Object howBright : (List<?>) drawn.get("howBrightTheKeysTheyHangUnderAre")) {
          assertThat(((Number) howBright).doubleValue())
              .as(
                  "an ink of its own is not enough: the key naming a group is written in a"
                      + " stronger ink than a key carrying a value, which on a light ground"
                      + " means a darker one and on a dark ground a lighter one, so a paler"
                      + " colour would read as less important than the keys under it, "
                      + at)
              .matches(
                  brightness ->
                      scheme == ColorScheme.LIGHT
                          ? brightness < howBrightAKeyCarryingAValueIs
                          : brightness > howBrightAKeyCarryingAValueIs,
                  "stronger than " + howBrightAKeyCarryingAValueIs + " in the " + scheme);
        }
        assertThat((List<?>) drawn.get("theSpaceAboveThem"))
            .as(
                "a group of facts stands under the key naming it rather than a block away from"
                    + " it, so it does not take the space the outermost list is given above"
                    + " itself, "
                    + at)
            .isEqualTo(List.of(A_GROUP_OF_FACTS_STANDS_THIS_FAR_BELOW_ITS_KEY));
        assertThat(drawn.get("thePageScrollsSideways"))
            .as("no width a group is set in by may push the answer off the screen, " + at)
            .isEqualTo(false);
      }
      theMarkEachSchemeDraws.add(String.valueOf(drawn.get("theMarksDownTheirLeftEdges")));
    }

    assertThat(theMarkEachSchemeDraws)
        .as(
            "the mark down a group's left edge is taken from the line colour of whichever scheme"
                + " the browser prefers, so the two schemes draw it in two colours; one colour"
                + " written into the rule itself would come out the same in both")
        .doesNotHaveDuplicates();

    theBrowserSawNothingGoWrong();
  }

  @Test
  @DisplayName("a field that receives focus under the fixed top bar is scrolled out from under it")
  void aFocusedFieldIsNeverHiddenUnderTheTopBar() {
    page().setViewportSize(A_LAPTOP_IS_THIS_WIDE, A_LAPTOP_IS_THIS_TALL);
    page().navigate(at("/ui/scenarios?provider=" + GROW + "&testRunId=" + testRunId()));
    page().getByText(THE_HAND_WRITTEN_RULE_IS_BEHIND_THIS).click();
    Locator field = page().locator("#merchantId");
    PlaywrightAssertions.assertThat(field).isVisible();

    page()
        .evaluate(
            "([field, from]) => window.scrollBy(0,"
                + " document.querySelector(field).getBoundingClientRect().top - from)",
            List.of("#merchantId", A_FIELD_SITS_THIS_FAR_DOWN_THE_WINDOW));
    double coveredBy =
        ((Number)
                page()
                    .evaluate(
                        "() => document.querySelector('header.topbar').getBoundingClientRect()"
                            + ".bottom"))
            .doubleValue();
    assertThat(field.boundingBox().y + field.boundingBox().height)
        .as(
            "before it is focused the field must sit wholly under the top bar, or this test"
                + " would pass without a top bar ever covering anything")
        .isLessThanOrEqualTo(coveredBy);

    field.focus();

    assertThat(
            (Boolean)
                page()
                    .evaluate(
                        "(field) => { const box = document.querySelector(field)"
                            + ".getBoundingClientRect();"
                            + " const top = document.elementFromPoint(box.left + box.width / 2,"
                            + " box.top + 1);"
                            + " const bottom = document.elementFromPoint(box.left + box.width / 2,"
                            + " box.bottom - 1);"
                            + " const mine = (found) => found != null"
                            + " && (found === document.querySelector(field)"
                            + " || document.querySelector(field).contains(found));"
                            + " return mine(top) && mine(bottom); }",
                        "#merchantId"))
        .as(
            "a field a tester has just focused must be uncovered from its top edge to its"
                + " bottom edge, not left under the "
                + coveredBy
                + "px top bar")
        .isTrue();

    theBrowserSawNothingGoWrong();
  }

  @Test
  @DisplayName("the API page, which has no rail, leaves no gutter where the rail would be")
  void theApiPageStartsAtTheLeftEdge() {
    page().setViewportSize(A_LAPTOP_IS_THIS_WIDE, A_LAPTOP_IS_THIS_TALL);
    page().navigate(at("/openapi/index.html"));

    assertThat(page().locator("nav.rail").count())
        .as("the API page is generated without the rail the screens carry")
        .isZero();
    assertThat(page().locator("body > main").count())
        .as(
            "the API page must hold its document in one main that is body's own child, the"
                + " element whose left edge this test measures and whose descendants the"
                + " screens' own rules must not reach, not a main Swagger nests inside it")
        .isEqualTo(1);
    double left =
        ((Number)
                page()
                    .evaluate(
                        "() => document.querySelector('body > main')"
                            + ".getBoundingClientRect().left"))
            .doubleValue();
    assertThat(left)
        .as(
            "the rail's "
                + THE_RAIL_IS_THIS_WIDE
                + "px offset belongs to pages that have a rail; the API page's main starts at "
                + left)
        .isLessThan(1.0);
  }

  @Test
  @DisplayName("the screens' own rules for tables, details and buttons stop at the API page")
  void theApiPageIsLeftToItsOwnStyles() {
    page().setViewportSize(A_LAPTOP_IS_THIS_WIDE, A_LAPTOP_IS_THIS_TALL);
    page().navigate(at("/ui/objects?provider=" + GROW + "&testRunId=" + testRunId()));
    page().mouse().move(0, 0);
    assertThat(page().evaluate(WHAT_THE_SCREENS_RULES_DO_TO_A_TABLE_A_DETAILS_AND_A_BUTTON))
        .as(
            "on a screen of the rail a header cell is set in capitals, every second row is"
                + " striped, a details keeps its distance and a button's label stays on one line;"
                + " unless the probe sees that here it cannot see it anywhere")
        .isEqualTo(WHAT_THE_SCREENS_RULES_DO);

    page().navigate(at("/openapi/index.html"));
    page().mouse().move(0, 0);
    assertThat(page().locator("body > main").count())
        .as("the API page holds its document in one main that is body's own child")
        .isEqualTo(1);
    assertThat(page().evaluate(WHAT_THE_SCREENS_RULES_DO_TO_A_TABLE_A_DETAILS_AND_A_BUTTON))
        .as(
            "the API page loads ui.css for its bar, and Swagger draws its own tables, details"
                + " and buttons inside its main; none of the screens' rules may reach them")
        .isEqualTo(WHAT_A_BROWSER_DOES_UNSTYLED);

    PlaywrightAssertions.assertThat(page().locator(".opblock").first()).isVisible();
    assertThat(page().evaluate(HOW_SWAGGERS_OWN_OPERATION_BUTTON_IS_PAINTED))
        .as(
            "an operation's summary is a real button Swagger draws itself; the screens' button"
                + " rule must not paint it with this UI's accent or force its label onto"
                + " one line")
        .isEqualTo(HOW_SWAGGER_PAINTS_ITS_OWN_BUTTON_UNSTYLED);
  }

  @Test
  @DisplayName("a preview that posts is secondary, and a button that writes after it is primary")
  void aPreviewIsSecondaryWhateverItsMethod() {
    page().setViewportSize(A_LAPTOP_IS_THIS_WIDE, A_LAPTOP_IS_THIS_TALL);
    page().navigate(at("/ui/scenarios?provider=" + GROW + "&testRunId=" + testRunId()));
    Locator preview =
        page()
            .getByRole(
                AriaRole.BUTTON,
                new Page.GetByRoleOptions().setName("Show the rules this preset expands into"));
    PlaywrightAssertions.assertThat(preview).isVisible();
    assertThat(backgroundOf(preview))
        .as(
            "showing what a preset expands into changes nothing, although it is posted, so its"
                + " button is secondary and painted on the panel")
        .isEqualTo(A_PANEL);
    assertThat(colourOf(preview))
        .as("a secondary button keeps the accent for its label")
        .isEqualTo(THE_ACCENT);

    preview.click();
    Locator write =
        page().getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Write these rules"));
    PlaywrightAssertions.assertThat(write).isVisible();
    assertThat(backgroundOf(write))
        .as("writing the rules the preview showed is the change, so its button is primary")
        .isEqualTo(THE_ACCENT);

    theBrowserSawNothingGoWrong();
  }

  @Test
  @DisplayName("the confirm of a preset sits in the row of the button it confirms")
  void thePresetsConfirmSitsBesideItsButton() {
    page().setViewportSize(A_LAPTOP_IS_THIS_WIDE, A_LAPTOP_IS_THIS_TALL);
    page().navigate(at("/ui/scenarios?provider=" + GROW + "&testRunId=" + testRunId()));
    page()
        .getByRole(
            AriaRole.BUTTON,
            new Page.GetByRoleOptions().setName("Show the rules this preset expands into"))
        .click();

    Locator write =
        page().getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Write these rules"));
    PlaywrightAssertions.assertThat(write).isVisible();
    Locator confirm = page().locator("#confirm-preset");

    double buttonMiddle = write.boundingBox().y + write.boundingBox().height / 2;
    double confirmMiddle = confirm.boundingBox().y + confirm.boundingBox().height / 2;
    assertThat(Math.abs(buttonMiddle - confirmMiddle))
        .as(
            "every other confirm on these screens is a group beside its button in the form's"
                + " actions row; the preset's confirm must be too, not a stray grid cell above it")
        .isLessThan(write.boundingBox().height / 2);
    assertThat(
            (Boolean)
                page()
                    .evaluate(
                        "() => document.querySelector('#confirm-preset').closest('.confirmGroup')"
                            + " === document.querySelector('label[for=confirm-preset]')"
                            + ".closest('.confirmGroup')"
                            + " && document.querySelector('#confirm-preset')"
                            + ".closest('.confirmGroup') != null"))
        .as("the preset's checkbox and its label are one confirm group")
        .isTrue();

    theBrowserSawNothingGoWrong();
  }

  @Test
  @DisplayName("on a table of one tbody per entry, every second entry's summary row is striped")
  void theSummaryRowOfEverySecondEntryIsStriped() {
    aPaymentProcessIsStartedOverGrowsOwnRoute("a-striped-grow-user", "a-striped-grow-page", 1);
    page().setViewportSize(A_LAPTOP_IS_THIS_WIDE, A_LAPTOP_IS_THIS_TALL);
    page().navigate(at("/ui/audit?provider=" + GROW + "&testRunId=" + testRunId()));
    page().mouse().move(0, 0);

    List<String> painted = linesOf(HOW_THE_FIRST_ROW_OF_EACH_ENTRY_IS_PAINTED);
    assertThat(painted)
        .as(
            "the audit gives each exchange a tbody of its own, so the stripe must follow the"
                + " entries, not the rows inside one: the first entry's summary row is left"
                + " plain and every cell of the second entry's summary row is striped")
        .filteredOn(cell -> cell.startsWith("entry 1: "))
        .isNotEmpty()
        .allSatisfy(cell -> assertThat(cell).isEqualTo("entry 1: " + AN_UNSTRIPED_CELL));
    assertThat(painted)
        .filteredOn(cell -> cell.startsWith("entry 2: "))
        .isNotEmpty()
        .allSatisfy(cell -> assertThat(cell).isEqualTo("entry 2: " + THE_STRIPE_OF_AN_EVEN_ENTRY));

    String theSecondEntry =
        (String)
            page()
                .getByRole(
                    AriaRole.LINK, new Page.GetByRoleOptions().setName("open").setExact(true))
                .nth(1)
                .evaluate("(link) => new URL(link.href).searchParams.get('open')");
    page()
        .navigate(
            at(
                "/ui/audit?provider="
                    + GROW
                    + "&testRunId="
                    + testRunId()
                    + "&open="
                    + theSecondEntry));
    PlaywrightAssertions.assertThat(page().locator("tr.open").first()).isVisible();
    page().mouse().move(0, 0);
    assertThat(linesOf(HOW_THE_FIRST_ROW_OF_EACH_ENTRY_IS_PAINTED))
        .as("an open entry is washed above the stripe its place in the table gives it")
        .filteredOn(cell -> cell.startsWith("entry 2: "))
        .isNotEmpty()
        .allSatisfy(cell -> assertThat(cell).isEqualTo("entry 2: " + THE_WASH_OF_AN_OPEN_ROW));

    theBrowserSawNothingGoWrong();
  }

  @Test
  @DisplayName("every text and field edge of every screen reads at its minimum in light and dark")
  void everyTextAndFieldEdgeIsReadableInBothSchemes() {
    aPaymentProcessIsStartedOverGrowsOwnRoute("a-legible-grow-user", "a-legible-grow-page", 1);

    List<String> grounds = new ArrayList<>();
    for (ColorScheme scheme : List.of(ColorScheme.LIGHT, ColorScheme.DARK)) {
      page().emulateMedia(new Page.EmulateMediaOptions().setColorScheme(scheme));
      page().navigate(at("/ui?provider=" + GROW + "&testRunId=" + testRunId()));
      grounds.add(
          (String) page().evaluate("() => getComputedStyle(document.body).backgroundColor"));
      for (String screen : THE_SCREENS_A_TESTER_READS) {
        page().setViewportSize(A_LAPTOP_IS_THIS_WIDE, A_LAPTOP_IS_THIS_TALL);
        page().navigate(at(screen + "?provider=" + GROW + "&testRunId=" + testRunId()));
        theScreenReadsAtItsMinimum(scheme, screen);
      }
      page().setViewportSize(A_LAPTOP_IS_THIS_WIDE, A_LAPTOP_IS_THIS_TALL);
      page().navigate(at("/ui/objects/new?provider=" + GROW + "&testRunId=" + testRunId()));
      page().locator("input[name=startedAs]").first().check();
      page()
          .getByRole(
              AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Next: what this payment says"))
          .click();
      PlaywrightAssertions.assertThat(
              page()
                  .getByRole(AriaRole.HEADING, new Page.GetByRoleOptions().setName("Step 2 of 2")))
          .isVisible();
      theScreenReadsAtItsMinimum(scheme, "the second step of the wizard");

      page().setViewportSize(A_LAPTOP_IS_THIS_WIDE, A_LAPTOP_IS_THIS_TALL);
      page().navigate(at("/ui/dashboard?provider=" + GROW + "&testRunId=" + testRunId()));
      page().evaluate(A_NOTICE_OF_EVERY_KIND_AND_A_BADGE_OF_EVERY_TONE);
      PlaywrightAssertions.assertThat(page().locator(".tag.attention").first()).isVisible();
      theScreenReadsAtItsMinimum(scheme, "a notice of every kind and a badge of every tone");
    }
    assertThat(grounds)
        .as("a browser that prefers the dark scheme is given a page grounded in another colour")
        .doesNotHaveDuplicates();

    theBrowserSawNothingGoWrong();
  }

  @Test
  @DisplayName("the rail costs a form a column, and takes the width only where two survive a bar")
  void theRailIsGivenItsWidthOnlyWhereTwoColumnsSurviveAScrollbar() {
    page().navigate(at("/ui/configuration/account?provider=" + GROW + "&testRunId=" + testRunId()));

    page().setViewportSize(THE_RAIL_TAKES_OVER_AT - 1, A_LAPTOP_IS_THIS_TALL);
    Object oneShortOfIt = page().evaluate(WHAT_THE_NAVIGATION_AND_THE_FORM_ARE, 0);
    page().setViewportSize(THE_RAIL_TAKES_OVER_AT, A_LAPTOP_IS_THIS_TALL);
    Object wideEnough = page().evaluate(WHAT_THE_NAVIGATION_AND_THE_FORM_ARE, 0);
    Object withABarBesideIt =
        page().evaluate(WHAT_THE_NAVIGATION_AND_THE_FORM_ARE, A_CLASSIC_SCROLLBAR_IS_THIS_WIDE);

    assertThat(oneShortOfIt)
        .as(
            "one pixel short of %spx the navigation wraps into rows and the whole window is the"
                + " form's, which is three columns; this is the column the rail costs, and the"
                + " rail is worth it because it gives that navigation back a fixed place and 43px"
                + " of the height the wrapping rows take",
            THE_RAIL_TAKES_OVER_AT)
        .isEqualTo(
            Map.of(
                "railIsAColumnOfItsOwn",
                false,
                "formColumns",
                3,
                "theNarrowestColumn",
                249,
                "thePageScrollsSideways",
                false));
    assertThat(wideEnough)
        .as(
            "at %spx the 232px rail leaves the form two columns of the 230px minimum its own grid"
                + " declares, with %spx to spare over the %spx where they fit exactly",
            THE_RAIL_TAKES_OVER_AT,
            THE_RAIL_TAKES_OVER_AT - THE_FORM_FITS_TWO_COLUMNS_BESIDE_THE_RAIL_FROM,
            THE_FORM_FITS_TWO_COLUMNS_BESIDE_THE_RAIL_FROM)
        .isEqualTo(
            Map.of(
                "railIsAColumnOfItsOwn",
                true,
                "formColumns",
                2,
                "theNarrowestColumn",
                250,
                "thePageScrollsSideways",
                false));
    assertThat(withABarBesideIt)
        .as(
            "a media query counts the scrollbar's width and the layout beside the rail does not,"
                + " so a classic %spx bar comes out of the same budget the second column needs."
                + " At %spx it would take the column away; the slack is what stops a platform"
                + " that draws its scrollbars in the page from getting a one-column form",
            A_CLASSIC_SCROLLBAR_IS_THIS_WIDE, THE_FORM_FITS_TWO_COLUMNS_BESIDE_THE_RAIL_FROM)
        .isEqualTo(
            Map.of(
                "railIsAColumnOfItsOwn",
                true,
                "formColumns",
                2,
                "theNarrowestColumn",
                242,
                "thePageScrollsSideways",
                false));
  }

  @Test
  @DisplayName("a badge in a table's pinned first column wraps inside it, whatever word it holds")
  void aBadgeInAPinnedFirstColumnNeverPaintsOverTheCellBesideIt() {
    for (int[] window :
        new int[][] {
          {TheBrowser.A_PHONE_IS_THIS_WIDE, TheBrowser.A_PHONE_IS_THIS_TALL},
          {A_LAPTOP_IS_THIS_WIDE, A_LAPTOP_IS_THIS_TALL},
          {A_WIDE_SCREEN_IS_THIS_WIDE, A_LAPTOP_IS_THIS_TALL}
        }) {
      page().navigate(at("/ui/personas?provider=autra&testRunId=" + testRunId()));
      page().setViewportSize(window[0], window[1]);
      Map<?, ?> measured = (Map<?, ?>) page().evaluate(HOW_FAR_A_PINNED_BADGE_RUNS_PAST_ITS_CELL);

      assertThat(((Number) measured.get("badges")).intValue())
          .as("a rule about badges in the first column is worth nothing if there are none")
          .isGreaterThan(0);
      assertThat(((Number) measured.get("worst")).intValue())
          .as(
              "at %spx the widest badge is %s, and a badge holds a provider's own published word,"
                  + " which no template chooses the length of: it wraps inside its column rather"
                  + " than running over the sentence beside it, because that sentence is the row",
              window[0], measured.get("widest"))
          .isLessThanOrEqualTo(0);
    }
  }

  @Test
  @DisplayName("on a very wide screen only the grids take the window and every other block stops")
  void onAVeryWideScreenOnlyTheGridsTakeTheWindow() {
    theGrowAccountIsConfiguredByClicking("a-wide-user", "a-wide-page");
    page().setViewportSize(A_WIDE_SCREEN_IS_THIS_WIDE, A_LAPTOP_IS_THIS_TALL);

    for (String screen : List.of("/ui/audit", "/ui/configuration", "/ui/idempotency")) {
      page().navigate(at(screen + "?provider=" + GROW + "&testRunId=" + testRunId()));
      Map<?, ?> measured = (Map<?, ?>) page().evaluate(WHAT_EVERY_BLOCK_OF_A_SCREEN_MEASURES);

      for (String block : List.of("form", "filters", "pairs")) {
        Object width = measured.get(block);
        if (width != null) {
          assertThat(((Number) width).intValue())
              .as(
                  "%s on %s is a block of controls, not a grid: nothing it holds reads better for"
                      + " being %spx wide, and before this cap the audit filter became seventeen"
                      + " columns across an ultrawide",
                  block, screen, A_WIDE_SCREEN_IS_THIS_WIDE)
              .isLessThanOrEqualTo(A_BLOCK_OF_CONTROLS_STOPS_AT);
        }
      }
      for (String block : List.of("prose", "notice")) {
        Object width = measured.get(block);
        if (width != null) {
          assertThat(((Number) width).intValue())
              .as("%s on %s is a sentence and keeps a measure", block, screen)
              .isLessThanOrEqualTo(A_SENTENCE_STOPS_AT);
        }
      }
      if ("/ui/audit".equals(screen)) {
        assertThat(((Number) measured.get("narrowTable")).intValue())
            .as(
                "a grid is the one thing that wants the window: the audit trail's fifteen columns"
                    + " are wider than any block of controls is allowed to be, and the cap on main"
                    + " used to stop them at 1600px and scroll them sideways")
            .isGreaterThan(A_BLOCK_OF_CONTROLS_STOPS_AT);
      }
    }
  }

  private static final String WHERE_THE_SPAN_CONTROL_SITS =
      """
      () => {
        const box = document.querySelector('input[name="field.captureWindow"]');
        const units = document.querySelector('select[name="unitOf.captureWindow"]');
        const cell = box.closest('form.form > div');
        const of = (element) => element.getBoundingClientRect();
        return {
          onOneRow: Math.abs(of(box).top - of(units).top) <= 1,
          pastItsCell: Math.round(Math.max(
            of(units).right - of(cell).right, of(cell).left - of(box).left)),
          aNumberAndAUnitAreBothReadable:
            Math.round(Math.min(of(box).width, of(units).width)) >= 48,
          thePageScrollsSideways:
            Math.ceil(document.documentElement.scrollWidth) > window.innerWidth,
        };
      }
      """;

  @Test
  @DisplayName("a span of time keeps its number and its unit side by side inside one form column")
  void theSpanControlFitsTheColumnItIsDrawnIn() {
    for (int[] window :
        List.of(
            new int[] {A_LAPTOP_IS_THIS_WIDE, A_LAPTOP_IS_THIS_TALL},
            new int[] {TheBrowser.A_PHONE_IS_THIS_WIDE, TheBrowser.A_PHONE_IS_THIS_TALL})) {
      page().setViewportSize(window[0], window[1]);
      page()
          .navigate(at("/ui/configuration/account?provider=" + GROW + "&testRunId=" + testRunId()));

      assertThat(page().evaluate(WHERE_THE_SPAN_CONTROL_SITS))
          .as(
              "the form's own measurement reads the account identity box, which is a plain"
                  + " column this control is not in, so the two controls of a span are measured"
                  + " here: they sit on one row, end inside the column the grid gives them, and"
                  + " push nothing sideways, at %sx%s",
              window[0], window[1])
          .isEqualTo(
              Map.of(
                  "onOneRow",
                  true,
                  "pastItsCell",
                  0,
                  "aNumberAndAUnitAreBothReadable",
                  true,
                  "thePageScrollsSideways",
                  false));
    }

    theBrowserSawNothingGoWrong();
  }

  @Test
  @DisplayName("headings step up the type scale, labels read as labels and prose keeps a measure")
  void theTypeScaleStepsAndProseKeepsItsMeasure() {
    page().setViewportSize(A_WIDE_SCREEN_IS_THIS_WIDE, A_LAPTOP_IS_THIS_TALL);
    page().navigate(at("/ui/configuration/account?provider=" + GROW + "&testRunId=" + testRunId()));

    assertThat(page().evaluate(WHAT_THE_TYPE_SCALE_MEASURES))
        .as(
            "an h3 must read larger than body text and an h2 larger than an h3; a label is"
                + " written in the soft ink at a weight that marks it a label; a paragraph stops"
                + " at 72 characters and a form stops adding columns, while main itself takes the"
                + " whole window, because a grid wants the width a sentence must not have")
        .isEqualTo(WHAT_THE_TYPE_SCALE_IS);
  }

  @Test
  @DisplayName("in the dark scheme the API page reads in Swagger's dark mode and in its light one")
  void theApiPageIsReadableInTheDarkScheme() {
    page().emulateMedia(new Page.EmulateMediaOptions().setColorScheme(ColorScheme.DARK));
    page().setViewportSize(A_LAPTOP_IS_THIS_WIDE, A_LAPTOP_IS_THIS_TALL);
    page().navigate(at("/openapi/index.html"));
    PlaywrightAssertions.assertThat(page().locator(".opblock").first()).isVisible();
    PlaywrightAssertions.assertThat(page().locator("html")).hasClass(SWAGGERS_DARK_MODE);

    theApiPageReads("with Swagger in the dark mode it chose for a browser that prefers dark");

    page().locator(".dark-mode-toggle button").click();
    PlaywrightAssertions.assertThat(page().locator("html")).not().hasClass(SWAGGERS_DARK_MODE);
    page().evaluate("() => Promise.all(document.getAnimations().map((each) => each.finished))");

    theApiPageReads(
        "with Swagger switched to its light mode, whose text is dark and whose ground is"
            + " whatever the page lays under it");

    page().setViewportSize(TheBrowser.A_PHONE_IS_THIS_WIDE, TheBrowser.A_PHONE_IS_THIS_TALL);
    assertThat(browser().howWideTheDocumentIs())
        .as("the API page must not scroll sideways on a phone in the dark scheme")
        .isLessThanOrEqualTo(browser().howWideTheViewportIs());
  }

  private void theScreenReadsAtItsMinimum(ColorScheme scheme, String screen) {
    page().evaluate(EVERY_DETAILS_IS_OPENED);
    page().mouse().move(0, 0);
    assertThat(
            (List<?>)
                page()
                    .evaluate(
                        THE_TEXT_AND_FIELD_EDGES_BELOW_THEIR_MINIMUM,
                        List.of(TEXT_CONTRASTS_AT_LEAST, A_FIELD_EDGE_CONTRASTS_AT_LEAST)))
        .as(
            "WCAG AA asks 4.5:1 of text and 3:1 of the edge that shows where a field is, with"
                + " every details open; in the "
                + scheme
                + " scheme "
                + screen
                + " falls short at")
        .isEmpty();
    page().setViewportSize(TheBrowser.A_PHONE_IS_THIS_WIDE, TheBrowser.A_PHONE_IS_THIS_TALL);
    assertThat(browser().howWideTheDocumentIs())
        .as("in the " + scheme + " scheme " + screen + " must not scroll sideways on a phone")
        .isLessThanOrEqualTo(browser().howWideTheViewportIs());
  }

  private void theApiPageReads(String when) {
    assertThat(
            linesOf(
                THE_WEAKEST_TEXT_OF_THE_API_PAGE,
                List.of(
                    "header.bar h1",
                    ".swagger-ui .info .title",
                    ".swagger-ui .opblock-tag",
                    ".swagger-ui .opblock-summary-path")))
        .as(when + ", every one of these must read at 4.5:1")
        .allSatisfy(
            line ->
                assertThat(Double.parseDouble(line.substring(line.lastIndexOf(' ') + 1)))
                    .as(line)
                    .isGreaterThanOrEqualTo(TEXT_CONTRASTS_AT_LEAST));
  }

  private void aControlReachedByShiftTabIsUncovered(
      Locator target, Locator after, boolean across, String pin) {
    target.evaluate(THE_TABLE_SCROLLS_UNTIL_THIS_SITS_UNDER_ITS_PINS, across);
    after.evaluate("(element) => element.focus({ preventScroll: true })");
    assertThat(cornersOf(target))
        .as(
            "before the shift-tab the control must sit under "
                + pin
                + ", or this test would pass without anything ever covering it")
        .contains(pin);

    page().keyboard().press("Shift+Tab");

    assertThat((Boolean) target.evaluate("(element) => element === document.activeElement"))
        .as("shift-tab from the next control of the table reaches this one")
        .isTrue();
    assertThat(cornersOf(target))
        .as(
            "a control a tester has just shift-tabbed to must be scrolled out from under "
                + pin
                + " of its table, at every corner")
        .containsOnly("itself");
    theBrowserSawNothingGoWrong();
  }

  private String colourOf(Locator element) {
    page().mouse().move(0, 0);
    return (String) element.evaluate("(element) => getComputedStyle(element).color");
  }

  private String backgroundOf(Locator element) {
    page().mouse().move(0, 0);
    return (String) element.evaluate("(element) => getComputedStyle(element).backgroundColor");
  }

  private List<String> cornersOf(Locator element) {
    return ((List<?>) element.evaluate(WHAT_COVERS_THE_CORNERS_OF))
        .stream().map(String::valueOf).toList();
  }

  private void aCallbackIsQueuedForAPaymentThePayerFinished(String userId, String pageCode) {
    theGrowAccountIsConfiguredByClicking(userId, pageCode);
    page().navigate(aHostedPageGrowMints(userId, pageCode, "one seat, row A"));
    page().locator("select").selectOption(THE_CARD_THE_PAYER_PICKS);
    page()
        .getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("paid on the page"))
        .click();
    PlaywrightAssertions.assertThat(page().getByText(TheMerchant.THANKS)).isVisible();
  }

  private String aHostedPageGrowMints(String userId, String pageCode, String paidFor) {
    return outsideTheBrowser()
        .theHostedPageUrlGrowMints(
            testRunId(),
            NotThroughTheBrowser.form(
                "userId",
                userId,
                "pageCode",
                pageCode,
                "chargeType",
                "2",
                "sum",
                "10.00",
                "description",
                paidFor,
                "successUrl",
                merchant().whereAPayerLandsHavingPaid(),
                "cancelUrl",
                merchant().whereAPayerLandsHavingGivenUp(),
                "pageField[fullName]",
                "Dana Levi",
                "pageField[phone]",
                "0501234567",
                "notifyUrl",
                merchant().whereCallbacksGo()));
  }

  private void aPaymentProcessIsStartedOverGrowsOwnRoute(
      String userId, String pageCode, int howMany) {
    theGrowAccountIsConfiguredByClicking(userId, pageCode);
    for (int started = 0; started < howMany; started++) {
      aHostedPageGrowMints(userId, pageCode, "one seat, row " + started);
    }
  }

  private void theAccountIsOpenOnTheConfigurationScreen(String userId, String pageCode) {
    theGrowAccountIsConfiguredByClicking(userId, pageCode);
    page()
        .navigate(
            at(
                "/ui/configuration?provider="
                    + GROW
                    + "&testRunId="
                    + testRunId()
                    + "&open="
                    + ACCOUNT));
    page().mouse().move(0, 0);
  }

  private List<String> linesOf(String script) {
    return ((List<?>) page().evaluate(script)).stream().map(String::valueOf).toList();
  }

  private List<String> linesOf(String script, Object argument) {
    return ((List<?>) page().evaluate(script, argument)).stream().map(String::valueOf).toList();
  }
}
