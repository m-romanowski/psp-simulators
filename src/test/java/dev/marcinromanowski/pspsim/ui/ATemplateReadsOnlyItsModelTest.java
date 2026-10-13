package dev.marcinromanowski.pspsim.ui;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.Timeout.ThreadMode;
import org.springframework.context.support.StaticApplicationContext;
import org.springframework.core.env.MapPropertySource;
import org.springframework.expression.spel.support.StandardEvaluationContext;
import org.thymeleaf.context.Context;
import org.thymeleaf.spring6.SpringTemplateEngine;
import org.thymeleaf.spring6.expression.ThymeleafEvaluationContext;
import org.thymeleaf.spring6.expression.ThymeleafEvaluationContextWrapper;
import org.thymeleaf.templateresolver.StringTemplateResolver;

class ATemplateReadsOnlyItsModelTest {

  private static final String THE_SECRET = "the-password-no-page-may-print";

  private static final String THE_PASSWORD_OF_THE_DATABASE = "spring.datasource.password";

  private static final List<String> WHAT_A_SCAN_IS_WORST_AT =
      List.of(
          "&#64",
          "&#",
          "&",
          "&#;",
          "&#x",
          "&#x;",
          "&commat",
          "&#99999999;",
          "&#9999999999;",
          "&#0;",
          "${",
          "${@",
          "${'",
          "${'@a",
          "${@environment",
          "*{",
          "#{",
          "${}",
          "${{",
          "${&#36;&#123;@x",
          "&#36;&#123;&#64;x",
          "${'}",
          "${'}'",
          "${''",
          "${'''",
          "${a}${",
          "${@a}${@b",
          "$",
          "{",
          "}",
          "${'\\'}",
          "${#strings.concat('a@b','",
          "${" + "{".repeat(5000),
          "${" + "'".repeat(5000),
          "&#64;".repeat(5000) + "${@x",
          "${" + "@a ".repeat(5000));

  private static final String READING_THE_PASSWORD =
      "<p th:text=\"${@environment.getProperty('" + THE_PASSWORD_OF_THE_DATABASE + "')}\"></p>";

  @Test
  @Timeout(value = 2, unit = TimeUnit.SECONDS, threadMode = ThreadMode.SEPARATE_THREAD)
  void a_bean_a_page_would_evaluate_is_named_wherever_an_expression_asks_for_one() {
    assertThat(ATemplateReadsOnlyItsModel.theBeansItReaches(READING_THE_PASSWORD))
        .containsExactly("environment");
    assertThat(
            ATemplateReadsOnlyItsModel.theBeansItReaches(
                "<p th:text=\"${@providerAccounts.identitiesOf(@providerRegistry.names())}\"></p>"))
        .containsExactly("providerAccounts", "providerRegistry");
    assertThat(ATemplateReadsOnlyItsModel.theBeansItReaches("<div th:with=\"x = *{@theRail}\">"))
        .containsExactly("theRail");
    assertThat(ATemplateReadsOnlyItsModel.theBeansItReaches("<p th:text=\"#{@messages}\"></p>"))
        .containsExactly("messages");
    assertThat(
            ATemplateReadsOnlyItsModel.theBeansItReaches(
                "<p th:text=\"${ {'a': 1}.size() + @b }\"></p>"))
        .as("an expression that opens and closes a brace of its own has not ended there")
        .containsExactly("b");
  }

