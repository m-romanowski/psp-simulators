package dev.marcinromanowski.pspsim.kernel.routing.internal;

import dev.marcinromanowski.pspsim.kernel.ProviderId;
import dev.marcinromanowski.pspsim.kernel.clock.SimulatorClock;
import dev.marcinromanowski.pspsim.kernel.routing.RouteAlias;
import dev.marcinromanowski.pspsim.kernel.routing.RouteAliasKey;
import dev.marcinromanowski.pspsim.kernel.routing.RouteAliases;
import dev.marcinromanowski.pspsim.kernel.testrun.TestRunId;
import dev.marcinromanowski.pspsim.kernel.transaction.ClaimedMeanwhileException;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.simple.JdbcClient;

public final class JdbcRouteAliases implements RouteAliases {

  public static final String IS_HELD_BY_ANOTHER_TEST_RUN =
      " names a URL this simulator has bound to another test run";

  private static final String COLUMNS =
      "select alias_key, provider, test_run_id, from_url, to_route from simulator_route_alias";

  private final JdbcClient jdbc;
  private final SimulatorClock clock;

  public JdbcRouteAliases(JdbcClient jdbc, SimulatorClock clock) {
    this.jdbc = jdbc;
    this.clock = clock;
  }

  @Override
  public void merge(ProviderId provider, TestRunId testRunId, List<RouteAlias> aliases) {
    OffsetDateTime updatedAt = clock.now(testRunId).atOffset(ZoneOffset.UTC);
    for (int index = 0; index < aliases.size(); index++) {
      RouteAlias alias = aliases.get(index);
      int bound =
          jdbc.sql(
                  """
                  insert into simulator_route_alias
                      (alias_key, provider, test_run_id, from_url, to_route, updated_at)
                  values (?, ?, ?, ?, ?, ?)
                  on conflict (alias_key) do update
                      set from_url = excluded.from_url,
                          to_route = excluded.to_route,
                          updated_at = excluded.updated_at
                      where simulator_route_alias.provider = excluded.provider
                        and simulator_route_alias.test_run_id = excluded.test_run_id
                  """)
              .param(alias.key().value())
              .param(provider.value())
              .param(testRunId.value())
              .param(alias.from())
              .param(alias.to())
              .param(updatedAt)
              .update();
      if (bound == 0) {
        String from = "routeAliases[" + index + "].from";
        throw new ClaimedMeanwhileException(from, from + IS_HELD_BY_ANOTHER_TEST_RUN);
      }
    }
  }

  @Override
  public boolean release(ProviderId provider, TestRunId testRunId, RouteAliasKey key) {
    return jdbc.sql(
                """
            delete from simulator_route_alias
            where alias_key = ? and provider = ? and test_run_id = ?
            """)
            .params(key.value(), provider.value(), testRunId.value())
            .update()
        == 1;
  }

  @Override
  public List<RouteAlias> of(ProviderId provider, TestRunId testRunId) {
    return jdbc.sql(COLUMNS + " where provider = ? and test_run_id = ? order by alias_key")
        .params(provider.value(), testRunId.value())
        .query((rs, row) -> aliasOf(rs))
        .list();
  }

  @Override
  public Optional<RouteAlias> boundTo(RouteAliasKey key) {
    return jdbc.sql(COLUMNS + " where alias_key = ?")
        .param(key.value())
        .query((rs, row) -> aliasOf(rs))
        .optional();
  }

  private static RouteAlias aliasOf(ResultSet row) throws SQLException {
    return new RouteAlias(
        ProviderId.of(row.getString("provider")),
        TestRunId.storedNamespace(row.getString("test_run_id")),
        storedKeyOf(row.getString("alias_key")),
        row.getString("from_url"),
        row.getString("to_route"));
  }

  private static RouteAliasKey storedKeyOf(String key) {
    int path = key.indexOf('/');
    return new RouteAliasKey(key.substring(0, path), key.substring(path));
  }
}
