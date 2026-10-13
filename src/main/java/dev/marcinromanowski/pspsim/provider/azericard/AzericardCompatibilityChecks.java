package dev.marcinromanowski.pspsim.provider.azericard;

import dev.marcinromanowski.pspsim.kernel.ProviderId;
import dev.marcinromanowski.pspsim.kernel.clock.SimulatorClock;
import dev.marcinromanowski.pspsim.kernel.ids.IdGenerator;
import dev.marcinromanowski.pspsim.kernel.smoke.CheckJson;
import dev.marcinromanowski.pspsim.kernel.smoke.CheckRequest;
import dev.marcinromanowski.pspsim.kernel.smoke.CheckResponse;
import dev.marcinromanowski.pspsim.kernel.smoke.CheckTransport;
import dev.marcinromanowski.pspsim.kernel.smoke.ContractCheck;
import dev.marcinromanowski.pspsim.kernel.smoke.ContractExpectation;
import dev.marcinromanowski.pspsim.kernel.smoke.DeclaredCheck;
import dev.marcinromanowski.pspsim.kernel.smoke.Observed;
import dev.marcinromanowski.pspsim.kernel.smoke.Published;
import dev.marcinromanowski.pspsim.kernel.smoke.SmokeNamespace;
import dev.marcinromanowski.pspsim.kernel.smoke.VendorSample;
import dev.marcinromanowski.pspsim.kernel.smoke.VendorSamples;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.jspecify.annotations.Nullable;

public final class AzericardCompatibilityChecks {

  static final ProviderId AZERICARD = AzericardGateway.AZERICARD;

  static final String THE_GATEWAY_AUTHORIZES = "POST /cgi-bin/cgi_link TRTYPE=1";

  static final String THE_GATEWAY_REFUSES_A_MAC = "POST /cgi-bin/cgi_link with a wrong P_SIGN";

  static final String THE_GATEWAY_MISSES_A_FIELD = "POST /cgi-bin/cgi_link with no TERMINAL";

  static final String THE_GATEWAY_LOOKS_UP_A_STATUS = "POST /cgi-bin/cgi_link TRTYPE=90";

  static final String THE_TRAIL_RECORDS = "GET /__simulator/azericard/audit";

  private static final String ACCOUNT = "merchant-1";

  private static final String TERMINAL = "77777777";

  private static final String CGI_LINK = "/cgi-bin/cgi_link";

  private static final String THE_AUTHORIZATION_TRTYPE = "1";

  private static final String THE_STATUS_TRTYPE = "90";

  private static final String AMOUNT = "120.50";

  private static final String CURRENCY = "AZN";

  private static final String MERCHANT_URL = "https://merchant.example/azericard";

  private static final String A_MAC_NO_KEY_PRODUCED = "00";

  private static final int THE_PUBLISHED_KEY_FLOOR_IN_BITS = 2048;

  private static final int ORDER_DIGITS = 14;

  private static final int NONCE_DIGITS = 16;

  private static final int THE_MEDIA_TYPE = 0;

  private static final int THE_CHARSET = 1;

  private static final int THE_OPENING = 0;

  private static final int THE_MESSAGE = 1;

  private static final Pattern PARAGRAPH = Pattern.compile("<p>(.*?)</p>", Pattern.DOTALL);

  private static final String CONTENT_TYPE = "Content-Type";

  private static final String ACTION = "action";

  private static final String RC = "rc";

  private static final String DESC = "desc";

  private final CheckJson json;
  private final IdGenerator ids;
  private final SimulatorClock clock;
  private final VendorSamples azericard;
  private final VendorSamples simulator;

  public AzericardCompatibilityChecks(CheckJson json, IdGenerator ids, SimulatorClock clock) {
    this.json = json;
    this.ids = ids;
    this.clock = clock;
    this.azericard = VendorSamples.of("azericard");
    this.simulator = VendorSamples.of("simulator");
  }

  public List<ContractCheck> all() {
    return List.of(authorization(), refusedMac(), missingTerminal(), statusRefusal(), auditTrail());
  }

