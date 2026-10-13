package com.example.shop;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

final class SimulatorUnderTest {

  private static final String POSTGRES_IMAGE = "postgres:18.6-alpine";

  private static final Duration READINESS_DEADLINE = Duration.ofMinutes(3);

  private static final String THE_SIMULATORS_BUILD = "rootProject.name = \"psp-simulators\"";

  private static SimulatorUnderTest started;

  private final String baseUrl;
  private final Process process;
  private final PostgreSQLContainer database;
  private final Path log;

  private SimulatorUnderTest(
      String baseUrl, Process process, PostgreSQLContainer database, Path log) {
    this.baseUrl = baseUrl;
    this.process = process;
    this.database = database;
    this.log = log;
  }

  static synchronized SimulatorUnderTest running() {
    if (started == null) {
      started =
          addressGivenToTheWalk()
              .map(SimulatorUnderTest::alreadyRunningAt)
              .orElseGet(SimulatorUnderTest::bootedByTheWalk);
      Runtime.getRuntime().addShutdownHook(new Thread(started::stop, "stop-simulator"));
    }
    return started;
  }

  String baseUrl() {
    return baseUrl;
  }

  private static Optional<String> addressGivenToTheWalk() {
    String given = System.getProperty("simulator.baseUrl");
    return given == null || given.isBlank()
        ? Optional.empty()
        : Optional.of(given.replaceAll("/+$", ""));
  }

  private static SimulatorUnderTest alreadyRunningAt(String baseUrl) {
    SimulatorUnderTest given = new SimulatorUnderTest(baseUrl, null, null, null);
    if (!given.readinessAnswered()) {
      throw new IllegalStateException(
          "no simulator answered " + baseUrl + "/actuator/health/readiness with 200");
    }
    return given;
  }

  private static SimulatorUnderTest bootedByTheWalk() {
    Path jar = theSimulatorJar();
    PostgreSQLContainer database =
        new PostgreSQLContainer(DockerImageName.parse(POSTGRES_IMAGE))
            .withDatabaseName("pspsim")
            .withUsername("pspsim")
            .withPassword("pspsim");
    database.start();
    int port = aFreePort();
    String baseUrl = "http://127.0.0.1:" + port;
    Path log = logFileFor(jar);
    SimulatorUnderTest booted =
        new SimulatorUnderTest(baseUrl, launch(jar, database, port, baseUrl, log), database, log);
    booted.waitForReadiness();
    return booted;
  }

  private static Process launch(
      Path jar, PostgreSQLContainer database, int port, String baseUrl, Path log) {
    ProcessBuilder builder =
        new ProcessBuilder(
            Path.of(System.getProperty("java.home"), "bin", "java").toString(),
            "-jar",
            jar.toString(),
            "--server.port=" + port,
            "--simulator.public-base-url=" + baseUrl);
    builder.environment().put("SIMULATOR_DB_URL", database.getJdbcUrl());
    builder.environment().put("SIMULATOR_DB_USERNAME", database.getUsername());
    builder.environment().put("SIMULATOR_DB_PASSWORD", database.getPassword());
    builder.redirectErrorStream(true);
    builder.redirectOutput(log.toFile());
    try {
      return builder.start();
    } catch (IOException notLaunched) {
      throw new IllegalStateException("could not launch " + jar, notLaunched);
    }
  }

  private void waitForReadiness() {
    Instant deadline = Instant.now().plus(READINESS_DEADLINE);
    while (Instant.now().isBefore(deadline)) {
      if (!process.isAlive()) {
        throw new IllegalStateException(
            "the simulator exited with status " + process.exitValue() + tail());
      }
      if (readinessAnswered()) {
        return;
      }
      try {
        Thread.sleep(250);
      } catch (InterruptedException interrupted) {
        Thread.currentThread().interrupt();
        throw new IllegalStateException("interrupted waiting for the simulator", interrupted);
      }
    }
    stop();
    throw new IllegalStateException(
        "the simulator did not report readiness within " + READINESS_DEADLINE + tail());
  }

