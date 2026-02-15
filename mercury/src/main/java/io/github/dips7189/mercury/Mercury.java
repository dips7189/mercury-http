package io.github.dips7189.mercury;

import java.io.IOException;
import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpConnectTimeoutException;
import java.net.http.HttpRequest.BodyPublisher;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import static io.github.dips7189.mercury.Method.DELETE;
import static io.github.dips7189.mercury.Method.GET;
import static io.github.dips7189.mercury.Method.PATCH;
import static io.github.dips7189.mercury.Method.POST;
import static io.github.dips7189.mercury.Method.PUT;

/**
 * Mercury is a lightweight HTTP client built on Java's {@link java.net.http.HttpClient}.
 *
 * <p>It provides a thin, fluent API for executing HTTP requests synchronously or asynchronously
 * without framework overhead.</p>
 *
 * <pre>{@code
 * Reply r = Mercury.fetch("https://api.example.com/data");
 *
 * CompletableFuture<Reply> f =
 *     Mercury.async().fetch("https://api.example.com/data");
 *
 * Reply r2 =
 *     Mercury.post("https://api.example.com/data", "{...}",
 *         Duration.ofSeconds(2),
 *         Mercury.qp("q", "java"),
 *         Mercury.bearer(token),
 *         "Accept", "application/json");
 * }</pre>
 *
 * <p>Retries may be enabled explicitly using {@link #retrying(Retry)}.</p>
 */
public final class Mercury {
  private Mercury() {
  }

  private static final HttpClient CLIENT = HttpClient.newBuilder()
      .followRedirects(HttpClient.Redirect.NORMAL)
      .connectTimeout(Duration.ofSeconds(5))
      .build();

  public static Reply fetch(String url) {
    return send(GET, url, BodyPublishers.noBody(), _ -> {});
  }

  public static Reply fetch(String url, String... headerPairs) {
    return send(GET, url, BodyPublishers.noBody(), null, null, null, headerPairs);
  }

  public static Reply fetch(String url, Duration timeout) {
    return send(GET, url, BodyPublishers.noBody(), timeout, null, null);
  }

  public static Reply fetch(String url, Query query) {
    return send(GET, url, BodyPublishers.noBody(), null, query, null);
  }

  public static Reply fetch(String url, Auth auth) {
    return send(GET, url, BodyPublishers.noBody(), null, null, auth);
  }

  public static Reply post(String url, String body) {
    return send(POST, url, BodyPublishers.ofString(Objects.requireNonNull(body, "body")), null, null, null, (String[]) null);
  }

  public static Reply post(String url, byte[] body) {
    return send(POST, url, BodyPublishers.ofByteArray(Objects.requireNonNull(body, "body")), null, null, null, (String[]) null);
  }

  public static Reply post(String url, String body, Consumer<Req> config) {
    return send(POST, url, BodyPublishers.ofString(Objects.requireNonNull(body, "body")), config);
  }

  public static Reply post(String url, String body, Duration timeout, Query query, Auth auth, String... headerPairs) {
    return send(POST, url, BodyPublishers.ofString(Objects.requireNonNull(body, "body")), timeout, query, auth, headerPairs);
  }

  public static Reply post(String url, byte[] body, Duration timeout, Query query, Auth auth, String... headerPairs) {
    return send(POST, url, BodyPublishers.ofByteArray(Objects.requireNonNull(body, "body")), timeout, query, auth, headerPairs);
  }

  public static Reply put(String url, String body) {
    return send(PUT, url, BodyPublishers.ofString(Objects.requireNonNull(body, "body")), _ -> {});
  }

  public static Reply put(String url, String body, Duration timeout, Query query, Auth auth, String... headerPairs) {
    return send(PUT, url, BodyPublishers.ofString(Objects.requireNonNull(body, "body")), timeout, query, auth, headerPairs);
  }

  public static Reply put(String url, byte[] body) {
    return send(PUT, url, BodyPublishers.ofByteArray(Objects.requireNonNull(body, "body")), _ -> {});
  }

  public static Reply put(String url, byte[] body, Duration timeout, Query query, Auth auth, String... headerPairs) {
    return send(PUT, url, BodyPublishers.ofByteArray(Objects.requireNonNull(body, "body")), timeout, query, auth, headerPairs);
  }

