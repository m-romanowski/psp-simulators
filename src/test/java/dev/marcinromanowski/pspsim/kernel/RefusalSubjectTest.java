package dev.marcinromanowski.pspsim.kernel;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class RefusalSubjectTest {

  @Test
  void only_the_object_a_provider_resolved_puts_a_refusal_on_a_timeline() {
    assertThat(RefusalSubject.theObject("734754").theObjectItNames()).contains("734754");
    assertThat(RefusalSubject.noObjectAnsweredTo("transactionId").theObjectItNames()).isEmpty();
    assertThat(RefusalSubject.severalObjectsAnsweredTo("ORDER", 2).theObjectItNames()).isEmpty();
    assertThat(RefusalSubject.anObjectItWillNotName("in its own words").theObjectItNames())
        .isEmpty();
    assertThat(RefusalSubject.noObjectWasNamed("in its own words").theObjectItNames()).isEmpty();
  }

  @Test
  void
      a_refusal_that_says_it_names_an_object_and_carries_a_blank_id_names_nothing_a_tester_opens() {
    assertThatThrownBy(() -> RefusalSubject.theObject(" "))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("neither open nor rule out");
  }

  @Test
  void a_refusal_that_found_no_object_names_the_field_it_looked_under_and_not_what_was_sent() {
    RefusalSubject.NoObjectAnsweredTo none = new RefusalSubject.NoObjectAnsweredTo("transactionId");

    assertThat(none.theFieldThatNamedIt()).isEqualTo("transactionId");
    assertThatThrownBy(() -> RefusalSubject.noObjectAnsweredTo(""))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("the wrong object from one that named the wrong field");
  }

  @Test
  void a_refusal_that_says_several_objects_answered_must_have_counted_at_least_two() {
    assertThat(new RefusalSubject.SeveralObjectsAnsweredTo("ORDER", 2).howManyAnswered())
        .isEqualTo(2);
    assertThatThrownBy(() -> RefusalSubject.severalObjectsAnsweredTo("ORDER", 1))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("counted at least two of them");
    assertThatThrownBy(() -> RefusalSubject.severalObjectsAnsweredTo(" ", 2))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("which of the things they seeded made it so");
  }

  @Test
  void a_provider_that_holds_the_object_and_will_not_name_it_says_why_in_its_own_words() {
    assertThat(
            new RefusalSubject.AnObjectItWillNotName("provider grow reports the refusal without it")
                .whyItsContractNamesNone())
        .isEqualTo("provider grow reports the refusal without it");
    assertThatThrownBy(() -> RefusalSubject.anObjectItWillNotName(""))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("a decision this simulator did not make");
  }

  @Test
  void a_refusal_that_names_no_object_says_in_its_own_words_what_it_refused_instead() {
    assertThat(
            new RefusalSubject.NoObjectWasNamed("provider stripe refuses on the parameters alone")
                .whatItRefusedInstead())
        .isEqualTo("provider stripe refuses on the parameters alone");
    assertThatThrownBy(() -> RefusalSubject.noObjectWasNamed(" "))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("whose object this simulator forgot to record");
  }
}
