package dev.marcinromanowski.pspsim.documentation;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.lang.classfile.AccessFlags;
import java.lang.classfile.Attributes;
import java.lang.classfile.ClassFile;
import java.lang.classfile.ClassModel;
import java.lang.classfile.FieldModel;
import java.lang.classfile.TypeAnnotation;
import java.lang.classfile.attribute.RecordComponentInfo;
import java.lang.classfile.attribute.RuntimeVisibleTypeAnnotationsAttribute;
import java.lang.constant.ClassDesc;
import java.lang.reflect.AccessFlag;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.JsonNodeFactory;
import tools.jackson.databind.node.ObjectNode;

public final class JsonSchemaOfARecord {

  public static final String NULL = "null";

  private static final String NULLABLE = "org.jspecify.annotations.Nullable";

  private static final String SIMULATOR = "dev.marcinromanowski.pspsim.";

  private static final JsonNodeFactory NODES = JsonNodeFactory.instance;

  private final CompiledRecords records;
  private final Path classesRoot;
  private final Map<String, String> nameOfClass = new TreeMap<>();
  private final Map<String, String> classOfName = new TreeMap<>();
  private final Map<String, ObjectNode> schemaOfName = new TreeMap<>();

  public JsonSchemaOfARecord(CompiledRecords records, Path classesRoot) {
    this.records = records;
    this.classesRoot = classesRoot;
  }

  public static final class UnpublishableShape extends RuntimeException {

    private static final long serialVersionUID = 1L;

    UnpublishableShape(String message) {
      super(message);
    }
  }

  public String schemaNameOf(String recordClass) {
    String known = nameOfClass.get(recordClass);
    if (known != null) {
      return known;
    }
    String proposed = recordClass.substring(recordClass.lastIndexOf('$') + 1);
    proposed = proposed.substring(proposed.lastIndexOf('.') + 1);
    String taken = classOfName.get(proposed);
    if (taken != null && !taken.equals(recordClass)) {
      throw new UnpublishableShape(
          CompiledRecords.readable(recordClass)
              + " and "
              + CompiledRecords.readable(taken)
              + " both want to be published as the schema named "
              + proposed
              + ", and one document cannot carry two schemas under one name. Rename one of the"
              + " records, or teach JsonSchemaOfARecord.schemaNameOf a longer name for both.");
    }
    nameOfClass.put(recordClass, proposed);
    classOfName.put(proposed, recordClass);
    return proposed;
  }

  public ObjectNode referenceTo(String recordClass) {
    ObjectNode reference = NODES.objectNode();
    reference.put("$ref", "#/components/schemas/" + define(recordClass));
    return reference;
  }

  public Map<String, ObjectNode> schemasDefined() {
    return Map.copyOf(schemaOfName);
  }

  public Set<String> recordsDefined() {
    return Set.copyOf(nameOfClass.keySet());
  }

  private String define(String recordClass) {
    String name = schemaNameOf(recordClass);
    if (schemaOfName.containsKey(name)) {
      return name;
    }
    if (!records.isRecord(recordClass)) {
      throw new UnpublishableShape(
          CompiledRecords.readable(recordClass)
              + " is published as the body of a route and no compiled record of that name is under "
              + classesRoot
              + ", so the published shape would be invented rather than read.");
    }
    schemaOfName.put(name, NODES.objectNode());
    ObjectNode properties = NODES.objectNode();
    ArrayNode required = NODES.arrayNode();
    for (String component : records.componentNames(recordClass)) {
      RecordComponentInfo info = records.component(recordClass, component);
      boolean nullable = isNullable(info);
      properties.set(component, withNull(schemaOf(recordClass, component, info), nullable));
      if (!nullable) {
        required.add(component);
      }
    }
    ObjectNode schema = NODES.objectNode();
    schema.put("type", "object");
    schema.set("properties", properties);
    if (!required.isEmpty()) {
      schema.set("required", required);
    }
    schema.put("additionalProperties", false);
    schemaOfName.put(name, schema);
    return name;
  }

