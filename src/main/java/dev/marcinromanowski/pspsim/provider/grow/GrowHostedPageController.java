package dev.marcinromanowski.pspsim.provider.grow;

import java.nio.charset.StandardCharsets;
import org.jspecify.annotations.Nullable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
class GrowHostedPageController {

  private final GrowHostedCheckout checkout;

  GrowHostedPageController(GrowHostedCheckout checkout) {
    this.checkout = checkout;
  }

  @GetMapping(GrowHostedPage.PATH)
  ResponseEntity<byte[]> open(
      @RequestParam(name = GrowHostedPage.LINK, required = false) @Nullable String link) {
    return written(checkout.open(link));
  }

  @PostMapping(GrowHostedPage.PATH)
  ResponseEntity<byte[]> submit(
      @RequestParam(name = GrowHostedPage.LINK, required = false) @Nullable String link,
      @RequestParam(name = GrowHostedPage.OUTCOME, required = false) @Nullable String outcome,
      @RequestParam(name = GrowHostedPage.CARD, required = false) @Nullable String card) {
    return written(checkout.submit(link, outcome, card));
  }

  private static ResponseEntity<byte[]> written(GrowHostedCheckout.Rendered rendered) {
    byte[] body = rendered.body().getBytes(StandardCharsets.UTF_8);
    ResponseEntity.BodyBuilder builder =
        ResponseEntity.status(rendered.httpStatus())
            .header("Content-Type", rendered.contentType())
            .headers(headers -> rendered.headers().forEach(headers::add));
    String location = rendered.location();
    if (location != null) {
      builder = builder.header("Location", location);
    }
    return builder.contentLength(body.length).body(body);
  }
}
