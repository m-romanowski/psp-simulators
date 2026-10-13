package dev.marcinromanowski.pspsim.documentation;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.lang.classfile.Attributes;
import java.lang.classfile.ClassFile;
import java.lang.classfile.ClassModel;
import java.lang.classfile.attribute.RecordAttribute;
import java.lang.classfile.attribute.RecordComponentInfo;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public final class CompiledRecords {

  private final Path classesRoot;
  private final Map<String, Optional<List<RecordComponentInfo>>> read = new LinkedHashMap<>();

  public CompiledRecords(Path classesRoot) {
    this.classesRoot = classesRoot;
  }

  public static String readable(String className) {
    return className.replace('$', '.');
  }

  Optional<List<RecordComponentInfo>> componentsOf(String className) {
    return read.computeIfAbsent(className, this::parse);
  }

  boolean isRecord(String className) {
    return componentsOf(className).isPresent();
  }

  List<String> componentNames(String className) {
    List<String> names = new ArrayList<>();
    for (RecordComponentInfo component : componentsOf(className).orElseThrow()) {
      names.add(component.name().stringValue());
    }
    return List.copyOf(names);
  }

  RecordComponentInfo component(String className, String name) {
    for (RecordComponentInfo component : componentsOf(className).orElseThrow()) {
      if (component.name().stringValue().equals(name)) {
        return component;
      }
    }
    throw new IllegalArgumentException(className + " has no component " + name);
  }

  private Optional<List<RecordComponentInfo>> parse(String className) {
    Path compiled = classesRoot.resolve(className.replace('.', '/') + ".class");
    if (!Files.isRegularFile(compiled)) {
      return Optional.empty();
    }
    try {
      ClassModel model = ClassFile.of().parse(compiled);
      return model.findAttribute(Attributes.record()).map(RecordAttribute::components);
    } catch (IOException unreadable) {
      throw new UncheckedIOException(unreadable);
    }
  }
}
