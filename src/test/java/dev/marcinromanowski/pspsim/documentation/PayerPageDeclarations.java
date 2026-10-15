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
import java.lang.classfile.constantpool.LoadableConstantEntry;
import java.lang.classfile.constantpool.StringEntry;
import java.lang.classfile.instruction.ConstantInstruction;
import java.lang.classfile.instruction.InvokeDynamicInstruction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Stream;

public final class PayerPageDeclarations {

  public static final String THE_HEADER_A_POLICY_IS_DECLARED_IN = "Content-Security-Policy";

  public static final String NOTHING_IS_LOADED_BY_DEFAULT = "default-src 'none'";

  public static final String THE_PAGES_OWN_STYLE = "style-src 'unsafe-inline'";

  public static final String THE_FLOOR_A_POLICY_STANDS_ON =
      NOTHING_IS_LOADED_BY_DEFAULT + "; " + THE_PAGES_OWN_STYLE;

  public static final String THE_CLASS_THAT_OWNS_THE_DECLARATION =
      "dev.marcinromanowski.pspsim.kernel.spi.WhatAPayerPageMayLoad";

  public static final String BETWEEN_DIRECTIVES = ";";

  public static final String THE_ELEMENT_THAT_CARRIES_A_PAGES_OWN_STYLE = "style";

  public static final List<String> THE_ELEMENTS_THAT_LOAD_A_SUBRESOURCE =
      List.of(
          "script", "img", "link", "iframe", "frame", "object", "embed", "audio", "video", "source",
          "track", "applet");

  public static final String AN_ELEMENT_THAT_RUNS_INLINE = "script";

  public static final String AN_INLINE_SCRIPT_CLOSES = "</script>";

  public static final String UNREADABLE = "\u0000";

  public static final String THE_DIRECTIVE_A_SCRIPT_IS_ADMITTED_BY = "script-src";

  public static final List<String> HOW_AN_HTML_DOCUMENT_OPENS = List.of("<!doctype html", "<html");

  private static final Pattern A_RUN_OF_SPACE = Pattern.compile("\\s+");

  private static final Pattern A_SOURCE_IT_LOADS = Pattern.compile("(^|\\s)src\\s*=");

  private static final Pattern A_POLICY_READS_LIKE_THIS =
      Pattern.compile("(?:^|;)\\s*[A-Za-z][A-Za-z0-9-]*\\s+'[^']*'");

  private PayerPageDeclarations() {}

  public record APage(String renderedBy, List<String> constants) {

    public APage {
      constants = List.copyOf(constants);
    }

    public boolean namesTheHeader() {
      for (String constant : constants) {
        if (constant.equalsIgnoreCase(THE_HEADER_A_POLICY_IS_DECLARED_IN)) {
          return true;
        }
      }
      return false;
    }

    public List<String> policies() {
      Set<String> policies = new LinkedHashSet<>();
      for (String constant : constants) {
        if (aPolicy(constant)) {
          policies.add(constant);
        }
      }
      return List.copyOf(policies);
    }

    public boolean carriesItsOwnStyle() {
      return namesAnElement(THE_ELEMENT_THAT_CARRIES_A_PAGES_OWN_STYLE);
    }

    public List<String> theElementsItLoadsASubresourceFor() {
      List<String> loading = new ArrayList<>();
      for (String element : THE_ELEMENTS_THAT_LOAD_A_SUBRESOURCE) {
        boolean loads =
            AN_ELEMENT_THAT_RUNS_INLINE.equals(element)
                ? namesAnElementLoadingASource(element)
                : namesAnElement(element);
        if (loads) {
          loading.add(element);
        }
      }
      return List.copyOf(loading);
    }

    public List<String> theInlineScripts() {
      List<String> scripts = new ArrayList<>();
      for (String constant : constants) {
        scripts.addAll(theInlineScriptsIn(constant));
      }
      return List.copyOf(scripts);
    }

