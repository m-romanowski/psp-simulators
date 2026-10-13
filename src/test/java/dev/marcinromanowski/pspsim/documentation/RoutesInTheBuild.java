package dev.marcinromanowski.pspsim.documentation;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.lang.classfile.Annotation;
import java.lang.classfile.AnnotationElement;
import java.lang.classfile.AnnotationValue;
import java.lang.classfile.Attributes;
import java.lang.classfile.ClassFile;
import java.lang.classfile.ClassModel;
import java.lang.classfile.MethodModel;
import java.lang.classfile.attribute.MethodParameterInfo;
import java.lang.classfile.attribute.MethodParametersAttribute;
import java.lang.classfile.attribute.RuntimeVisibleAnnotationsAttribute;
import java.lang.classfile.attribute.RuntimeVisibleParameterAnnotationsAttribute;
import java.lang.constant.ClassDesc;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

public final class RoutesInTheBuild {

  private static final String ANNOTATIONS = "org.springframework.web.bind.annotation.";

  private static final String REST_CONTROLLER = ANNOTATIONS + "RestController";

  private static final String CONTROLLER = "org.springframework.stereotype.Controller";

  private static final String REQUEST_MAPPING = ANNOTATIONS + "RequestMapping";

  private static final String PATH_VARIABLE = ANNOTATIONS + "PathVariable";

  private static final String REQUEST_PARAM = ANNOTATIONS + "RequestParam";

  private static final String REQUEST_BODY = ANNOTATIONS + "RequestBody";

  private static final String SERVLET_REQUEST = "jakarta.servlet.http.HttpServletRequest";

  private static final String CONTROL_PACKAGE = "dev.marcinromanowski.pspsim.control";

  private static final String SCREEN_PACKAGE = "dev.marcinromanowski.pspsim.ui";

  private static final String PROVIDER_PACKAGE = "dev.marcinromanowski.pspsim.provider.";

  public static final String CONTROL = "control";

  public static final String SCREENS = "screens";

  public static final String PROVIDER = "provider:";

  private static final Map<String, String> VERB_OF_ANNOTATION =
      Map.of(
          ANNOTATIONS + "GetMapping", "get",
          ANNOTATIONS + "PostMapping", "post",
          ANNOTATIONS + "PutMapping", "put",
          ANNOTATIONS + "DeleteMapping", "delete",
          ANNOTATIONS + "PatchMapping", "patch");

  private RoutesInTheBuild() {}

  public record Parameter(String name, String in, boolean required) {}

  public record Route(
      String httpMethod,
      String path,
      String operationId,
      String controllerClass,
      String family,
      List<Parameter> parameters,
      Optional<String> requestBodyClass,
      boolean requestBodyRequired,
      boolean bodyArrivesUnparsed) {

    public String signature() {
      return httpMethod.toUpperCase(Locale.ROOT) + " " + path;
    }
  }

  public static List<Route> under(Path classesRoot) {
    List<Route> found = new ArrayList<>();
    for (Path compiled : classFilesUnder(classesRoot)) {
      ClassModel model = parse(compiled);
      List<Annotation> onTheClass = annotationsOf(model);
      if (!names(onTheClass, REST_CONTROLLER) && !names(onTheClass, CONTROLLER)) {
        continue;
      }
      String className = model.thisClass().asInternalName().replace('/', '.');
      String prefix = firstPathOf(annotation(onTheClass, REQUEST_MAPPING)).orElse("");
      for (MethodModel method : model.methods()) {
        found.addAll(routesOf(className, prefix, method));
      }
    }
    found.sort(Comparator.comparing(Route::path).thenComparing(Route::httpMethod));
    return List.copyOf(found);
  }

  public static String familyOf(String className) {
    String owner = className.substring(0, className.lastIndexOf('.'));
    if (owner.equals(CONTROL_PACKAGE)) {
      return CONTROL;
    }
    if (owner.equals(SCREEN_PACKAGE)) {
      return SCREENS;
    }
    if (owner.startsWith(PROVIDER_PACKAGE)) {
      String rest = owner.substring(PROVIDER_PACKAGE.length());
      if (!rest.contains(".")) {
        return PROVIDER + rest;
      }
    }
    throw new IllegalStateException(
        className
            + " answers an HTTP route and sits in package "
            + owner
            + ", which is neither "
            + CONTROL_PACKAGE
            + ", nor "
            + SCREEN_PACKAGE
            + ", nor a single "
            + PROVIDER_PACKAGE
            + "<provider> package. Nothing can say whether its routes belong in the control"
            + " document, in a provider document, or in neither, so the published API view would"
            + " silently omit them. Move the controller into one of those packages or widen"
            + " RoutesInTheBuild.familyOf and say in the commit which document the new family is"
            + " published in.");
  }

