package dev.marcinromanowski.pspsim.documentation;

import dev.marcinromanowski.pspsim.documentation.PayerPageDeclarations.APage;
import dev.marcinromanowski.pspsim.documentation.PayerPageDeclarations.Directive;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.TreeSet;

public final class PayerPagePolicyConvention {

  public static final String THE_RULE =
      "Every class of this build that renders an HTML document a browser loads declares, in that"
          + " same class, the content security policy that document is served under, and every"
          + " provider that tells a tester this simulator serves the payer's page renders such a"
          + " class. The floor every policy stands on is "
          + PayerPageDeclarations.NOTHING_IS_LOADED_BY_DEFAULT
          + ", and a page carrying its own style also names "
          + PayerPageDeclarations.THE_PAGES_OWN_STYLE
          + ". A policy may name a further directive, and every one it names is registered here"
          + " against the page that needs it with the reason, so widening one page never widens"
          + " the others and a reader can see in one place what each page asked for. form-action"
          + " is registered against every page whose form stays on this origin and is not"
          + " required anywhere: grow's page is submitted here and answered with a redirect off"
          + " this origin, which is what the live provider does, and browsers disagree on whether"
          + " form-action is checked again across that hop.";

  public static final String WHAT_A_WIDER_POLICY_WOULD_COST =
      "a page that loads a subresource cannot work under "
          + PayerPageDeclarations.NOTHING_IS_LOADED_BY_DEFAULT
          + ", and the answer is not to widen the policy every payer page shares: say what the"
          + " page loads and let that be decided for that page";

  public static final String HOW_A_FURTHER_DIRECTIVE_IS_ADMITTED =
      "a directive beyond the floor is admitted by registering it in"
          + " PayerPagePolicyConvention.THE_FURTHER_DIRECTIVES_A_PAGE_MAY_NAME against the page"
          + " that needs it, with the reason that page needs it. A directive naming a source for"
          + " what default-src 'none' exists to block is the thing this gate is for, so the"
          + " registration is the decision and this gate only reports that nobody made it";

  public record AFurtherDirective(String page, String directive, String why) {}

  public static final List<AFurtherDirective> THE_FURTHER_DIRECTIVES_A_PAGE_MAY_NAME =
      List.of(
          new AFurtherDirective(
              "dev.marcinromanowski.pspsim.provider.autra.AutraCardPage",
              "form-action 'self'",
              "its forms post to this origin and are answered with a document, never a redirect"
                  + " off it"),
          new AFurtherDirective(
              "dev.marcinromanowski.pspsim.provider.azericard.AzericardErrorPage",
              "form-action 'self'",
              "it carries the same declaration as azericard's payer page, which it stands in for"
                  + " on the same two routes"),
          new AFurtherDirective(
              "dev.marcinromanowski.pspsim.provider.azericard.AzericardErrorPage",
              "frame-ancestors 'none'",
              "the page it stands in for takes a card and nothing publishes either as embeddable"),
          new AFurtherDirective(
              "dev.marcinromanowski.pspsim.provider.azericard.AzericardErrorPage",
              "base-uri 'none'",
              "it resolves no relative address, so nothing may set one for it"),
          new AFurtherDirective(
              "dev.marcinromanowski.pspsim.provider.azericard.AzericardPayerPage",
              "form-action 'self'",
              "its form posts to this origin and is answered with a document; the payer returns"
                  + " to the merchant by following a link"),
          new AFurtherDirective(
              "dev.marcinromanowski.pspsim.provider.azericard.AzericardPayerPage",
              "frame-ancestors 'none'",
              "the page takes a card number and nothing publishes it as embeddable"),
          new AFurtherDirective(
              "dev.marcinromanowski.pspsim.provider.azericard.AzericardPayerPage",
              "base-uri 'none'",
              "the page resolves no relative address, so nothing may set one for it"),
          new AFurtherDirective(
              "dev.marcinromanowski.pspsim.provider.worldline.WorldlineHostedTokenizationPage",
              "form-action 'self'",
              "its form posts to this origin and is answered with a document; the shop frames"
                  + " this page, which is why it names no frame-ancestors"));

  private PayerPagePolicyConvention() {}

