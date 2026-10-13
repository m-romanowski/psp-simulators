package dev.marcinromanowski.pspsim.ui;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.jspecify.annotations.Nullable;
import org.springframework.expression.AccessException;
import org.springframework.expression.BeanResolver;
import org.springframework.expression.EvaluationContext;
import org.springframework.expression.spel.support.StandardEvaluationContext;
import org.thymeleaf.context.IExpressionContext;
import org.thymeleaf.spring6.dialect.SpringStandardDialect;
import org.thymeleaf.spring6.expression.SPELVariableExpressionEvaluator;
import org.thymeleaf.spring6.expression.ThymeleafEvaluationContext;
import org.thymeleaf.standard.expression.IStandardVariableExpression;
import org.thymeleaf.standard.expression.IStandardVariableExpressionEvaluator;
import org.thymeleaf.standard.expression.StandardExpressionExecutionContext;

public final class ATemplateReadsOnlyItsModel {

  public static final String WHAT_A_TEMPLATE_READS =
      "a page of this simulator reads the model its screen handed it and nothing else";

  static final String THE_CONTEXT_THE_ENGINE_EVALUATES_IN =
      ThymeleafEvaluationContext.THYMELEAF_EVALUATION_CONTEXT_CONTEXT_VARIABLE_NAME;

  private static final String AN_EXPRESSION_OPENS_WITH = "$*#";

  private static final int NOWHERE_IN_IT = -1;

  private static final Pattern A_CHARACTER_REFERENCE =
      Pattern.compile("&(#[xX][0-9a-fA-F]{1,6}|#[0-9]{1,8}|[A-Za-z][A-Za-z0-9]{1,31});");

  private static final Map<String, String> THE_NAMES_OF_WHAT_AN_EXPRESSION_IS_MADE_OF =
      Map.of(
          "commat", "@",
          "dollar", "$",
          "lcub", "{",
          "rcub", "}",
          "num", "#",
          "ast", "*",
          "apos", "'",
          "quot", "\"");

  private static final BeanResolver NO_BEAN_IS_ON_OFFER = new NoBeanIsOnOffer();

  private static final IStandardVariableExpressionEvaluator ONE_THAT_HANDS_NO_BEAN =
      new AnEvaluatorThatHandsNoBean();

  private ATemplateReadsOnlyItsModel() {}

  public static SpringStandardDialect asTheDialectOfTheEngine() {
    return new ADialectThatHandsNoBean();
  }

  public static List<String> theBeansItReaches(String markup) {
    String asTheEngineReadsIt = withEveryCharacterReferenceResolved(markup);
    List<String> reached = new ArrayList<>();
    int at = 0;
    while (at + 1 < asTheEngineReadsIt.length()) {
      if (asTheEngineReadsIt.charAt(at + 1) != '{'
          || AN_EXPRESSION_OPENS_WITH.indexOf(asTheEngineReadsIt.charAt(at)) < 0) {
        at++;
        continue;
      }
      at = theEndOfTheExpressionFrom(asTheEngineReadsIt, at + 2, reached);
    }
    return List.copyOf(reached);
  }

  static String withEveryCharacterReferenceResolved(String markup) {
    Matcher written = A_CHARACTER_REFERENCE.matcher(markup);
    StringBuilder asTheEngineReadsIt = new StringBuilder(markup.length());
    while (written.find()) {
      written.appendReplacement(
          asTheEngineReadsIt, Matcher.quoteReplacement(theCharacterWritten(written.group(1))));
    }
    written.appendTail(asTheEngineReadsIt);
    return asTheEngineReadsIt.toString();
  }

  private static String theCharacterWritten(String reference) {
    if (!reference.startsWith("#")) {
      return THE_NAMES_OF_WHAT_AN_EXPRESSION_IS_MADE_OF.getOrDefault(
          reference.toLowerCase(Locale.ROOT), "&" + reference + ";");
    }
    boolean inHex = reference.charAt(1) == 'x' || reference.charAt(1) == 'X';
    int code =
        Integer.parseInt(inHex ? reference.substring(2) : reference.substring(1), inHex ? 16 : 10);
    if (!Character.isValidCodePoint(code)) {
      return "&" + reference + ";";
    }
    return Character.toString(code);
  }

  static String theRefusalOfABeanAPageAskedFor(String bean) {
    return "this page asked the application for the bean \""
        + bean
        + "\", and "
        + WHAT_A_TEMPLATE_READS
        + ". A page that reaches a bean reaches every bean, so the one that holds the database"
        + " password is as close as the one that lists a provider's accounts, and a screen"
        + " contributed by dropping a file in would read both. Whatever this page needs, its"
        + " screen puts in the model and the page names there.";
  }

  static int theEndOfTheExpressionFrom(String markup, int from, List<String> reached) {
    int depth = 1;
    int at = from;
    while (at < markup.length() && depth > 0) {
      char current = markup.charAt(at);
      if (current == '\'') {
        at = theEndOfWhatItSpellsOut(markup, at);
        continue;
      }
      if (current == '{') {
        depth++;
      }
      if (current == '}') {
        depth--;
      }
      if (current == '@') {
        reached.add(theNameFollowing(markup, at + 1));
      }
      at++;
    }
    return at;
  }

  static int theEndOfWhatItSpellsOut(String markup, int quote) {
    int closed = markup.indexOf('\'', quote + 1);
    if (closed == NOWHERE_IN_IT) {
      return markup.length();
    }
    return closed + 1;
  }

  private static String theNameFollowing(String markup, int from) {
    int at = from;
    while (at < markup.length() && namesABean(markup.charAt(at))) {
      at++;
    }
    return markup.substring(from, at);
  }

  private static boolean namesABean(char character) {
    return Character.isLetterOrDigit(character) || character == '_';
  }

  static void handNoBeanTo(@Nullable Object evaluationContext) {
    if (evaluationContext instanceof StandardEvaluationContext reachingTheApplication) {
      reachingTheApplication.setBeanResolver(NO_BEAN_IS_ON_OFFER);
      return;
    }
    if (evaluationContext == null) {
      return;
    }
    throw new IllegalStateException(
        "the template engine evaluates this page in a "
            + evaluationContext.getClass().getName()
            + ", and this simulator takes the bean resolver off that context so that "
            + WHAT_A_TEMPLATE_READS
            + ". Only a "
            + StandardEvaluationContext.class.getSimpleName()
            + " carries a resolver this can be taken off, so the boundary cannot be applied to this"
            + " one and every template would be back to reading any bean it names. Nothing about"
            + " this page is wrong; the framework underneath it has moved, and a page is refused"
            + " rather than served with the reach open.");
  }

  private static final class NoBeanIsOnOffer implements BeanResolver {

    @Override
    public Object resolve(EvaluationContext context, String beanName) throws AccessException {
      throw new AccessException(theRefusalOfABeanAPageAskedFor(beanName));
    }
  }

  private static final class ADialectThatHandsNoBean extends SpringStandardDialect {

    @Override
    public IStandardVariableExpressionEvaluator getVariableExpressionEvaluator() {
      return ONE_THAT_HANDS_NO_BEAN;
    }
  }

  private static final class AnEvaluatorThatHandsNoBean
      implements IStandardVariableExpressionEvaluator {

    @Override
    public @Nullable Object evaluate(
        IExpressionContext context,
        IStandardVariableExpression expression,
        StandardExpressionExecutionContext expressionContext) {
      handNoBeanTo(context.getVariable(THE_CONTEXT_THE_ENGINE_EVALUATES_IN));
      return SPELVariableExpressionEvaluator.INSTANCE.evaluate(
          context, expression, expressionContext);
    }
  }
}