  public static Reply patch(String url, String body) {
    return send(PATCH, url, BodyPublishers.ofString(Objects.requireNonNull(body, "body")), _ -> {});
  }

  public static Reply patch(String url, String body, Duration timeout, Query query, Auth auth, String... headerPairs) {
    return send(PATCH, url, BodyPublishers.ofString(Objects.requireNonNull(body, "body")), timeout, query, auth, headerPairs);
  }

  public static Reply patch(String url, byte[] body) {
    return send(PATCH, url, BodyPublishers.ofByteArray(Objects.requireNonNull(body, "body")), _ -> {});
  }

  public static Reply patch(String url, byte[] body, Duration timeout, Query query, Auth auth, String... headerPairs) {
    return send(PATCH, url, BodyPublishers.ofByteArray(Objects.requireNonNull(body, "body")), timeout, query, auth, headerPairs);
  }

  public static Reply delete(String url) {
    return send(DELETE, url, BodyPublishers.noBody(), null);
  }

  public static Reply delete(String url, Duration timeout, Query query, Auth auth, String... headerPairs) {
    return send(DELETE, url, BodyPublishers.noBody(), timeout, query, auth, headerPairs);
  }

  public static Reply delete(String url, Consumer<Req> config) {
    return send(DELETE, url, BodyPublishers.noBody(), config);
  }

  // TODO OPTIONS

  public static Retrying retrying(Retry retry) {
    return new Retrying(retry);
  }

  private static Req buildReq(Method method, String url, BodyPublisher body, Duration timeout, Query query, Auth auth, String... headerPairs) {
    URI base = URI.create(Objects.requireNonNull(url, "url"));
    URI uri = (query == null) ? base : query.apply(base);
    if (auth != null) uri = auth.applyToUri(uri);

    if (body == null) body = BodyPublishers.noBody();

    var req = new Req(method.name(), uri).body(body);

    if (timeout != null) req.timeout(timeout);

    // precedence: headerPairs first, then auth (auth wins)
    if (headerPairs != null) req.headers(headerPairs);
    if (auth != null) auth.applyToReq(req);

    return req;
  }

  private static Reply send(Method method, String url, BodyPublisher body, Duration timeout, Query query, Auth auth, String... headerPairs) {
    var req = buildReq(method, url, body, timeout, query, auth, headerPairs);
    return execute(req);
  }

  private static Reply send(Method method, String url, BodyPublisher body, Consumer<Req> config) {
    var req = buildReq(method, url, body, null, null, null, (String[]) null);
    if (config != null) config.accept(req);
    return execute(req);
  }

  private static Reply execute(Req req) {
    try {
      var httpReq = req.toHttpRequest();

      HttpResponse<byte[]> res = CLIENT.send(httpReq, HttpResponse.BodyHandlers.ofByteArray());

      return new Reply(res);
    } catch (Exception e) {
      throw new MercuryException("Mercury request failed: " + req.getMethod() + " " + req.getUri(), e);
    }
  }

  private static Async async() {
    return new Async(null);
  }

  /**
   *
   */
  private static final class Async {
    private final Retry retry;

    private Async(Retry retry) {
      this.retry = retry;
    }

    public CompletableFuture<Reply> fetch(String url) {
      var req = buildReq(GET, url, BodyPublishers.noBody(), null, null, null, (String[]) null);
      return executeAsyncMaybeRetry(req, GET, this.retry);
    }

    public CompletableFuture<Reply> fetch(String url, Duration timeout, Query query, Auth auth, String... headerPairs) {
      var req = buildReq(GET, url, BodyPublishers.noBody(), timeout, query, auth, headerPairs);
      return executeAsyncMaybeRetry(req, GET, this.retry);
    }

    public CompletableFuture<Reply> post(String url, String body) {
      var req = buildReq(POST, url, BodyPublishers.ofString(body), null, null, null, (String[]) null);
      return executeAsyncMaybeRetry(req, POST, this.retry);
    }

    public CompletableFuture<Reply> post(String url, String body, Duration timeout, Query query, Auth auth, String... headerPairs) {
      var req = buildReq(POST, url, BodyPublishers.ofString(body), timeout, query, auth, headerPairs);
      return executeAsyncMaybeRetry(req, POST, this.retry);
    }