  private boolean readinessAnswered() {
    try (HttpClient client = HttpClient.newHttpClient()) {
      HttpResponse<Void> answer =
          client.send(
              HttpRequest.newBuilder(URI.create(baseUrl + "/actuator/health/readiness"))
                  .timeout(Duration.ofSeconds(5))
                  .build(),
              HttpResponse.BodyHandlers.discarding());
      return answer.statusCode() == 200;
    } catch (IOException notYet) {
      return false;
    } catch (InterruptedException interrupted) {
      Thread.currentThread().interrupt();
      return false;
    }
  }

  private void stop() {
    if (process != null) {
      process.destroy();
      try {
        if (!process.waitFor(20, TimeUnit.SECONDS)) {
          process.destroyForcibly();
        }
      } catch (InterruptedException interrupted) {
        Thread.currentThread().interrupt();
        process.destroyForcibly();
      }
    }
    if (database != null) {
      database.stop();
    }
  }

  private String tail() {
    if (log == null || !Files.exists(log)) {
      return "";
    }
    try {
      List<String> lines = Files.readAllLines(log);
      return ". The last lines it printed were:\n"
          + String.join("\n", lines.subList(Math.max(0, lines.size() - 40), lines.size()));
    } catch (IOException unreadable) {
      return ". Its log at " + log + " could not be read";
    }
  }

  private static Path theSimulatorJar() {
    String given = System.getProperty("simulator.jar");
    if (given != null && !given.isBlank()) {
      Path named = Path.of(given);
      if (!Files.isRegularFile(named)) {
        throw new IllegalStateException("simulator.jar was set to " + named + ", which is no file");
      }
      return named;
    }
    Path repository = theRepositoryThisShopSitsIn();
    buildTheJar(repository);
    Path libraries = repository.resolve("build/libs");
    try (Stream<Path> files = Files.list(libraries)) {
      return files
          .filter(file -> file.getFileName().toString().endsWith(".jar"))
          .filter(file -> !file.getFileName().toString().endsWith("-plain.jar"))
          .max(Comparator.comparing(file -> file.toFile().lastModified()))
          .orElseThrow(() -> new IllegalStateException("no boot jar under " + libraries));
    } catch (IOException unreadable) {
      throw new IllegalStateException("could not list " + libraries, unreadable);
    }
  }

  private static Path theRepositoryThisShopSitsIn() {
    Path candidate = Path.of("").toAbsolutePath();
    while (candidate != null) {
      if (isTheSimulatorsBuild(candidate.resolve("settings.gradle.kts"))) {
        return candidate;
      }
      candidate = candidate.getParent();
    }
    throw new IllegalStateException(
        "no simulator repository above "
            + Path.of("").toAbsolutePath()
            + ". Name a running simulator with -Dsimulator.baseUrl or a jar with -Dsimulator.jar.");
  }

  private static boolean isTheSimulatorsBuild(Path settings) {
    try {
      return Files.isRegularFile(settings)
          && Files.readString(settings).contains(THE_SIMULATORS_BUILD);
    } catch (IOException unreadable) {
      return false;
    }
  }

  private static void buildTheJar(Path repository) {
    String wrapper =
        System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win")
            ? "gradlew.bat"
            : "gradlew";
    ProcessBuilder builder =
        new ProcessBuilder(repository.resolve(wrapper).toString(), "bootJar", "--console=plain");
    builder.directory(repository.toFile());
    builder.inheritIO();
    try {
      int status = builder.start().waitFor();
      if (status != 0) {
        throw new IllegalStateException("building the simulator failed with status " + status);
      }
    } catch (IOException notLaunched) {
      throw new IllegalStateException("could not run " + wrapper + " bootJar", notLaunched);
    } catch (InterruptedException interrupted) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException("interrupted building the simulator", interrupted);
    }
  }

  private static Path logFileFor(Path jar) {
    try {
      return Files.createTempFile("demo-shop-simulator-", ".log");
    } catch (IOException unwritable) {
      throw new IllegalStateException("no log file could be made for " + jar, unwritable);
    }
  }

  static int aFreePort() {
    try (ServerSocket socket = new ServerSocket(0)) {
      return socket.getLocalPort();
    } catch (IOException none) {
      throw new IllegalStateException("no free port could be claimed", none);
    }
  }
}
