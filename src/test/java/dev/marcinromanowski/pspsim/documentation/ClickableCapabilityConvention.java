package dev.marcinromanowski.pspsim.documentation;

import dev.marcinromanowski.pspsim.documentation.RoutesInTheBuild.Route;
import dev.marcinromanowski.pspsim.ui.ContributedScreens;
import dev.marcinromanowski.pspsim.ui.TheRail;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.lang.classfile.ClassFile;
import java.lang.classfile.ClassModel;
import java.lang.classfile.CodeElement;
import java.lang.classfile.MethodModel;
import java.lang.classfile.constantpool.LoadableConstantEntry;
import java.lang.classfile.constantpool.MemberRefEntry;
import java.lang.classfile.constantpool.MethodHandleEntry;
import java.lang.classfile.instruction.InvokeDynamicInstruction;
import java.lang.classfile.instruction.InvokeInstruction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

public final class ClickableCapabilityConvention {

  public static final String TEMPLATES = "src/main/resources/templates";

  public static final String FIELD_SEPARATOR = "|";

  public static final String MEMBER = "#";

  private static final String CLICKABLE = "clickable=";

  private static final String API_ONLY = "apiOnly=";

  private static final String KERNEL = "dev.marcinromanowski.pspsim.kernel.";

  private static final String SIMULATOR = "dev.marcinromanowski.pspsim.";

  private static final Set<String> VERBS_THAT_CHANGE_THE_SIMULATOR =
      Set.of("post", "put", "delete", "patch");

  private static final Pattern LINKED_FROM_A_TEMPLATE =
      Pattern.compile("(?:action|href)=\"@\\{([^\"(}]+)");

  private static final Pattern A_PATH_VARIABLE = Pattern.compile("\\{[^/}]+}");

  private static final String THE_SCREEN_NAMESPACE = "/ui";

  private static final String ONE_SEGMENT_OF_A_PATH = "[^/]+";

  private ClickableCapabilityConvention() {}

  public enum Reason {
    A_SCREEN_REACHES_THE_ROUTE_BY_ANOTHER_ROAD,
    NO_SCREEN_OFFERS_IT_AND_ONE_SHOULD
  }

  public record Binding(String capability, String screen, String effect) {

    public static Binding parse(String specification) {
      List<String> fields = List.of(specification.split("\\" + FIELD_SEPARATOR, -1));
      if (fields.size() != 3) {
        throw new IllegalArgumentException(
            "a binding is capability|screen|effect, not " + specification);
      }
      return new Binding(fields.get(0), fields.get(1), fields.get(2));
    }
  }

  public record ApiOnly(String capability, String effect, Reason reason, String note) {

    public static ApiOnly parse(String specification) {
      List<String> fields = List.of(specification.split("\\" + FIELD_SEPARATOR, -1));
      if (fields.size() != 4) {
        throw new IllegalArgumentException(
            "an API-only facet is capability|effect|reason|note, not " + specification);
      }
      return new ApiOnly(
          fields.get(0), fields.get(1), Reason.valueOf(fields.get(2)), fields.get(3));
    }
  }

  public record Finding(String message) {

    @Override
    public String toString() {
      return message;
    }
  }

  public record Verdict(
      List<Finding> findings,
      int capabilities,
      int screens,
      int bindings,
      int effects,
      int facets,
      int destinations) {

    public Verdict {
      findings = List.copyOf(findings);
    }
  }

  public static final class Reach {

    private final Map<String, Set<String>> calls;

    private final Set<String> declared;

    private final Map<String, Set<String>> kernelReachedFrom = new LinkedHashMap<>();

    private Reach(Map<String, Set<String>> calls, Set<String> declared) {
      this.calls = calls;
      this.declared = declared;
    }

    public static Reach under(Path classesRoot) {
      Map<String, Set<String>> calls = new LinkedHashMap<>();
      Set<String> declared = new LinkedHashSet<>();
      for (Path compiled : classFilesUnder(classesRoot)) {
        ClassModel model = parse(compiled);
        String owner = model.thisClass().asInternalName().replace('/', '.');
        if (!owner.startsWith(SIMULATOR)) {
          continue;
        }
        for (MethodModel method : model.methods()) {
          String from = owner + MEMBER + method.methodName().stringValue();
          declared.add(from);
          Set<String> invoked = calls.computeIfAbsent(from, _ -> new LinkedHashSet<>());
          method
              .code()
              .ifPresent(code -> code.forEach(element -> invoked.addAll(targetsOf(element))));
        }
      }
      return new Reach(calls, declared);
    }

