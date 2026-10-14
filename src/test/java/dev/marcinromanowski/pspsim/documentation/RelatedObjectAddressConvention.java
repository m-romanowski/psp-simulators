package dev.marcinromanowski.pspsim.documentation;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.lang.classfile.ClassFile;
import java.lang.classfile.ClassModel;
import java.lang.classfile.CodeElement;
import java.lang.classfile.MethodModel;
import java.lang.classfile.constantpool.LoadableConstantEntry;
import java.lang.classfile.constantpool.MemberRefEntry;
import java.lang.classfile.constantpool.MethodHandleEntry;
import java.lang.classfile.constantpool.StringEntry;
import java.lang.classfile.instruction.ConstantInstruction;
import java.lang.classfile.instruction.InvokeDynamicInstruction;
import java.lang.classfile.instruction.InvokeInstruction;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.SequencedMap;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

public final class RelatedObjectAddressConvention {

  public static final String THE_RULE =
      "No provider module builds the state of a provider object out of the address of the payer"
          + " page that same module serves. A state carries relatedObjects, which a control read,"
          + " the object screen, the timeline, the Correlation screen and the inspector all print"
          + " verbatim and redact nowhere, so an address reaching that map is published as a"
          + " working link to the page it serves, while ThePayerStep tells every tester that the"
          + " link the provider answers with is never printed on a screen and that the payer step"
          + " is taken on the object's own screen instead. The identifier the address is built"
          + " from is what belongs there, and the address itself belongs to the answer the"
          + " provider's own API makes.";

  public static final String THE_STATE_OF_A_PROVIDER_OBJECT =
      "dev.marcinromanowski.pspsim.kernel.spi.ProviderObjectState";

  public static final String THIS_BUILDS_OWN_CODE = "dev.marcinromanowski.pspsim.";

  static final String A_STATE_IS_BUILT_IN_A_METHOD_THIS_BUILD_READ =
      "a place where a provider builds the state of a provider object is one of the methods this"
          + " gate read the body of, because the walk out of it reads that body for the values it"
          + " hands over, and a place absent from them would be walked out of in silence and"
          + " reported as a place that handed no address over. The place named by nothing read is ";

  static final String A_READING_NAMES_THE_STATE_IT_WAS_REACHED_FROM =
      "a value read along the walk out of a state carries the way it was reached, whose first"
          + " step is the place a provider built that state, and that step is what names the"
          + " module the value is charged to. A reading reached from nowhere would be charged to"
          + " no module and swept for no route at all. The value named by no way is ";

  static final String A_PATH_THIS_GATE_READS_NO_CLASS_UNDER =
      " is not a directory of compiled classes, so the walk out of every place a provider builds"
          + " the state of a provider object would read nothing and report that it found no payer"
          + " page address anywhere. A sweep that found no address and a sweep that read no class"
          + " must not print the same thing.";

  static final String NO_PROVIDER_DECLARES_A_PAYER_PAGE =
      " declares a payer page at all, so this gate would sweep every value a provider hands over"
          + " for no address and report that it found none. Every provider that serves a payer a"
          + " page declares the route it is served at with "
          + PayerPageAddresses.THE_DECLARATION
          + ", so a run that reads no declaration has a broken classpath, not a clean tree.";

  static final String NO_PROVIDER_MODULE_BUILDS_A_STATE =
      " builds the state of a provider object, so this gate would walk out of nothing and report"
          + " that it found no address. Every provider answers a control read with a "
          + THE_STATE_OF_A_PROVIDER_OBJECT
          + ", so a run that reads no such place has a broken classpath, not a clean tree.";

  static final String WHERE_AN_ADDRESS_ENDS_OR_ITS_QUERY_BEGINS = "?#";

  static final String WHAT_EVERY_ENUM_OF_THIS_BUILD_EXTENDS = "java.lang.Enum";

  static final String WHAT_AN_ENUM_CONSTANT_IS_CALLED = "name";

  static final String THE_DESCRIPTOR_OF_WHAT_AN_ENUM_CONSTANT_IS_CALLED = "()Ljava/lang/String;";

  static final String WHERE_AN_ENUM_CONSTANT_STANDS_IN_ITS_OWN_TYPE = "ordinal";

