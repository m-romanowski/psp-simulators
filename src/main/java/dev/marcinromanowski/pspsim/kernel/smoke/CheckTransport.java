package dev.marcinromanowski.pspsim.kernel.smoke;

public interface CheckTransport {

  CheckResponse send(CheckRequest request);
}