  @Test
  @Timeout(value = 2, unit = TimeUnit.SECONDS, threadMode = ThreadMode.SEPARATE_THREAD)
  void a_bean_written_as_a_character_reference_is_the_same_bean_to_the_engine() {
    assertThat(
            ATemplateReadsOnlyItsModel.theBeansItReaches(
                "<p th:text=\"${&#64;environment.getProperty('x')}\"></p>"))
        .as(
            "an attribute value is unescaped before its expression is parsed, so &#64; is an @ by"
                + " the time the engine reads it, and a scan of the raw markup alone let this page"
                + " be committed")
        .containsExactly("environment");
    assertThat(
            ATemplateReadsOnlyItsModel.theBeansItReaches(
                "<p th:text=\"${&commat;providerAccounts}\"></p>"))
        .containsExactly("providerAccounts");
    assertThat(
            ATemplateReadsOnlyItsModel.theBeansItReaches(
                "<p th:text=\"${&#x40;environment}\"></p>"))
        .containsExactly("environment");
    assertThat(
            ATemplateReadsOnlyItsModel.theBeansItReaches(
                "<p th:text=\"&#36;&#123;&#64;environment&#125;\"></p>"))
        .as("the braces and the dollar of an expression can be written that way too")
        .containsExactly("environment");
  }

  @Test
  @Timeout(value = 2, unit = TimeUnit.SECONDS, threadMode = ThreadMode.SEPARATE_THREAD)
  void a_character_reference_that_names_no_character_is_left_as_it_was_written() {
    assertThat(ATemplateReadsOnlyItsModel.withEveryCharacterReferenceResolved("&#9999999; &nbsp;"))
        .isEqualTo("&#9999999; &nbsp;");
    assertThat(ATemplateReadsOnlyItsModel.withEveryCharacterReferenceResolved("&amp; &#x40;"))
        .isEqualTo("&amp; @");
  }

  @Test
  @Timeout(value = 2, unit = TimeUnit.SECONDS, threadMode = ThreadMode.SEPARATE_THREAD)
  void an_at_sign_inside_something_a_page_spells_out_is_no_bean_of_this_application() {
    assertThat(
            ATemplateReadsOnlyItsModel.theBeansItReaches(
                "<p th:text=\"${'write to support@example.com'}\"></p>"))
        .as(
            "a page refused at startup for a bean it never asked for is a refusal its contributor"
                + " cannot act on")
        .isEmpty();
    assertThat(
            ATemplateReadsOnlyItsModel.theBeansItReaches(
                "<p th:text=\"${#strings.concat('a@b','')}\"></p>"))
        .isEmpty();
    assertThat(ATemplateReadsOnlyItsModel.theBeansItReaches("<p th:text=\"${'a' + @b}\"></p>"))
        .as("what stands outside the quotes is still read")
        .containsExactly("b");
  }

  @Test
  @Timeout(value = 2, unit = TimeUnit.SECONDS, threadMode = ThreadMode.SEPARATE_THREAD)
  void a_page_that_asks_its_model_for_everything_reaches_no_bean() {
    assertThat(
            ATemplateReadsOnlyItsModel.theBeansItReaches(
                """
                <a th:href="@{/ui/objects(provider=${provider},testRunId=${testRunId})}">Payments</a>
                <li th:each="group : ${rail}"><span th:text="${group.name}"></span></li>
                <p th:if="${#lists.isEmpty(objects)}">none</p>
                <p th:text="${provider}">grow</p>
                <a th:href="@{/ui/audit}">Audit</a>
                <p>Write to support@example.com about a Pix</p>
                """))
        .as(
            "a link expression is where an @ belongs, and one that follows an expression follows a"
                + " region that has ended")
        .isEmpty();
  }

  @Test
  @Timeout(value = 2, unit = TimeUnit.SECONDS, threadMode = ThreadMode.SEPARATE_THREAD)
  void an_expression_that_never_closes_is_read_to_the_end_of_the_page_and_no_further() {
    assertThat(ATemplateReadsOnlyItsModel.theBeansItReaches("${@environment"))
        .as(
            "a page whose last expression is never closed runs the scan to the last character"
                + " there is, which is the one place an index moved by one reads past the end")
        .containsExactly("environment");
    assertThat(ATemplateReadsOnlyItsModel.theBeansItReaches("<p th:text=\"${@a\">"))
        .containsExactly("a");
    assertThat(ATemplateReadsOnlyItsModel.theBeansItReaches("${'unclosed"))
        .as("nor does something a page starts spelling out and never finishes")
        .isEmpty();
  }

