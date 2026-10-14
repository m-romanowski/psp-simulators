package dev.marcinromanowski.pspsim.provider.tranzila;

import dev.marcinromanowski.pspsim.kernel.spi.ThePayerStep;
import dev.marcinromanowski.pspsim.kernel.spi.WhatAPayerPageMayLoad;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

final class TranzilaHostedFieldsPage {

  static final String HOST = "/tranzila/hf";

  static final String SCRIPT_ROUTE = HOST + "/assets/js/thostedf.js";

  static final String FIELD_ROUTE = HOST + "/assets/js/hfields_depend/genfield.php";

  static final String CONTROL_ROUTE = HOST + "/assets/js/hfields_depend/controlfields.html";

  static final String PROCESS_ROUTE = HOST + "/process/ajax_process_transaction";

  static final ThePayerStep THE_FIELD_A_PAYER_TYPES_INTO =
      new ThePayerStep.APageThisSimulatorServes(FIELD_ROUTE);

  static final ThePayerStep THE_FRAME_THAT_CHARGES =
      new ThePayerStep.APageThisSimulatorServes(CONTROL_ROUTE);

  static final ThePayerStep THE_ROUTE_THE_CARD_IS_POSTED_TO =
      new ThePayerStep.APageThisSimulatorServes(PROCESS_ROUTE);

  static final String CONTENT_TYPE = "text/html; charset=utf-8";

  static final String THE_SCRIPT =
      "(function () {"
          + "var asked = new URLSearchParams(location.search);"
          + "var name = asked.get(\"field_name\") || \"\";"
          + "var instance = asked.get(\"instance_identifier\") || \"\";"
          + "var labels = {credit_card_number: \"Card number\", cvv: \"CVV\", expiry: \"Expiry\", card_holder_id_number: \"ID number\"};"
          + "var completes = {credit_card_number: \"cc-number\", cvv: \"cc-csc\", expiry: \"cc-exp\"};"
          + "var holder = document.getElementById(\"field\");"
          + "var controls = [];"
          + "var valid = null;"
          + "var brand = null;"
          + "function read(text) {"
          + "try {"
          + "return JSON.parse(text);"
          + "} catch (notJson) {"
          + "return null;"
          + "}"
          + "}"
          + "function tell(message) {"
          + "message.fieldName = name;"
          + "parent.postMessage(JSON.stringify(message), \"*\");"
          + "}"
          + "function share(value) {"
          + "var update = JSON.stringify({type: \"update\", fieldName: name, instance: instance, data: value});"
          + "for (var at = 0; at < parent.frames.length; at++) {"
          + "parent.frames[at].postMessage(update, location.origin);"
          + "}"
          + "}"
          + "function luhn(digits) {"
          + "var sum = 0;"
          + "for (var at = 0; at < digits.length; at++) {"
          + "var digit = Number(digits.charAt(digits.length - 1 - at));"
          + "if (at % 2 === 1) {"
          + "digit = digit * 2;"
          + "if (digit > 9) {"
          + "digit = digit - 9;"
          + "}"
          + "}"
          + "sum = sum + digit;"
          + "}"
          + "return sum % 10 === 0;"
          + "}"
          + "function brandOf(digits) {"
          + "if (!digits) {"
          + "return \"unknown\";"
          + "}"
          + "if (/^4/.test(digits)) {"
          + "return \"visa\";"
          + "}"
          + "if (/^3[47]/.test(digits)) {"
          + "return \"amex\";"
          + "}"
          + "if (/^3[068]/.test(digits)) {"
          + "return \"diners\";"
          + "}"
          + "if (/^(5[1-5]|2[2-7])/.test(digits)) {"
          + "return \"mastercard\";"
          + "}"
          + "if (/^(5[06-9]|6)/.test(digits)) {"
          + "return \"maestro\";"
          + "}"
          + "return \"isracard\";"
          + "}"
          + "function expiresLater(value) {"
          + "var parts = value.split(\"/\");"
          + "if (parts.length !== 2 || !/^[0-9]{1,2}$/.test(parts[0]) || !/^([0-9]{2}|[0-9]{4})$/.test(parts[1])) {"
          + "return false;"
          + "}"
          + "var month = Number(parts[0]);"
          + "var year = parts[1].length === 2 ? 2000 + Number(parts[1]) : Number(parts[1]);"
          + "var today = new Date();"
          + "var thisYear = today.getFullYear();"
          + "return month >= 1 && month <= 12 && (year > thisYear || (year === thisYear && month >= today.getMonth() + 1));"
          + "}"
          + "function judge(value) {"
          + "if (name === \"credit_card_number\") {"
          + "var digits = value.replace(/ /g, \"\");"
          + "return /^[0-9]{8,19}$/.test(digits) && luhn(digits);"
          + "}"
          + "if (name === \"cvv\") {"
          + "return /^[0-9]{3,4}$/.test(value);"
          + "}"
          + "if (name === \"card_holder_id_number\") {"
          + "return /^[0-9]{1,9}$/.test(value);"
          + "}"
          + "return name === \"expiry\" && expiresLater(value);"
          + "}"
          + "function current() {"
          + "if (controls.length === 2) {"
          + "return controls[0].value && controls[1].value ? controls[0].value + \"/\" + controls[1].value : \"\";"
          + "}"
          + "return controls[0].value;"
          + "}"
          + "function changed() {"
          + "var value = current();"
          + "share(name === \"credit_card_number\" ? value.replace(/ /g, \"\") : value);"
          + "var judged = judge(value);"
          + "if (judged !== valid) {"
          + "valid = judged;"
          + "tell({type: \"validityChange\", valid: judged});"
          + "}"
          + "if (name === \"credit_card_number\") {"
          + "var seen = brandOf(value.replace(/[^0-9]/g, \"\"));"
          + "if (seen !== brand) {"
          + "brand = seen;"
          + "tell({type: \"cardTypeChange\", cardType: seen});"
          + "}"
          + "}"
          + "}"
          + "function listened(control) {"
          + "control.addEventListener(\"focus\", function () {"
          + "tell({type: \"focus\"});"
          + "});"
          + "control.addEventListener(\"blur\", function () {"
          + "tell({type: \"blur\"});"
          + "});"
          + "control.addEventListener(\"keydown\", function (pressed) {"
          + "if (pressed.key === \"Enter\") {"
          + "pressed.preventDefault();"
          + "tell({type: \"submit\"});"
          + "}"
          + "});"
          + "holder.appendChild(control);"
          + "controls.push(control);"
          + "}"
          + "function anInput(label) {"
          + "var input = document.createElement(\"input\");"
          + "input.type = \"text\";"
          + "input.inputMode = \"numeric\";"
          + "input.autocomplete = completes[name] || \"off\";"
          + "input.placeholder = asked.get(\"placeholder\") || \"\";"
          + "input.setAttribute(\"aria-label\", label);"
          + "input.addEventListener(\"input\", changed);"
          + "listened(input);"
          + "}"
          + "function aChoice(label, first, last) {"
          + "var choice = document.createElement(\"select\");"
          + "choice.setAttribute(\"aria-label\", label);"
          + "var none = document.createElement(\"option\");"
          + "none.value = \"\";"
          + "none.textContent = label;"
          + "choice.appendChild(none);"
          + "for (var at = first; at <= last; at++) {"
          + "var option = document.createElement(\"option\");"
          + "option.value = at > 9 ? String(at) : \"0\" + at;"
          + "option.textContent = option.value;"
          + "choice.appendChild(option);"
          + "}"
          + "choice.addEventListener(\"change\", changed);"
          + "listened(choice);"
          + "}"
          + "function styled(styles) {"
          + "if (!styles || typeof styles !== \"object\") {"
          + "return;"
          + "}"
          + "controls.forEach(function (control) {"
          + "var rules = styles[control.tagName.toLowerCase()];"
          + "if (!rules || typeof rules !== \"object\") {"
          + "return;"
          + "}"
          + "Object.keys(rules).forEach(function (property) {"
          + "if (/^[a-z-]+$/.test(property)) {"
          + "control.style.setProperty(property, String(rules[property]));"
          + "}"
          + "});"
          + "});"
          + "}"
          + "if (name === \"expiry\" && asked.get(\"version\") === \"2\") {"
          + "var thisYear = new Date().getFullYear() % 100;"
          + "aChoice(\"Expiry month\", 1, 12);"
          + "aChoice(\"Expiry year\", thisYear, thisYear + 15);"
          + "} else {"
          + "anInput(labels[name] || name);"
          + "}"
          + "window.addEventListener(\"message\", function (arrived) {"
          + "if (arrived.source !== parent || typeof arrived.data !== \"string\") {"
          + "return;"
          + "}"
          + "var message = read(arrived.data);"
          + "if (message && message.type === \"applyStyles\") {"
          + "styled(message.data);"
          + "}"
          + "});"
          + "window.addEventListener(\"focus\", function () {"
          + "controls[0].focus();"
          + "});"
          + "share(\"\");"
          + "})();";

