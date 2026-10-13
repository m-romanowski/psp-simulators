package dev.marcinromanowski.pspsim.ui;

import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
class ContributedScreenController {

  static final String UNDER = "/ui/x/";

  private static final String PATH = UNDER + "{provider}/{screen}";

  private final ContributedPage page;

  ContributedScreenController(ContributedPage page) {
    this.page = page;
  }

  @GetMapping(PATH)
  String contributedScreen(
      @PathVariable String provider,
      @PathVariable String screen,
      @RequestParam(required = false) @Nullable String testRunId,
      Model model) {
    return page.render(provider, screen, testRunId, model);
  }
}
