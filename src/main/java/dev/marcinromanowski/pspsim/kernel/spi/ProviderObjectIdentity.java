package dev.marcinromanowski.pspsim.kernel.spi;

import java.util.ArrayList;
import java.util.List;

public record ProviderObjectIdentity(String canonical, List<String> members) {

  public ProviderObjectIdentity {
    members = List.copyOf(members);
    refuseAnIdentityNoLookupCouldReach(canonical, members);
  }

  public static ProviderObjectIdentity of(String canonical) {
    return new ProviderObjectIdentity(canonical, List.of(canonical));
  }

  public ProviderObjectIdentity answeringAlsoTo(String further) {
    List<String> widened = new ArrayList<>(members);
    widened.add(further);
    return new ProviderObjectIdentity(canonical, widened);
  }

  public boolean answersTo(String identifier) {
    return members.contains(identifier);
  }

  public boolean isTheCanonicalMember(String identifier) {
    return canonical.equals(identifier);
  }

  public boolean keepsOneIdentifierForLife() {
    return members.size() == 1;
  }

  private static void refuseAnIdentityNoLookupCouldReach(String canonical, List<String> members) {
    if (canonical.isBlank()) {
      throw new IllegalArgumentException(
          "a provider object is addressed by its canonical identifier and a blank canonical"
              + " identifier addresses nothing, so every screen and every audit row would name"
              + " this object by a string no lookup can be given");
    }
    if (members.isEmpty()) {
      throw new IllegalArgumentException(
          "a provider object's identity lists every identifier it has ever answered to and this"
              + " one lists none, so the object could not be reached even by the canonical"
              + " identifier it was created under");
    }
    if (!canonical.equals(members.get(0))) {
      throw new IllegalArgumentException(
          "a provider object's identity lists the canonical identifier first and every later"
              + " identifier after it, and this one does not begin with its canonical identifier,"
              + " so a reader taking the first member for the canonical one would link the object"
              + " by an identifier the object was not created under");
    }
    for (int member = 0; member < members.size(); member++) {
      if (members.get(member).isBlank()) {
        throw new IllegalArgumentException(
            "a provider object's identity lists identifiers a lookup can be given and member "
                + member
                + " is blank, so a request carrying no identifier at all would resolve to this"
                + " object");
      }
      if (members.indexOf(members.get(member)) != member) {
        throw new IllegalArgumentException(
            "a provider object's identity lists each identifier it has answered to once, and"
                + " member "
                + member
                + " repeats an earlier member, so a count of the identifiers this object has"
                + " taken would report more than it has");
      }
    }
  }
}
