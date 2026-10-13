package dev.marcinromanowski.pspsim.ui;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.stream.Stream;

final class TemplatesOneTestReadsFrom {

  private static final Path WHERE_THEY_ARE_SHIPPED =
      Path.of("src", "main", "resources", "templates");

  private TemplatesOneTestReadsFrom() {}

  static Path everyTemplateOfThisBuildWith(String folder, String contributedPage, String markup) {
    Path copy = Path.of("build", "tmp", folder, "templates");
    emptied(copy);
    try (Stream<Path> walked = Files.walk(WHERE_THEY_ARE_SHIPPED)) {
      for (Path template : walked.filter(Files::isRegularFile).toList()) {
        write(copy.resolve(WHERE_THEY_ARE_SHIPPED.relativize(template)), read(template));
      }
    } catch (IOException unreadable) {
      throw new UncheckedIOException(unreadable);
    }
    write(copy.resolve(contributedPage), markup);
    return copy;
  }

  static String asAPrefixForThymeleaf(Path templates) {
    return "file:" + templates.toAbsolutePath() + "/";
  }

  private static void emptied(Path folder) {
    if (!Files.exists(folder)) {
      return;
    }
    try (Stream<Path> walked = Files.walk(folder)) {
      walked.sorted(Comparator.reverseOrder()).forEach(TemplatesOneTestReadsFrom::delete);
    } catch (IOException unreadable) {
      throw new UncheckedIOException(unreadable);
    }
  }

  private static String read(Path template) {
    try {
      return Files.readString(template);
    } catch (IOException unreadable) {
      throw new UncheckedIOException(unreadable);
    }
  }

  private static void write(Path where, String markup) {
    try {
      Files.createDirectories(where.getParent());
      Files.writeString(where, markup, StandardCharsets.UTF_8);
    } catch (IOException unwritable) {
      throw new UncheckedIOException(unwritable);
    }
  }

  private static void delete(Path what) {
    try {
      Files.delete(what);
    } catch (IOException undeletable) {
      throw new UncheckedIOException(undeletable);
    }
  }
}