  @Test
  @Timeout(value = 2, unit = TimeUnit.SECONDS, threadMode = ThreadMode.SEPARATE_THREAD)
  void where_an_expression_ends_is_the_character_after_the_brace_that_closed_it() {
    List<String> reached = new ArrayList<>();

    assertThat(ATemplateReadsOnlyItsModel.theEndOfTheExpressionFrom("${@a} ${@b}", 2, reached))
        .as(
            "the scan goes on from where this says the expression ended, so a reading that is"
                + " short by one reads the same expression twice and one that is long by one"
                + " reads past what it was given")
        .isEqualTo(5);
    assertThat(reached).containsExactly("a");
    assertThat(
            ATemplateReadsOnlyItsModel.theEndOfTheExpressionFrom("${ {'a':1} + @b }", 2, reached))
        .as("a brace of its own inside the expression is not the brace that closes it")
        .isEqualTo(17);
    assertThat(ATemplateReadsOnlyItsModel.theEndOfTheExpressionFrom("${@a", 2, reached))
        .as("and one that closes nowhere ends at the end of the page")
        .isEqualTo(4);
  }

  @Test
  @Timeout(value = 2, unit = TimeUnit.SECONDS, threadMode = ThreadMode.SEPARATE_THREAD)
  void where_something_a_page_spells_out_ends_is_the_character_after_its_closing_quote() {
    assertThat(ATemplateReadsOnlyItsModel.theEndOfWhatItSpellsOut("${'a@b' + x}", 3))
        .as(
            "the scan resumes there, so a reading short by one leaves the scan inside the quotes"
                + " and reads what they hold as an expression, which is the false refusal this"
                + " skipping exists to stop")
        .isEqualTo(7);
    assertThat(ATemplateReadsOnlyItsModel.theEndOfWhatItSpellsOut("${''}", 3)).isEqualTo(5);
    assertThat(ATemplateReadsOnlyItsModel.theEndOfWhatItSpellsOut("${'never closed", 3))
        .as("what a page never finishes spelling out runs to the end of the page")
        .isEqualTo(15);
  }

  @Test
  @Timeout(value = 2, unit = TimeUnit.SECONDS, threadMode = ThreadMode.SEPARATE_THREAD)
  void every_step_of_the_scan_moves_forward_whatever_a_page_puts_in_front_of_it() {
    assertTimeoutPreemptively(
        Duration.ofSeconds(3),
        () -> {
          for (String pageOrPartOfOne : WHAT_A_SCAN_IS_WORST_AT) {
            assertThat(ATemplateReadsOnlyItsModel.theBeansItReaches(pageOrPartOfOne))
                .as(
                    "["
                        + pageOrPartOfOne
                        + "] is answered rather than scanned forever: every step of both loops"
                        + " moves the reading position on by at least one, because a step either"
                        + " adds one to it or jumps it to the end of an expression or of something"
                        + " spelled out, and both of those lie further along than where they were"
                        + " asked from")
                .isNotNull();
          }
        });
  }

  @Test
  @Timeout(value = 2, unit = TimeUnit.SECONDS, threadMode = ThreadMode.SEPARATE_THREAD)
  void the_delimiters_of_an_expression_written_as_references_are_still_its_delimiters() {
    assertThat(ATemplateReadsOnlyItsModel.theBeansItReaches("&#36;&#123;&#64;environment&#125;"))
        .containsExactly("environment");
    assertThat(ATemplateReadsOnlyItsModel.theBeansItReaches("${&#36;&#123;@x"))
        .containsExactly("x");
    assertThat(ATemplateReadsOnlyItsModel.withEveryCharacterReferenceResolved("&#x10FFFF;"))
        .as("the last code point there is is a code point, and is resolved like any other")
        .isEqualTo(Character.toString(Character.MAX_CODE_POINT));
  }

