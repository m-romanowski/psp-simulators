package dev.marcinromanowski.pspsim.kernel.transaction;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.marcinromanowski.pspsim.PostgresBackedTest;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.simple.JdbcClient;

class UnitOfWorkIntegrationTest extends PostgresBackedTest {

  private static final String RUN = "unit-of-work-it";

  @Autowired private UnitOfWork unitOfWork;
  @Autowired private List<ClaimConstraints> declared;
  @Autowired private JdbcClient jdbc;

  @BeforeEach
  @AfterEach
  void forgetTheRun() {
    jdbc.sql("delete from grow_account where test_run_id = ?").param(RUN).update();
    jdbc.sql("delete from simulator_test_run where test_run_id = ?").param(RUN).update();
  }

  @Test
  void every_unique_index_of_a_provider_account_table_is_declared_a_claim_and_nothing_else_is() {
    Set<String> declaredClaims = new TreeSet<>();
    for (ClaimConstraints claims : declared) {
      declaredClaims.addAll(claims.names());
    }

    List<String> theUniqueIndexesOfTheAccountTables =
        jdbc.sql(
                """
                select candidate_index.relname
                from pg_index candidate
                join pg_class candidate_index on candidate_index.oid = candidate.indexrelid
                where candidate_index.relnamespace = current_schema()::regnamespace
                  and candidate.indisunique
                  and not candidate.indisprimary
                  and candidate.indrelid in (
                      select primary_key.indrelid
                      from pg_index primary_key
                      join pg_attribute keyed
                        on keyed.attrelid = primary_key.indrelid
                       and keyed.attnum = any(primary_key.indkey)
                      where primary_key.indisprimary
                      group by primary_key.indrelid
                      having array_agg(keyed.attname::text order by keyed.attname)
                             = array['account_id', 'test_run_id'])
                """)
            .query(String.class)
            .list();

    assertThat(theUniqueIndexesOfTheAccountTables).isNotEmpty();
    assertThat(declaredClaims)
        .containsExactlyInAnyOrderElementsOf(theUniqueIndexesOfTheAccountTables);
  }

  @Test
  void a_credential_another_account_of_the_run_holds_is_answered_as_a_claim() {
    assertThatThrownBy(
            () ->
                unitOfWork.keepingOnly(
                    written -> true,
                    () -> {
                      aGrowAccount("account-1", "a-user", "page-1");
                      aGrowAccount("account-2", "a-user", "page-2");
                      return "written";
                    }))
        .isInstanceOf(ClaimedMeanwhileException.class);
    assertThat(theGrowAccountsOfTheRun()).isZero();
  }

  @Test
  void a_unique_violation_that_is_no_claim_reaches_the_caller_as_the_error_it_is() {
    assertThatThrownBy(
            () ->
                unitOfWork.keepingOnly(
                    written -> true,
                    () -> {
                      aTestRun();
                      aTestRun();
                      return "written";
                    }))
        .isInstanceOf(DuplicateKeyException.class)
        .isNotInstanceOf(ClaimedMeanwhileException.class);
  }

  @Test
  void a_unit_that_keeps_what_a_nested_unit_discarded_fails_fast_and_keeps_nothing() {
    assertThatThrownBy(
            () ->
                unitOfWork.keepingOnly(
                    written -> true,
                    () -> {
                      aGrowAccount("account-1", "a-user", "page-1");
                      return unitOfWork.keepingOnly("written"::equals, () -> "refused");
                    }))
        .isInstanceOf(KeptAfterADiscardedUnitException.class);
    assertThat(theGrowAccountsOfTheRun()).isZero();
  }

  private void aGrowAccount(String accountId, String userId, String pageCode) {
    jdbc.sql(
            """
            insert into grow_account
                (test_run_id, account_id, user_id, page_code, callback_acknowledgement,
                 settlement_ceiling, refund_allowance, refund_excess_rejection, merchant_balance,
                 process_lookup_rejection, wire_form, capture_window, automatic_void_after)
            values (?, ?, ?, ?, 'HTTP_2XX', 'FRAME_CAPTURE', 'ONE_REFUND', 'ORIGINAL_AMOUNT',
                    'AVAILABLE', 'PUBLISHED_EXAMPLE', 'LIVE_WIRE', 'P3D', 'P7D')
            """)
        .params(RUN, accountId, userId, pageCode)
        .update();
  }

  private void aTestRun() {
    jdbc.sql("insert into simulator_test_run (test_run_id) values (?)").param(RUN).update();
  }

  private int theGrowAccountsOfTheRun() {
    return jdbc.sql("select count(*) from grow_account where test_run_id = ?")
        .param(RUN)
        .query(Integer.class)
        .single();
  }
}
