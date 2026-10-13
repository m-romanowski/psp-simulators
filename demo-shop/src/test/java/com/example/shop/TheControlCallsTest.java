package com.example.shop;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;

import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class TheControlCallsTest {

  private static final Duration A_READ_TIMEOUT = Duration.ofMillis(300);

  private final List<String> asked = new CopyOnWriteArrayList<>();
  private final CountDownLatch theTestIsOver = new CountDownLatch(1);
  private HttpServer simulator;

  @AfterEach
  void stopTheSimulator() {
    theTestIsOver.countDown();
    if (simulator != null) {
      simulator.stop(0);
    }
  }

  @Test
  @DisplayName(
      "removing a run's account deletes that account of that run, and a 200 is taken as done")
  void removingAnAccountDeletesItForTheRun() throws IOException {
    SimulatorControl control = aSimulatorAnswering(200);

    control.removeTheAccount("shop-1", StoryRun.ACCOUNT);

    assertThat(asked)
        .containsExactly("DELETE /__simulator/stripe/config/accounts/demo-shop?testRunId=shop-1");
  }

  @ParameterizedTest(name = "HTTP {0}")
  @ValueSource(ints = {204, 404, 500})
  @DisplayName(
      "an account removal the simulator did not answer with 200 is refused, not taken as done")
  void anAccountRemovalNotAnsweredWith200IsRefused(int status) throws IOException {
    SimulatorControl control = aSimulatorAnswering(status);

    assertThatThrownBy(() -> control.removeTheAccount("shop-1", StoryRun.ACCOUNT))
        .isInstanceOf(TheSimulatorRefused.class)
        .hasMessageContaining("HTTP " + status);
  }

  @Test
  @DisplayName("resetting a run posts that run's id to the reset route")
  void resettingARunPostsItsId() throws IOException {
    SimulatorControl control = aSimulatorAnswering(200);

    control.reset("shop-1");

    assertThat(asked).containsExactly("POST /__simulator/stripe/reset {\"testRunId\":\"shop-1\"}");
  }

  @Test
  @DisplayName("a control call the simulator never answers gives up after the read timeout")
  void aControlCallNeverAnsweredGivesUpAfterTheReadTimeout() throws IOException {
    SimulatorControl control = aSimulatorThatNeverAnswers();

    assertTimeoutPreemptively(
        A_READ_TIMEOUT.multipliedBy(10),
        () ->
            assertThatThrownBy(() -> control.reset("shop-1"))
                .isInstanceOf(TheSimulatorDidNotAnswer.class)
                .hasMessageContaining("reset shop-1"));
  }

  private SimulatorControl aSimulatorThatNeverAnswers() throws IOException {
    simulator = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
    simulator.createContext(
        "/",
        exchange -> {
          try {
            theTestIsOver.await(1, TimeUnit.MINUTES);
          } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
          }
          exchange.close();
        });
    simulator.start();
    return new SimulatorControlOverHttp(settingsFor(simulator));
  }

  private static ShopSettings settingsFor(HttpServer simulator) {
    String address = "http://127.0.0.1:" + simulator.getAddress().getPort();
    return new ShopSettings(address, address, address, Duration.ofSeconds(1), A_READ_TIMEOUT);
  }

  private SimulatorControl aSimulatorAnswering(int status) throws IOException {
    simulator = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
    simulator.createContext(
        "/",
        exchange -> {
          String body = new String(exchange.getRequestBody().readAllBytes());
          String query = exchange.getRequestURI().getRawQuery();
          asked.add(
              exchange.getRequestMethod()
                  + " "
                  + exchange.getRequestURI().getRawPath()
                  + (query == null ? "" : "?" + query)
                  + (body.isEmpty() ? "" : " " + body));
          exchange.sendResponseHeaders(status, -1);
          exchange.close();
        });
    simulator.start();
    return new SimulatorControlOverHttp(settingsFor(simulator));
  }
}
