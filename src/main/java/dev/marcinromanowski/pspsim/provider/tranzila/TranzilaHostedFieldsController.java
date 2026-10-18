package dev.marcinromanowski.pspsim.provider.tranzila;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
class TranzilaHostedFieldsController {

  private final TranzilaHostedFields hostedFields;
  private final TranzilaDelivery delivery;

  TranzilaHostedFieldsController(TranzilaHostedFields hostedFields, TranzilaDelivery delivery) {
    this.hostedFields = hostedFields;
    this.delivery = delivery;
  }

  @GetMapping(TranzilaHostedFieldsPage.SCRIPT_ROUTE)
  ResponseEntity<byte[]> script() {
    return TranzilaDelivery.written(hostedFields.script());
  }

  @GetMapping(TranzilaHostedFieldsPage.FIELD_ROUTE)
  ResponseEntity<byte[]> field() {
    return TranzilaDelivery.written(hostedFields.field());
  }

  @GetMapping(TranzilaHostedFieldsPage.CONTROL_ROUTE)
  ResponseEntity<byte[]> control() {
    return TranzilaDelivery.written(hostedFields.control());
  }

  @PostMapping(TranzilaHostedFieldsPage.PROCESS_ROUTE)
  Object charged(HttpServletRequest request) {
    return delivery.delivered(hostedFields.charge(TranzilaWireRequest.pageCallOf(request)));
  }
}
