package dev.marcinromanowski.pspsim.kernel.testrun;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.marcinromanowski.pspsim.kernel.ids.IdGenerator;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class FingerprintKeysTest {

  private static final String MINTED =
      "000102030405060708090a0b0c0d0e0f101112131415161718191a1b1c1d1e1f";
  private static final String KEPT =
      "0102030405060708090a0b0c0d0e0f101112131415161718191a1b1c1d1e1f20";
  private static final TestRunId RUN = TestRunId.of("fingerprint-keys");

  private final List<Integer> asked = new ArrayList<>();
  private final IdGenerator ids =
      new IdGenerator() {
        @Override
        public String hex(int length) {
          asked.add(length);
          return MINTED;
        }

        @Override
        public String digits(int length) {
          throw new UnsupportedOperationException();
        }
      };

  @Test
  void a_key_the_run_already_holds_is_answered_and_nothing_is_minted() {
    FingerprintKey held =
        new MintedFingerprintKeys(storeHolding(Optional.of(new FingerprintKey(KEPT)), KEPT), ids)
            .of(RUN);

    assertThat(held.secret()).isEqualTo(KEPT);
    assertThat(asked).isEmpty();
  }

  @Test
  void a_run_holding_no_key_is_given_one_of_sixty_four_hex_digits_minted_by_the_id_generator() {
    List<FingerprintKey> offered = new ArrayList<>();
    FingerprintKeyStore store =
        new FingerprintKeyStore() {
          @Override
          public Optional<FingerprintKey> held(TestRunId testRunId) {
            return Optional.empty();
          }

          @Override
          public FingerprintKey keptOr(TestRunId testRunId, FingerprintKey minted) {
            offered.add(minted);
            return minted;
          }
        };

    assertThat(new MintedFingerprintKeys(store, ids).of(RUN).secret()).isEqualTo(MINTED);
    assertThat(asked).containsExactly(64);
    assertThat(offered).extracting(FingerprintKey::secret).containsExactly(MINTED);
  }

  @Test
  void a_key_another_request_kept_first_is_the_one_answered() {
    assertThat(
            new MintedFingerprintKeys(storeHolding(Optional.empty(), KEPT), ids).of(RUN).secret())
        .isEqualTo(KEPT);
  }

  @Test
  void the_reserved_run_of_a_failed_resolution_is_given_no_key() {
    assertThatThrownBy(
            () ->
                new MintedFingerprintKeys(storeHolding(Optional.empty(), KEPT), ids)
                    .of(TestRunId.UNKNOWN))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void a_key_is_thirty_two_bytes_written_as_hexadecimal_and_never_printed() {
    FingerprintKey key = new FingerprintKey(MINTED);

    assertThat(key.bytes()).hasSize(32).startsWith((byte) 0, (byte) 1).endsWith((byte) 31);
    assertThat(key.toString()).doesNotContain(MINTED).contains("withheld");
    assertThatThrownBy(() -> new FingerprintKey(MINTED.substring(1)))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("64");
    assertThatThrownBy(() -> new FingerprintKey("z" + MINTED.substring(1)))
        .isInstanceOf(IllegalArgumentException.class);
  }

  private static FingerprintKeyStore storeHolding(Optional<FingerprintKey> held, String kept) {
    return new FingerprintKeyStore() {
      @Override
      public Optional<FingerprintKey> held(TestRunId testRunId) {
        return held;
      }

      @Override
      public FingerprintKey keptOr(TestRunId testRunId, FingerprintKey minted) {
        return new FingerprintKey(kept);
      }
    };
  }
}
