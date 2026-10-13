package com.example.tester.ui;

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
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.stream.Stream;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

public final class SimulatorUnderTest {

  private static final String POSTGRES_IMAGE = "postgres:18.6-alpine";

  private static final Duration READINESS_DEADLINE = Duration.ofMinutes(3);

  private static final Duration POLL = Duration.ofMillis(250);

  private static final int LAST_LINES_OF_THE_LOG = 40;

  private static SimulatorUnderTest started;

  private final String baseUrl;
  private final boolean ownedByThisSuite;
  private final Process process;
  private final PostgreSQLContainer<?> database;
  private final Path log;

  private SimulatorUnderTest(
      String baseUrl,
      boolean ownedByThisSuite,
      Process process,
      PostgreSQLContainer<?> database,
      Path log) {
    this.baseUrl = baseUrl;
    this.ownedByThisSuite = ownedByThisSuite;
    this.process = process;
    this.database = database;
    this.log = log;
  }

  public static synchronized SimulatorUnderTest running() {
    if (started == null) {
      started =
          addressGivenToTheSuite()
              .map(SimulatorUnderTest::alreadyRunningAt)
              .orElseGet(SimulatorUnderTest::bootedByThisSuite);
      Runtime.getRuntime().addShutdownHook(new Thread(started::stop, "stop-simulator"));
    }
    return started;
  }

  public String baseUrl() {
    return baseUrl;
  }

  private static Optional<String> addressGivenToTheSuite() {
    String property = System.getProperty("simulator.baseUrl");
    String environment = System.getenv("SIMULATOR_BASE_URL");
    String given = property != null && !property.isBlank() ? property : environment;
    return given == null || given.isBlank()
        ? Optional.empty()
        : Optional.of(given.replaceAll("/+$", ""));
  }

  private static SimulatorUnderTest alreadyRunningAt(String baseUrl) {
    SimulatorUnderTest given = new SimulatorUnderTest(baseUrl, false, null, null, null);
    if (!given.readinessAnswered()) {
      throw new IllegalStateException(
          "no simulator answered "
              + baseUrl
              + "/actuator/health/readiness with 200. This suite was told to use an already"
              + " running simulator, so it starts none of its own: either start one, or drop"
              + " simulator.baseUrl and SIMULATOR_BASE_URL and let the suite boot its own.");
    }
    return given;
  }

  private static SimulatorUnderTest bootedByThisSuite() {
    Path jar = theSimulatorJar();
    PostgreSQLContainer<?> database =
        new PostgreSQLContainer<>(DockerImageName.parse(POSTGRES_IMAGE))
            .withDatabaseName("pspsim")
            .withUsername("pspsim")
            .withPassword("pspsim");
    database.start();
    int port = aFreePort();
    String baseUrl = "http://127.0.0.1:" + port;
    Path log = logFileBeside(jar);
    Process process = launch(jar, database, port, baseUrl, log);
    SimulatorUnderTest booted = new SimulatorUnderTest(baseUrl, true, process, database, log);
    booted.waitForReadiness();
    return booted;
  }

