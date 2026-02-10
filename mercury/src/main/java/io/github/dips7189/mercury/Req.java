package io.github.dips7189.mercury;

import java.net.URI;
import java.net.http.HttpRequest;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public final class Req {
  private final String method;
  private final URI uri;

  private Duration timeout;
  private final Map<String, List<String>> headers = new LinkedHashMap<>();

  private HttpRequest.BodyPublisher bodyPublisher = HttpRequest.BodyPublishers.noBody();

  Req(String method, URI uri) {
    this.method = method;
    this.uri = uri;
  }

  public Req timeout(Duration d) {
    this.timeout = d;
    return this;
  }

  public Req header(String name, String value) {
    Objects.requireNonNull(name, "header name");
    Objects.requireNonNull(value, "header value");
    headers.computeIfAbsent(name, _ -> new ArrayList<>()).add(value);
    return this;
  }

  public Req headers(String... pairs) {
    if ((pairs.length & 1) != 0) {
      throw new IllegalArgumentException("headerPairs must be even: name,value,...");
    }

    for (int i = 0; i < pairs.length; i += 2) {
      header(pairs[i], pairs[i + 1]);
    }

    return this;
  }

  public Req headers(Map<String, String> m) {
    if (m == null) return this;

    for (var e : m.entrySet()) {
      setHeader(e.getKey(), e.getValue());
    }

    return this;
  }

  public Req headersMulti(Map<String, List<String>> m) {
    if (m == null) return this;

    for (var e : m.entrySet()) {
      removeHeader(e.getKey());
      var values = e.getValue();
      if (values == null) continue;
      for (String v : values) {
        if (v != null) header(e.getKey(), v);
      }
    }

    return this;
  }

  public Req removeHeader(String name) {
    if (name == null) return this;
    // remove case-insensitively without rebuilding entire map:
    String toRemove = null;
    for (String k : headers.keySet()) {
      if (k.equalsIgnoreCase(name)) {
        toRemove = k;
        break;
      }
    }

    if (toRemove != null) {
      headers.remove(toRemove);
    }

    return this;
  }

  public Req setHeader(String name, String value) {
    Objects.requireNonNull(name, "header name");
    Objects.requireNonNull(value, "header value");
    removeHeader(name);
    header(name, value);
    return this;
  }

  public Req bodyString(String s) {
    return bodyString(s, StandardCharsets.UTF_8);
  }

  public Req bodyString(String s, Charset charset) {
    Objects.requireNonNull(s, "body");
    Objects.requireNonNull(charset, "charset");
    this.bodyPublisher = HttpRequest.BodyPublishers.ofString(s, charset);
    return this;
  }

  public Req bodyBytes(byte[] bytes) {
    Objects.requireNonNull(bytes, "body");
    this.bodyPublisher = HttpRequest.BodyPublishers.ofByteArray(bytes);
    return this;
  }

  public Req body(HttpRequest.BodyPublisher publisher) {
    this.bodyPublisher = Objects.requireNonNull(publisher, "publisher");
    return this;
  }

  HttpRequest toHttpRequest() {
    var b = HttpRequest.newBuilder(uri).method(method, bodyPublisher);
    if (timeout != null) b.timeout(timeout);
    headers.forEach((k, vs) -> vs.forEach(v -> b.header(k, v)));
    return b.build();
  }

  public String getMethod() {
    return method;
  }

  public URI getUri() {
    return uri;
  }
}