    private boolean namesAnElementLoadingASource(String element) {
      for (String constant : constants) {
        if (namesTheElementLoadingASource(constant, element)) {
          return true;
        }
      }
      return false;
    }

    private boolean namesAnElement(String element) {
      for (String constant : constants) {
        if (namesTheElement(constant, element)) {
          return true;
        }
      }
      return false;
    }
  }

  public record Directive(String name, String value) {

    public String asItReads() {
      return value.isEmpty() ? name : name + " " + value;
    }
  }

  public static boolean opensAnHtmlDocument(String constant) {
    String opening = constant.stripLeading().toLowerCase(Locale.ROOT);
    for (String shape : HOW_AN_HTML_DOCUMENT_OPENS) {
      if (opening.startsWith(shape)) {
        return true;
      }
    }
    return false;
  }

  public static boolean namesTheElement(String text, String element) {
    String written = text.toLowerCase(Locale.ROOT);
    String opening = "<" + element;
    int at = written.indexOf(opening);
    while (at >= 0) {
      int after = at + opening.length();
      if (after >= written.length() || !Character.isLetterOrDigit(written.charAt(after))) {
        return true;
      }
      at = written.indexOf(opening, at + 1);
    }
    return false;
  }

  public static boolean namesTheElementLoadingASource(String text, String element) {
    String written = text.toLowerCase(Locale.ROOT);
    String opening = "<" + element;
    int at = written.indexOf(opening);
    while (at >= 0) {
      int after = at + opening.length();
      if (after >= written.length()) {
        return true;
      }
      if (!Character.isLetterOrDigit(written.charAt(after))) {
        int closes = written.indexOf('>', after);
        String tag = closes < 0 ? written.substring(after) : written.substring(after, closes);
        if (closes < 0 || !tag.isEmpty()) {
          return true;
        }
      }
      at = written.indexOf(opening, at + 1);
    }
    return false;
  }

  public static List<String> theInlineScriptsIn(String constant) {
    List<String> scripts = new ArrayList<>();
    String written = constant.toLowerCase(Locale.ROOT);
    String opening = "<" + AN_ELEMENT_THAT_RUNS_INLINE;
    int at = written.indexOf(opening);
    while (at >= 0) {
      int after = at + opening.length();
      int tagCloses = written.indexOf('>', after);
      if (tagCloses < 0) {
        break;
      }
      String attributes = written.substring(after, tagCloses);
      if (!attributes.isEmpty()
          && (Character.isLetterOrDigit(attributes.charAt(0))
              || A_SOURCE_IT_LOADS.matcher(attributes).find())) {
        at = written.indexOf(opening, after);
        continue;
      }
      int from = tagCloses + 1;
      int to = written.indexOf(AN_INLINE_SCRIPT_CLOSES, from);
      scripts.add(to < 0 ? UNREADABLE : constant.substring(from, to));
      at = to < 0 ? -1 : written.indexOf(opening, to);
    }
    return List.copyOf(scripts);
  }

  public static String theHashOf(String script) {
    try {
      return "'sha256-"
          + Base64.getEncoder()
              .encodeToString(
                  MessageDigest.getInstance("SHA-256")
                      .digest(script.getBytes(StandardCharsets.UTF_8)))
          + "'";
    } catch (NoSuchAlgorithmException unavailable) {
      throw new IllegalStateException(unavailable);
    }
  }

  public static boolean aPolicy(String constant) {
    return A_POLICY_READS_LIKE_THIS.matcher(constant).find();
  }

  public static List<Directive> theDirectivesOf(String policy) {
    List<Directive> named = new ArrayList<>();
    for (String written : policy.split(BETWEEN_DIRECTIVES, -1)) {
      String directive = A_RUN_OF_SPACE.matcher(written.strip()).replaceAll(" ");
      if (directive.isEmpty()) {
        continue;
      }
      int space = directive.indexOf(' ');
      String name = space < 0 ? directive : directive.substring(0, space);
      String value = space < 0 ? "" : directive.substring(space + 1);
      named.add(new Directive(name.toLowerCase(Locale.ROOT), value));
    }
    return List.copyOf(named);
  }