  private ObjectNode withNull(ObjectNode schema, boolean nullable) {
    if (!nullable) {
      return schema;
    }
    if (schema.has("$ref")) {
      ObjectNode union = NODES.objectNode();
      ArrayNode either = union.putArray("anyOf");
      either.add(schema);
      ObjectNode nothing = NODES.objectNode();
      nothing.put("type", NULL);
      either.add(nothing);
      return union;
    }
    String only = schema.get("type").stringValue();
    ArrayNode both = NODES.arrayNode();
    both.add(only);
    both.add(NULL);
    schema.set("type", both);
    return schema;
  }

  private ObjectNode schemaOf(String owner, String component, RecordComponentInfo info) {
    String signature =
        info.findAttribute(Attributes.signature())
            .map(attribute -> attribute.signature().stringValue())
            .orElseGet(() -> info.descriptorSymbol().descriptorString());
    return schemaOf(owner + "." + component, new Descriptors(signature).read());
  }

  private ObjectNode schemaOf(String where, Descriptors.JavaType type) {
    return switch (type) {
      case Descriptors.Primitive primitive -> primitiveSchema(where, primitive.code());
      case Descriptors.ArrayOf array -> arraySchema(where, array.element());
      case Descriptors.Variable variable ->
          unpublishable(where, "a type variable " + variable.name());
      case Descriptors.Parameterized named -> namedSchema(where, named);
    };
  }

  private ObjectNode arraySchema(String where, Descriptors.JavaType element) {
    if (element instanceof Descriptors.Primitive primitive && primitive.code() == 'B') {
      return unpublishable(
          where,
          "a byte array, whose media type and shape are decided at run time and not by any"
              + " compiled type");
    }
    ObjectNode array = NODES.objectNode();
    array.put("type", "array");
    array.set("items", schemaOf(where + "[]", element));
    return array;
  }

  private ObjectNode namedSchema(String where, Descriptors.Parameterized named) {
    String binary = named.binaryName();
    List<Descriptors.JavaType> arguments = named.arguments();
    if (binary.equals("java.lang.String") || binary.equals("java.lang.Character")) {
      return typed("string");
    }
    if (binary.equals("java.lang.Integer")
        || binary.equals("java.lang.Long")
        || binary.equals("java.lang.Short")
        || binary.equals("java.lang.Byte")) {
      return typed("integer");
    }
    if (binary.equals("java.lang.Boolean")) {
      return typed("boolean");
    }
    if (binary.equals("java.lang.Double")
        || binary.equals("java.lang.Float")
        || binary.equals("java.math.BigDecimal")) {
      return typed("number");
    }
    if (binary.equals("java.time.Instant")) {
      ObjectNode moment = typed("string");
      moment.put("format", "date-time");
      return moment;
    }
    if (binary.equals("java.util.List") || binary.equals("java.util.Set")) {
      if (arguments.size() != 1) {
        return unpublishable(where, binary + " with no element type");
      }
      ObjectNode array = NODES.objectNode();
      array.put("type", "array");
      array.set("items", schemaOf(where + "[]", arguments.get(0)));
      return array;
    }
    if (binary.equals("java.util.Map")) {
      if (arguments.size() != 2) {
        return unpublishable(where, binary + " with no key and value types");
      }
      if (!(arguments.get(0) instanceof Descriptors.Parameterized key)
          || !key.binaryName().equals("java.lang.String")) {
        return unpublishable(where, "a map keyed by something other than a string");
      }
      ObjectNode keyed = NODES.objectNode();
      keyed.put("type", "object");
      keyed.set("additionalProperties", schemaOf(where + ".*", arguments.get(1)));
      return keyed;
    }
    if (!binary.startsWith(SIMULATOR)) {
      return unpublishable(where, binary);
    }
    if (records.isRecord(binary)) {
      return referenceTo(binary);
    }
    List<String> constants = constantsOf(binary);
    if (!constants.isEmpty()) {
      ObjectNode constant = typed("string");
      ArrayNode values = constant.putArray("enum");
      constants.forEach(values::add);
      return constant;
    }
    return unpublishable(where, binary + ", which is neither a record nor an enum of this build");
  }