  public record Verdict(List<String> offences, List<String> read) {

    public Verdict {
      offences = List.copyOf(offences);
      read = List.copyOf(read);
    }
  }

  public static void main(String[] args) {
    if (args.length != 1) {
      throw new IllegalArgumentException(
          "this gate takes the directory of compiled main classes and was given " + List.of(args));
    }
    Path classes = Path.of(args[0]);
    Verdict verdict = verdictOn(classes);
    verdict
        .read()
        .forEach(line -> System.out.println("verifyEveryPayerPageDeclaresWhatItMayLoad: " + line));
    if (verdict.offences().isEmpty()) {
      return;
    }
    verdict.offences().forEach(offence -> System.out.println("  - " + offence));
    throw new IllegalStateException(
        THE_RULE
            + " "
            + verdict.offences().size()
            + " place(s) break it, each named above. A page is found by the HTML document it"
            + " renders, read out of the compiled string constants of the class that builds it, so"
            + " a sixth provider widens this gate by serving a page rather than by an edit here."
            + " Two shapes are missed on purpose: a document assembled so that no single constant"
            + " opens it, and a page rendered from a Thymeleaf template rather than built in Java,"
            + " which is how this UI's own screens are written and which no payer page is. The"
            + " second is why a provider that declares a payer page and renders no such document"
            + " is an offence of its own. That a policy reaches a browser at all is not read here"
            + " and cannot be: each page's answer carries it, and a unit test of that answer is"
            + " what the mutation gate scores.");
  }

  public static Verdict verdictOn(Path classes) {
    List<APage> pages = PayerPageDeclarations.inTheBuild(classes);
    int classesRead = PayerPageDeclarations.howManyClassesAreUnder(classes);
    if (pages.isEmpty()) {
      throw new IllegalStateException(
          "no class under "
              + classes
              + " renders an HTML document, so this gate would examine nothing and report that"
              + " every page it examined declares a policy. This simulator serves the payer's page"
              + " for four of its providers, so a run that reads no page has a broken classpath,"
              + " not a clean tree. "
              + classesRead
              + " class file(s) were read.");
    }
    List<String> declaring = PayerPageAddresses.inTheBuild(classes).providersThatDeclareAPage();
    List<String> offences = new ArrayList<>(theOffencesOf(pages));
    offences.addAll(theProvidersThatDeclareAPageAndRenderNone(pages, declaring));
    offences.addAll(theOffenceOfTheClassThatOwnsTheDeclaration(classes));
    offences.addAll(theRegistrationsNoPageUses(pages));

    List<String> read = new ArrayList<>();
    read.add(
        "read "
            + classesRead
            + " compiled classes and examined the "
            + pages.size()
            + " that render an HTML document: "
            + theNamesOf(pages));
    read.add(
        "read "
            + howManyPolicies(pages)
            + " policies across those "
            + pages.size()
            + " pages, "
            + howManyCarryTheirOwnStyle(pages)
            + " of which carry their own style and so name "
            + PayerPageDeclarations.THE_PAGES_OWN_STYLE
            + "; the floor each policy stands on is "
            + PayerPageDeclarations.theFloor());
    read.add(
        THE_FURTHER_DIRECTIVES_A_PAGE_MAY_NAME.size()
            + " directive(s) beyond that floor are registered, over "
            + thePagesThatRegisterADirective().size()
            + " page(s): "
            + theRegistrationsAsRead());
    read.add(
        "the providers that declare a payer page are "
            + declaring
            + " and the providers that render one are "
            + theProvidersOf(pages)
            + "; every provider in the first list must stand in the second");
    read.add(
        "the header every policy travels in and the floor it stands on are owned by "
            + PayerPageDeclarations.THE_CLASS_THAT_OWNS_THE_DECLARATION
            + ", which this gate reads against its own literals rather than borrowing");
    return new Verdict(offences, read);
  }