  private static List<Route> routesOf(String className, String prefix, MethodModel method) {
    List<Annotation> onTheMethod = annotationsOf(method);
    List<Route> routes = new ArrayList<>();
    for (Map.Entry<String, String> mapped : sortedVerbs()) {
      Optional<Annotation> found = annotation(onTheMethod, mapped.getKey());
      if (found.isEmpty()) {
        continue;
      }
      for (String path : pathsOf(found.get())) {
        routes.add(
            new Route(
                mapped.getValue(),
                prefix + path,
                method.methodName().stringValue(),
                className,
                familyOf(className),
                parametersOf(method),
                bodyClassOf(method),
                bodyRequiredOf(method),
                takesTheRequestItself(method)));
      }
    }
    return routes;
  }

  private static List<Map.Entry<String, String>> sortedVerbs() {
    List<Map.Entry<String, String>> sorted = new ArrayList<>(VERB_OF_ANNOTATION.entrySet());
    sorted.sort(Map.Entry.comparingByValue());
    return sorted;
  }

  private static List<Parameter> parametersOf(MethodModel method) {
    List<Parameter> declared = new ArrayList<>();
    List<List<Annotation>> perParameter = parameterAnnotationsOf(method);
    List<String> names = parameterNamesOf(method);
    List<ClassDesc> types = method.methodTypeSymbol().parameterList();
    for (int at = 0; at < types.size(); at++) {
      List<Annotation> annotations = at < perParameter.size() ? perParameter.get(at) : List.of();
      Optional<Annotation> path = annotation(annotations, PATH_VARIABLE);
      Optional<Annotation> query = annotation(annotations, REQUEST_PARAM);
      if (path.isPresent()) {
        declared.add(new Parameter(nameOf(path.get(), names, at), "path", true));
      } else if (query.isPresent()) {
        declared.add(
            new Parameter(
                nameOf(query.get(), names, at), "query", requiredOf(query.get()).orElse(true)));
      }
    }
    return List.copyOf(declared);
  }

  private static String nameOf(Annotation annotation, List<String> names, int at) {
    Optional<String> declared = stringElement(annotation, "name");
    if (declared.isPresent()) {
      return declared.get();
    }
    Optional<String> aliased = stringElement(annotation, "value");
    if (aliased.isPresent()) {
      return aliased.get();
    }
    if (at < names.size()) {
      return names.get(at);
    }
    throw new IllegalStateException(
        "a route parameter at position "
            + at
            + " names itself neither in its annotation nor in the MethodParameters attribute of the"
            + " compiled class, so the published API view cannot name it either");
  }

  private static Optional<String> bodyClassOf(MethodModel method) {
    List<List<Annotation>> perParameter = parameterAnnotationsOf(method);
    List<ClassDesc> types = method.methodTypeSymbol().parameterList();
    for (int at = 0; at < types.size() && at < perParameter.size(); at++) {
      if (annotation(perParameter.get(at), REQUEST_BODY).isPresent()) {
        return Optional.of(binaryNameOf(types.get(at)));
      }
    }
    return Optional.empty();
  }

  private static boolean bodyRequiredOf(MethodModel method) {
    List<List<Annotation>> perParameter = parameterAnnotationsOf(method);
    for (List<Annotation> annotations : perParameter) {
      Optional<Annotation> body = annotation(annotations, REQUEST_BODY);
      if (body.isPresent()) {
        return requiredOf(body.get()).orElse(true);
      }
    }
    return false;
  }

  private static boolean takesTheRequestItself(MethodModel method) {
    for (ClassDesc type : method.methodTypeSymbol().parameterList()) {
      if (SERVLET_REQUEST.equals(binaryNameOf(type))) {
        return true;
      }
    }
    return false;
  }

  private static String binaryNameOf(ClassDesc type) {
    return type.isClassOrInterface() ? type.packageName() + "." + type.displayName() : "";
  }

