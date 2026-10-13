package dev.marcinromanowski.pspsim;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;

class SpringReadsAContentTypeUnderEverySpellingTest {

  private static final String CONTENT_TYPE_IN_ANOTHER_SPELLING = "content-type";

  private static final String WHY_THIS_READER_IS_PINNED =
      "Every claim of this suite that an answer declares no media type reads getContentType and"
          + " names no spelling of Content-Type at all. "
          + TheSpellingsOfAHeaderName.ONE_HEADER_ANSWERS_TO_EVERY_SPELLING_OF_ITS_NAME
          + " Content-Type is such a header, so those claims are the claim they read as only"
          + " because the reader answers about the header rather than about the spelling the"
          + " answer happened to be written in. That is a property of the reader and not of the"
          + " claims, so it is pinned here: a reader that stopped folding the case of the name"
          + " would leave every one of those absences passing over an answer whose media type a"
          + " client would read.";

  @Test
  void a_media_type_carried_under_another_spelling_is_still_the_one_spring_reads_off_an_answer() {
    HttpHeaders answered = new HttpHeaders();
    answered.add(CONTENT_TYPE_IN_ANOTHER_SPELLING, MediaType.APPLICATION_JSON_VALUE);

    assertThat(answered.getContentType())
        .as(WHY_THIS_READER_IS_PINNED)
        .isEqualTo(MediaType.APPLICATION_JSON);
  }

  @Test
  void an_answer_carrying_no_spelling_of_content_type_declares_no_media_type_to_spring() {
    HttpHeaders answered = new HttpHeaders();
    answered.add("Idempotent-Replayed", "true");

    assertThat(answered.getContentType()).as(WHY_THIS_READER_IS_PINNED).isNull();
  }

  @Test
  void a_media_type_set_under_another_spelling_is_still_the_one_a_mock_answer_reads_back() {
    MockHttpServletResponse answered = new MockHttpServletResponse();
    answered.setHeader(CONTENT_TYPE_IN_ANOTHER_SPELLING, MediaType.APPLICATION_JSON_VALUE);

    assertThat(answered.getContentType())
        .as(WHY_THIS_READER_IS_PINNED)
        .contains(MediaType.APPLICATION_JSON_VALUE);
  }

  @Test
  void a_mock_answer_set_no_spelling_of_content_type_reads_back_as_declaring_no_media_type() {
    MockHttpServletResponse answered = new MockHttpServletResponse();
    answered.setHeader("Idempotent-Replayed", "true");

    assertThat(answered.getContentType()).as(WHY_THIS_READER_IS_PINNED).isNull();
  }
}
