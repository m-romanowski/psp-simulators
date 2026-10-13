package com.example.shop;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class TheLiveOrderTest {

  private static final Instant NOW = Instant.parse("2026-09-15T10:00:00Z");

  private final StoryRuns tidiedAwayRightAfterItIsLookedUp =
      new StoryRuns() {
        @Override
        public Optional<StoryRun> keptAs(String testRunId) {
          Optional<StoryRun> found = super.keptAs(testRunId);
          found.ifPresent(this::forget);
          return found;
        }
      };

  @Test
  @DisplayName(
      "a poll of a run tidied up just after the poll found it answers with the order it found")
  void aPollOfARunTidiedUpJustAfterItWasFoundAnswersWithTheOrderItFound() throws Exception {
    StoryRun run = new StoryRun("shop-run-1", Story.A_CAPTURE_THAT_GOES_THROUGH, NOW);
    Order order = run.ordered(new Order("order-shop-run-1", Product.THE_ONE_WE_SELL, "pi_1"));
    tidiedAwayRightAfterItIsLookedUp.keep(run);

    theShop()
        .perform(get("/runs/" + run.testRunId() + "/order/live"))
        .andExpect(status().isOk())
        .andExpect(view().name("order :: live"))
        .andExpect(model().attribute("run", run))
        .andExpect(model().attribute("order", order));
    assertThat(tidiedAwayRightAfterItIsLookedUp.keptAs(run.testRunId())).isEmpty();
  }

  private MockMvc theShop() {
    AnInMemorySimulator simulator = new AnInMemorySimulator();
    ShopSettings settings =
        new ShopSettings(
            "http://simulator:8080/",
            "http://localhost:8080",
            "http://shop:8081/",
            Duration.ofSeconds(2),
            Duration.ofSeconds(10));
    return MockMvcBuilders.standaloneSetup(
            new ShopController(
                new Stories(
                    simulator,
                    tidiedAwayRightAfterItIsLookedUp,
                    settings,
                    Clock.fixed(NOW, ZoneOffset.UTC)),
                new Checkout(simulator, tidiedAwayRightAfterItIsLookedUp),
                tidiedAwayRightAfterItIsLookedUp,
                new SimulatorScreens(settings)))
        .build();
  }
}
