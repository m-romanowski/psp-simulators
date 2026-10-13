package dev.marcinromanowski.pspsim.documentation;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.lang.classfile.Attributes;
import java.lang.classfile.ClassFile;
import java.lang.classfile.ClassModel;
import java.lang.classfile.CodeElement;
import java.lang.classfile.FieldModel;
import java.lang.classfile.MethodModel;
import java.lang.classfile.attribute.ConstantValueAttribute;
import java.lang.classfile.constantpool.StringEntry;
import java.lang.classfile.instruction.ConstantInstruction;
import java.lang.classfile.instruction.FieldInstruction;
import java.lang.classfile.instruction.InvokeInstruction;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.jspecify.annotations.Nullable;

public final class PayerPageAddresses {

  public static final String THE_DECLARATION =
      "dev.marcinromanowski.pspsim.kernel.spi.ThePayerStep$APageThisSimulatorServes";

  public static final String A_PAGE_IS_SERVED_AS = "text/html";

  public static final String THE_MEDIA_TYPES_SPRING_NAMES = "org.springframework.http.MediaType";

  public static final String THE_HTML_MEDIA_TYPES = "TEXT_HTML";

  public static final String PROVIDER_PACKAGE = "dev.marcinromanowski.pspsim.provider.";

  public static final String TEMPLATES = "src/main/resources/templates";

  public static final String SCREENS = "src/main/java/dev/marcinromanowski/pspsim/ui";

  public static final String INITIALIZER = "<init>";

  public static final String THE_ROOT_OF_EVERY_ROUTE = "/";

  private static final Pattern A_PATH_VARIABLE = Pattern.compile("\\{[^/}]*}");

  private static final String ONE_SEGMENT = "[^/\"'\\s<>]+";

  private static final String THE_SEGMENT_CONTINUES = "[A-Za-z0-9_-]";

  private PayerPageAddresses() {}

  public record Declared(String provider, String route, String declaredIn) {}

  public record Sighting(String file, int line, String route, String asItReads) {}

  public record TheBuild(List<Declared> declared, Set<String> providersThatServeAPage) {

    public TheBuild {
      declared = List.copyOf(declared);
      providersThatServeAPage = Set.copyOf(providersThatServeAPage);
    }

    public List<String> routes() {
      Set<String> routes = new LinkedHashSet<>();
      for (Declared page : declared) {
        routes.add(page.route());
      }
      return List.copyOf(routes);
    }

    public List<String> providersThatDeclareAPage() {
      Set<String> named = new TreeSet<>();
      for (Declared page : declared) {
        named.add(page.provider());
      }
      return List.copyOf(named);
    }
  }

  public static TheBuild inTheBuild(Path classesRoot) {
    List<Declared> declared = new ArrayList<>();
    Set<String> serving = new TreeSet<>();
    for (Path compiled : classFilesUnder(classesRoot)) {
      ClassModel model = parse(compiled);
      String owner = model.thisClass().asInternalName().replace('/', '.');
      if (!owner.startsWith(PROVIDER_PACKAGE)) {
        continue;
      }
      String provider = providerOf(owner);
      if (aPageIsServedBy(model)) {
        serving.add(provider);
      }
      for (MethodModel method : model.methods()) {
        declared.addAll(theRoutesDeclaredIn(provider, owner, method));
      }
    }
    declared.sort(Comparator.comparing(Declared::provider).thenComparing(Declared::route));
    return new TheBuild(declared, serving);
  }

  static String providerOf(String owner) {
    String rest = owner.substring(PROVIDER_PACKAGE.length());
    int next = rest.indexOf('.');
    return next < 0 ? rest : rest.substring(0, next);
  }

  private static List<Declared> theRoutesDeclaredIn(
      String provider, String owner, MethodModel method) {
    Optional<List<CodeElement>> body = instructionsOf(method);
    if (body.isEmpty()) {
      return List.of();
    }
    List<CodeElement> elements = body.get();
    List<Declared> declared = new ArrayList<>();
    String latest = null;
    for (CodeElement element : elements) {
      if (element instanceof ConstantInstruction loaded
          && loaded.constantValue() instanceof String text) {
        latest = text;
      }
      if (!declaresAPayerPage(element)) {
        continue;
      }
      if (!aRouteThisGateCanRead(latest)) {
        throw new IllegalStateException(
            owner
                + "."
                + method.methodName().stringValue()
                + " builds a "
                + THE_DECLARATION
                + " and the last unread string constant before it in the compiled method is "
                + (latest == null ? "absent" : latest)
                + ", which is no absolute path and so no route this build could answer. This gate"
                + " reads the last string constant pushed before each construction and consumes"
                + " it, which is exact for a route written as a compile-time constant and nothing"
                + " else: a route a provider computes arrives here as an absent constant or as one"
                + " belonging to another call. Declare the route as a compile-time constant. A"
                + " route that is absolute and still wrong is caught after this, because every"
                + " address read here must be answered by a provider route of this build.");
      }
      declared.add(new Declared(provider, latest, owner));
      latest = null;
    }
    return List.copyOf(declared);
  }