  static final String THE_DESCRIPTOR_OF_WHERE_AN_ENUM_CONSTANT_STANDS = "()I";

  static final String HOW_A_MODULE_IS_HELD_TO_ITS_OWN_ROUTES =
      "The addresses swept for are the ones each provider declares as the payer page it hosts,"
          + " read out of the compiled declarations, so core holds no list of any provider's"
          + " vocabulary and a provider added later widens this gate by declaring its page and"
          + " compiling. Each module's values are swept for the routes that same module declares,"
          + " because the fact this rule refuses is a module publishing a working link to the page"
          + " it hosts itself: the address and the one-time token that page is reached with are"
          + " both minted by that module, and ThePayerStep's promise that the link is never"
          + " printed on a screen is that module's promise about its own object. A module naming"
          + " another module's route is read here and is no offence, which is a different thing:"
          + " the path it names is answered for another provider's object and carries no token"
          + " this module mints, so it reaches a payer page that has no payment on it rather than"
          + " the published link to this object's own, and the module that would have to mint that"
          + " token is not the module handing the value over.";

  static final String WHAT_EVERY_CONSTANT_OF_THE_CLOSURE_IS_READ_FOR =
      "Every string constant of a method in that closure is read, the name a map entry is given"
          + " among them, because telling a key constant from a value constant needs the operand"
          + " stack this gate does not simulate: a module naming its own payer page route anywhere"
          + " in the closure that builds an object's state is worth refusing whichever side of the"
          + " put it lands on, so that is what this reads and renaming an entry to that route is"
          + " refused like any other reading of it. No value a client sent reaches this gate, which"
          + " reads the constants compiled into this build and no value of any run.";

  static final String AN_ADDRESS_MINTED_BEHIND_A_DECLARATION_CARRYING_NO_BODY =
      "A call this closure does not follow, counted and named on the read line above, because this"
          + " build compiles no body for the method under the owner the call names. What the shape"
          + " lets through is an address minted in a method the closure reaches only through a"
          + " declaration carrying no body, an interface, an abstract method or the abstract"
          + " method an enum leaves to each of its constants, whose implementation this build does"
          + " compile under another owner. A call reading the name() or ordinal() an enum of this"
          + " build inherits is none of them: java.lang.Enum declares both final, so no class of"
          + " this build overrides either, and the body such a call reaches is java.lang.Enum's"
          + " own wherever the owner is a class this build compiled with java.lang.Enum as its"
          + " superclass. What a constant is called is a Java identifier, which carries none of"
          + " the "
          + PayerPageAddresses.THE_ROOT_OF_EVERY_ROUTE
          + " every declared route begins with, and where it stands is a number rather than a"
          + " string of any shape, so neither is an address and both are counted apart on the"
          + " read line above. Resolving a call"
          + " that is not followed by its name and descriptor was measured to reach every"
          + " same-shaped method in the build and reported four addresses no state is built from.";

  static final List<String> THE_SHAPES_THIS_GATE_MISSES =
      List.of(
          "An address a provider computes into a static field at class initialisation is read"
              + " nowhere here, because the walk starts where a state is built and a class"
              + " initialiser is no such place.",
          "An address a provider stores on a persisted row at create time and hands over as a"
              + " stored value reaches a reader as a value read back from a row rather than as a"
              + " constant of this closure. No module of this build keeps such a row: grow carried"
              + " hostedPageUrl and autra hostedCardUrl as components persisted beside the token"
              + " each was built from, and the migration"
              + " a_payer_page_address_is_built_and_not_kept dropped both columns, so each address"
              + " is now built where it is answered and kept nowhere, which is what takes this"
              + " shape out of this build rather than out of this gate. The shape this gate was"
              + " built for, worldline computing the address where the state is read, is caught; a"
              + " stored one would not be, and would be read where a tester meets it instead, by"
              + " the sweep a journey client runs over every page it serves under /ui that names a"
              + " provider. It is left open here rather than closed by refusing every value a"
              + " module hands over from a method of its own whose name reads as an address,"
              + " because successUrl, cancelUrl and notifyUrl are names grow carries for the"
              + " merchant's own addresses, which"
              + " TrailWithholdingConvention.THE_ADDRESSES_A_MODULE_MAY_ANSWER_IN_THE_CLEAR"
              + " registers as addresses grow may answer in the clear, and a floor over that name"
              + " would refuse them.",
          "An address assembled from literals each too short to name a whole path segment of a"
              + " declared route is read as none of them.",
          "The floor on what counts as an address at all: a route counts only where nothing but an"
              + " address leads up to it and the value ends where the route does, at the end of"
              + " the value or at the ? or # a query or a fragment begins with, so a refusal"
              + " message or a declaration naming its own route in a sentence is read here and is"
              + " no offence, which is the same floor that keeps a vendor example quoted in this"
              + " build's prose from failing it, and so is a longer path that merely starts with a"
              + " declared route, which this build answers on nowhere.",
          AN_ADDRESS_MINTED_BEHIND_A_DECLARATION_CARRYING_NO_BODY);

