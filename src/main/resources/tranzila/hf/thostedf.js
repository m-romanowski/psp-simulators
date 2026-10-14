var TzlaHostedFields = (function () {
  var theScript = "/assets/js/thostedf.js";
  var theFields = ["credit_card_number", "cvv", "expiry", "card_holder_id_number"];
  var optional = {cvv: true, expiry: true, card_holder_id_number: true};
  var titles = {credit_card_number: "Card number", cvv: "CVV", expiry: "Expiry", card_holder_id_number: "ID number"};
  var frameStyle = "border: none; width: 100%; height: 100%; float: left; overflow: hidden;";
  var challengeHolderStyle = "position: fixed; transform: translate(-50%, -50%); z-index: 1000000; background-color: #fff; top: 50%; left: 50%; width: 410px; max-width: 100vw; height: 650px; max-height: 100vh; border: none; box-sizing: border-box;";
  var challengeFrameStyle = "width: 400px; max-width: 100%; height: 650px; max-height: calc(100% - 44px); border: none;";
  var challengeCloseStyle = "display: block; margin-left: auto; min-width: 44px; min-height: 44px; border: none; background: transparent; color: #6b6b6b; font-size: 28px; font-weight: bold; cursor: pointer;";

  function whereItWasLoadedFrom() {
    var current = document.currentScript;
    if (current && current.src && current.src.indexOf(theScript) >= 0) {
      return current.src;
    }
    var scripts = document.getElementsByTagName("script");
    for (var at = 0; at < scripts.length; at++) {
      if (scripts[at].src && scripts[at].src.indexOf(theScript) >= 0) {
        return scripts[at].src;
      }
    }
    return location.href;
  }

  var loadedFrom = whereItWasLoadedFrom();
  var base = loadedFrom.indexOf(theScript) >= 0 ? loadedFrom.substring(0, loadedFrom.indexOf(theScript)) : "";
  var origin = new URL(base || location.href, location.href).origin;

  function now() {
    return Number(new Date());
  }

  function read(text) {
    try {
      return JSON.parse(text);
    } catch (notJson) {
      return null;
    }
  }

  function aHash(value) {
    return value !== null && typeof value === "object" && value.constructor === Object;
  }

  function demand(condition, refusal) {
    if (!condition) {
      throw refusal;
    }
  }

  function checked(config) {
    demand(aHash(config), "Hosted fields config must be a hash");
    demand(config.fields, "Config fields is required");
    demand(aHash(config.fields), "Config fields must be a hash");
    demand(Object.keys(config.fields).length >= 1, "Config fields must have at least 1 key, " + theFields);
    theFields.forEach(function (id) {
      if (optional[id] && !config.fields[id]) {
        return;
      }
      demand(config.fields[id], "Config fields must have key :: " + id);
      demand(config.fields[id].selector, "Config fields." + id + ".selector must be present");
      demand(typeof config.fields[id].selector === "string", "Config fields." + id + ".selector must be a string");
      var matched = document.querySelectorAll(config.fields[id].selector).length;
      demand(matched >= 1, "Selector for field '" + id + "' matches " + matched + " elements, must match 1 element");
    });
    return config;
  }

  function anInstanceIdentifier() {
    var drawn = new Uint32Array(2);
    crypto.getRandomValues(drawn);
    return drawn[0].toString(16) + drawn[1].toString(16);
  }

  function HostedFields(config) {
    var fields = checked(config).fields;
    var styles = config.styles || {};
    var instance = anInstanceIdentifier();
    var callbacks = {};
    var frames = {};
    var loaded = {};
    var control = null;
    var challenge = null;
    var requests = 0;
    var self = this;

    function trigger(event) {
      if (event.type === "focus") {
        var errors = document.getElementById("errors_for_" + event.field);
        if (errors) {
          errors.textContent = "";
        }
      }
      (callbacks[event.type] || []).forEach(function (callback) {
        callback(event);
      });
    }

    function marked(fieldName, valid) {
      var container = fields[fieldName] && document.querySelector(fields[fieldName].selector);
      if (!container) {
        return;
      }
      container.classList.toggle("hosted-fields-valid", valid === true);
      container.classList.toggle("hosted-fields-invalid", valid !== true);
    }

    function oneOfItsFrames(source) {
      if (control && source === control.contentWindow) {
        return true;
      }
      return Object.keys(frames).some(function (id) {
        return frames[id].contentWindow === source;
      });
    }

    function aFrame(id, src, hidden) {
      var frame = document.createElement("iframe");
      frame.id = "tranzi." + id;
      frame.src = src;
      frame.title = titles[id] || "Tranzila hosted fields";
      frame.setAttribute("frameborder", "0");
      frame.setAttribute("scrolling", "no");
      frame.style.cssText = hidden ? "display: none;" : frameStyle;
      return frame;
    }

    function theFieldAddress(id, declared) {
      var address = base + "/assets/js/hfields_depend/genfield.php?field_name=" + encodeURIComponent(id) + "&instance_identifier=" + instance;
      if (declared.placeholder) {
        address += "&placeholder=" + encodeURIComponent(declared.placeholder);
      }
      if (declared.label) {
        address += "&label=" + encodeURIComponent(declared.label);
      }
      if (config.autocomplete) {
        address += "&autocomplete=" + encodeURIComponent(String(config.autocomplete));
      }
      if (declared.version) {
        address += "&version=" + encodeURIComponent(declared.version);
      }
      return address;
    }

    function focusedByItsLabel(frame, id, declared) {
      var labels = declared.labelSelector
        ? document.querySelectorAll(declared.labelSelector)
        : document.querySelectorAll("label[for=\"" + declared.selector.replace("#", "") + "\"]");
      labels.forEach(function (label) {
        label.addEventListener("click", function () {
          frame.contentWindow.focus();
        });
      });
    }

    function whenLoaded(id) {
      loaded[id] = true;
      var everyOne = Object.keys(loaded).every(function (named) {
        return loaded[named];
      });
      if (everyOne) {
        trigger({type: "ready", timestamp: now()});
      }
    }

    function closeTheChallenge() {
      if (challenge) {
        challenge.remove();
        challenge = null;
      }
    }

    function showTheChallenge(address) {
      var shown;
      try {
        shown = new URL(address, location.href);
      } catch (notAnAddress) {
        return;
      }
      if (shown.origin !== origin) {
        return;
      }
      closeTheChallenge();
      var holder = document.createElement("div");
      var close = document.createElement("button");
      var frame = document.createElement("iframe");
      holder.id = "hf-challenge-cont";
      holder.className = "hf-challenge-cont";
      holder.style.cssText = challengeHolderStyle;
      close.type = "button";
      close.textContent = "\u00d7";
      close.setAttribute("aria-label", "Close the verification");
      close.style.cssText = challengeCloseStyle;
      close.addEventListener("click", closeTheChallenge);
      frame.id = "hf-challenge-frame";
      frame.title = "3-D Secure verification";
      frame.src = shown.href;
      frame.style.cssText = challengeFrameStyle;
      holder.appendChild(close);
      holder.appendChild(frame);
      document.body.appendChild(holder);
      challenge = holder;
    }

    function insertTheFields() {
      Object.keys(fields).forEach(function (id) {
        loaded[id] = false;
      });
      Object.keys(fields).forEach(function (id) {
        var declared = fields[id];
        var container = document.querySelector(declared.selector);
        var frame = aFrame(id, theFieldAddress(id, declared), false);
        if (declared.tabindex !== undefined) {
          frame.tabIndex = declared.tabindex;
        }
        container.classList.add(declared.version === "2" ? "container-for-dmy-hostedfield" : "container-for-hostedfield");
        frame.addEventListener("load", function () {
          frame.contentWindow.postMessage(JSON.stringify({type: "applyStyles", data: styles}), origin);
          focusedByItsLabel(frame, id, declared);
          whenLoaded(id);
        });
        frames[id] = frame;
        container.appendChild(frame);
      });
    }

    window.addEventListener("message", function (arrived) {
      if (arrived.origin !== origin || typeof arrived.data !== "string" || !oneOfItsFrames(arrived.source)) {
        return;
      }
      var message = read(arrived.data);
      if (!message || typeof message.type !== "string") {
        return;
      }
      if (message.type === "showChallenge") {
        if (control && arrived.source === control.contentWindow && message.response && typeof message.response.challengeUrl === "string") {
          showTheChallenge(message.response.challengeUrl);
        }
      } else if (message.type === "chargeResponse") {
        closeTheChallenge();
        (callbacks["charge." + message.requestId] || []).forEach(function (callback) {
          callback(message.err, message.response);
        });
      } else if (message.type === "validityChange") {
        marked(message.fieldName, message.valid);
        trigger({type: "validityChange", field: message.fieldName, isValid: message.valid, timestamp: now()});
      } else if (message.type === "cardTypeChange") {
        trigger({type: "cardTypeChange", cardType: message.cardType, timestamp: now()});
      } else {
        trigger({type: message.type, field: message.fieldName, timestamp: now()});
      }
    });

    self.onEvent = function (type, callback) {
      (callbacks[type] = callbacks[type] || []).push(callback);
    };

    self.charge = function (params, callback) {
      demand(aHash(params), "Parameters to send must be a hash");
      demand(typeof callback === "function", "Charge callback must be a function");
      requests = requests + 1;
      var requestId = requests;
      callbacks["charge." + requestId] = [callback];
      if (!control) {
        setTimeout(function () {
          callback({error: "network_error", error_description: "the hosted fields have not loaded yet"}, null);
        }, 0);
        return;
      }
      control.contentWindow.postMessage(JSON.stringify({type: "charge", params: params, sandbox: config.sandbox === true, requestId: requestId}), origin);
    };

    var starting = aFrame("control", base + "/assets/js/hfields_depend/controlfields.html?instance_identifier=" + instance, true);
    starting.addEventListener("load", function () {
      control = starting;
      insertTheFields();
    });
    document.body.appendChild(starting);
  }

  return {
    create: function (config) {
      return new HostedFields(config);
    },
    nowDT: now
  };
})();
