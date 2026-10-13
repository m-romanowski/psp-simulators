package dev.marcinromanowski.pspsim.platform;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;

import dev.marcinromanowski.pspsim.PostgresBackedTest;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.web.server.LocalServerPort;

class TheVersionThisSimulatorSaysItIsIntegrationTest extends PostgresBackedTest {

  private static final Path WHERE_THIS_BUILD_STATES_ITS_VERSION = Path.of("gradle.properties");

  private static final Pattern THE_LINE_THAT_STATES_IT =
      Pattern.compile("^version=(.*)$", Pattern.MULTILINE);

  private static final Pattern A_SEMANTIC_VERSION = Pattern.compile("[0-9]+\\.[0-9]+\\.[0-9]+");

  private static final Pattern THE_VERSION_THE_ANSWER_CARRIES =
      Pattern.compile("\"version\"\\s*:\\s*\"([^\"]+)\"");

  @LocalServerPort private int port;

  @Test
  void a_running_simulator_is_asked_which_version_it_is_and_answers_the_one_it_was_built_from() {
    String stated = theVersionThisBuildStates();

    String answered = theVersionTheRunningSimulatorSays();

    assertThat(answered)
        .as(
            "a tester who reports a defect and a suite that pins a behaviour both have to name"
                + " which build they met, and the only version either can read off a running"
                + " simulator is the one it says here; a build that stopped carrying it would"
                + " leave them naming the host they happened to reach")
        .isEqualTo(stated);
  }

  @Test
  void the_one_line_this_build_states_its_version_on_is_the_one_a_release_rewrites() {
    List<String> stating =
        THE_LINE_THAT_STATES_IT
            .matcher(whatThePropertiesFileHolds())
            .results()
            .map(found -> found.group(0))
            .toList();

    assertThat(stating)
        .as(
            "scripts/release.sh reads this version, bumps it and writes it back by rewriting the"
                + " one line that begins with version=, so a second such line or a version moved"
                + " into the build script would leave a release tagging one number and the jar"
                + " carrying another")
        .hasSize(1);
    assertThat(theVersionThisBuildStates())
        .as("and a release computes the next version from this one as three numbers")
        .matches(A_SEMANTIC_VERSION);
  }

  private String theVersionThisBuildStates() {
    Matcher stated = THE_LINE_THAT_STATES_IT.matcher(whatThePropertiesFileHolds());
    assertThat(stated.find())
        .as("%s states the version of this build", WHERE_THIS_BUILD_STATES_ITS_VERSION)
        .isTrue();
    return stated.group(1).strip();
  }

  private String whatThePropertiesFileHolds() {
    try {
      return Files.readString(WHERE_THIS_BUILD_STATES_ITS_VERSION);
    } catch (IOException unreadable) {
      throw new IllegalStateException(
          "the file this build states its version in was not readable at "
              + WHERE_THIS_BUILD_STATES_ITS_VERSION.toAbsolutePath(),
          unreadable);
    }
  }

  private String theVersionTheRunningSimulatorSays() {
    String answered = new String(answerTo("/actuator/info").body(), UTF_8);
    Matcher carried = THE_VERSION_THE_ANSWER_CARRIES.matcher(answered);
    assertThat(carried.find()).as("the answer /actuator/info gave: %s", answered).isTrue();
    return carried.group(1);
  }

  private HttpResponse<byte[]> answerTo(String path) {
    try (HttpClient client =
        HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build()) {
      return client.send(
          HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path)).GET().build(),
          HttpResponse.BodyHandlers.ofByteArray());
    } catch (IOException unreachable) {
      throw new IllegalStateException("the simulator did not answer " + path, unreachable);
    } catch (InterruptedException interrupted) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException("interrupted while asking for " + path, interrupted);
    }
  }
}