  private static List<String> parameterNamesOf(MethodModel method) {
    Optional<MethodParametersAttribute> attribute =
        method.findAttribute(Attributes.methodParameters());
    if (attribute.isEmpty()) {
      return List.of();
    }
    List<String> names = new ArrayList<>();
    for (MethodParameterInfo parameter : attribute.get().parameters()) {
      names.add(parameter.name().map(name -> name.stringValue()).orElse(""));
    }
    return List.copyOf(names);
  }

  private static List<List<Annotation>> parameterAnnotationsOf(MethodModel method) {
    return method
        .findAttribute(Attributes.runtimeVisibleParameterAnnotations())
        .map(RuntimeVisibleParameterAnnotationsAttribute::parameterAnnotations)
        .orElse(List.of());
  }

  private static List<Annotation> annotationsOf(ClassModel model) {
    return model
        .findAttribute(Attributes.runtimeVisibleAnnotations())
        .map(RuntimeVisibleAnnotationsAttribute::annotations)
        .orElse(List.of());
  }

  private static List<Annotation> annotationsOf(MethodModel method) {
    return method
        .findAttribute(Attributes.runtimeVisibleAnnotations())
        .map(RuntimeVisibleAnnotationsAttribute::annotations)
        .orElse(List.of());
  }

  private static boolean names(List<Annotation> annotations, String binaryName) {
    return annotation(annotations, binaryName).isPresent();
  }

  private static Optional<Annotation> annotation(List<Annotation> annotations, String binaryName) {
    for (Annotation annotation : annotations) {
      if (binaryNameOf(annotation.classSymbol()).equals(binaryName)) {
        return Optional.of(annotation);
      }
    }
    return Optional.empty();
  }

  private static Optional<String> firstPathOf(Optional<Annotation> annotation) {
    if (annotation.isEmpty()) {
      return Optional.empty();
    }
    List<String> paths = pathsOf(annotation.get());
    return paths.isEmpty() ? Optional.empty() : Optional.of(paths.get(0));
  }

  private static List<String> pathsOf(Annotation annotation) {
    List<String> paths = new ArrayList<>(stringsElement(annotation, "value"));
    if (paths.isEmpty()) {
      paths.addAll(stringsElement(annotation, "path"));
    }
    return List.copyOf(paths);
  }

  private static List<String> stringsElement(Annotation annotation, String name) {
    List<String> strings = new ArrayList<>();
    for (AnnotationElement element : annotation.elements()) {
      if (!element.name().stringValue().equals(name)) {
        continue;
      }
      switch (element.value()) {
        case AnnotationValue.OfArray array -> {
          for (AnnotationValue value : array.values()) {
            if (value instanceof AnnotationValue.OfString text) {
              strings.add(text.stringValue());
            }
          }
        }
        case AnnotationValue.OfString text -> strings.add(text.stringValue());
        default -> {
          throw new IllegalStateException(
              "the "
                  + name
                  + " element of "
                  + annotation.classSymbol().displayName()
                  + " is not a string or an array of strings, so no route can be read out of it");
        }
      }
    }
    return List.copyOf(strings);
  }

  private static Optional<String> stringElement(Annotation annotation, String name) {
    for (AnnotationElement element : annotation.elements()) {
      if (element.name().stringValue().equals(name)
          && element.value() instanceof AnnotationValue.OfString text
          && !text.stringValue().isEmpty()) {
        return Optional.of(text.stringValue());
      }
    }
    return Optional.empty();
  }

  private static Optional<Boolean> requiredOf(Annotation annotation) {
    for (AnnotationElement element : annotation.elements()) {
      if (element.name().stringValue().equals("required")
          && element.value() instanceof AnnotationValue.OfBoolean declared) {
        return Optional.of(declared.booleanValue());
      }
    }
    return Optional.empty();
  }

  private static ClassModel parse(Path compiled) {
    try {
      return ClassFile.of().parse(compiled);
    } catch (IOException unreadable) {
      throw new UncheckedIOException(unreadable);
    }
  }

  private static List<Path> classFilesUnder(Path classesRoot) {
    Map<String, Path> byName = new LinkedHashMap<>();
    try (Stream<Path> walked = Files.walk(classesRoot)) {
      walked
          .filter(Files::isRegularFile)
          .filter(path -> path.getFileName().toString().endsWith(".class"))
          .sorted()
          .forEach(path -> byName.put(classesRoot.relativize(path).toString(), path));
    } catch (IOException unreadable) {
      throw new UncheckedIOException(unreadable);
    }
    return List.copyOf(byName.values());
  }
}