  static final String WHAT_A_FIELD_PERMITS =
      WhatAPayerPageMayLoad.NOTHING_BUT_THE_PAGES_OWN_STYLE
          + "; script-src 'sha256-23jK9lXHhnkwUTXXOCCNQUQxFvItoQmpsKOELlvNc18='";

  static final Map<String, List<String>> DEFENCE_HEADERS =
      WhatAPayerPageMayLoad.eachOnADocumentDeclaring(WHAT_A_FIELD_PERMITS);

  static final String TITLE = "Tranzila hosted field";

  static final String STYLE =
      "html,body{margin:0;padding:0;height:100%;background:transparent}"
          + "#field{display:flex;height:100%}"
          + "input,select{flex:1 1 0;min-width:0;box-sizing:border-box;height:100%;"
          + "min-height:2.5rem;margin:0;border:0;padding:0 .5rem;"
          + "font:16px/1.25 system-ui,sans-serif;color:#1b1b1b;background:transparent}";

  static final String THE_DOCUMENT =
      "<!DOCTYPE html><html lang=\"en\"><head><meta charset=\"utf-8\">"
          + "<meta name=\"viewport\" content=\"width=device-width, initial-scale=1\">"
          + "<title>"
          + TITLE
          + "</title><style>"
          + STYLE
          + "</style></head><body><div id=\"field\"></div><script>"
          + THE_SCRIPT
          + "</script></body></html>";

  private TranzilaHostedFieldsPage() {}

  static byte[] theDocument() {
    return THE_DOCUMENT.getBytes(StandardCharsets.UTF_8);
  }
}