  static List<String> theOffenceOfTheClassThatOwnsTheDeclaration(Path classes) {
    List<String> constants =
        PayerPageDeclarations.theConstantsOf(
            classes, PayerPageDeclarations.THE_CLASS_THAT_OWNS_THE_DECLARATION);
    if (constants.isEmpty()) {
      return List.of(
          PayerPageDeclarations.THE_CLASS_THAT_OWNS_THE_DECLARATION
              + " is not in this build, so nothing names the header a payer page's policy travels"
              + " in and no page could share a floor with another");
    }
    List<String> offences = new ArrayList<>();
    if (!constants.contains(PayerPageDeclarations.THE_HEADER_A_POLICY_IS_DECLARED_IN)) {
      offences.add(
          PayerPageDeclarations.THE_CLASS_THAT_OWNS_THE_DECLARATION
              + " names no "
              + PayerPageDeclarations.THE_HEADER_A_POLICY_IS_DECLARED_IN
              + ", so the header every payer page's policy is declared in has moved or been"
              + " renamed, and a browser would be told nothing by a policy sent under another"
              + " name");
    }
    if (!theFloorTheDeclarationOwnerNames(constants)) {
      offences.add(
          PayerPageDeclarations.THE_CLASS_THAT_OWNS_THE_DECLARATION
              + " names no policy whose directives are exactly "
              + PayerPageDeclarations.theFloor()
              + ", so the floor it hands every page and the floor this gate requires have come"
              + " apart. The policies it names are "
              + thePoliciesAmong(constants));
    }
    return List.copyOf(offences);
  }

  static boolean theFloorTheDeclarationOwnerNames(List<String> constants) {
    for (String policy : thePoliciesAmong(constants)) {
      if (PayerPageDeclarations.theCanonicalFormOf(policy)
          .equals(PayerPageDeclarations.theFloor())) {
        return true;
      }
    }
    return false;
  }

  static List<String> thePoliciesAmong(List<String> constants) {
    List<String> policies = new ArrayList<>();
    for (String constant : constants) {
      if (PayerPageDeclarations.aPolicy(constant)) {
        policies.add(constant);
      }
    }
    return List.copyOf(policies);
  }

  static List<String> theOffencesOf(List<APage> pages) {
    List<String> offences = new ArrayList<>();
    for (APage page : pages) {
      offences.addAll(theOffenceOf(page));
    }
    return List.copyOf(offences);
  }

  static List<String> theOffenceOf(APage page) {
    List<String> offences = new ArrayList<>();
    List<String> loading = page.theElementsItLoadsASubresourceFor();
    if (!loading.isEmpty()) {
      offences.add(
          page.renderedBy()
              + " renders a document naming "
              + loading
              + ", and "
              + WHAT_A_WIDER_POLICY_WOULD_COST);
    }
    List<String> policies = page.policies();
    if (policies.isEmpty()) {
      offences.add(
          page.renderedBy()
              + " renders an HTML document a browser loads and declares no policy this gate can"
              + " read: a policy is a compile-time constant of the class that renders the"
              + " document, naming "
              + PayerPageDeclarations.NOTHING_IS_LOADED_BY_DEFAULT);
    }
    for (String policy : policies) {
      offences.addAll(theOffenceOfThePolicy(page, policy));
    }
    return List.copyOf(offences);
  }

  static List<String> theOffenceOfThePolicy(APage page, String policy) {
    List<String> offences = new ArrayList<>();
    if (!PayerPageDeclarations.namesTheDirective(
        policy, PayerPageDeclarations.NOTHING_IS_LOADED_BY_DEFAULT)) {
      offences.add(
          page.renderedBy()
              + " declares the policy "
              + policy
              + ", whose directives "
              + PayerPageDeclarations.theCanonicalFormOf(policy)
              + " do not include "
              + PayerPageDeclarations.NOTHING_IS_LOADED_BY_DEFAULT
              + ", so what it does not mention is loaded rather than blocked");
    }
    if (page.carriesItsOwnStyle()
        && !PayerPageDeclarations.namesTheDirective(
            policy, PayerPageDeclarations.THE_PAGES_OWN_STYLE)) {
      offences.add(
          page.renderedBy()
              + " renders a document carrying its own style and declares the policy "
              + policy
              + ", whose directives "
              + PayerPageDeclarations.theCanonicalFormOf(policy)
              + " do not include "
              + PayerPageDeclarations.THE_PAGES_OWN_STYLE
              + ", so a browser would block the page's own style and the payer would read an"
              + " unstyled page");
    }
    for (Directive directive : PayerPageDeclarations.theDirectivesBeyondTheFloor(policy)) {
      if (!registered(page.renderedBy(), directive)) {
        offences.add(
            page.renderedBy()
                + " declares the policy "
                + policy
                + ", which names "
                + directive.asItReads()
                + " beyond the floor "
                + PayerPageDeclarations.theFloor()
                + " and nothing registers it against this page: "
                + HOW_A_FURTHER_DIRECTIVE_IS_ADMITTED);
      }
    }
    return List.copyOf(offences);
  }

