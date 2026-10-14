package dev.marcinromanowski.pspsim.provider.tranzila;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.format.ResolverStyle;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.jspecify.annotations.Nullable;

record TranzilaLookup(
    String terminalName,
    @Nullable Long index,
    @Nullable LocalDate start,
    @Nullable LocalDate end,
    long page,
    int pageResults,
    boolean newestFirst,
    boolean detailed) {

  static final int THE_MOST_ROWS_A_PAGE_ANSWERS = 1000;

  static final String TRANSACTION_INDEX = "transaction_index";

  static final String TRANSACTION_START_DATE = "transaction_start_date";

  static final String TRANSACTION_END_DATE = "transaction_end_date";

  static final String PAGE = "page";

  static final String DETAILED = "detailed";

  static final String PAGE_RESULTS = "page_results";

  static final String ORDER_DIRECTION = "order_direction";

  static final String UFIELDS = "ufields";

  static final String DFIELDS = "dfields";

  static final String YES = "Y";

  static final String NO = "N";

  static final String NEWEST_FIRST = "desc";

  static final String OLDEST_FIRST = "asc";

  static final String A_DATE = "(?!0000)[0-9]{4}-[0-9]{2}-[0-9]{2}";

  static final DateTimeFormatter THE_DATE_FORMAT =
      DateTimeFormatter.ofPattern("uuuu-MM-dd").withResolverStyle(ResolverStyle.STRICT);

  static final Set<String> THE_FIELDS_OF_THE_API =
      Set.of(
          TranzilaFields.TERMINAL_NAME,
          TRANSACTION_INDEX,
          TRANSACTION_START_DATE,
          TRANSACTION_END_DATE,
          PAGE);

  static final Set<String> THE_FIELDS_OF_THE_REPORT =
      Set.of(
          TranzilaFields.TERMINAL_NAME,
          TRANSACTION_INDEX,
          TRANSACTION_START_DATE,
          TRANSACTION_END_DATE,
          PAGE,
          DETAILED,
          PAGE_RESULTS,
          ORDER_DIRECTION,
          UFIELDS,
          DFIELDS);

  static final String NOT_BOTH =
      "Provide either transaction_index or both transaction_start_date and"
          + " transaction_end_date, not both.";

  static final String ONE_OF_THEM =
      "transaction_index, or both transaction_start_date and transaction_end_date, is required.";

  static final String NOT_SERVED =
      " filters are not served by this simulator, so they are refused rather than ignored: an"
          + " ignored filter would answer rows the caller excluded.";

  record Window(LocalDate start, LocalDate end, int direction, long offset, int limit) {}

  static TranzilaLookup ofTheApi(TranzilaJson.JsonObject body) {
    refuseWhatIsNotIn(body, THE_FIELDS_OF_THE_API, "TransactionsBody");
    return read(body, THE_MOST_ROWS_A_PAGE_ANSWERS, false, false);
  }

  static TranzilaLookup ofTheReport(TranzilaJson.JsonObject body) {
    refuseWhatIsNotIn(body, THE_FIELDS_OF_THE_REPORT, "TransactionRequest");
    refuseAFilter(body, UFIELDS);
    refuseAFilter(body, DFIELDS);
    long pageResults =
        boundedNumber(
            body, PAGE_RESULTS, THE_MOST_ROWS_A_PAGE_ANSWERS, THE_MOST_ROWS_A_PAGE_ANSWERS);
    boolean detailed = YES.equals(oneOf(body, DETAILED, NO, List.of(YES, NO)));
    boolean newestFirst =
        NEWEST_FIRST.equals(
            oneOf(body, ORDER_DIRECTION, NEWEST_FIRST, List.of(NEWEST_FIRST, OLDEST_FIRST)));
    return read(body, (int) pageResults, newestFirst, detailed);
  }

  private static TranzilaLookup read(
      TranzilaJson.JsonObject body, int pageResults, boolean newestFirst, boolean detailed) {
    String terminalName =
        body.text(TranzilaFields.TERMINAL_NAME)
            .filter(sent -> !sent.isBlank())
            .orElseThrow(
                () -> new TranzilaSchemaMismatch(TranzilaFields.TERMINAL_NAME + " is required."));
    Long index = whole(body, TRANSACTION_INDEX).orElse(null);
    LocalDate start = dateIn(body, TRANSACTION_START_DATE);
    LocalDate end = dateIn(body, TRANSACTION_END_DATE);
    boolean aRange = start != null && end != null;
    if (index != null && (start != null || end != null)) {
      throw new TranzilaSchemaMismatch(NOT_BOTH);
    }
    if (index == null && !aRange) {
      throw new TranzilaSchemaMismatch(ONE_OF_THEM);
    }
    long page = boundedNumber(body, PAGE, 1, Integer.MAX_VALUE);
    return new TranzilaLookup(
        terminalName, index, start, end, page, pageResults, newestFirst, detailed);
  }

  long offset() {
    return (page - 1) * pageResults;
  }

  Window window() {
    LocalDate from = start;
    LocalDate until = end;
    if (from == null || until == null) {
      throw new IllegalStateException("a lookup by transaction_index has no window of dates");
    }
    return new Window(from, until, newestFirst ? -1 : 1, offset(), pageResults);
  }

  <T> List<T> thePageOf(List<T> found) {
    long from = Math.min(offset(), found.size());
    long until = Math.min(from + pageResults, found.size());
    return List.copyOf(found.subList((int) from, (int) until));
  }

  private static void refuseWhatIsNotIn(
      TranzilaJson.JsonObject body, Set<String> published, String schema) {
    for (String sent : body.members().keySet()) {
      if (!published.contains(sent)) {
        throw new TranzilaSchemaMismatch(sent + " is not a field of the " + schema + " schema.");
      }
    }
  }

  private static void refuseAFilter(TranzilaJson.JsonObject body, String filter) {
    if (!body.has(filter)) {
      return;
    }
    List<TranzilaJson> filters =
        body.array(filter)
            .map(TranzilaJson.JsonArray::elements)
            .orElseThrow(() -> new TranzilaSchemaMismatch(filter + " must be an array."));
    if (!filters.isEmpty()) {
      throw new TranzilaSchemaMismatch(filter + NOT_SERVED);
    }
  }

  private static String oneOf(
      TranzilaJson.JsonObject body, String field, String byDefault, List<String> published) {
    if (!body.has(field)) {
      return byDefault;
    }
    return body.text(field)
        .filter(published::contains)
        .orElseThrow(
            () ->
                new TranzilaSchemaMismatch(
                    field + " must be " + String.join(" or ", published) + "."));
  }

  private static long boundedNumber(
      TranzilaJson.JsonObject body, String field, long byDefault, long most) {
    long sent = whole(body, field).orElse(Long.valueOf(byDefault)).longValue();
    if (sent < 1 || sent > most) {
      throw new TranzilaSchemaMismatch(field + " must be a whole number from 1 to " + most + ".");
    }
    return sent;
  }

  private static Optional<Long> whole(TranzilaJson.JsonObject body, String field) {
    if (!body.has(field)) {
      return Optional.empty();
    }
    return Optional.of(
        body.number(field)
            .filter(lexical -> lexical.matches("-?[0-9]{1,18}"))
            .map(Long::valueOf)
            .orElseThrow(() -> new TranzilaSchemaMismatch(field + " must be an integer.")));
  }

  private static @Nullable LocalDate dateIn(TranzilaJson.JsonObject body, String field) {
    if (!body.has(field)) {
      return null;
    }
    String sent =
        body.text(field)
            .filter(written -> written.matches(A_DATE))
            .orElseThrow(() -> aDateIsExpected(field));
    try {
      return LocalDate.parse(sent, THE_DATE_FORMAT);
    } catch (DateTimeParseException notADay) {
      throw aDateIsExpected(field);
    }
  }

  private static TranzilaSchemaMismatch aDateIsExpected(String field) {
    return new TranzilaSchemaMismatch(
        field + " must be a calendar date written YYYY-MM-DD, from 0001-01-01.");
  }
}