    public boolean declaresAKernelMethod(String effect) {
      return effect.startsWith(KERNEL) && declared.contains(effect);
    }

    public boolean from(String handler, String effect) {
      return kernelReachedFrom
          .computeIfAbsent(handler, this::everyKernelMethodReachedFrom)
          .contains(effect);
    }

    private Set<String> everyKernelMethodReachedFrom(String handler) {
      Set<String> kernel = new LinkedHashSet<>();
      Set<String> walked = new LinkedHashSet<>(List.of(handler));
      Deque<String> waiting = new ArrayDeque<>(List.of(handler));
      while (!waiting.isEmpty()) {
        for (String invoked : calls.getOrDefault(waiting.poll(), Set.of())) {
          if (invoked.startsWith(KERNEL)) {
            kernel.add(invoked);
          }
          if (declared.contains(invoked) && walked.add(invoked)) {
            waiting.add(invoked);
          }
        }
      }
      return Set.copyOf(kernel);
    }

    private static List<String> targetsOf(CodeElement element) {
      if (element instanceof InvokeInstruction invoked) {
        return List.of(
            invoked.owner().asInternalName().replace('/', '.')
                + MEMBER
                + invoked.name().stringValue());
      }
      if (!(element instanceof InvokeDynamicInstruction composed)) {
        return List.of();
      }
      List<String> targets = new ArrayList<>();
      for (LoadableConstantEntry argument : composed.invokedynamic().bootstrap().arguments()) {
        if (argument instanceof MethodHandleEntry handle) {
          MemberRefEntry referenced = handle.reference();
          targets.add(
              referenced.owner().asInternalName().replace('/', '.')
                  + MEMBER
                  + referenced.name().stringValue());
        }
      }
      return List.copyOf(targets);
    }

    private static ClassModel parse(Path compiled) {
      try {
        return ClassFile.of().parse(compiled);
      } catch (IOException unreadable) {
        throw new UncheckedIOException(unreadable);
      }
    }

