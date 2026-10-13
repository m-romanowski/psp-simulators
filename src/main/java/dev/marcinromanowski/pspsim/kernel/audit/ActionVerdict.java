package dev.marcinromanowski.pspsim.kernel.audit;

import dev.marcinromanowski.pspsim.kernel.RefusalSubject;

public sealed interface ActionVerdict {

  int httpStatus();

  record Applied(int httpStatus) implements ActionVerdict {

    public Applied {
      if (RefusedActionStatus.refuses(httpStatus)) {
        throw new IllegalArgumentException(
            "an action recorded as having changed something must carry the status of the answer it"
                + " changed something in, and a refusing status on that row would tell a tester"
                + " reading the trail that a call which took effect was turned away");
      }
      if (RefusedActionStatus.nothingDecided(httpStatus)) {
        throw new IllegalArgumentException(
            "an action recorded as having changed something must carry the status of the answer it"
                + " changed something in, and a status nothing decided on that row would tell a"
                + " tester reading the trail that a call this simulator failed on took effect");
      }
    }
  }

  record Refused(RefusedActionStatus status, RefusalSubject subject) implements ActionVerdict {

    @Override
    public int httpStatus() {
      return status.httpStatus();
    }
  }

  static Applied applied(int httpStatus) {
    return new Applied(httpStatus);
  }

  static Refused rejected(RefusalSubject subject) {
    return new Refused(RefusedActionStatus.REJECTED, subject);
  }

  static Refused notFound(RefusalSubject subject) {
    return new Refused(RefusedActionStatus.NOT_FOUND, subject);
  }

  static Refused conflicting(RefusalSubject subject) {
    return new Refused(RefusedActionStatus.CONFLICTING, subject);
  }

  static Refused refused(RefusalSubject subject) {
    return new Refused(RefusedActionStatus.REFUSED, subject);
  }
}
