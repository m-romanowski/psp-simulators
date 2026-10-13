package dev.marcinromanowski.pspsim.architecture;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.lang.classfile.ClassFile;
import java.lang.classfile.ClassModel;
import java.lang.classfile.CodeElement;
import java.lang.classfile.CodeModel;
import java.lang.classfile.MethodModel;
import java.lang.classfile.Opcode;
import java.lang.classfile.instruction.BranchInstruction;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

public final class ExclusionBranchBudget {

  public static final String FIELD_SEPARATOR = "|";

  private static final String NOTHING = "";

  private ExclusionBranchBudget() {}

  public record Family(String glob, int branchBudget, int methodBudget, List<String> classNames) {

    public static Family parse(String specification) {
      List<String> fields = List.of(specification.split("\\" + FIELD_SEPARATOR, -1));
      if (fields.size() != 4) {
        throw new IllegalArgumentException(
            "a family is glob|branchBudget|methodBudget|classNames, not " + specification);
      }
      String named = fields.get(3);
      List<String> classNames = named.isBlank() ? List.of() : List.of(named.split(",", -1));
      return new Family(
          fields.get(0),
          Integer.parseInt(fields.get(1)),
          Integer.parseInt(fields.get(2)),
          classNames);
    }
  }

  public record Branching(String owner, int branches) {}

  public record Reading(String glob, int branches, List<Branching> methods) {

    public Optional<Branching> worstMethod() {
      return methods.stream().max(Comparator.comparingInt(Branching::branches));
    }
  }

  public static int conditionalJumps(CodeModel code) {
    int found = 0;
    for (CodeElement element : code) {
      if (element instanceof BranchInstruction branch && isConditional(branch.opcode())) {
        found++;
      }
    }
    return found;
  }

  private static boolean isConditional(Opcode opcode) {
    return opcode != Opcode.GOTO
        && opcode != Opcode.GOTO_W
        && opcode != Opcode.JSR
        && opcode != Opcode.JSR_W;
  }

  public static Reading read(Path classesRoot, Family family) {
    List<Branching> methods = new ArrayList<>();
    int branches = 0;
    for (String className : family.classNames()) {
      ClassModel compiled = parse(classesRoot, className);
      for (MethodModel method : compiled.methods()) {
        Optional<CodeModel> body = method.code();
        if (body.isEmpty()) {
          continue;
        }
        int jumps = conditionalJumps(body.get());
        branches += jumps;
        methods.add(
            new Branching(
                className + "." + method.methodName().stringValue() + descriptorOf(method), jumps));
      }
    }
    return new Reading(family.glob(), branches, List.copyOf(methods));
  }

  private static String descriptorOf(MethodModel method) {
    return method.methodType().stringValue();
  }

  private static ClassModel parse(Path classesRoot, String className) {
    Path compiled = classesRoot.resolve(className.replace('.', '/') + ".class");
    if (!Files.isRegularFile(compiled)) {
      throw new IllegalArgumentException(
          "no compiled class for " + className + " under " + classesRoot);
    }
    try {
      return ClassFile.of().parse(compiled);
    } catch (IOException unreadable) {
      throw new UncheckedIOException(unreadable);
    }
  }

  public static List<String> overruns(Path classesRoot, List<Family> families) {
    List<String> found = new ArrayList<>();
    for (Family family : families) {
      Reading reading = read(classesRoot, family);
      if (reading.branches() > family.branchBudget()) {
        found.add(
            "`"
                + family.glob()
                + "` excludes "
                + family.classNames().size()
                + " compiled classes from mutation testing, and they now hold "
                + reading.branches()
                + " conditional jumps. The ratchet stands at "
                + family.branchBudget()
                + ", which is what that family held the day it was measured.");
      }
      Optional<Branching> worst = reading.worstMethod();
      if (worst.isPresent() && worst.get().branches() > family.methodBudget()) {
        found.add(
            "`"
                + family.glob()
                + "` excludes "
                + worst.get().owner()
                + " from mutation testing, and it holds "
                + worst.get().branches()
                + " conditional jumps in one method. The ratchet stands at "
                + family.methodBudget()
                + ", which is the densest method that family held the day it was measured.");
      }
    }
    return List.copyOf(found);
  }

  public static String report(Path classesRoot, List<Family> families) {
    StringBuilder said = new StringBuilder();
    for (Family family : families) {
      Reading reading = read(classesRoot, family);
      Optional<Branching> worst = reading.worstMethod();
      said.append("  ")
          .append(family.glob())
          .append(": ")
          .append(family.classNames().size())
          .append(" classes, ")
          .append(reading.branches())
          .append('/')
          .append(family.branchBudget())
          .append(" conditional jumps, densest method ")
          .append(worst.map(Branching::branches).orElse(0))
          .append('/')
          .append(family.methodBudget())
          .append(worst.map(w -> " (" + w.owner() + ")").orElse(NOTHING))
          .append('\n');
    }
    return said.toString();
  }

  public static void main(String[] args) {
    Path classesRoot = Path.of(args[0]);
    List<Family> families = new ArrayList<>();
    for (int at = 1; at < args.length; at++) {
      families.add(Family.parse(args[at]));
    }
    List<String> overruns = overruns(classesRoot, families);
    if (!overruns.isEmpty()) {
      StringBuilder failure = new StringBuilder();
      failure.append(
          "`pitest.excludedClasses` hides these classes from mutation testing on the grounds "
              + "that they are thin wiring. A decision they carry is a decision nothing "
              + "measures, so each glob is held to the number of conditional jumps it carried "
              + "when this ratchet was written:\n");
      overruns.forEach(overrun -> failure.append("  - ").append(overrun).append('\n'));
      failure.append(
          "The ratchet does not ask for the existing ones back. It refuses a new one. Move the "
              + "decision into a class PIT mutates and delegate to it, or raise the number in "
              + "`mutationExclusionBudgets` and say in the commit that raises it what it "
              + "bought.\n");
      failure.append("Measured now:\n").append(report(classesRoot, families));
      System.out.println(failure);
      System.exit(1);
    }
    System.out.println(
        "verifyExclusionBudget: "
            + families.stream().mapToInt(family -> family.classNames().size()).sum()
            + " classes excluded from mutation testing, none over its family's ratchet:\n"
            + report(classesRoot, families));
  }
}
