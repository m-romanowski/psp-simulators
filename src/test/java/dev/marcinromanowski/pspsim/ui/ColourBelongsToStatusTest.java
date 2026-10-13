package dev.marcinromanowski.pspsim.ui;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;

class ColourBelongsToStatusTest {

  private static final String THE_TEMPLATES = "classpath*:templates/**/*.html";

  private static final String THE_STYLESHEET = "classpath:static/ui.css";

  private static final String THE_FRAGMENTS = "fragments.html";

  private static final String THE_ONE_CLASS_A_MODEL_DECIDES = "${badge.tone.cssClass()}";

  private static final String THE_NEUTRAL_FACT = "fact";

  private static final Pattern A_CLASS_ATTRIBUTE = Pattern.compile("\\sclass=\"([^\"]*)\"");

  private static final Pattern A_CLASS_THYMELEAF_ADDS =
      Pattern.compile("\\bth:(?:classappend|class)=\"([^\"]*)\"");

  private static final Pattern AN_ATTR_THYMELEAF_SETS =
      Pattern.compile("\\bth:attr(?:append)?=\"([^\"]*)\"");

  private static final Pattern A_CLASS_WITHIN_ATTR =
      Pattern.compile("\\bclass\\s*=\\s*('[^']*'|[^,]+)");

  private static final Pattern A_QUOTED_NAME = Pattern.compile("'([\\w-]+)'");

  private static final Pattern A_VARIANT_OF_A_TAG = Pattern.compile("\\.tag\\.([A-Za-z][\\w-]*)");

  private final PathMatchingResourcePatternResolver resources =
      new PathMatchingResourcePatternResolver();

  @Test
  void no_template_writes_a_status_tone_itself() throws IOException {
    Set<String> tones = theToneClasses();
    List<String> written = new ArrayList<>();
    for (Resource template : resources.getResources(THE_TEMPLATES)) {
      String markup = read(template);
      Matcher attribute = A_CLASS_ATTRIBUTE.matcher(markup);
      while (attribute.find()) {
        for (String named : List.of(attribute.group(1).strip().split("\\s+"))) {
          if (tones.contains(named)) {
            written.add(template.getFilename() + ": class=\"" + attribute.group(1) + "\"");
          }
        }
      }
      for (String[] assignment : theClassAssignments(markup)) {
        Matcher quoted = A_QUOTED_NAME.matcher(assignment[1]);
        while (quoted.find()) {
          if (tones.contains(quoted.group(1))) {
            written.add(template.getFilename() + ": " + assignment[0]);
          }
        }
      }
    }

    assertThat(written)
        .as(
            "a status tone is decided by StatusBadge and written only by fragments :: status;"
                + " a template that writes one itself paints a fact, or a rule of its own, in"
                + " the colour of a status")
        .isEmpty();
  }

  @Test
  void the_only_class_a_template_takes_from_the_model_is_the_tone_of_a_status_badge()
      throws IOException {
    List<String> computed = new ArrayList<>();
    int decidedByTheModel = 0;
    for (Resource template : resources.getResources(THE_TEMPLATES)) {
      for (String[] assignment : theClassAssignments(read(template))) {
        String expression = assignment[1].strip();
        if (A_QUOTED_NAME.matcher(expression).find()) {
          continue;
        }
        if (THE_FRAGMENTS.equals(template.getFilename())
            && THE_ONE_CLASS_A_MODEL_DECIDES.equals(expression)) {
          decidedByTheModel++;
          continue;
        }
        computed.add(template.getFilename() + ": " + assignment[0]);
      }
    }

    assertThat(computed)
        .as(
            "a class a template takes whole from the model could be any tone at all; only the"
                + " status fragment may, and only from the badge's own tone")
        .isEmpty();
    assertThat(decidedByTheModel)
        .as("the status fragment writes the tone its badge carries, and nothing else does")
        .isEqualTo(1);
  }

  @Test
  void the_template_scan_reaches_every_subdirectory() throws IOException {
    Set<String> found = new TreeSet<>();
    for (Resource template : resources.getResources(THE_TEMPLATES)) {
      found.add(template.getFilename());
    }

    assertThat(found).contains(THE_FRAGMENTS, "objects.html");
  }

  @Test
  void every_variant_of_a_tag_the_stylesheet_paints_is_a_status_tone_or_the_neutral_fact()
      throws IOException {
    Set<String> allowed = theToneClasses();
    allowed.add(THE_NEUTRAL_FACT);
    Set<String> painted = new TreeSet<>();
    Matcher variant = A_VARIANT_OF_A_TAG.matcher(read(resources.getResource(THE_STYLESHEET)));
    while (variant.find()) {
      painted.add(variant.group(1));
    }

    assertThat(painted)
        .as("a tag is coloured only as the tone of a status, or set as a plain fact")
        .isEqualTo(allowed);
  }

  private static List<String[]> theClassAssignments(String markup) {
    List<String[]> assignments = new ArrayList<>();
    Matcher added = A_CLASS_THYMELEAF_ADDS.matcher(markup);
    while (added.find()) {
      assignments.add(new String[] {added.group(), added.group(1)});
    }
    Matcher attr = AN_ATTR_THYMELEAF_SETS.matcher(markup);
    while (attr.find()) {
      Matcher withinAttr = A_CLASS_WITHIN_ATTR.matcher(attr.group(1));
      if (withinAttr.find()) {
        assignments.add(new String[] {attr.group(), withinAttr.group(1)});
      }
    }
    return assignments;
  }

  private static Set<String> theToneClasses() {
    Set<String> tones = new TreeSet<>();
    for (StatusBadge.Tone tone : StatusBadge.Tone.values()) {
      tones.add(tone.cssClass());
    }
    return tones;
  }

  private static String read(Resource resource) throws IOException {
    try (InputStream in = resource.getInputStream()) {
      return new String(in.readAllBytes(), StandardCharsets.UTF_8);
    }
  }
}