    public CompletableFuture<Reply> put(String url, String body) {
      var req = buildReq(PUT, url, BodyPublishers.ofString(body), null, null, null, (String[]) null);
      return executeAsyncMaybeRetry(req, PUT, this.retry);
    }

    public CompletableFuture<Reply> put(String url, String body, Duration timeout, Query query, Auth auth, String... headerPairs) {
      var req = buildReq(PUT, url, BodyPublishers.ofString(body), timeout, query, auth, headerPairs);
      return executeAsyncMaybeRetry(req, PUT, this.retry);
    }

    public CompletableFuture<Reply> patch(String url, String body) {
      var req = buildReq(PATCH, url, BodyPublishers.ofString(body), null, null, null, (String[]) null);
      return executeAsyncMaybeRetry(req, PATCH, this.retry);
    }

    public CompletableFuture<Reply> patch(String url, String body, Duration timeout, Query query, Auth auth, String... headerPairs) {
      var req = buildReq(PATCH, url, BodyPublishers.ofString(body), timeout, query, auth, headerPairs);
      return executeAsyncMaybeRetry(req, PATCH, this.retry);
    }

    public CompletableFuture<Reply> delete(String url) {
      var req = buildReq(DELETE, url, BodyPublishers.noBody(), null, null, null, (String[]) null);
      return executeAsyncMaybeRetry(req, DELETE, this.retry);
    }

    public CompletableFuture<Reply> delete(String url, Duration timeout, Query query, Auth auth, String... headerPairs) {
      var req = buildReq(DELETE, url, BodyPublishers.noBody(), timeout, query, auth, headerPairs);
      return executeAsyncMaybeRetry(req, DELETE, this.retry);
    }

    private static CompletableFuture<Reply> executeAsync(Req req) {
      var httpReq = req.toHttpRequest();
      return CLIENT.sendAsync(httpReq, HttpResponse.BodyHandlers.ofByteArray()).thenApply(Reply::new);
    }

    private static CompletableFuture<Reply> executeAsyncWithRetry(Req req, Method method, Retry retry, int attempt) {
      if (attempt == 1) {
        if (!isMethodRetryAllowed(method, retry) || !req.isRepeatableBody()) {
          return Async.executeAsync(req);
        }
      }

      return Async.executeAsync(req).handle((reply, err) -> {
        if (err == null) {
          if (attempt >= retry.maxAttempts || !shouldRetryStatus(reply)) {
            return CompletableFuture.completedFuture(reply);
          }

          Duration d = retryDelayForStatus(reply, attempt, retry);
          return CompletableFuture.supplyAsync(
              () -> null,
              CompletableFuture.delayedExecutor(d.toMillis(), TimeUnit.MILLISECONDS)
          ).thenCompose(_ -> executeAsyncWithRetry(req, method, retry, attempt + 1));
        } else {
          Throwable cause = (err instanceof CompletionException ce) ? ce.getCause() : err;

          if (attempt >= retry.maxAttempts || !shouldRetryException(cause)) {
            return CompletableFuture.<Reply>failedFuture(cause);
          }

          Duration d = retryDelayForException(attempt, retry);
          return CompletableFuture.supplyAsync(
              () -> null,
              CompletableFuture.delayedExecutor(d.toMillis(), TimeUnit.MILLISECONDS)
          ).thenCompose(_ -> executeAsyncWithRetry(req, method, retry, attempt + 1));
        }
      }).thenCompose(x -> x);
    }

    private static CompletableFuture<Reply> executeAsyncMaybeRetry(Req req, Method method, Retry retry) {

      if (retry == null) {
        return executeAsync(req);
      }

      return executeAsyncWithRetry(req, method, retry, 1);
    }
  }

  /**
   *
   */
  public static final class Retrying {
    private final Retry retry;

    private Retrying(Retry retry) {
      this.retry = Objects.requireNonNull(retry, "retry");
    }

    public Reply fetch(String url) {
      return sendWithRetry(buildReq(GET, url, BodyPublishers.noBody(), null, null, null, (String[]) null), GET);
    }

    public Reply fetch(String url, Duration timeout, Query query, Auth auth, String... headerPairs) {
      return sendWithRetry(buildReq(GET, url, BodyPublishers.noBody(), timeout, query, auth, headerPairs), GET);
    }