  private ContractCheck authorization() {
    VendorSample approved = azericard.sample("authorization-approved");
    return DeclaredCheck.applying(
        AZERICARD,
        THE_GATEWAY_AUTHORIZES,
        approved.provenance(),
        List.of(
            new ContractExpectation(
                "contentType", azericard.sample("authorization-content-type").header(CONTENT_TYPE)),
            new ContractExpectation("action", elementOf(approved, ACTION)),
            new ContractExpectation("rc", elementOf(approved, RC)),
            new ContractExpectation("desc", elementOf(approved, DESC))),
        transport -> {
          PrivateKey merchantKey = prepare(transport);
          CheckResponse held = transport.send(aSignedAuthorization(merchantKey));
          return theElementsOf(held);
        });
  }

  private ContractCheck refusedMac() {
    VendorSample thePublishedRefusal = azericard.sample("refused-p-sign");
    return DeclaredCheck.applying(
        AZERICARD,
        THE_GATEWAY_REFUSES_A_MAC,
        thePublishedRefusal.provenance(),
        List.of(
            new ContractExpectation("httpStatus", thePublishedRefusal.status()),
            new ContractExpectation("action", elementOf(thePublishedRefusal, ACTION)),
            new ContractExpectation("rc", elementOf(thePublishedRefusal, RC)),
            new ContractExpectation("desc", elementOf(thePublishedRefusal, DESC))),
        transport -> {
          prepare(transport);
          Map<String, String> form = anAuthorizationForm();
          form.put(AzericardFields.P_SIGN, A_MAC_NO_KEY_PRODUCED);
          CheckResponse refused = transport.send(CheckRequest.form("POST", CGI_LINK, form));
          Map<String, String> read = theElementsOf(refused);
          read.put("httpStatus", String.valueOf(refused.status()));
          return read;
        });
  }

  private ContractCheck missingTerminal() {
    VendorSample page = azericard.sample("missing-terminal");
    return DeclaredCheck.applying(
        AZERICARD,
        THE_GATEWAY_MISSES_A_FIELD,
        page.provenance(),
        List.of(
            new ContractExpectation("httpStatus", page.status()),
            new ContractExpectation("contentType", page.header(CONTENT_TYPE)),
            new ContractExpectation(
                "page.opening", page.body().map(text -> itemOf(linesOf(text), THE_OPENING))),
            new ContractExpectation(
                "page.message", page.body().map(text -> itemOf(linesOf(text), THE_MESSAGE)))),
        transport -> {
          prepare(transport);
          Map<String, String> form = anAuthorizationForm();
          form.remove(AzericardFields.TERMINAL);
          form.put(AzericardFields.P_SIGN, A_MAC_NO_KEY_PRODUCED);
          CheckResponse refused = transport.send(CheckRequest.form("POST", CGI_LINK, form));
          Map<String, String> read = new LinkedHashMap<>();
          read.put("httpStatus", String.valueOf(refused.status()));
          Observed.put(read, "contentType", refused.header(CONTENT_TYPE));
          List<String> paragraphs = paragraphsOf(refused.body());
          Observed.put(read, "page.opening", itemOf(paragraphs, THE_OPENING));
          Observed.put(read, "page.message", itemOf(paragraphs, THE_MESSAGE));
          return read;
        });
  }