  static boolean registered(String page, Directive directive) {
    for (AFurtherDirective admitted : THE_FURTHER_DIRECTIVES_A_PAGE_MAY_NAME) {
      if (admitted.page().equals(page)
          && PayerPageDeclarations.theCanonicalFormOf(admitted.directive())
              .contains(directive.asItReads())) {
        return true;
      }
    }
    return false;
  }

  static List<String> theRegistrationsNoPageUses(List<APage> pages) {
    List<String> offences = new ArrayList<>();
    for (AFurtherDirective admitted : THE_FURTHER_DIRECTIVES_A_PAGE_MAY_NAME) {
      if (!someonePolicyNames(pages, admitted)) {
        offences.add(
            admitted.directive()
                + " is registered against "
                + admitted.page()
                + ", which declares no policy naming it, so this registration admits a directive"
                + " nothing asks for and would go on admitting it after the page stopped needing"
                + " it. Registered because: "
                + admitted.why());
      }
    }
    return List.copyOf(offences);
  }

  static boolean someonePolicyNames(List<APage> pages, AFurtherDirective admitted) {
    for (APage page : pages) {
      if (!page.renderedBy().equals(admitted.page())) {
        continue;
      }
      for (String policy : page.policies()) {
        if (PayerPageDeclarations.namesTheDirective(policy, admitted.directive())) {
          return true;
        }
      }
    }
    return false;
  }

  static List<String> thePagesThatRegisterADirective() {
    TreeSet<String> named = new TreeSet<>();
    for (AFurtherDirective admitted : THE_FURTHER_DIRECTIVES_A_PAGE_MAY_NAME) {
      named.add(admitted.page());
    }
    return List.copyOf(named);
  }

  static List<String> theRegistrationsAsRead() {
    TreeSet<String> read = new TreeSet<>();
    for (AFurtherDirective admitted : THE_FURTHER_DIRECTIVES_A_PAGE_MAY_NAME) {
      read.add(admitted.page() + " may name " + admitted.directive());
    }
    return List.copyOf(read);
  }

  static List<String> theProvidersThatDeclareAPageAndRenderNone(
      List<APage> pages, List<String> declaring) {
    List<String> rendering = theProvidersOf(pages);
    List<String> offences = new ArrayList<>();
    for (String provider : declaring) {
      if (!rendering.contains(provider)) {
        offences.add(
            "provider "
                + provider
                + " tells a tester this simulator serves the payer's page and renders no HTML"
                + " document this gate can read, so nothing here holds that page to a policy. The"
                + " providers that render one are "
                + rendering);
      }
    }
    return List.copyOf(offences);
  }

  static List<String> theProvidersOf(List<APage> pages) {
    TreeSet<String> named = new TreeSet<>();
    for (APage page : pages) {
      if (page.renderedBy().startsWith(PayerPageAddresses.PROVIDER_PACKAGE)) {
        named.add(PayerPageAddresses.providerOf(page.renderedBy()));
      }
    }
    return List.copyOf(named);
  }

  static int howManyPolicies(List<APage> pages) {
    int counted = 0;
    for (APage page : pages) {
      counted += page.policies().size();
    }
    return counted;
  }

  static int howManyCarryTheirOwnStyle(List<APage> pages) {
    int counted = 0;
    for (APage page : pages) {
      if (page.carriesItsOwnStyle()) {
        counted++;
      }
    }
    return counted;
  }

  static List<String> theNamesOf(List<APage> pages) {
    List<String> named = new ArrayList<>();
    for (APage page : pages) {
      named.add(page.renderedBy());
    }
    return List.copyOf(named);
  }
}
