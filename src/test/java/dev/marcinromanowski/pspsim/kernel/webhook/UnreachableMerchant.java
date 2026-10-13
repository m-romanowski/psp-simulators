package dev.marcinromanowski.pspsim.kernel.webhook;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.util.ArrayList;
import java.util.List;

public final class UnreachableMerchant implements AutoCloseable {

  private static final int TO_COMPLETE = 250;

  private static final int MOST_PLAUSIBLE_BACKLOG = 64;

  private final ServerSocket socket;
  private final List<Socket> parked = new ArrayList<>();

  public UnreachableMerchant() {
    try {
      socket = new ServerSocket(0, 1, InetAddress.getLoopbackAddress());
      fillTheAcceptQueue();
    } catch (IOException cannotBind) {
      throw new UncheckedIOException(cannotBind);
    }
  }

  public String url() {
    return "http://"
        + socket.getInetAddress().getHostAddress()
        + ":"
        + socket.getLocalPort()
        + LocalMerchantServer.PATH;
  }

  @Override
  @SuppressWarnings("EmptyCatch")
  public void close() {
    for (Socket connection : parked) {
      try {
        connection.close();
      } catch (IOException _) {
      }
    }
    try {
      socket.close();
    } catch (IOException _) {
    }
  }

  private void fillTheAcceptQueue() throws IOException {
    InetSocketAddress here =
        new InetSocketAddress(InetAddress.getLoopbackAddress(), socket.getLocalPort());
    for (int parking = 0; parking < MOST_PLAUSIBLE_BACKLOG; parking++) {
      Socket connection = new Socket();
      try {
        connection.connect(here, TO_COMPLETE);
        parked.add(connection);
      } catch (SocketTimeoutException queueIsFull) {
        connection.close();
        return;
      }
    }
    throw new IllegalStateException(
        "a listening socket with a backlog of one completed "
            + MOST_PLAUSIBLE_BACKLOG
            + " connections, so nothing here produces a connect timeout");
  }
}
