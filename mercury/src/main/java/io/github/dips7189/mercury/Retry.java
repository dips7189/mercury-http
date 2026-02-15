package io.github.dips7189.mercury;

import java.time.Duration;

public final class Retry {

  public final int maxAttempts;
  public final Duration baseDelay;
  public final Duration maxDelay;
  public final boolean jitter;
  public final boolean retryPatch;
  public final boolean retryPost;
  public final boolean respectRetryAfter;

  public Retry(int maxAttempts, Duration baseDelay, Duration maxDelay, boolean jitter, boolean retryPatch, boolean retryPost, boolean respectRetryAfter) {
    this.maxAttempts = maxAttempts;
    this.baseDelay = baseDelay;
    this.maxDelay = maxDelay;
    this.jitter = jitter;
    this.retryPatch = retryPatch;
    this.retryPost = retryPost;
    this.respectRetryAfter = respectRetryAfter;
  }

  public static Retry fixed(int maxAttempts, Duration delay) {
    return new Retry(maxAttempts, delay, delay, false, false, false, true);
  }

  public static Retry exponential(int maxAttempts, Duration baseDelay, Duration maxDelay, boolean jitter) {
    return new Retry(maxAttempts, baseDelay, maxDelay, jitter, false, false, true);
  }

  public Retry allowPost()  {
    return new Retry(maxAttempts, baseDelay, maxDelay, jitter, retryPatch, true, respectRetryAfter);
  }

  public Retry allowPatch() {
    return new Retry(maxAttempts, baseDelay, maxDelay, jitter, true, retryPost, respectRetryAfter);
  }
}