  public static Set<String> theCanonicalFormOf(String policy) {
    Set<String> canonical = new LinkedHashSet<>();
    for (Directive directive : theDirectivesOf(policy)) {
      canonical.add(directive.asItReads());
    }
    return Collections.unmodifiableSet(canonical);
  }

  public static boolean namesTheDirective(String policy, String directive) {
    return theCanonicalFormOf(policy).contains(canonical(directive));
  }

  public static List<Directive> theDirectivesBeyondTheFloor(String policy) {
    List<Directive> beyond = new ArrayList<>();
    for (Directive directive : theDirectivesOf(policy)) {
      if (!theFloor().contains(directive.asItReads())) {
        beyond.add(directive);
      }
    }
    return List.copyOf(beyond);
  }

  public static Set<String> theFloor() {
    return theCanonicalFormOf(THE_FLOOR_A_POLICY_STANDS_ON);
  }

  private static String canonical(String directive) {
    List<Directive> read = theDirectivesOf(directive);
    return read.isEmpty() ? "" : read.get(0).asItReads();
  }

  public static List<APage> inTheBuild(Path classesRoot) {
    List<APage> pages = new ArrayList<>();
    for (Path compiled : classFilesUnder(classesRoot)) {
      ClassModel model = parse(compiled);
      List<String> constants = constantsOf(model);
      if (constants.stream().anyMatch(PayerPageDeclarations::opensAnHtmlDocument)) {
        pages.add(new APage(model.thisClass().asInternalName().replace('/', '.'), constants));
      }
    }
    pages.sort(Comparator.comparing(APage::renderedBy));
    return List.copyOf(pages);
  }

  public static List<String> theConstantsOf(Path classesRoot, String className) {
    Path compiled = classesRoot.resolve(className.replace('.', '/') + ".class");
    if (!Files.isRegularFile(compiled)) {
      return List.of();
    }
    return constantsOf(parse(compiled));
  }

  public static int howManyClassesAreUnder(Path classesRoot) {
    return classFilesUnder(classesRoot).size();
  }

  static List<String> constantsOf(ClassModel model) {
    Set<String> read = new LinkedHashSet<>();
    for (FieldModel field : model.fields()) {
      Optional<ConstantValueAttribute> constant = field.findAttribute(Attributes.constantValue());
      if (constant.isPresent() && constant.get().constant() instanceof StringEntry text) {
        read.add(text.stringValue());
      }
    }
    for (MethodModel method : model.methods()) {
      for (CodeElement element : instructionsOf(method)) {
        if (element instanceof ConstantInstruction loaded
            && loaded.constantValue() instanceof String text) {
          read.add(text);
        }
        if (element instanceof InvokeDynamicInstruction composed) {
          read.addAll(theTextsJoinedBy(composed));
        }
      }
    }
    return List.copyOf(read);
  }

  static List<String> theTextsJoinedBy(InvokeDynamicInstruction composed) {
    List<String> joined = new ArrayList<>();
    for (LoadableConstantEntry argument : composed.invokedynamic().bootstrap().arguments()) {
      if (argument instanceof StringEntry text) {
        joined.add(text.stringValue());
      }
    }
    return List.copyOf(joined);
  }

  private static List<CodeElement> instructionsOf(MethodModel method) {
    return method
        .code()
        .map(
            code -> {
              List<CodeElement> elements = new ArrayList<>();
              code.forEach(elements::add);
              return List.copyOf(elements);
            })
        .orElse(List.of());
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
      throw new IllegalStateException(
          classesRoot
              + " is not a directory of compiled classes, so the sweep for the pages this"
              + " simulator serves a payer would read nothing and report that it found none. A"
              + " sweep that found no page and a sweep that read no class must not print the same"
              + " thing.");
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