  static boolean aRouteThisGateCanRead(@Nullable String constant) {
    return constant != null && constant.startsWith(THE_ROOT_OF_EVERY_ROUTE);
  }

  private static boolean declaresAPayerPage(CodeElement element) {
    return element instanceof InvokeInstruction invoked
        && invoked.name().stringValue().equals(INITIALIZER)
        && invoked.owner().asInternalName().replace('/', '.').equals(THE_DECLARATION);
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

  static boolean aPageIsServedBy(ClassModel model) {
    for (FieldModel field : model.fields()) {
      Optional<ConstantValueAttribute> constant = field.findAttribute(Attributes.constantValue());
      if (constant.isPresent()
          && constant.get().constant() instanceof StringEntry text
          && aContentTypeOfAPage(text.stringValue())) {
        return true;
      }
    }
    for (MethodModel method : model.methods()) {
      for (CodeElement element : instructionsOf(method).orElse(List.of())) {
        if (element instanceof ConstantInstruction loaded
            && loaded.constantValue() instanceof String text
            && aContentTypeOfAPage(text)) {
          return true;
        }
        if (element instanceof FieldInstruction read
            && theHtmlMediaType(
                read.owner().asInternalName().replace('/', '.'), read.name().stringValue())) {
          return true;
        }
      }
    }
    return false;
  }

  static boolean aContentTypeOfAPage(String constant) {
    return constant.stripLeading().startsWith(A_PAGE_IS_SERVED_AS);
  }

  static boolean theHtmlMediaType(String owner, String field) {
    return owner.equals(THE_MEDIA_TYPES_SPRING_NAMES) && field.startsWith(THE_HTML_MEDIA_TYPES);
  }

  public static Pattern asAScreenWouldWriteIt(String route) {
    StringBuilder expression = new StringBuilder();
    Matcher variables = A_PATH_VARIABLE.matcher(route);
    int from = 0;
    while (variables.find()) {
      expression.append(Pattern.quote(route.substring(from, variables.start())));
      expression
          .append("(?:")
          .append(ONE_SEGMENT)
          .append("|")
          .append(Pattern.quote(variables.group()))
          .append(")?");
      from = variables.end();
    }
    expression.append(Pattern.quote(route.substring(from)));
    expression.append("(?!").append(THE_SEGMENT_CONTINUES).append(")");
    return Pattern.compile(expression.toString());
  }

  public static List<Sighting> sightingsIn(String file, String text, List<String> routes) {
    List<Sighting> seen = new ArrayList<>();
    List<String> lines = List.of(text.split("\n", -1));
    for (String route : routes) {
      Pattern written = asAScreenWouldWriteIt(route);
      for (int at = 0; at < lines.size(); at++) {
        if (written.matcher(lines.get(at)).find()) {
          seen.add(new Sighting(file, at + 1, route, lines.get(at).strip()));
        }
      }
    }
    return List.copyOf(seen);
  }

  public static List<Path> theFilesAScreenIsMadeOf(Path projectRoot) {
    List<Path> files = new ArrayList<>();
    files.addAll(under(projectRoot.resolve(TEMPLATES), ".html"));
    files.addAll(under(projectRoot.resolve(SCREENS), ".java"));
    return List.copyOf(files);
  }

  private static List<Path> under(Path root, String extension) {
    if (!Files.isDirectory(root)) {
      throw new IllegalStateException(
          root
              + " is not a directory, so the sweep for a payer URL on a screen would read nothing"
              + " and report that it found none. A sweep that found nothing and a sweep that read"
              + " nothing must not print the same thing.");
    }
    try (Stream<Path> walked = Files.walk(root)) {
      return walked
          .filter(Files::isRegularFile)
          .filter(path -> path.getFileName().toString().endsWith(extension))
          .sorted()
          .toList();
    } catch (IOException unreadable) {
      throw new UncheckedIOException(unreadable);
    }
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
