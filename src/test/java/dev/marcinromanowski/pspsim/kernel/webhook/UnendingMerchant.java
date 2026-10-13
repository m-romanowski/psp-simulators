package dev.marcinromanowski.pspsim.kernel.webhook;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

public final class UnendingMerchant implements AutoCloseable {

  private static final long OBSERVATION = TimeUnit.SECONDS.toMillis(2);

  private static final byte[] PAGE = "x".repeat(4096).getBytes(StandardCharsets.US_ASCII);

  private final ServerSocket listening;
  private final CountDownLatch decided = new CountDownLatch(1);
  private volatile boolean abandonedMidResponse;
  private volatile String outcome = "no callback arrived";

  public UnendingMerchant() {
    try {
      listening = new ServerSocket(0, 1, InetAddress.getLoopbackAddress());
    } catch (IOException cannotBind) {
      throw new UncheckedIOException(cannotBind);
    }
    Thread.ofVirtual().name("unending-merchant").start(this::serve);
  }

  public String url() {
    return "http://"
        + listening.getInetAddress().getHostAddress()
        + ":"
        + listening.getLocalPort()
        + LocalMerchantServer.PATH;
  }

  public boolean awaitAbandonedMidResponse() {
    try {
      if (!decided.await(OBSERVATION * 2, TimeUnit.MILLISECONDS)) {
        throw new IllegalStateException("the merchant never reached a verdict: " + outcome);
      }
    } catch (InterruptedException interrupted) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException(interrupted);
    }
    return abandonedMidResponse;
  }

  public String outcome() {
    return outcome;
  }

  @Override
  @SuppressWarnings("EmptyCatch")
  public void close() {
    try {
      listening.close();
    } catch (IOException _) {
    }
  }

  private void serve() {
    try (Socket connection = listening.accept()) {
      readTheCallback(connection.getInputStream());
      OutputStream out = connection.getOutputStream();
      out.write(
          ("HTTP/1.1 200 OK\r\n"
                  + "Content-Type: text/html\r\n"
                  + "Transfer-Encoding: chunked\r\n"
                  + "\r\n"
                  + Integer.toHexString(PAGE.length)
                  + "\r\n")
              .getBytes(StandardCharsets.US_ASCII));
      out.write(PAGE);
      out.write("\r\n".getBytes(StandardCharsets.US_ASCII));
      out.flush();
      watchForTheClientLeaving(connection, out);
    } catch (IOException stopped) {
      outcome = "the merchant stopped: " + stopped;
    } finally {
      decided.countDown();
    }
  }

  private void watchForTheClientLeaving(Socket connection, OutputStream out) throws IOException {
    connection.setSoTimeout((int) OBSERVATION);
    try {
      int ended = connection.getInputStream().read();
      abandonedMidResponse = true;
      outcome = "the client closed the connection (read gave " + ended + ")";
    } catch (SocketTimeoutException stillReading) {
      outcome = "the client was still reading after " + OBSERVATION + "ms";
      out.write("0\r\n\r\n".getBytes(StandardCharsets.US_ASCII));
      out.flush();
    } catch (IOException reset) {
      abandonedMidResponse = true;
      outcome = "the client dropped the connection: " + reset;
    }
  }

  private static void readTheCallback(InputStream in) throws IOException {
    ByteArrayOutputStream head = new ByteArrayOutputStream();
    int next;
    while ((next = in.read()) != -1) {
      head.write(next);
      if (head.size() >= 4 && head.toString(StandardCharsets.US_ASCII).endsWith("\r\n\r\n")) {
        break;
      }
    }
    in.readNBytes(announcedLength(head.toString(StandardCharsets.US_ASCII)));
  }

  private static int announcedLength(String head) {
    for (String line : head.split("\r\n", -1)) {
      if (line.toLowerCase(Locale.ROOT).startsWith("content-length:")) {
        return Integer.parseInt(line.substring(line.indexOf(':') + 1).trim());
      }
    }
    return 0;
  }
}
