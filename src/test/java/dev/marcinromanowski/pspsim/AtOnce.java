package dev.marcinromanowski.pspsim;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

public final class AtOnce {

  private AtOnce() {}

  public static <T> List<T> sent(int clients, Callable<T> request) throws Exception {
    ExecutorService senders = Executors.newFixedThreadPool(clients);
    CountDownLatch ready = new CountDownLatch(clients);
    CountDownLatch go = new CountDownLatch(1);
    try {
      List<Future<T>> sent = new ArrayList<>();
      for (int client = 0; client < clients; client++) {
        sent.add(
            senders.submit(
                () -> {
                  ready.countDown();
                  go.await();
                  return request.call();
                }));
      }
      ready.await(10, TimeUnit.SECONDS);
      go.countDown();
      List<T> answers = new ArrayList<>();
      for (Future<T> answer : sent) {
        answers.add(answer.get(60, TimeUnit.SECONDS));
      }
      return answers;
    } finally {
      senders.shutdownNow();
    }
  }
}
