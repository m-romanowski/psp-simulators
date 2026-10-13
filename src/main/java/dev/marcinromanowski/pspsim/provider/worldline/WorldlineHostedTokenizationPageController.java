package dev.marcinromanowski.pspsim.provider.worldline;

import java.nio.charset.StandardCharsets;
import org.jspecify.annotations.Nullable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
class WorldlineHostedTokenizationPageController {

  private final WorldlineHostedTokenizationCheckout checkout;

  WorldlineHostedTokenizationPageController(WorldlineHostedTokenizationCheckout checkout) {
    this.checkout = checkout;
  }

  @GetMapping(WorldlineHostedTokenizationPage.ROUTE)
  ResponseEntity<byte[]> open(
      @PathVariable(WorldlineHostedTokenizationPage.ID) String hostedTokenizationId) {
    return written(checkout.open(hostedTokenizationId));
  }

  @PostMapping(WorldlineHostedTokenizationPage.ROUTE)
  ResponseEntity<byte[]> submit(
      @PathVariable(WorldlineHostedTokenizationPage.ID) String hostedTokenizationId,
      @RequestParam(name = WorldlineHostedTokenizationPage.OUTCOME, required = false)
          @Nullable String outcome,
      @RequestParam(name = WorldlineHostedTokenizationPage.CARD, required = false)
          @Nullable String card) {
    return written(checkout.submit(hostedTokenizationId, outcome, card));
  }

  private static ResponseEntity<byte[]> written(
      WorldlineHostedTokenizationCheckout.Rendered rendered) {
    byte[] body = rendered.body().getBytes(StandardCharsets.UTF_8);
    return ResponseEntity.status(rendered.httpStatus())
        .header("Content-Type", WorldlineHostedTokenizationPage.CONTENT_TYPE)
        .headers(headers -> rendered.headers().forEach(headers::add))
        .contentLength(body.length)
        .body(body);
  }
}
