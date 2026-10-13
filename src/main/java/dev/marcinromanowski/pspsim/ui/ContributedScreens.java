package dev.marcinromanowski.pspsim.ui;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Component;

@Component
public final class ContributedScreens {

  static final String EVERY_ONE_OF_THEM = "classpath*:templates/extensions/**/*.html";

  static final String WHERE_ONE_SITS = "<provider>/<screen>.html";

  static final String THE_FOLDER_THEY_SIT_UNDER = "templates/extensions/";

  static final String TAB = "tab";

  static final String GROUP = "group";

  static final String ORDER = "order";

  private static final Pattern A_META_TAG =
      Pattern.compile("<meta\\b[^>]*>", Pattern.CASE_INSENSITIVE);

  private static final Pattern AN_ATTRIBUTE = Pattern.compile("([A-Za-z-]+)\\s*=\\s*\"([^\"]*)\"");

  private static final Pattern A_PATH_SEGMENT = Pattern.compile("[a-z][a-z0-9-]*");

  private static final Pattern ONE_FOLDER_UNDER_EXTENSIONS =
      Pattern.compile("([^/]+)/([^/]+)\\.html");

  private static final String A_PAGE = ".html";

  private static final int NOWHERE_IN_IT = -1;

  private final List<ContributedScreen> screens;

  public ContributedScreens() {
    this(readFrom(everyPageOnTheClasspath()));
  }

  ContributedScreens(List<ContributedScreen> screens) {
    this.screens = List.copyOf(screens);
  }

  public List<ContributedScreen> all() {
    return screens;
  }

  public List<ContributedScreen> of(String provider) {
    List<ContributedScreen> theirs = new ArrayList<>();
    for (ContributedScreen screen : screens) {
      if (screen.provider().equals(provider)) {
        theirs.add(screen);
      }
    }
    return List.copyOf(theirs);
  }

  public Optional<ContributedScreen> serving(String provider, String screen) {
    for (ContributedScreen contributed : screens) {
      if (contributed.provider().equals(provider) && contributed.screen().equals(screen)) {
        return Optional.of(contributed);
      }
    }
    return Optional.empty();
  }

  public String theRefusalOfAScreenThisBuildDoesNotServe(String provider, String screen) {
    List<String> served = new ArrayList<>();
    for (ContributedScreen contributed : screens) {
      served.add(contributed.path());
    }
    return "no page of this build is contributed at "
        + ContributedScreen.WHERE_THEY_LIVE
        + provider
        + "/"
        + screen
        + A_PAGE
        + ", and a screen is served from the file that is there and from nothing else, so that"
        + " this route can never be asked to read a file the contributors did not put there."
        + " The pages this build serves are "
        + served;
  }

  static List<ContributedScreen> readFrom(Map<String, String> markupByFile) {
    List<ContributedScreen> read = new ArrayList<>();
    for (Map.Entry<String, String> page : markupByFile.entrySet()) {
      read.add(oneOf(page.getKey(), page.getValue()));
    }
    read.sort(
        (one, other) -> {
          int byProvider = one.provider().compareTo(other.provider());
          return byProvider == 0 ? one.screen().compareTo(other.screen()) : byProvider;
        });
    return List.copyOf(read);
  }

  private static ContributedScreen oneOf(String file, String markup) {
    Matcher where = ONE_FOLDER_UNDER_EXTENSIONS.matcher(file);
    if (!where.matches()) {
      throw new IllegalStateException(theRefusalOfAPageThatSitsSomewhereElse(file));
    }
    String provider = where.group(1);
    String screen = where.group(2);
    refuseANameNoRouteCouldCarry(file, provider, screen);
    refuseAPageThatReachesIntoTheApplication(file, markup);
    Map<String, String> declared = theMetaTagsOf(markup);
    return new ContributedScreen(
        provider,
        screen,
        theLabelOf(file, declared),
        theGroupOf(file, declared),
        theOrderOf(file, declared));
  }

  private static String theLabelOf(String file, Map<String, String> declared) {
    String label = declared.getOrDefault(TAB, "").strip();
    if (label.isEmpty()) {
      throw new IllegalStateException(
          ContributedScreen.WHERE_THEY_LIVE
              + file
              + " is served as a screen of this simulator and declares no"
              + " <meta name=\""
              + TAB
              + "\" content=\"...\"> in its head. The navigation prints that content as the label"
              + " of the tab it offers and derives nothing from the name of the file, because a"
              + " tab reading \"pix\" is a word a tester either already knows or does not, and"
              + " what a screen is for is the contributor's to say and not this simulator's to"
              + " guess.");
    }
    return label;
  }

  private static RailGroup theGroupOf(String file, Map<String, String> declared) {
    String token = declared.getOrDefault(GROUP, "").strip();
    if (token.isEmpty()) {
      throw new IllegalStateException(
          ContributedScreen.WHERE_THEY_LIVE
              + file
              + " declares no <meta name=\""
              + GROUP
              + "\" content=\"...\"> in its head, and the navigation has no ungrouped place to put"
              + " a tab. A group says which part of a tester's work a screen belongs to, and this"
              + " build carries "
              + RailGroup.theOnesThisRailHas()
              + ".");
    }
    return RailGroup.named(token)
        .orElseThrow(
            () ->
                new IllegalStateException(
                    ContributedScreen.WHERE_THEY_LIVE
                        + file
                        + " declares <meta name=\""
                        + GROUP
                        + "\" content=\""
                        + token
                        + "\">, and this navigation has no group of that name. The groups say what"
                        + " part of a tester's work a screen belongs to and are not a list of"
                        + " vendors, so a contributed screen picks one this build already carries:"
                        + " "
                        + RailGroup.theOnesThisRailHas()
                        + "."));
  }

