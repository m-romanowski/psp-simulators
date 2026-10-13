package dev.marcinromanowski.pspsim.ui;

import java.util.Map;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
class ReadController {

  private final ReadPage page;

  ReadController(ReadPage page) {
    this.page = page;
  }

  @GetMapping(ReadPage.PATH)
  String reads(
      @RequestParam(required = false) @Nullable String provider,
      @RequestParam(required = false) @Nullable String testRunId,
      @RequestParam(required = false) @Nullable String accountId,
      @RequestParam(required = false) @Nullable String readNamed,
      @RequestParam Map<String, String> parameters,
      Model model) {
    return page.render(provider, testRunId, accountId, readNamed, parameters, model);
  }

  @GetMapping(ReadPage.ASK)
  String theAnswerToIt(
      @RequestParam(required = false) @Nullable String provider,
      @RequestParam(required = false) @Nullable String testRunId,
      @RequestParam(required = false) @Nullable String accountId,
      @RequestParam(required = false) @Nullable String readNamed,
      @RequestParam Map<String, String> parameters,
      Model model) {
    return page.answer(provider, testRunId, accountId, readNamed, parameters, model);
  }
}