  static String theShapesThisGateMissesOnPurpose() {
    return THE_SHAPES_THIS_GATE_MISSES.size()
        + " shapes are missed on purpose. "
        + String.join(" ", THE_SHAPES_THIS_GATE_MISSES);
  }

  static String how() {
    return HOW_A_MODULE_IS_HELD_TO_ITS_OWN_ROUTES
        + " "
        + WHAT_EVERY_CONSTANT_OF_THE_CLOSURE_IS_READ_FOR
        + " "
        + theShapesThisGateMissesOnPurpose();
  }

  private static final Pattern WHERE_A_WOVEN_STRING_TAKES_AN_ARGUMENT =
      Pattern.compile("[\u0001\u0002]");

  private static final Pattern NOTHING_BUT_AN_ADDRESS_LEADS_UP_TO_IT =
      Pattern.compile("[^\\s\"'<>]*");

  private RelatedObjectAddressConvention() {}

  public record Site(String owner, String name, String descriptor) {

    public String asItReads() {
      return owner + "." + name;
    }
  }

  public record Code(List<Site> calls, List<String> values) {

    public Code {
      calls = List.copyOf(calls);
      values = List.copyOf(values);
    }
  }

  public record TheCompiledBuild(
      SequencedMap<Site, Code> methods,
      List<Site> whereAProviderBuildsAState,
      List<Site> whereAnythingBuildsAState,
      List<String> providerModules,
      List<String> theEnumsOfThisBuild) {

    public TheCompiledBuild {
      methods = Collections.unmodifiableSequencedMap(new LinkedHashMap<>(methods));
      whereAProviderBuildsAState = List.copyOf(whereAProviderBuildsAState);
      whereAnythingBuildsAState = List.copyOf(whereAnythingBuildsAState);
      providerModules = List.copyOf(providerModules);
      theEnumsOfThisBuild = List.copyOf(theEnumsOfThisBuild);
      for (Site site : whereAProviderBuildsAState) {
        if (!methods.containsKey(site)) {
          throw new IllegalArgumentException(A_STATE_IS_BUILT_IN_A_METHOD_THIS_BUILD_READ + site);
        }
      }
    }

    public List<String> theModulesThatBuildAState() {
      Set<String> named = new TreeSet<>();
      for (Site site : whereAProviderBuildsAState) {
        named.add(moduleOf(site.owner()));
      }
      return List.copyOf(named);
    }

    public boolean anEnumOfThisBuild(String owner) {
      return theEnumsOfThisBuild.contains(owner);
    }

    public List<Site> theStatesBuiltBy(String module) {
      List<Site> built = new ArrayList<>();
      for (Site site : whereAProviderBuildsAState) {
        if (moduleOf(site.owner()).equals(module)) {
          built.add(site);
        }
      }
      return List.copyOf(built);
    }
  }

  public record ACallWithNoBodyToRead(Site at, Site called) {

    public String asItReads() {
      return at.asItReads() + " calls " + called.asItReads() + called.descriptor();
    }
  }

  public record Reading(Site where, String value, List<Site> howItIsReached) {

    public Reading {
      howItIsReached = List.copyOf(howItIsReached);
      if (howItIsReached.isEmpty()) {
        throw new IllegalArgumentException(A_READING_NAMES_THE_STATE_IT_WAS_REACHED_FROM + value);
      }
    }

    public String theModuleThatHandsItOver() {
      return moduleOf(howItIsReached.getFirst().owner());
    }
  }