  private ContractCheck statusRefusal() {
    VendorSample inquiry = azericard.sample("status-inquiry-refused");
    return DeclaredCheck.applying(
        AZERICARD,
        THE_GATEWAY_LOOKS_UP_A_STATUS,
        inquiry.provenance(),
        List.of(
            new ContractExpectation(
                "mediaType",
                inquiry.header(CONTENT_TYPE).map(type -> itemOf(partsOf(type), THE_MEDIA_TYPE))),
            new ContractExpectation(
                "charset",
                inquiry.header(CONTENT_TYPE).map(type -> itemOf(partsOf(type), THE_CHARSET))),
            new ContractExpectation("actionCode", inquiry.json("/actionCode")),
            new ContractExpectation("responseCode", inquiry.json("/responseCode")),
            new ContractExpectation("statusMsg", inquiry.json("/statusMsg")),
            new ContractExpectation("tr_type", inquiry.json("/tr_type"))),
        transport -> {
          prepare(transport);
          Map<String, String> form = new LinkedHashMap<>();
          form.put(AzericardFields.TERMINAL, TERMINAL);
          form.put(AzericardFields.TRTYPE, THE_STATUS_TRTYPE);
          form.put(AzericardFields.TRAN_TRTYPE, THE_AUTHORIZATION_TRTYPE);
          form.put(AzericardFields.ORDER, anOrder());
          form.put(AzericardFields.TIMESTAMP, aTimestamp());
          form.put(AzericardFields.NONCE, aNonce());
          form.put(AzericardFields.P_SIGN, A_MAC_NO_KEY_PRODUCED);
          CheckResponse refused = transport.send(CheckRequest.form("POST", CGI_LINK, form));
          Map<String, String> read = new LinkedHashMap<>();
          List<String> contentType = partsOf(refused.header(CONTENT_TYPE));
          Observed.put(read, "mediaType", itemOf(contentType, THE_MEDIA_TYPE));
          Observed.put(read, "charset", itemOf(contentType, THE_CHARSET));
          Observed.put(read, "actionCode", json.at(refused.body(), "/actionCode"));
          Observed.put(read, "responseCode", json.at(refused.body(), "/responseCode"));
          Observed.put(read, "statusMsg", json.at(refused.body(), "/statusMsg"));
          Observed.put(read, "tr_type", json.at(refused.body(), "/tr_type"));
          return read;
        });
  }

  private ContractCheck auditTrail() {
    VendorSample auditRecord = simulator.sample("audit-record");
    return DeclaredCheck.applying(
        AZERICARD,
        THE_TRAIL_RECORDS,
        auditRecord.provenance(),
        List.of(
            new ContractExpectation("audit.direction", auditRecord.json("/direction")),
            new ContractExpectation("audit.outcome", auditRecord.json("/outcome"))),
        transport -> {
          PrivateKey merchantKey = prepare(transport);
          transport.send(aSignedAuthorization(merchantKey));
          CheckResponse trail =
              transport.send(CheckRequest.get("/__simulator/azericard/audit?origin=PROVIDER_API"));
          Map<String, String> read = new LinkedHashMap<>();
          Observed.put(read, "audit.direction", json.at(trail.body(), "/0/direction"));
          Observed.put(read, "audit.outcome", json.at(trail.body(), "/1/outcome"));
          return read;
        });
  }

  private PrivateKey prepare(CheckTransport transport) {
    KeyPair pair = aKeyPair();
    transport.send(
        CheckRequest.json("POST", "/__simulator/azericard/reset", "{\"scope\":\"TEST_RUN\"}"));
    transport.send(
        CheckRequest.json(
            "PUT",
            "/__simulator/azericard/config",
            "{\"accounts\":[{\"accountId\":\""
                + ACCOUNT
                + "\",\"configuration\":{\""
                + AzericardTerminal.TERMINAL_PROPERTY
                + "\":\""
                + TERMINAL
                + "\",\""
                + AzericardTerminal.MERCHANT_KEY_PROPERTY
                + "\":\""
                + pemOf(AzericardKeys.X509_PUBLIC, pair.getPublic().getEncoded())
                + "\",\""
                + AzericardTerminal.GATEWAY_KEY_PROPERTY
                + "\":\""
                + pemOf(AzericardKeys.PKCS8_PRIVATE, pair.getPrivate().getEncoded())
                + "\"}}],\"webhooks\":[{\"accountId\":\""
                + ACCOUNT
                + "\",\"url\":\"http://127.0.0.1:1/smoke\"}]}"));
    return pair.getPrivate();
  }

  private CheckRequest aSignedAuthorization(PrivateKey merchantKey) {
    Map<String, String> form = anAuthorizationForm();
    form.put(
        AzericardFields.P_SIGN,
        AzericardSignature.sign(
            AzericardMac.sourceOf(
                form.get(AzericardFields.AMOUNT),
                form.get(AzericardFields.CURRENCY),
                form.get(AzericardFields.TERMINAL),
                form.get(AzericardFields.TRTYPE),
                form.get(AzericardFields.TIMESTAMP),
                form.get(AzericardFields.NONCE),
                form.get(AzericardFields.MERCH_URL)),
            merchantKey));
    return CheckRequest.form("POST", CGI_LINK, form);
  }

