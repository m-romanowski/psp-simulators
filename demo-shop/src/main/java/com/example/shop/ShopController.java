package com.example.shop;

import java.util.Arrays;
import java.util.Optional;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
class ShopController {

  private final Stories stories;
  private final Checkout checkout;
  private final StoryRuns runs;
  private final SimulatorScreens screens;

  ShopController(Stories stories, Checkout checkout, StoryRuns runs, SimulatorScreens screens) {
    this.stories = stories;
    this.checkout = checkout;
    this.runs = runs;
    this.screens = screens;
  }

  @ModelAttribute("screens")
  SimulatorScreens screens() {
    return screens;
  }

  @GetMapping("/")
  String home(Model model) {
    model.addAttribute("stories", Arrays.asList(Story.values()));
    model.addAttribute("runs", runs.newestFirst());
    model.addAttribute("product", Product.THE_ONE_WE_SELL);
    return "home";
  }

  @PostMapping("/stories/{slug}")
  String play(@PathVariable String slug) {
    StoryRun run = stories.play(Story.calledBy(slug));
    return "redirect:/runs/" + run.testRunId() + "/product";
  }

  @GetMapping("/runs/{testRunId}/product")
  String product(@PathVariable String testRunId, Model model) {
    return inTheRun(testRunId, "product", model);
  }

  @GetMapping("/runs/{testRunId}/checkout")
  String checkoutPage(@PathVariable String testRunId, Model model) {
    return inTheRun(testRunId, "checkout", model);
  }

  @PostMapping("/runs/{testRunId}/checkout")
  String pay(@PathVariable String testRunId) {
    checkout.buy(testRunId);
    return "redirect:/runs/" + testRunId + "/order";
  }

  @GetMapping("/runs/{testRunId}/order")
  String order(@PathVariable String testRunId, Model model) {
    return inTheRun(testRunId, "order", model);
  }

  @GetMapping("/runs/{testRunId}/order/live")
  ModelAndView orderAsItStandsNow(@PathVariable String testRunId, Model model) {
    Optional<StoryRun> kept = runs.keptAs(testRunId);
    if (kept.isEmpty()) {
      return new ModelAndView("fragments :: run-gone", HttpStatus.NOT_FOUND);
    }
    StoryRun run = kept.get();
    if (run.order().isEmpty()) {
      throw new NotAtThatStepYet(testRunId, "there is an order to show");
    }
    inTheRun(run, "order", model);
    return new ModelAndView("order :: live");
  }

  @PostMapping("/runs/{testRunId}/order/capture")
  String capture(@PathVariable String testRunId) {
    checkout.capture(testRunId);
    return "redirect:/runs/" + testRunId + "/order";
  }

  @PostMapping("/runs/{testRunId}/stripe-reports-the-finished-payment")
  String stripeReportsTheFinishedPayment(@PathVariable String testRunId) {
    stories.stripeReportsTheFinishedPayment(testRunId);
    return "redirect:/runs/" + testRunId + "/order";
  }

  @PostMapping("/tidy-up")
  String tidyUp(RedirectAttributes afterwards) {
    afterwards.addFlashAttribute("leftBehind", stories.tidyUp().leftBehind());
    return "redirect:/";
  }

  @ExceptionHandler({NoSuchStoryRun.class, NoSuchStory.class})
  @ResponseStatus(HttpStatus.NOT_FOUND)
  String notHere(RuntimeException missing, Model model) {
    model.addAttribute("problem", missing.getMessage());
    return "problem";
  }

  @ExceptionHandler(NotAtThatStepYet.class)
  @ResponseStatus(HttpStatus.CONFLICT)
  String notYet(NotAtThatStepYet early, Model model) {
    model.addAttribute("problem", early.getMessage());
    return "problem";
  }

  @ExceptionHandler({
    TheSimulatorRefused.class,
    TheSimulatorDidNotAnswer.class,
    ThePaymentProviderFailed.class
  })
  @ResponseStatus(HttpStatus.BAD_GATEWAY)
  String somethingBeyondTheShopFailed(RuntimeException failed, Model model) {
    model.addAttribute("problem", failed.getMessage());
    return "problem";
  }

  private String inTheRun(String testRunId, String view, Model model) {
    return inTheRun(runs.called(testRunId), view, model);
  }

  private String inTheRun(StoryRun run, String view, Model model) {
    model.addAttribute("run", run);
    model.addAttribute("product", Product.THE_ONE_WE_SELL);
    model.addAttribute("order", run.order().orElse(null));
    model.addAttribute("step", view);
    return view;
  }
}