  @Test
  void the_engine_of_this_simulator_hands_a_page_no_bean_of_this_application() {
    assertThatThrownBy(() -> theEngineOfThisSimulator().process(READING_THE_PASSWORD, aPage()))
        .hasStackTraceContaining("this page asked the application for the bean \"environment\"")
        .hasStackTraceContaining("a page of this simulator reads the model its screen handed it")
        .hasStackTraceContaining("as close as the one that lists a provider's accounts");
  }

  @Test
  void the_engine_as_it_comes_out_of_the_box_prints_that_password_on_the_page() {
    assertThat(anEngineThatHandsOutBeans().process(READING_THE_PASSWORD, aPage()))
        .as(
            "this is the attack the boundary exists to stop, executed against the engine this"
                + " simulator would have had: one contributed html file printing the password of"
                + " the database on a screen a tester opened")
        .contains(THE_SECRET);
  }

  @Test
  void the_engine_still_reads_the_model_its_screen_handed_it() {
    Context page = aPage();
    page.setVariable("provider", "autra");

    assertThat(theEngineOfThisSimulator().process("<p th:text=\"${provider}\"></p>", page))
        .isEqualTo("<p>autra</p>");
  }

  @Test
  void a_context_that_resolves_beans_is_handed_a_resolver_that_refuses_every_one_of_them() {
    StandardEvaluationContext reachingTheApplication = new StandardEvaluationContext();

    ATemplateReadsOnlyItsModel.handNoBeanTo(reachingTheApplication);

    assertThat(reachingTheApplication.getBeanResolver()).isNotNull();
    assertThatThrownBy(
            () ->
                reachingTheApplication
                    .getBeanResolver()
                    .resolve(reachingTheApplication, "providerAccounts"))
        .hasMessageContaining("this page asked the application for the bean \"providerAccounts\"");
  }

  @Test
  void a_page_the_engine_evaluates_in_a_context_of_no_known_kind_is_refused_rather_than_served() {
    assertThatCode(() -> ATemplateReadsOnlyItsModel.handNoBeanTo(null))
        .as(
            "with no evaluation context of its own the engine builds one that has no bean resolver"
                + " at all, and SpEL refuses a bean reference against it")
        .doesNotThrowAnyException();
    assertThatCode(
            () ->
                ATemplateReadsOnlyItsModel.handNoBeanTo(
                    new ThymeleafEvaluationContextWrapper(new StandardEvaluationContext())))
        .as(
            "a wrapper implements the interface and is no StandardEvaluationContext, so the"
                + " resolver cannot be taken off it: the one thing that must not happen is this"
                + " skipping quietly and every template reading any bean again")
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("ThymeleafEvaluationContextWrapper")
        .hasMessageContaining("back to reading any bean it names");
  }

  private static SpringTemplateEngine theEngineOfThisSimulator() {
    SpringTemplateEngine engine = anEngineThatHandsOutBeans();
    engine.setDialect(ATemplateReadsOnlyItsModel.asTheDialectOfTheEngine());
    return engine;
  }

  private static SpringTemplateEngine anEngineThatHandsOutBeans() {
    SpringTemplateEngine engine = new SpringTemplateEngine();
    StringTemplateResolver theMarkupItself = new StringTemplateResolver();
    theMarkupItself.setCacheable(false);
    engine.setTemplateResolver(theMarkupItself);
    return engine;
  }

  private static Context aPage() {
    StaticApplicationContext application = new StaticApplicationContext();
    application.refresh();
    application
        .getEnvironment()
        .getPropertySources()
        .addFirst(
            new MapPropertySource(
                "what a datasource is configured with",
                Map.of(THE_PASSWORD_OF_THE_DATABASE, THE_SECRET)));
    Context page = new Context();
    page.setVariable(
        ATemplateReadsOnlyItsModel.THE_CONTEXT_THE_ENGINE_EVALUATES_IN,
        new ThymeleafEvaluationContext(application, null));
    return page;
  }
}
