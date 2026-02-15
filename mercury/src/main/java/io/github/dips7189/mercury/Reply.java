package io.github.dips7189.mercury;

import java.net.URI;
import java.net.http.HttpResponse;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * An immutable view over an HTTP response returned by Mercury.
 *
 * <p>The underlying response data is captured once at receive time and never modified.
 * {@code Reply} itself is immutable and thread-safe.</p>
 *
 * <p>Derived views such as {@link #text()} and header lookups are computed lazily and cached
 * on first access for performance.</p>
 *
 * <p><strong>Body mutability note:</strong> {@link #content()} returns the underlying byte array
 * backing the response body for efficiency. Callers must not modify the returned array.
 * If a defensive copy is required, use {@link #contentCopy()}.</p>
 */
public final class Reply {

  private final HttpResponse<byte[]> raw;

  Reply(HttpResponse<byte[]> raw) {
    this.raw = raw;
  }

  /**
   * Return the HTTP status code.
   * @return The HTTP status code.
   */
  public int status() {
    return raw.statusCode();
  }

  public URI uri() {
    return raw.uri();
  }

  /**
   * Returns an unmodifiable multi Map view of this HttpHeaders.
   *
   * @return The Map.
   */
  public Map<String, List<String>> headers() {
    return raw.headers().map();
  }

  /**
   * Returns the first value of the specified header, case-insensitively.
   *
   * <p>If the header is not present, an empty {@code Optional} is returned.</p>
   *
   * @param name
   * @return
   */
  public Optional<String> header(String name) {
    return raw.headers().firstValue(name);
  }

  /**
   * Returns an unmodifiable List of all of the header string values of the
   * given named header. Always returns a List, which may be empty if the
   * header is not present.
   *
   * @param name the header name
   * @return a List of headers string values
   */
  public List<String> headers(String name) {
    return raw.headers().allValues(name);
  }

  /**
   * Returns whether the HTTP Status is a success status or not.
   *
   * @return whether the status is successful.
   */
  public boolean ok() {
    return status() >= 200 && status() < 300;
  }

  /**
   * Returns the raw response body bytes.
   *
   * <p>The returned array is the underlying buffer used by this {@code Reply} and must not
   * be modified by the caller.</p>
   *
   * <p>This method avoids copying for performance.</p>
   */
  public byte[] content() {
    return raw.body();
  }

  /**
   * Returns a defensive copy of the response body bytes.
   *
   * <p>This method allocates a new array and is safe to modify.</p>
   */
  public byte[] contentCopy() {
    return raw.body().clone();
  }

  /**
   * Returns the response body decoded as text.
   *
   * <p>The charset is determined from the {@code Content-Type} header if present,
   * otherwise UTF-8 is used.</p>
   *
   * <p>The decoded value is computed lazily and cached on first access.</p>
   */
  public String text() {
    return new String(raw.body(), charset());
  }

  // todo method to return JSON ...

  private Charset charset() {
    return header("Content-Type")
        .flatMap(Reply::extractCharset)
        .orElse(StandardCharsets.UTF_8);
  }

  private static Optional<Charset> extractCharset(String ct) {
    int i = ct.toLowerCase(Locale.ROOT).indexOf("charset=");
    if (i == -1) return Optional.empty();

    String cs = ct.substring(i + 8).trim();
    try {
      return Optional.of(Charset.forName(cs));
    } catch (Exception e) {
      return Optional.empty();
    }
  }
}
