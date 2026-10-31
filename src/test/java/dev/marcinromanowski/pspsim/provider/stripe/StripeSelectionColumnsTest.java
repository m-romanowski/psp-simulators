package dev.marcinromanowski.pspsim.provider.stripe;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Proxy;
import java.sql.Array;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.simple.JdbcClient;

class StripeSelectionColumnsTest {

  @Test
  void a_dynamic_selection_binds_every_column_it_carries_in_the_order_the_names_list_them() {
    List<Object> bound = new ArrayList<>();
    JdbcClient.StatementSpec statement = aStatementRecording(bound);

    JdbcClient.StatementSpec returned =
        StripeSelectionColumns.bound(
            statement,
            new StripeSelection(
                true,
                StripeRedirects.NEVER,
                null,
                List.of("paypal"),
                "pmc_kept",
                List.of(StripePaymentMethodType.CARD)));

    assertThat(returned).isSameAs(statement);
    assertThat(bound)
        .containsExactly(true, "never", "null", List.of("paypal"), "pmc_kept", List.of("card"));
  }

  @Test
  void a_selection_of_allowed_types_binds_null_to_every_column_it_does_not_carry() {
    List<Object> bound = new ArrayList<>();

    StripeSelectionColumns.bound(
        aStatementRecording(bound),
        new StripeSelection(
            null,
            null,
            List.of("card", "us_bank_account"),
            null,
            null,
            List.of(StripePaymentMethodType.CARD)));

    assertThat(bound)
        .containsExactly(
            "null", "null", List.of("card", "us_bank_account"), "null", "null", List.of("card"));
  }

  @Test
  void a_dynamic_row_reads_back_as_the_selection_it_stores() throws SQLException {
    Map<String, Object> row = new HashMap<>();
    row.put("automatic_payment_methods_enabled", true);
    row.put("automatic_payment_methods_allow_redirects", "never");
    row.put("excluded_payment_method_types", anArrayOf("paypal"));
    row.put("payment_method_configuration", "pmc_kept");
    row.put("payment_method_types", anArrayOf("card", "paypal"));

    StripeSelection read = StripeSelectionColumns.read(aRowOf(row));

    assertThat(read)
        .isEqualTo(
            new StripeSelection(
                true,
                StripeRedirects.NEVER,
                null,
                List.of("paypal"),
                "pmc_kept",
                List.of(StripePaymentMethodType.CARD, StripePaymentMethodType.PAYPAL)));
  }

  @Test
  void a_row_of_allowed_types_reads_back_with_no_dynamic_setting() throws SQLException {
    Map<String, Object> row = new HashMap<>();
    row.put("allowed_payment_method_types", anArrayOf("card", "us_bank_account"));
    row.put("payment_method_types", anArrayOf("card"));

    StripeSelection read = StripeSelectionColumns.read(aRowOf(row));

    assertThat(read)
        .isEqualTo(
            new StripeSelection(
                null,
                null,
                List.of("card", "us_bank_account"),
                null,
                null,
                List.of(StripePaymentMethodType.CARD)));
  }

  private static JdbcClient.StatementSpec aStatementRecording(List<Object> bound) {
    return (JdbcClient.StatementSpec)
        Proxy.newProxyInstance(
            StripeSelectionColumnsTest.class.getClassLoader(),
            new Class<?>[] {JdbcClient.StatementSpec.class},
            (proxy, method, arguments) -> {
              if (!method.getName().equals("param") || arguments.length != 1) {
                throw new UnsupportedOperationException(method.getName());
              }
              Object value = arguments[0];
              if (value == null) {
                bound.add("null");
              } else if (value instanceof String[] elements) {
                bound.add(Arrays.asList(elements));
              } else {
                bound.add(value);
              }
              return proxy;
            });
  }

  private static ResultSet aRowOf(Map<String, Object> columns) {
    return (ResultSet)
        Proxy.newProxyInstance(
            StripeSelectionColumnsTest.class.getClassLoader(),
            new Class<?>[] {ResultSet.class},
            (proxy, method, arguments) ->
                switch (method.getName()) {
                  case "getString", "getArray", "getObject" -> columns.get((String) arguments[0]);
                  default -> throw new UnsupportedOperationException(method.getName());
                });
  }

  private static Array anArrayOf(String... elements) {
    return (Array)
        Proxy.newProxyInstance(
            StripeSelectionColumnsTest.class.getClassLoader(),
            new Class<?>[] {Array.class},
            (proxy, method, arguments) -> {
              if (!method.getName().equals("getArray") || arguments != null) {
                throw new UnsupportedOperationException(method.getName());
              }
              return elements.clone();
            });
  }
}