  private static int theOrderOf(String file, Map<String, String> declared) {
    String order = declared.getOrDefault(ORDER, "").strip();
    if (order.isEmpty()) {
      throw new IllegalStateException(
          ContributedScreen.WHERE_THEY_LIVE
              + file
              + " declares no <meta name=\""
              + ORDER
              + "\" content=\"...\"> in its head, and two tabs of one group would then sit in"
              + " whichever order the classpath happened to hand them over, which is not an order"
              + " anything here decided.");
    }
    try {
      return Integer.parseInt(order);
    } catch (NumberFormatException notAWholeNumber) {
      throw new IllegalStateException(
          ContributedScreen.WHERE_THEY_LIVE
              + file
              + " declares <meta name=\""
              + ORDER
              + "\" content=\""
              + order
              + "\">, which is no whole number, and a group sorts its tabs by that number.",
          notAWholeNumber);
    }
  }

  private static String theRefusalOfAPageThatSitsSomewhereElse(String file) {
    return ContributedScreen.WHERE_THEY_LIVE
        + file
        + " is on the classpath under "
        + THE_FOLDER_THEY_SIT_UNDER
        + " and is served by nothing. A screen is one page exactly one folder under"
        + " extensions, that folder naming the provider whose navigation the tab joins,"
        + " because the route is built out of that folder and that file name and has room for"
        + " no more and no fewer. Drop it in at "
        + ContributedScreen.WHERE_THEY_LIVE
        + WHERE_ONE_SITS
        + ", or take it out: a page nothing serves is a tab nobody sees and a 404 on every"
        + " address anyone would guess.";
  }

  private static void refuseAPageThatReachesIntoTheApplication(String file, String markup) {
    List<String> beans = ATemplateReadsOnlyItsModel.theBeansItReaches(markup);
    if (!beans.isEmpty()) {
      throw new IllegalStateException(
          ContributedScreen.WHERE_THEY_LIVE
              + file
              + " evaluates the beans "
              + beans
              + " of this application, and "
              + ATemplateReadsOnlyItsModel.WHAT_A_TEMPLATE_READS
              + ". A page that asks for one bean can ask for any of them, so the one holding the"
              + " database password is as close to a contributed file as the one listing another"
              + " provider's accounts, and dropping a file in is all it takes. Whatever this screen"
              + " needs, the model it is rendered with carries.");
    }
  }

  private static void refuseANameNoRouteCouldCarry(String file, String provider, String screen) {
    if (!A_PATH_SEGMENT.matcher(provider).matches() || !A_PATH_SEGMENT.matcher(screen).matches()) {
      throw new IllegalStateException(
          ContributedScreen.WHERE_THEY_LIVE
              + file
              + " is served at a route built out of the folder it sits in and its own file name,"
              + " so both are lowercase letters, digits and hyphens beginning with a letter, and"
              + " \""
              + provider
              + "/"
              + screen
              + "\" is not.");
    }
  }

  private static Map<String, String> theMetaTagsOf(String markup) {
    Map<String, String> declared = new LinkedHashMap<>();
    Matcher tag = A_META_TAG.matcher(markup);
    while (tag.find()) {
      Map<String, String> attributes = new LinkedHashMap<>();
      Matcher attribute = AN_ATTRIBUTE.matcher(tag.group());
      while (attribute.find()) {
        attributes.put(attribute.group(1).toLowerCase(Locale.ROOT), attribute.group(2));
      }
      String name = attributes.get("name");
      if (name != null) {
        declared.putIfAbsent(name.toLowerCase(Locale.ROOT), attributes.getOrDefault("content", ""));
      }
    }
    return declared;
  }

  private static Map<String, String> everyPageOnTheClasspath() {
    Map<String, String> pages = new LinkedHashMap<>();
    try {
      for (Resource page :
          new PathMatchingResourcePatternResolver().getResources(EVERY_ONE_OF_THEM)) {
        pages.put(theFileNameOf(page), read(page));
      }
    } catch (IOException unreadable) {
      throw new UncheckedIOException(unreadable);
    }
    return pages;
  }

  private static String theFileNameOf(Resource page) throws IOException {
    String where = page.getURL().toString();
    int under = where.lastIndexOf(THE_FOLDER_THEY_SIT_UNDER);
    if (under == NOWHERE_IN_IT) {
      throw new IllegalStateException(
          where
              + " was handed over by "
              + EVERY_ONE_OF_THEM
              + " and does not sit under "
              + THE_FOLDER_THEY_SIT_UNDER
              + " at all, so there is no folder to read a provider out of and no file name to read"
              + " a screen out of. The glob and this reading of what it found have drifted apart,"
              + " and a screen named out of the wrong part of a URL would be served at a route"
              + " nobody meant.");
    }
    return where.substring(under + THE_FOLDER_THEY_SIT_UNDER.length());
  }

  private static String read(Resource page) throws IOException {
    try (InputStream in = page.getInputStream()) {
      return new String(in.readAllBytes(), StandardCharsets.UTF_8);
    }
  }
}
