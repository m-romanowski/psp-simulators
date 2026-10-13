package dev.marcinromanowski.pspsim.provider.autra;

import java.nio.charset.StandardCharsets;
import org.jspecify.annotations.Nullable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
class AutraCardPageController {

  private final PayerOnTheHostedCardPage page;

  AutraCardPageController(PayerOnTheHostedCardPage page) {
    this.page = page;
  }

  @GetMapping(AutraCardPage.PATH)
  ResponseEntity<byte[]> open(
      @PathVariable(AutraCardPage.SESSION) String hostedCardSessionId,
      @RequestParam(name = AutraCardPage.TOKEN, required = false) @Nullable String token) {
    return written(page.open(hostedCardSessionId, token));
  }

  @PostMapping(AutraCardPage.PATH)
  ResponseEntity<byte[]> submit(
      @PathVariable(AutraCardPage.SESSION) String hostedCardSessionId,
      @RequestParam(name = AutraCardPage.TOKEN, required = false) @Nullable String token,
      @RequestParam(name = AutraCardPage.OUTCOME, required = false) @Nullable String outcome,
      @RequestParam(name = AutraCardPage.CARD, required = false) @Nullable String card) {
    return written(page.submit(hostedCardSessionId, token, outcome, card));
  }

  private static ResponseEntity<byte[]> written(PayerOnTheHostedCardPage.Rendered rendered) {
    byte[] body = rendered.body().getBytes(StandardCharsets.UTF_8);
    return ResponseEntity.status(rendered.httpStatus())
        .header("Content-Type", AutraCardPage.CONTENT_TYPE)
        .headers(headers -> rendered.headers().forEach(headers::add))
        .contentLength(body.length)
        .body(body);
  }
}