  private ObjectNode primitiveSchema(String where, char code) {
    return switch (code) {
      case 'I', 'J', 'S', 'B' -> typed("integer");
      case 'Z' -> typed("boolean");
      case 'D', 'F' -> typed("number");
      case 'C' -> typed("string");
      default -> unpublishable(where, "the primitive type " + code);
    };
  }

  private static ObjectNode typed(String type) {
    ObjectNode schema = NODES.objectNode();
    schema.put("type", type);
    return schema;
  }

  private ObjectNode unpublishable(String where, String what) {
    throw new UnpublishableShape(
        where
            + " is served as "
            + what
            + ", and this generator publishes only what it can read out of a compiled type: a"
            + " string, a number, a boolean, an instant, a list, a string-keyed map, an enum of"
            + " this build or another record of this build. Rather than publish a shape it would"
            + " have to invent, it refuses. Change the component to a shape it reads, or take the"
            + " route out of the control document and publish it as a provider route, whose body"
            + " is declared untyped and cited to the document that specifies it.");
  }

  private static boolean isNullable(RecordComponentInfo info) {
    Optional<RuntimeVisibleTypeAnnotationsAttribute> annotated =
        info.findAttribute(Attributes.runtimeVisibleTypeAnnotations());
    if (annotated.isEmpty()) {
      return false;
    }
    for (TypeAnnotation annotation : annotated.get().annotations()) {
      if (binaryNameOf(annotation.annotation().classSymbol()).equals(NULLABLE)) {
        return true;
      }
    }
    return false;
  }

  private static String binaryNameOf(ClassDesc type) {
    return type.isClassOrInterface() ? type.packageName() + "." + type.displayName() : "";
  }

  private List<String> constantsOf(String binaryName) {
    Path compiled = classesRoot.resolve(binaryName.replace('.', '/') + ".class");
    if (!Files.isRegularFile(compiled)) {
      return List.of();
    }
    ClassModel model;
    try {
      model = ClassFile.of().parse(compiled);
    } catch (IOException unreadable) {
      throw new UncheckedIOException(unreadable);
    }
    List<String> constants = new ArrayList<>();
    for (FieldModel field : model.fields()) {
      AccessFlags flags = field.flags();
      if (flags.has(AccessFlag.ENUM)) {
        constants.add(field.fieldName().stringValue());
      }
    }
    return List.copyOf(constants);
  }

  static final class Descriptors {

    sealed interface JavaType {}

    record Primitive(char code) implements JavaType {}

    record ArrayOf(JavaType element) implements JavaType {}

    record Variable(String name) implements JavaType {}

    record Parameterized(String binaryName, List<JavaType> arguments) implements JavaType {}

    private final String text;
    private int at;

    Descriptors(String text) {
      this.text = text;
    }

    JavaType read() {
      JavaType type = next();
      if (at != text.length()) {
        throw new UnpublishableShape(
            text + " was read as a type and " + text.substring(at) + " was left over");
      }
      return type;
    }

    private JavaType next() {
      char code = text.charAt(at);
      if (code == '[') {
        at++;
        return new ArrayOf(next());
      }
      if (code == 'T') {
        int end = text.indexOf(';', at);
        String name = text.substring(at + 1, end);
        at = end + 1;
        return new Variable(name);
      }
      if (code != 'L') {
        at++;
        return new Primitive(code);
      }
      at++;
      StringBuilder name = new StringBuilder();
      while (text.charAt(at) != ';' && text.charAt(at) != '<') {
        name.append(text.charAt(at));
        at++;
      }
      List<JavaType> arguments = new ArrayList<>();
      if (text.charAt(at) == '<') {
        at++;
        while (text.charAt(at) != '>') {
          if (text.charAt(at) == '+' || text.charAt(at) == '-' || text.charAt(at) == '*') {
            at++;
            continue;
          }
          arguments.add(next());
        }
        at++;
      }
      at++;
      return new Parameterized(name.toString().replace('/', '.'), List.copyOf(arguments));
    }
  }
}