  private Map<String, String> anAuthorizationForm() {
    Map<String, String> form = new LinkedHashMap<>();
    form.put(AzericardFields.AMOUNT, AMOUNT);
    form.put(AzericardFields.CURRENCY, CURRENCY);
    form.put(AzericardFields.TERMINAL, TERMINAL);
    form.put(AzericardFields.TRTYPE, THE_AUTHORIZATION_TRTYPE);
    form.put(AzericardFields.ORDER, anOrder());
    form.put(AzericardFields.MERCH_URL, MERCHANT_URL);
    form.put(AzericardFields.MERCH_NAME, "Contract compatibility smoke check");
    form.put(AzericardFields.DESC, "contract compatibility smoke check");
    form.put(AzericardFields.BACKREF, "http://127.0.0.1:1/smoke");
    form.put(AzericardFields.TIMESTAMP, aTimestamp());
    form.put(AzericardFields.NONCE, aNonce());
    AzericardTestCard sentS2s = AzericardTestCard.FIRST;
    form.put(AzericardFields.CARD, sentS2s.pan());
    form.put(AzericardFields.EXP, sentS2s.expiryMonth());
    form.put(AzericardFields.EXP_YEAR, sentS2s.expiryYear());
    form.put(AzericardFields.CVC2, sentS2s.cvc());
    return form;
  }

  private String anOrder() {
    return ids.digits(ORDER_DIGITS);
  }

  private String aNonce() {
    return ids.hex(NONCE_DIGITS);
  }

  private String aTimestamp() {
    return AzericardTimestamp.write(clock.now(SmokeNamespace.RUN));
  }

  private Map<String, String> theElementsOf(CheckResponse answer) {
    Map<String, String> read = new LinkedHashMap<>();
    Observed.put(read, "contentType", answer.header(CONTENT_TYPE));
    Observed.put(read, "action", elementOf(answer.body(), ACTION));
    Observed.put(read, "rc", elementOf(answer.body(), RC));
    Observed.put(read, "desc", elementOf(answer.body(), DESC));
    return read;
  }

  private static Published elementOf(VendorSample sample, String name) {
    return sample.body().map(xml -> elementOf(xml, name));
  }

  private static List<String> linesOf(String text) {
    return text.lines().map(String::strip).toList();
  }

  private static List<String> partsOf(@Nullable String contentType) {
    if (contentType == null) {
      return List.of();
    }
    List<String> parts = new ArrayList<>();
    for (String part : contentType.split(";", -1)) {
      parts.add(part.strip());
    }
    return parts;
  }

  private static List<String> paragraphsOf(String html) {
    List<String> paragraphs = new ArrayList<>();
    Matcher paragraph = PARAGRAPH.matcher(html);
    while (paragraph.find()) {
      paragraphs.add(paragraph.group(1));
    }
    return paragraphs;
  }

  private static @Nullable String itemOf(List<String> found, int ordinal) {
    return ordinal < found.size() ? found.get(ordinal) : null;
  }

  private static @Nullable String elementOf(String xml, String name) {
    Matcher element =
        Pattern.compile("<" + name + ">(.*?)</" + name + ">", Pattern.DOTALL).matcher(xml);
    return element.find() ? element.group(1) : null;
  }

  private static KeyPair aKeyPair() {
    try {
      KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
      generator.initialize(THE_PUBLISHED_KEY_FLOOR_IN_BITS);
      return generator.generateKeyPair();
    } catch (NoSuchAlgorithmException unavailable) {
      throw new IllegalStateException(
          "a compatibility check of provider azericard needs RSA to sign a request P_SIGN with",
          unavailable);
    }
  }

  private static String pemOf(String label, byte[] der) {
    return "-----BEGIN "
        + label
        + "-----"
        + Base64.getEncoder().encodeToString(der)
        + "-----END "
        + label
        + "-----";
  }
}
