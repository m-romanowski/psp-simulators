package dev.marcinromanowski.pspsim.kernel.spi;

public sealed interface KeyClaim {

  boolean canBeHeld();

  String describedAs();

  record ClaimedBeforeTheOperationRuns(String whatARequestMeetingItIsAnswered) implements KeyClaim {

    public ClaimedBeforeTheOperationRuns {
      if (whatARequestMeetingItIsAnswered.isBlank()) {
        throw new IllegalArgumentException(
            "a key this provider claims before the operation runs can be met by a request that has"
                + " to be answered something, and this provider must say in its own words what"
                + " that is, or that it publishes no answer for it: a blank field reads as a fact"
                + " this simulator failed to record");
      }
    }

    @Override
    public boolean canBeHeld() {
      return true;
    }

    @Override
    public String describedAs() {
      return whatARequestMeetingItIsAnswered;
    }
  }

  record NeverHeldByARequest(String whyNoRequestCanMeetIt) implements KeyClaim {

    public NeverHeldByARequest {
      if (whyNoRequestCanMeetIt.isBlank()) {
        throw new IllegalArgumentException(
            "a key no request can ever find held must say in this provider's own words why there"
                + " is nothing to hold it, because a blank field reads as unknown where the answer"
                + " is that this provider claims nothing");
      }
    }

    @Override
    public boolean canBeHeld() {
      return false;
    }

    @Override
    public String describedAs() {
      return whyNoRequestCanMeetIt;
    }
  }
}