  private static Process launch(
      Path jar, PostgreSQLContainer<?> database, int port, String baseUrl, Path log) {
    List<String> command =
        List.of(
            javaExecutable(),
            "-jar",
            jar.toString(),
            "--server.port=" + port,
            "--simulator.public-base-url=" + baseUrl,
            "--simulator.webhook.poll-interval=PT1H");
    ProcessBuilder builder = new ProcessBuilder(command);
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
      if (process != null && !process.isAlive()) {
        throw new IllegalStateException(
            "the simulator exited with status " + process.exitValue() + tail());
      }
      if (readinessAnswered()) {
        return;
      }
      sleep();
    }
    stop();
    throw new IllegalStateException(
        "the simulator did not report readiness within " + READINESS_DEADLINE + tail());
  }

  private boolean readinessAnswered() {
    try (HttpClient client =
        HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build()) {
      HttpResponse<Void> answer =
          client.send(
              HttpRequest.newBuilder(URI.create(baseUrl + "/actuator/health/readiness"))
                  .timeout(Duration.ofSeconds(10))
                  .GET()
                  .build(),
              HttpResponse.BodyHandlers.discarding());
      return answer.statusCode() == 200;
    } catch (IOException notYet) {
      return false;
    } catch (InterruptedException interrupted) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException("interrupted while asking the simulator for readiness");
    }
  }

  private void stop() {
    if (!ownedByThisSuite) {
      return;
    }
    if (process != null) {
      process.destroy();
      try {
        if (!process.waitFor(20, java.util.concurrent.TimeUnit.SECONDS)) {
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
      List<String> last =
          new ArrayList<>(
              lines.subList(Math.max(0, lines.size() - LAST_LINES_OF_THE_LOG), lines.size()));
      return ". The last lines it printed were:\n" + String.join("\n", last);
    } catch (IOException unreadable) {
      return ". Its log at " + log + " could not be read: " + unreadable.getMessage();
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
    Path repository = theRepositoryThisSuiteSitsIn();
    buildTheJar(repository);
    return newestJarUnder(repository.resolve("build/libs"))
        .orElseThrow(
            () ->
                new IllegalStateException(
                    "no runnable simulator jar appeared under "
                        + repository.resolve("build/libs")
                        + " after building it"));
  }

  private static Path theRepositoryThisSuiteSitsIn() {
    Path candidate = Path.of("").toAbsolutePath();
    while (candidate != null) {
      if (Files.isRegularFile(candidate.resolve("compose.yaml"))
          && Files.isRegularFile(candidate.resolve("src/main/resources/application.yaml"))) {
        return candidate;
      }
      candidate = candidate.getParent();
    }
    throw new IllegalStateException(
        "this suite could not find the simulator repository above "
            + Path.of("").toAbsolutePath()
            + ". Point it at a running simulator with -Dsimulator.baseUrl=http://host:port, or at"
            + " a runnable jar with -Dsimulator.jar=/path/to/psp-simulators.jar.");
  }

  private static Optional<Path> newestJarUnder(Path directory) {
    if (!Files.isDirectory(directory)) {
      return Optional.empty();
    }
    try (Stream<Path> files = Files.list(directory)) {
      return files
          .filter(file -> file.getFileName().toString().endsWith(".jar"))
          .filter(file -> !file.getFileName().toString().endsWith("-plain.jar"))
          .max(Comparator.comparing(SimulatorUnderTest::lastModified));
    } catch (IOException unreadable) {
      throw new IllegalStateException("could not list " + directory, unreadable);
    }
  }

  private static Instant lastModified(Path file) {
    try {
      return Files.getLastModifiedTime(file).toInstant();
    } catch (IOException unreadable) {
      return Instant.EPOCH;
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
        throw new IllegalStateException(
            "building the simulator with " + wrapper + " bootJar failed with status " + status);
      }
    } catch (IOException notLaunched) {
      throw new IllegalStateException("could not run " + wrapper + " bootJar", notLaunched);
    } catch (InterruptedException interrupted) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException("interrupted while building the simulator", interrupted);
    }
  }

  private static String javaExecutable() {
    return Path.of(System.getProperty("java.home"), "bin", "java").toString();
  }

  private static Path logFileBeside(Path jar) {
    Path directory = jar.getParent() == null ? Path.of(".") : jar.getParent();
    return directory.resolve("browser-e2e-simulator.log");
  }

  private static int aFreePort() {
    try (ServerSocket socket = new ServerSocket(0)) {
      return socket.getLocalPort();
    } catch (IOException none) {
      throw new IllegalStateException("no free port could be claimed for the simulator", none);
    }
  }

  private static void sleep() {
    try {
      Thread.sleep(POLL.toMillis());
    } catch (InterruptedException interrupted) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException("interrupted while waiting for the simulator", interrupted);
    }
  }
}
