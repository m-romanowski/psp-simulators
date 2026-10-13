package dev.marcinromanowski.pspsim.kernel.spi;

public sealed interface ProviderWireAnswer {

  int LOWEST_HTTP_STATUS = 100;

  int HIGHEST_HTTP_STATUS = 599;

  final class InThisProvidersOwnWords implements ProviderWireAnswer {

    private final int httpStatus;
    private final String contentType;
    private final byte[] body;

    public InThisProvidersOwnWords(int httpStatus, String contentType, byte[] body) {
      if (httpStatus < LOWEST_HTTP_STATUS || httpStatus > HIGHEST_HTTP_STATUS) {
        throw new IllegalArgumentException(
            "a provider module answers a request off its routes with a status its own contract"
                + " publishes, and "
                + httpStatus
                + " is not an HTTP status at all");
      }
      if (contentType.isBlank()) {
        throw new IllegalArgumentException(
            "a provider module renders its own bytes and names the media type they are read as,"
                + " because a client that asked for JSON and was handed an unlabelled body would"
                + " read this simulator's answer differently from the vendor's");
      }
      this.httpStatus = httpStatus;
      this.contentType = contentType;
      this.body = body.clone();
    }

    public int httpStatus() {
      return httpStatus;
    }

    public String contentType() {
      return contentType;
    }

    public byte[] body() {
      return body.clone();
    }
  }

  record NothingThisContractPublishes(String whatThisContractPublishesInstead)
      implements ProviderWireAnswer {

    public NothingThisContractPublishes {
      if (whatThisContractPublishesInstead.isBlank()) {
        throw new IllegalArgumentException(
            "a provider module whose contract publishes no answer for this fault says what it"
                + " publishes instead, because the alternative is an envelope in the vendor's own"
                + " field names that the vendor never sends, and a client logging one cannot tell"
                + " it from the real thing");
      }
    }
  }
}
