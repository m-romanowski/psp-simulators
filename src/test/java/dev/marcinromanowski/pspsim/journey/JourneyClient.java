package dev.marcinromanowski.pspsim.journey;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;

final class JourneyClient {

  enum Kind {
    WRITE,
    READ
  }

  record Step(String what, String method, String path, int status, Kind kind) {}

  private static final ParameterizedTypeReference<Map<String, Object>> OBJECT =
      new ParameterizedTypeReference<>() {};

  private static final ParameterizedTypeReference<List<Map<String, Object>>> ARRAY =
      new ParameterizedTypeReference<>() {};

  private final RestClient http;
  private final RestClient reader;
  private final List<Step> steps = new ArrayList<>();
  private volatile String answeredAs = "";

  JourneyClient(int port) {
    String base = "http://localhost:" + port;
    this.http =
        RestClient.builder()
            .baseUrl(base)
            .requestFactory(aBrowserThatShowsItsRedirects())
            .defaultStatusHandler(HttpStatusCode::isError, (request, response) -> {})
            .build();
    this.reader =
        RestClient.builder()
            .baseUrl(base)
            .requestFactory(aBrowserThatShowsItsRedirects())
            .defaultStatusHandler(HttpStatusCode::isError, (request, response) -> {})
            .requestInterceptor(
                (request, body, execution) -> readAsJson(execution.execute(request, body)))
            .build();
  }

  private static JdkClientHttpRequestFactory aBrowserThatShowsItsRedirects() {
    return new JdkClientHttpRequestFactory(
        HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NEVER).build());
  }

  List<Step> steps() {
    return List.copyOf(steps);
  }

  long counted(Kind kind) {
    return steps.stream().filter(step -> step.kind() == kind).count();
  }

  String answeredAs() {
    return answeredAs;
  }

  Map<String, Object> json(String what, Kind kind, String method, String path, String body) {
    ResponseEntity<Map<String, Object>> answer =
        http.method(HttpMethod.valueOf(method))
            .uri(path)
            .contentType(MediaType.APPLICATION_JSON)
            .body(body)
            .retrieve()
            .toEntity(OBJECT);
    steps.add(new Step(what, method, path, answer.getStatusCode().value(), kind));
    return answer.getBody();
  }

  ResponseEntity<String> posted(
      String what, Kind kind, String path, String json, Map<String, String> headers) {
    ResponseEntity<String> answer =
        http.post()
            .uri(path)
            .contentType(MediaType.APPLICATION_JSON)
            .headers(sent -> headers.forEach(sent::add))
            .body(json)
            .retrieve()
            .toEntity(String.class);
    steps.add(new Step(what, "POST", path, answer.getStatusCode().value(), kind));
    return answer;
  }

  List<Map<String, Object>> getList(String what, String path) {
    ResponseEntity<List<Map<String, Object>>> answer =
        http.get().uri(path).retrieve().toEntity(ARRAY);
    steps.add(new Step(what, "GET", path, answer.getStatusCode().value(), Kind.READ));
    return answer.getBody();
  }

  ResponseEntity<String> page(String what, String path) {
    ResponseEntity<String> answer = http.get().uri(path).retrieve().toEntity(String.class);
    steps.add(new Step(what, "GET", path, answer.getStatusCode().value(), Kind.READ));
    NoWorkingPayerAddressOnAScreen.refuseAnAddressOfAPayerPageOn(path, answer.getBody());
    return answer;
  }

  ResponseEntity<String> page(String what, URI path) {
    ResponseEntity<String> answer = http.get().uri(path).retrieve().toEntity(String.class);
    steps.add(new Step(what, "GET", path.getRawPath(), answer.getStatusCode().value(), Kind.READ));
    NoWorkingPayerAddressOnAScreen.refuseAnAddressOfAPayerPageOn(
        theScreenRead(path), answer.getBody());
    return answer;
  }

  private static String theScreenRead(URI path) {
    return path.getRawQuery() == null
        ? path.getRawPath()
        : path.getRawPath() + "?" + path.getRawQuery();
  }

  Map<String, Object> get(String what, String path) {
    ResponseEntity<Map<String, Object>> answer = http.get().uri(path).retrieve().toEntity(OBJECT);
    steps.add(new Step(what, "GET", path, answer.getStatusCode().value(), Kind.READ));
    return answer.getBody();
  }

  Map<String, Object> multipart(String what, Kind kind, String path, String... namesAndValues) {
    MultiValueMap<String, String> fields = new LinkedMultiValueMap<>();
    for (int index = 0; index < namesAndValues.length; index += 2) {
      fields.add(namesAndValues[index], namesAndValues[index + 1]);
    }
    ResponseEntity<Map<String, Object>> answer =
        reader
            .post()
            .uri(path)
            .contentType(MediaType.MULTIPART_FORM_DATA)
            .body(fields)
            .retrieve()
            .toEntity(OBJECT);
    steps.add(new Step(what, "POST", path, answer.getStatusCode().value(), kind));
    return answer.getBody();
  }

  void discardTheTestRun(String provider, String testRunId) {
    http.post()
        .uri("/__simulator/" + provider + "/reset")
        .contentType(MediaType.APPLICATION_JSON)
        .body("{\"testRunId\":\"" + testRunId + "\"}")
        .retrieve()
        .toBodilessEntity();
  }

  ResponseEntity<String> form(
      String what, Kind kind, String path, MultiValueMap<String, String> fields) {
    ResponseEntity<String> answer =
        http.post()
            .uri(path)
            .contentType(MediaType.APPLICATION_FORM_URLENCODED)
            .body(fields)
            .retrieve()
            .toEntity(String.class);
    steps.add(new Step(what, "POST", path, answer.getStatusCode().value(), kind));
    return answer;
  }

  private ClientHttpResponse readAsJson(ClientHttpResponse answered) {
    MediaType sent = answered.getHeaders().getContentType();
    answeredAs = sent == null ? "" : sent.toString();
    HttpHeaders relabelled = new HttpHeaders();
    relabelled.putAll(answered.getHeaders());
    relabelled.setContentType(MediaType.APPLICATION_JSON);
    return new ClientHttpResponse() {

      @Override
      public HttpStatusCode getStatusCode() throws IOException {
        return answered.getStatusCode();
      }

      @Override
      public String getStatusText() throws IOException {
        return answered.getStatusText();
      }

      @Override
      public void close() {
        answered.close();
      }

      @Override
      public InputStream getBody() throws IOException {
        return answered.getBody();
      }

      @Override
      public HttpHeaders getHeaders() {
        return relabelled;
      }
    };
  }
}
