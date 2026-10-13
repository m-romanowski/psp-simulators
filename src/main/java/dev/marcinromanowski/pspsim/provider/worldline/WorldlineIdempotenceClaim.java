package dev.marcinromanowski.pspsim.provider.worldline;

import java.time.Instant;

sealed interface WorldlineIdempotenceClaim {

  record Fresh() implements WorldlineIdempotenceClaim {}

  record Replayed(WorldlineStoredAnswer answer, Instant theFirstRequestWasAt)
      implements WorldlineIdempotenceClaim {}

  record HeldByARequestStillRunning() implements WorldlineIdempotenceClaim {}
}