    private static List<Path> classFilesUnder(Path classesRoot) {
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

  public static Set<String> everyPathLinkedFrom(Path templates) {
    Set<String> linked = new TreeSet<>();
    for (Path page : pagesUnder(templates)) {
      Matcher found = LINKED_FROM_A_TEMPLATE.matcher(read(page));
      while (found.find()) {
        linked.add(found.group(1));
      }
    }
    return Set.copyOf(linked);
  }

  public static Verdict check(
      Reach reach,
      List<Route> routes,
      Set<String> linked,
      List<String> offered,
      List<Binding> bindings,
      List<ApiOnly> facets) {
    Map<String, Route> capabilities = routesOf(routes, RoutesInTheBuild.CONTROL);
    Map<String, Route> screens = routesOf(routes, RoutesInTheBuild.SCREENS);
    List<Finding> findings = new ArrayList<>();
    Set<String> bound = new LinkedHashSet<>();
    Set<String> pairs = new LinkedHashSet<>();
    Set<String> effects = new TreeSet<>();
    for (Binding binding : bindings) {
      effects.add(binding.effect());
      if (!pairs.add(binding.capability() + FIELD_SEPARATOR + binding.screen())) {
        findings.add(
            new Finding(
                "`"
                    + binding.capability()
                    + "` is bound to `"
                    + binding.screen()
                    + "` twice, and one capability needs one binding per screen that offers it;"
                    + " delete the repeat or bind the second one to the screen it meant"));
        continue;
      }
      findings.addAll(whatTheBindingCannotShow(reach, capabilities, screens, binding));
      if (capabilities.containsKey(binding.capability())) {
        bound.add(binding.capability());
      }
    }
    for (String signature : new TreeSet<>(capabilities.keySet())) {
      if (bound.contains(signature)) {
        continue;
      }
      findings.add(
          new Finding(
              "`"
                  + signature
                  + "` is a capability of the control API that no screen offers: nothing a tester"
                  + " can click reaches it, so this simulator is usable from CI and not from the"
                  + " UI for that one thing. Bind it in `screensThatOfferAControlCapability` to"
                  + " the screen route that performs it and to the kernel method both of them"
                  + " reach, or, if clicking is not supposed to reach it, declare it in"
                  + " `facetsOnlyTheApiOffers` with the reason clicking cannot"));
    }
    for (ApiOnly facet : facets) {
      findings.addAll(whatTheFacetCannotShow(reach, capabilities, screens, bound, facet));
    }
    for (String signature : new TreeSet<>(screens.keySet())) {
      String path = screens.get(signature).path();
      if (linked.contains(path) || aDestinationReaches(offered, path)) {
        continue;
      }
      findings.add(
          new Finding(
              "screen route `"
                  + signature
                  + "` is a form target or a link target on no page under "
                  + TEMPLATES
                  + " and is the destination of no tab of the navigation, so a tester cannot reach"
                  + " it by clicking and only a hand-written request gets there; give some screen a"
                  + " form or a link that names `"
                  + path
                  + "`, offer it as a destination of the navigation, or delete the route"));
    }
    findings.addAll(whatTheDestinationsCannotShow(offered, screens));
    return new Verdict(
        findings,
        capabilities.size(),
        screens.size(),
        bindings.size(),
        effects.size(),
        facets.size(),
        offered.size());
  }

  private static List<Finding> whatTheDestinationsCannotShow(
      List<String> offered, Map<String, Route> screens) {
    List<Finding> findings = new ArrayList<>();
    for (String destination : new TreeSet<>(offered)) {
      if (!destination.startsWith(THE_SCREEN_NAMESPACE)
          || aScreenRouteAnswers(screens, destination)) {
        continue;
      }
      findings.add(
          new Finding(
              "the navigation offers `"
                  + destination
                  + "` as a tab, and no screen route of this build answers it, so a tester who"
                  + " clicks that tab is handed whatever this simulator does with a path it does"
                  + " not map. The destinations are read out of the navigation itself rather than"
                  + " out of the markup of a page, which is what lets a screen be contributed"
                  + " without the navigation changing, and it is also what stops the markup"
                  + " catching a destination that leads nowhere: name a route this build maps, or"
                  + " stop offering the tab"));
    }
    return List.copyOf(findings);
  }

  private static boolean aDestinationReaches(List<String> offered, String routePath) {
    for (String destination : offered) {
      if (matches(routePath, destination)) {
        return true;
      }
    }
    return false;
  }

  private static boolean aScreenRouteAnswers(Map<String, Route> screens, String destination) {
    for (Route route : screens.values()) {
      if (matches(route.path(), destination)) {
        return true;
      }
    }
    return false;
  }

  private static boolean matches(String routePath, String destination) {
    if (routePath.equals(destination)) {
      return true;
    }
    Matcher variable = A_PATH_VARIABLE.matcher(routePath);
    StringBuilder pattern = new StringBuilder();
    int from = 0;
    while (variable.find()) {
      pattern.append(Pattern.quote(routePath.substring(from, variable.start())));
      pattern.append(ONE_SEGMENT_OF_A_PATH);
      from = variable.end();
    }
    if (from == 0) {
      return false;
    }
    pattern.append(Pattern.quote(routePath.substring(from)));
    return Pattern.compile(pattern.toString()).matcher(destination).matches();
  }

  private static List<Finding> whatTheBindingCannotShow(
      Reach reach, Map<String, Route> capabilities, Map<String, Route> screens, Binding binding) {
    List<Finding> findings = new ArrayList<>();
    Optional<Route> capability = Optional.ofNullable(capabilities.get(binding.capability()));
    Optional<Route> screen = Optional.ofNullable(screens.get(binding.screen()));
    if (capability.isEmpty()) {
      findings.add(
          new Finding(
              "`"
                  + binding.capability()
                  + "` is bound to screen `"
                  + binding.screen()
                  + "`, and no control route in this build answers it. The control routes are read"
                  + " from the compiled classes, so a binding outlives the route it names: point"
                  + " it at the route that replaced it, or delete it"));
    }
    if (screen.isEmpty()) {
      findings.add(
          new Finding(
              "`"
                  + binding.capability()
                  + "` is bound to screen `"
                  + binding.screen()
                  + "`, and no screen route in this build answers it. A tester clicks a route,"
                  + " not a declaration, so name the screen route that offers this capability now"));
    }
    if (!reach.declaresAKernelMethod(binding.effect())) {
      findings.add(
          new Finding(
              "`"
                  + binding.capability()
                  + "` is bound to screen `"
                  + binding.screen()
                  + "` by `"
                  + binding.effect()
                  + "`, which no class under "
                  + KERNEL
                  + " in this build declares. A binding is held to a kernel method because that is"
                  + " what the two sides can be shown to share; a method renamed leaves the"
                  + " binding asserting nothing, so name the method that carries this capability"
                  + " now"));
      return List.copyOf(findings);
    }
    capability.ifPresent(
        route -> {
          if (!reach.from(handlerOf(route), binding.effect())) {
            findings.add(
                new Finding(
                    "`"
                        + binding.capability()
                        + "` is bound to screen `"
                        + binding.screen()
                        + "` by `"
                        + binding.effect()
                        + "`, and "
                        + handlerOf(route)
                        + " reaches no such method. The binding describes a capability this"
                        + " control route no longer performs, so it holds the screen to nothing:"
                        + " name the kernel method this route performs now"));
          }
        });
    screen.ifPresent(
        route -> {
          if (!reach.from(handlerOf(route), binding.effect())) {
            findings.add(
                new Finding(
                    "`"
                        + binding.capability()
                        + "` is bound to screen `"
                        + binding.screen()
                        + "` by `"
                        + binding.effect()
                        + "`, and "
                        + handlerOf(route)
                        + " reaches no such method, so clicking that screen no longer performs"
                        + " this capability. Either the screen lost it and has to get it back, or"
                        + " it moved to another screen and this binding has to name that one"));
          }
        });
    if (capability.isPresent() && screen.isPresent()) {
      findings.addAll(whatTheVerbsCannotShow(binding, capability.get(), screen.get()));
    }
    return List.copyOf(findings);
  }

  private static List<Finding> whatTheVerbsCannotShow(
      Binding binding, Route capability, Route screen) {
    if (!changesTheSimulator(capability) || changesTheSimulator(screen)) {
      return List.of();
    }
    return List.of(
        new Finding(
            "`"
                + binding.capability()
                + "` changes the simulator and is bound to `"
                + binding.screen()
                + "`, which only reads it. A screen that reads a capability out does not offer it:"
                + " bind this one to the screen route a tester submits to"));
  }

  private static List<Finding> whatTheFacetCannotShow(
      Reach reach,
      Map<String, Route> capabilities,
      Map<String, Route> screens,
      Set<String> bound,
      ApiOnly facet) {
    List<Finding> findings = new ArrayList<>();
    Optional<Route> capability = Optional.ofNullable(capabilities.get(facet.capability()));
    if (capability.isEmpty()) {
      findings.add(
          new Finding(
              "`"
                  + facet.capability()
                  + "` is declared to offer `"
                  + facet.effect()
                  + "` only through the API, and no control route in this build answers it, so the"
                  + " declaration excuses nothing that exists"));
    }
    if (!reach.declaresAKernelMethod(facet.effect())) {
      findings.add(
          new Finding(
              "`"
                  + facet.capability()
                  + "` is declared to offer `"
                  + facet.effect()
                  + "` only through the API, and no class under "
                  + KERNEL
                  + " in this build declares that method. An API-only facet is named by the kernel"
                  + " method no screen reaches; a method that no longer exists names no facet and"
                  + " nothing can tell whether a screen offers it"));
      return List.copyOf(findings);
    }
    capability.ifPresent(
        route -> {
          if (!reach.from(handlerOf(route), facet.effect())) {
            findings.add(
                new Finding(
                    "`"
                        + facet.capability()
                        + "` is declared to offer `"
                        + facet.effect()
                        + "` only through the API, and "
                        + handlerOf(route)
                        + " reaches no such method. The route does not offer that facet either any"
                        + " more, so the declaration records a difference this build has not got:"
                        + " delete it"));
          }
        });
    Set<String> offering = new TreeSet<>();
    for (Map.Entry<String, Route> screen : screens.entrySet()) {
      if (reach.from(handlerOf(screen.getValue()), facet.effect())) {
        offering.add(screen.getKey());
      }
    }
    if (!offering.isEmpty()) {
      findings.add(
          new Finding(
              "`"
                  + facet.capability()
                  + "` is declared to offer `"
                  + facet.effect()
                  + "` only through the API, and "
                  + String.join(", ", offering)
                  + " reaches that method, so the reason the declaration gives has stopped being"
                  + " true. Clicking now performs this facet: bind it in"
                  + " `screensThatOfferAControlCapability` and delete the declaration"));
    }
    if (facet.reason() == Reason.A_SCREEN_REACHES_THE_ROUTE_BY_ANOTHER_ROAD
        && !bound.contains(facet.capability())) {
      findings.add(
          new Finding(
              "`"
                  + facet.capability()
                  + "` declares `"
                  + facet.effect()
                  + "` API-only because a screen reaches the rest of the route by another road ("
                  + facet.note()
                  + "), and no screen is bound to that route at all. The reason rests on a binding"
                  + " that is not there, so what the declaration excuses is the whole capability"
                  + " and not a facet of it"));
    }
    if (facet.reason() == Reason.NO_SCREEN_OFFERS_IT_AND_ONE_SHOULD) {
      findings.add(
          new Finding(
              "`"
                  + facet.capability()
                  + "` offers `"
                  + facet.effect()
                  + "` and no screen reaches it, and this build records that a screen should: "
                  + facet.note()
                  + ". This is the gap itself, not a defect in the declaration; it fails until a"
                  + " screen offers the facet and the binding replaces this entry, or until the"
                  + " owner rules that clicking is not supposed to reach it"));
    }
    return List.copyOf(findings);
  }

  private static boolean changesTheSimulator(Route route) {
    return VERBS_THAT_CHANGE_THE_SIMULATOR.contains(route.httpMethod().toLowerCase(Locale.ROOT));
  }

  private static String handlerOf(Route route) {
    return route.controllerClass() + MEMBER + route.operationId();
  }

  private static Map<String, Route> routesOf(List<Route> routes, String family) {
    Map<String, Route> found = new LinkedHashMap<>();
    for (Route route : routes) {
      if (route.family().equals(family)) {
        found.put(route.signature(), route);
      }
    }
    return Map.copyOf(found);
  }

  private static List<Path> pagesUnder(Path templates) {
    try (Stream<Path> walked = Files.walk(templates)) {
      return walked
          .filter(Files::isRegularFile)
          .filter(path -> path.getFileName().toString().endsWith(".html"))
          .sorted()
          .toList();
    } catch (IOException unreadable) {
      throw new UncheckedIOException(unreadable);
    }
  }

  private static String read(Path page) {
    try {
      return Files.readString(page, StandardCharsets.UTF_8);
    } catch (IOException unreadable) {
      throw new UncheckedIOException(unreadable);
    }
  }

  public static List<String> everyDestinationTheNavigationOffers() {
    return TheRail.everyDestinationOnOffer(new ContributedScreens());
  }

  public static void main(String[] args) {
    Path root = Path.of(args[0]);
    Path classesRoot = Path.of(args[1]);
    List<Binding> bindings = new ArrayList<>();
    List<ApiOnly> facets = new ArrayList<>();
    for (int at = 2; at < args.length; at++) {
      String argument = args[at];
      if (argument.startsWith(CLICKABLE)) {
        bindings.add(Binding.parse(argument.substring(CLICKABLE.length())));
      } else if (argument.startsWith(API_ONLY)) {
        facets.add(ApiOnly.parse(argument.substring(API_ONLY.length())));
      } else {
        throw new IllegalArgumentException("unreadable argument " + argument);
      }
    }
    Verdict verdict =
        check(
            Reach.under(classesRoot),
            RoutesInTheBuild.under(classesRoot),
            everyPathLinkedFrom(root.resolve(TEMPLATES)),
            everyDestinationTheNavigationOffers(),
            List.copyOf(bindings),
            List.copyOf(facets));
    if (!verdict.findings().isEmpty()) {
      StringBuilder failure = new StringBuilder();
      failure.append(
          "This simulator has to be usable as a test environment from the control API, for CI and"
              + " for an end-to-end suite, and from the UI by clicking with no technical"
              + " knowledge, and UI coverage has to equal API coverage. These capabilities and the"
              + " screens that are supposed to offer them disagree:\n");
      verdict.findings().forEach(finding -> failure.append("  - ").append(finding).append('\n'));
      failure.append(
          "A capability is one control route, because a route is what arrives in a diff and"
              + " nothing finer can be discovered from the build. A capability is bound to a"
              + " screen route by a kernel method both of their handlers reach in the compiled"
              + " classes, so a screen that stops performing it fails here instead of passing on"
              + " the strength of a declaration. A facet of a route that no screen reaches is"
              + " declared in `facetsOnlyTheApiOffers` with the reason it is not clickable, and"
              + " that declaration fails the day a screen reaches it. A screen route reachable by"
              + " no link in any page is reachable by a tab of the navigation instead, and the"
              + " navigation is asked what it offers rather than read out of markup, so a screen"
              + " the navigation discovers counts and a tab that leads nowhere fails.\n");
      System.out.println(failure);
      System.exit(1);
    }
    System.out.println(
        "verifyClickableCapabilities: "
            + verdict.capabilities()
            + " control capabilities, each offered by at least one of "
            + verdict.screens()
            + " screen routes; "
            + verdict.bindings()
            + " bindings over "
            + verdict.effects()
            + " kernel methods both sides reach, "
            + verdict.facets()
            + " facets declared API-only and reached by no screen; every screen route is a form or"
            + " link target on a page a tester can be looking at or the destination of one of "
            + verdict.destinations()
            + " tabs the navigation offers, and every tab under "
            + THE_SCREEN_NAMESPACE
            + " is answered by a route of this build.");
  }
}
