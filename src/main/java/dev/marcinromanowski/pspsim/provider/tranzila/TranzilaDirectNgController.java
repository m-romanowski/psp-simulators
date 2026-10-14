package dev.marcinromanowski.pspsim.provider.tranzila;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
class TranzilaDirectNgController {

  private final TranzilaDirectNg page;
  private final TranzilaDelivery delivery;

  TranzilaDirectNgController(TranzilaDirectNg page, TranzilaDelivery delivery) {
    this.page = page;
    this.delivery = delivery;
  }

  @GetMapping(TranzilaDirectNgPage.ROUTE)
  Object openedByAGet(
      @PathVariable(TranzilaDirectNgPage.TERMINAL) String terminal, HttpServletRequest request) {
    return delivery.delivered(page.open(TranzilaWireRequest.pageCallOf(request), terminal));
  }

  @PostMapping(TranzilaDirectNgPage.ROUTE)
  Object openedByAPost(
      @PathVariable(TranzilaDirectNgPage.TERMINAL) String terminal, HttpServletRequest request) {
    return delivery.delivered(page.open(TranzilaWireRequest.pageCallOf(request), terminal));
  }

  @PostMapping(TranzilaDirectNgPage.PAYER_ROUTE)
  Object submitted(
      @PathVariable(TranzilaDirectNgPage.TERMINAL) String terminal,
      @PathVariable(TranzilaDirectNgPage.PAGE) String pageId,
      HttpServletRequest request) {
    return delivery.delivered(
        page.submit(TranzilaWireRequest.pageCallOf(request), terminal, pageId));
  }
}
