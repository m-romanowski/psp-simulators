package dev.marcinromanowski.pspsim;

import static org.assertj.core.api.Assertions.assertThat;

import dev.marcinromanowski.pspsim.kernel.idempotency.ExchangeVerdict;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.web.client.RestClient;

public final class TheRequestsUnderOneKey {

  public record ARecordedRequest(int seq, String verdict) {}

  private TheRequestsUnderOneKey() {}

  public static List<ARecordedRequest> recordedUnderTheColumn(
      JdbcClient jdbc, String table, String keyColumn, String keyValue, String testRunId) {
    return jdbc.sql(
            "select seq, verdict from "
                + table
                + " where test_run_id = ? and "
                + keyColumn
                + " = ? order by seq")
        .params(testRunId, keyValue)
        .query(ARecordedRequest.class)
        .list();
  }

  public static List<ARecordedRequest> recordedUnderTheName(
      JdbcClient jdbc, String table, String keyName, String keyValue, String testRunId) {
    return jdbc.sql(
            "select seq, verdict from "
                + table
                + " where test_run_id = ? and key_name = ? and key_value = ? order by seq")
        .params(testRunId, keyName, keyValue)
        .query(ARecordedRequest.class)
        .list();
  }

  public static void areNumberedAfterTheOneThatClaimedTheKey(
      List<ARecordedRequest> recorded, int expected, Set<ExchangeVerdict> repeatsMayBe) {
    assertThat(recorded).as("every request sent under the key is recorded").hasSize(expected);
    List<Integer> sequences = new ArrayList<>();
    List<Integer> numbered = new ArrayList<>();
    for (int place = 0; place < recorded.size(); place++) {
      sequences.add(recorded.get(place).seq());
      numbered.add(place);
    }
    assertThat(sequences).as("the requests are numbered without a gap").isEqualTo(numbered);
    List<String> ran = new ArrayList<>();
    for (ARecordedRequest request : recorded) {
      ExchangeVerdict verdict = ExchangeVerdict.valueOf(request.verdict());
      if (verdict.couldHaveChangedState() || verdict.answersFromWhatTheRequestThatRanStored()) {
        ran.add(request.verdict());
      }
    }
    assertThat(ran)
        .as(
            "the earliest request that ran or replayed is the one that claimed the key: %s",
            recorded)
        .first()
        .isEqualTo(ExchangeVerdict.FIRST_REQUEST.name());
    List<String> names = new ArrayList<>();
    for (ExchangeVerdict verdict : repeatsMayBe) {
      names.add(verdict.name());
    }
    List<String> repeats = new ArrayList<>();
    boolean claimed = false;
    for (ARecordedRequest request : recorded) {
      if (claimed) {
        repeats.add(request.verdict());
      }
      claimed |= ExchangeVerdict.valueOf(request.verdict()).couldHaveChangedState();
    }
    assertThat(repeats)
        .as(
            "no request after the one that claimed the key is recorded as claiming it: %s",
            recorded)
        .allMatch(names::contains);
  }

  public static void isReadableOnTheInspector(RestClient http, String provider, String testRunId) {
    Integer answered =
        http.get()
            .uri("/__simulator/" + provider + "/idempotency?testRunId=" + testRunId)
            .exchange((request, response) -> response.getStatusCode().value());
    assertThat(answered)
        .as("the control API reads the exchanges back without refusing what was recorded")
        .isEqualTo(200);
  }
}
