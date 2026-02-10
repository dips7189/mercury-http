package io.github.dips7189.mercury;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest.BodyPublisher;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

import static io.github.dips7189.mercury.Method.DELETE;
import static io.github.dips7189.mercury.Method.GET;
import static io.github.dips7189.mercury.Method.PATCH;
import static io.github.dips7189.mercury.Method.POST;
import static io.github.dips7189.mercury.Method.PUT;

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

  private static Reply send(Method method, String url, BodyPublisher body, Duration timeout, Query query, Auth auth, String... headerPairs) {
    URI base = URI.create(Objects.requireNonNull(url, "url"));
    URI uri = query == null ? base : query.apply(base);
    if (auth != null) uri = auth.applyToUri(uri);

    var req = new Req(method.name(), uri).body(body);

    if (timeout != null) req.timeout(timeout);

    // precedence: headerPairs first, then auth (auth wins)
    if (headerPairs != null) req.headers(headerPairs);
    if (auth != null) auth.applyToReq(req);

    return execute(req);
  }

  private static Reply send(Method method, String url, BodyPublisher body, Consumer<Req> config) {
    URI uri = URI.create(Objects.requireNonNull(url, "url"));
    var req = new Req(method.name(), uri).body(body);
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
    return Async.INSTANCE;
  }

  /**
   *
   */
  private static final class Async {
    private static final Async INSTANCE = new Async();

    private Async() {}

    public CompletableFuture<Reply> fetch(String url) {
      return sendAsync(GET, url, BodyPublishers.noBody(), null, null, null, (String[]) null);
    }

    public CompletableFuture<Reply> fetch(String url, Duration timeout, Query query, Auth auth, String... headerPairs) {
      return sendAsync(GET, url, BodyPublishers.noBody(), timeout, query, auth, headerPairs);
    }

    public CompletableFuture<Reply> post(String url, String body) {
      return sendAsync(POST, url, BodyPublishers.ofString(body), null, null, null, (String[]) null);
    }

    public CompletableFuture<Reply> post(String url, String body, Duration timeout, Query query, Auth auth, String... headerPairs) {
      return sendAsync(POST, url, BodyPublishers.ofString(body), timeout, query, auth, headerPairs);
    }

    public CompletableFuture<Reply> put(String url, String body) {
      return sendAsync(PUT, url, BodyPublishers.ofString(body), null, null, null, (String[]) null);
    }

    public CompletableFuture<Reply> put(String url, String body, Duration timeout, Query query, Auth auth, String... headerPairs) {
      return sendAsync(PUT, url, BodyPublishers.ofString(body), timeout, query, auth, headerPairs);
    }

    public CompletableFuture<Reply> patch(String url, String body) {
      return sendAsync(PATCH, url, BodyPublishers.ofString(body), null, null, null, (String[]) null);
    }

    public CompletableFuture<Reply> patch(String url, String body, Duration timeout, Query query, Auth auth, String... headerPairs) {
      return sendAsync(PATCH, url, BodyPublishers.ofString(body), timeout, query, auth, headerPairs);
    }

    public CompletableFuture<Reply> delete(String url) {
      return sendAsync(DELETE, url, BodyPublishers.noBody(), null, null, null, (String[]) null);
    }

    public CompletableFuture<Reply> delete(String url, Duration timeout, Query query, Auth auth, String... headerPairs) {
      return sendAsync(DELETE, url, BodyPublishers.noBody(), timeout, query, auth, headerPairs);
    }

    private static CompletableFuture<Reply> executeAsync(Req req) {
      var httpReq = req.toHttpRequest();
      return CLIENT.sendAsync(httpReq, HttpResponse.BodyHandlers.ofByteArray()).thenApply(Reply::new);
    }

    private static CompletableFuture<Reply> sendAsync(Method method, String url, BodyPublisher body, Duration timeout, Query query, Auth auth, String... headerPairs) {
      URI base = URI.create(Objects.requireNonNull(url, "url"));
      URI uri = query == null ? base : query.apply(base);
      if (auth != null) uri = auth.applyToUri(uri);

      if (body == null) {
        body = BodyPublishers.noBody();
      }

      var req = new Req(method.name(), uri).body(body);

      if (timeout != null) req.timeout(timeout);
      if (headerPairs != null) req.headers(headerPairs);
      if (auth != null) auth.applyToReq(req);

      return executeAsync(req);
    }
  }
}