  public record TheClosure(
      List<Site> methods,
      List<Reading> readings,
      List<ACallWithNoBodyToRead> callsNotFollowed,
      List<ACallWithNoBodyToRead> callsReadingWhatJavaLangEnumDeclaresFinal) {

    public TheClosure {
      methods = List.copyOf(methods);
      readings = List.copyOf(readings);
      callsNotFollowed = List.copyOf(callsNotFollowed);
      callsReadingWhatJavaLangEnumDeclaresFinal =
          List.copyOf(callsReadingWhatJavaLangEnumDeclaresFinal);
    }

    public List<String> theCallsNotFollowedAsTheyRead() {
      return asTheyRead(callsNotFollowed);
    }

    public List<String> theCallsReadingWhatAnEnumInheritsAsTheyRead() {
      return asTheyRead(callsReadingWhatJavaLangEnumDeclaresFinal);
    }

    private static List<String> asTheyRead(List<ACallWithNoBodyToRead> calls) {
      List<String> named = new ArrayList<>();
      for (ACallWithNoBodyToRead call : calls) {
        named.add(call.asItReads());
      }
      return List.copyOf(named);
    }
  }

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
    Verdict verdict = verdictOn(Path.of(args[0]));
    verdict
        .read()
        .forEach(line -> System.out.println("verifyNoPayerUrlAmongRelatedObjects: " + line));
    if (verdict.offences().isEmpty()) {
      return;
    }
    verdict.offences().forEach(offence -> System.out.println("  - " + offence));
    throw new IllegalStateException(
        THE_RULE
            + " "
            + verdict.offences().size()
            + " place(s) break it, each named above. "
            + how());
  }

  public static Verdict verdictOn(Path classes) {
    TheCompiledBuild build = compiledUnder(classes);
    PayerPageAddresses.TheBuild pages = PayerPageAddresses.inTheBuild(classes);
    refuseABuildThatDeclaresNoPayerPage(classes, pages);
    refuseABuildThatBuildsNoProviderObjectState(classes, build);
    List<String> offences = new ArrayList<>(theModulesThisGateReadsNothingOf(build));
    TheClosure closure = theClosureOf(build);
    offences.addAll(theAddressesAmongThem(closure, pages.declared()));
    return new Verdict(offences, theScopeOf(build, closure, pages));
  }

  private static List<String> theScopeOf(
      TheCompiledBuild build, TheClosure closure, PayerPageAddresses.TheBuild pages) {
    List<String> read = new ArrayList<>();
    read.add(
        "read "
            + build.providerModules().size()
            + " provider modules "
            + build.providerModules()
            + " and "
            + build.whereAProviderBuildsAState().size()
            + " places where one builds the state of a provider object, declared by "
            + build.theModulesThatBuildAState()
            + ", reaching "
            + closure.methods().size()
            + " of this build's own methods and "
            + closure.readings().size()
            + " values of a string those methods read, each one charged to the module whose state"
            + " the walk to it started at");
    read.add(
        "swept each module's own values for the "
            + pages.declared().size()
            + " payer page(s) this build declares, every one of them read out of a compiled "
            + PayerPageAddresses.THE_DECLARATION
            + ", module by module: "
            + theRoutesEachModuleDeclares(pages.declared())
            + "; and swept for no route at all the module(s) that build a state and declare no"
            + " page of their own: "
            + theModulesThatDeclareNoPageOfTheirOwn(build, pages));
    read.add(
        "excused: "
            + (build.whereAnythingBuildsAState().size() - build.whereAProviderBuildsAState().size())
            + " place(s) outside "
            + PayerPageAddresses.PROVIDER_PACKAGE
            + " build the same state and are not read, because the rule holds a provider module to"
            + " what it hands over and the kernel's own constructors hand over nothing; and "
            + closure.callsNotFollowed().size()
            + " call(s) from inside this closure named a method this build compiles no body for"
            + " under the owner the call names, and were not followed: "
            + closure.theCallsNotFollowedAsTheyRead()
            + "; and "
            + closure.callsReadingWhatJavaLangEnumDeclaresFinal().size()
            + " call(s) read the name() or ordinal() java.lang.Enum declares final, on an owner"
            + " this build compiled with "
            + WHAT_EVERY_ENUM_OF_THIS_BUILD_EXTENDS
            + " as its superclass, so neither call can reach a body of this build and neither"
            + " answers an address: they are counted here rather than among the calls above: "
            + closure.theCallsReadingWhatAnEnumInheritsAsTheyRead());
    return read;
  }

  static SequencedMap<String, List<String>> theRoutesEachModuleDeclares(
      List<PayerPageAddresses.Declared> declared) {
    SequencedMap<String, List<String>> byModule = new TreeMap<>();
    for (PayerPageAddresses.Declared page : declared) {
      byModule.computeIfAbsent(page.provider(), module -> new ArrayList<>()).add(page.route());
    }
    return Collections.unmodifiableSequencedMap(byModule);
  }

  static List<String> theModulesThatDeclareNoPageOfTheirOwn(
      TheCompiledBuild build, PayerPageAddresses.TheBuild pages) {
    List<String> silent = new ArrayList<>();
    for (String module : build.theModulesThatBuildAState()) {
      if (!pages.providersThatDeclareAPage().contains(module)) {
        silent.add(module);
      }
    }
    return List.copyOf(silent);
  }

  static List<String> theModulesThisGateReadsNothingOf(TheCompiledBuild build) {
    List<String> building = build.theModulesThatBuildAState();
    List<String> offences = new ArrayList<>();
    for (String module : build.providerModules()) {
      if (building.contains(module)) {
        continue;
      }
      offences.add(
          "provider module "
              + module
              + " is compiled here and builds the state of no provider object anywhere, so this"
              + " gate reads no value of it at all and would report that it found no address"
              + " while sweeping nothing. A provider answers a control read with a "
              + THE_STATE_OF_A_PROVIDER_OBJECT
              + ", so a module that builds none has a broken classpath rather than a clean tree");
    }
    return List.copyOf(offences);
  }

  static void refuseABuildThatDeclaresNoPayerPage(Path classes, PayerPageAddresses.TheBuild pages) {
    if (!pages.declared().isEmpty()) {
      return;
    }
    throw new IllegalStateException(
        "no provider in " + classes + NO_PROVIDER_DECLARES_A_PAYER_PAGE);
  }

  static void refuseABuildThatBuildsNoProviderObjectState(Path classes, TheCompiledBuild build) {
    if (!build.whereAProviderBuildsAState().isEmpty()) {
      return;
    }
    throw new IllegalStateException(
        "no provider module in " + classes + NO_PROVIDER_MODULE_BUILDS_A_STATE);
  }

  public static TheClosure theClosureOf(TheCompiledBuild build) {
    Set<Site> reached = new LinkedHashSet<>();
    Set<Reading> readings = new LinkedHashSet<>();
    Set<ACallWithNoBodyToRead> notFollowed = new LinkedHashSet<>();
    Set<ACallWithNoBodyToRead> readingWhatAnEnumInherits = new LinkedHashSet<>();
    for (String module : build.theModulesThatBuildAState()) {
      walkOutOf(
          build,
          build.theStatesBuiltBy(module),
          reached,
          readings,
          notFollowed,
          readingWhatAnEnumInherits);
    }
    return new TheClosure(
        List.copyOf(reached),
        List.copyOf(readings),
        List.copyOf(notFollowed),
        List.copyOf(readingWhatAnEnumInherits));
  }

  private static void walkOutOf(
      TheCompiledBuild build,
      List<Site> states,
      Set<Site> reached,
      Set<Reading> readings,
      Set<ACallWithNoBodyToRead> notFollowed,
      Set<ACallWithNoBodyToRead> readingWhatAnEnumInherits) {
    Set<Site> seen = new LinkedHashSet<>(states);
    Deque<Site> waiting = new ArrayDeque<>(states);
    Map<Site, Site> reachedFrom = new LinkedHashMap<>();
    while (!waiting.isEmpty()) {
      Site at = waiting.poll();
      reached.add(at);
      Code code = build.methods().get(at);
      for (String value : code.values()) {
        readings.add(new Reading(at, value, theWayTo(at, reachedFrom)));
      }
      for (Site called : code.calls()) {
        if (!called.owner().startsWith(THIS_BUILDS_OWN_CODE)) {
          continue;
        }
        if (!build.methods().containsKey(called)) {
          if (build.anEnumOfThisBuild(called.owner()) && aMethodJavaLangEnumDeclaresFinal(called)) {
            readingWhatAnEnumInherits.add(new ACallWithNoBodyToRead(at, called));
            continue;
          }
          notFollowed.add(new ACallWithNoBodyToRead(at, called));
          continue;
        }
        if (seen.add(called)) {
          reachedFrom.put(called, at);
          waiting.add(called);
        }
      }
    }
  }

  private static List<Site> theWayTo(Site site, Map<Site, Site> reachedFrom) {
    List<Site> back = new ArrayList<>();
    Site at = site;
    while (at != null && !back.contains(at)) {
      back.add(at);
      at = reachedFrom.get(at);
    }
    return List.copyOf(back.reversed());
  }

  public static List<String> theAddressesAmongThem(
      TheClosure closure, List<PayerPageAddresses.Declared> declared) {
    Set<String> offences = new LinkedHashSet<>();
    for (PayerPageAddresses.Declared page : declared) {
      for (Reading reading : closure.readings()) {
        if (!reading.theModuleThatHandsItOver().equals(page.provider())) {
          continue;
        }
        if (!anAddressOfThePageAt(page.route(), reading.value())) {
          continue;
        }
        offences.add(asAnOffence(page, reading));
      }
    }
    return List.copyOf(offences);
  }

  private static String asAnOffence(PayerPageAddresses.Declared page, Reading reading) {
    List<String> howItIsReached = new ArrayList<>();
    for (Site step : reading.howItIsReached()) {
      howItIsReached.add(step.asItReads());
    }
    String built =
        reading.where().asItReads()
            + " reads "
            + reading.value()
            + ", which is the address of the payer page provider "
            + page.provider()
            + " declares at "
            + page.route()
            + " in "
            + page.declaredIn();
    return built
        + ", and that same module builds the state of a provider object out of it along "
        + howItIsReached
        + ", so a tester reading that object is handed a working link to the page";
  }

  public static boolean anAddressOfThePageAt(String route, String value) {
    Matcher written = PayerPageAddresses.asAScreenWouldWriteIt(route).matcher(value);
    while (written.find()) {
      if (nothingButAnAddressLeadsUpToIt(value, written.start())
          && theValueEndsWhereTheRouteDoes(value, written.end())) {
        return true;
      }
    }
    return false;
  }

  private static boolean nothingButAnAddressLeadsUpToIt(String value, int start) {
    return NOTHING_BUT_AN_ADDRESS_LEADS_UP_TO_IT.matcher(value.substring(0, start)).matches();
  }

  private static boolean theValueEndsWhereTheRouteDoes(String value, int end) {
    return end == value.length()
        || WHERE_AN_ADDRESS_ENDS_OR_ITS_QUERY_BEGINS.indexOf(value.charAt(end)) >= 0;
  }

  public static TheCompiledBuild compiledUnder(Path classesRoot) {
    SequencedMap<Site, Code> methods = new LinkedHashMap<>();
    List<Site> byAProvider = new ArrayList<>();
    List<Site> byAnything = new ArrayList<>();
    Set<String> modules = new TreeSet<>();
    Set<String> enums = new TreeSet<>();
    for (Path compiled : classFilesUnder(classesRoot)) {
      ClassModel model = parse(compiled);
      String owner = model.thisClass().asInternalName().replace('/', '.');
      if (aModuleIsNamedIn(owner)) {
        modules.add(moduleOf(owner));
      }
      if (compiledAsAnEnum(model)) {
        enums.add(owner);
      }
      for (MethodModel method : model.methods()) {
        Optional<List<CodeElement>> body = instructionsOf(method);
        if (body.isEmpty()) {
          continue;
        }
        Site site =
            new Site(owner, method.methodName().stringValue(), method.methodType().stringValue());
        Code code = theCodeOf(body.get());
        methods.put(site, code);
        if (!aStateIsBuiltIn(code)) {
          continue;
        }
        byAnything.add(site);
        if (aModuleIsNamedIn(owner)) {
          byAProvider.add(site);
        }
      }
    }
    return new TheCompiledBuild(
        methods, byAProvider, byAnything, List.copyOf(modules), List.copyOf(enums));
  }

  static boolean compiledAsAnEnum(ClassModel model) {
    return model
        .superclass()
        .map(extended -> extended.asInternalName().replace('/', '.'))
        .filter(WHAT_EVERY_ENUM_OF_THIS_BUILD_EXTENDS::equals)
        .isPresent();
  }

  static boolean aMethodJavaLangEnumDeclaresFinal(Site called) {
    return readsWhatAConstantIsCalled(called) || readsWhereAConstantStands(called);
  }

  private static boolean readsWhatAConstantIsCalled(Site called) {
    return called.name().equals(WHAT_AN_ENUM_CONSTANT_IS_CALLED)
        && called.descriptor().equals(THE_DESCRIPTOR_OF_WHAT_AN_ENUM_CONSTANT_IS_CALLED);
  }

  private static boolean readsWhereAConstantStands(Site called) {
    return called.name().equals(WHERE_AN_ENUM_CONSTANT_STANDS_IN_ITS_OWN_TYPE)
        && called.descriptor().equals(THE_DESCRIPTOR_OF_WHERE_AN_ENUM_CONSTANT_STANDS);
  }

  static boolean aModuleIsNamedIn(String owner) {
    return owner.startsWith(PayerPageAddresses.PROVIDER_PACKAGE)
        && owner.substring(PayerPageAddresses.PROVIDER_PACKAGE.length()).contains(".");
  }

  static String moduleOf(String owner) {
    return PayerPageAddresses.providerOf(owner);
  }

  static boolean aStateIsBuiltIn(Code code) {
    for (Site called : code.calls()) {
      if (called.owner().equals(THE_STATE_OF_A_PROVIDER_OBJECT)
          && called.name().equals(PayerPageAddresses.INITIALIZER)) {
        return true;
      }
    }
    return false;
  }

  static List<String> theConstantsWovenInto(String woven) {
    List<String> found = new ArrayList<>();
    for (String piece : WHERE_A_WOVEN_STRING_TAKES_AN_ARGUMENT.split(woven, -1)) {
      if (!piece.isEmpty()) {
        found.add(piece);
      }
    }
    return List.copyOf(found);
  }

  private static Code theCodeOf(List<CodeElement> elements) {
    List<Site> calls = new ArrayList<>();
    List<String> values = new ArrayList<>();
    for (CodeElement element : elements) {
      if (element instanceof ConstantInstruction loaded
          && loaded.constantValue() instanceof String text) {
        values.add(text);
      }
      if (element instanceof InvokeInstruction invoked) {
        calls.add(
            new Site(
                invoked.owner().asInternalName().replace('/', '.'),
                invoked.name().stringValue(),
                invoked.type().stringValue()));
      }
      if (element instanceof InvokeDynamicInstruction woven) {
        for (LoadableConstantEntry argument : woven.invokedynamic().bootstrap().arguments()) {
          if (argument instanceof StringEntry text) {
            values.addAll(theConstantsWovenInto(text.stringValue()));
          }
          if (argument instanceof MethodHandleEntry handle) {
            MemberRefEntry referenced = handle.reference();
            calls.add(
                new Site(
                    referenced.owner().asInternalName().replace('/', '.'),
                    referenced.name().stringValue(),
                    referenced.type().stringValue()));
          }
        }
      }
    }
    return new Code(calls, values);
  }

  private static Optional<List<CodeElement>> instructionsOf(MethodModel method) {
    return method
        .code()
        .map(
            code -> {
              List<CodeElement> elements = new ArrayList<>();
              code.forEach(elements::add);
              return List.copyOf(elements);
            });
  }

  private static ClassModel parse(Path compiled) {
    try {
      return ClassFile.of().parse(compiled);
    } catch (IOException unreadable) {
      throw new UncheckedIOException(unreadable);
    }
  }

  private static List<Path> classFilesUnder(Path classesRoot) {
    if (!Files.isDirectory(classesRoot)) {
      throw new IllegalStateException(classesRoot + A_PATH_THIS_GATE_READS_NO_CLASS_UNDER);
    }
    try (Stream<Path> walked = Files.walk(classesRoot)) {
      return walked
          .filter(Files::isRegularFile)
          .filter(path -> path.getFileName().toString().endsWith(".class"))
          .sorted()
          .toList();
    } catch (IOException unreadable) {
      throw new UncheckedIOException(unreadable);
    }
  }
}