    public Reply post(String url, String body, Duration timeout, Query query, Auth auth, String... headerPairs) {
      return sendWithRetry(buildReq(POST, url, BodyPublishers.ofString(Objects.requireNonNull(body, "body")), timeout, query, auth, headerPairs), POST);
    }

    public Reply put(String url, String body, Duration timeout, Query query, Auth auth, String... headerPairs) {
      return sendWithRetry(buildReq(PUT, url, BodyPublishers.ofString(Objects.requireNonNull(body, "body")), timeout, query, auth, headerPairs), PUT);
    }

    public Reply patch(String url, String body, Duration timeout, Query query, Auth auth, String... headerPairs) {
      return sendWithRetry(buildReq(PATCH, url, BodyPublishers.ofString(Objects.requireNonNull(body, "body")), timeout, query, auth, headerPairs), PATCH);
    }

    public Reply delete(String url, Duration timeout, Query query, Auth auth, String... headerPairs) {
      return sendWithRetry(buildReq(DELETE, url, BodyPublishers.noBody(), timeout, query, auth, headerPairs), DELETE);
    }

    public Async async() {
      return new Async(retry);
    }

    private Reply sendWithRetry(Req req, Method method) {
      return executeWithRetry(req, method, retry);
    }
  }

  private static Reply executeWithRetry(Req req, Method method, Retry retry) {
    if (!isMethodRetryAllowed(method, retry)) {
      return execute(req);
    }

    if (!req.isRepeatableBody()) {
      // do not retry unknown/non-repeatable bodies by default.
      return execute(req);
    }

    MercuryException lastException = null;
    Reply lastReply = null;

    for (int attempt = 1; attempt <= retry.maxAttempts; attempt++) {
      try {
        Reply r = execute(req);
        lastReply = r;

        if (!shouldRetryStatus(r) || attempt == retry.maxAttempts) {
          return r;
        }

        Duration d = retryDelayForStatus(r, attempt, retry);
        sleep(d);
      } catch (MercuryException e) {
        lastException = e;

        if (attempt == retry.maxAttempts) {
          throw e;
        }

        if (!shouldRetryException(e.getCause())) {
          throw e;
        }

        Duration d = retryDelayForException(attempt, retry);
        sleep(d);
      }
    }

    if (lastException != null) {
      throw lastException;
    }

    return Objects.requireNonNull(lastReply);
  }

  private static boolean isMethodRetryAllowed(Method method, Retry retry) {
    return switch (method) {
      case GET, PUT, DELETE -> true;
      case POST -> retry.retryPost;
      case PATCH -> retry.retryPatch;
    };
  }

  private static boolean shouldRetryStatus(Reply r) {
    return switch (r.status()) {
      case 429, 502, 503, 504 -> true;
      default -> false;
    };
  }

  private static boolean shouldRetryException(Throwable t) {
    return switch (t) {
      case ConnectException e -> true;
      case SocketTimeoutException e -> true;
      case HttpConnectTimeoutException e -> true;

      case IOException e -> true;

      case null, default -> false;
    };
  }

  private static Duration retryDelayForException(int attempt, Retry retry) {
    return computeBackoff(attempt, retry);
  }

  private static Duration retryDelayForStatus(Reply r, int attempt, Retry retry) {
    // support for Retry-After?
    return computeBackoff(attempt, retry);
  }

  private static Duration computeBackoff(int attempt, Retry retry) {
    long n = Math.max(0, attempt - 1);
    long baseMs = retry.baseDelay.toMillis();

    long ms;
    if (retry.baseDelay.equals(retry.maxDelay)) {
      ms = baseMs;
    } else {
      // exponential: base * 2^n, clamped
      long exp = 1L << Math.min(30, n); // cap shift
      ms = Math.min(retry.maxDelay.toMillis(), Math.multiplyExact(baseMs, exp));
    }

    if (retry.jitter && ms > 1) {
      ms = java.util.concurrent.ThreadLocalRandom.current().nextLong(ms / 2, ms + 1);
    }

    return Duration.ofMillis(ms);
  }

  private static void sleep(Duration d) {
    if (d == null || d.isZero() || d.isNegative()) {
      return;
    }

    try {
      Thread.sleep(d.toMillis());
    } catch (InterruptedException ie) {
      Thread.currentThread().interrupt();
      throw new MercuryException("Retry interrupted", ie);
    }
  }
}
