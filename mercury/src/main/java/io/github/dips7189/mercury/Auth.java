package io.github.dips7189.mercury;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Objects;

import static io.github.dips7189.mercury.Query.qpReplace;

public interface Auth {
  default URI applyToUri(URI uri) {
    return uri;
  }

  default void applyToReq(Req req) {
  }

  static Auth bearer(String token) {
    Objects.requireNonNull(token, "token");
    return new Auth() {
      @Override
      public void applyToReq(Req req) {
        req.setHeader("Authorization", "Bearer " + token); // replace
      }

      @Override
      public String toString() {
        return "Auth(bearer ****)";
      }
    };
  }

  static Auth basic(String user, String pass) {
    Objects.requireNonNull(user, "user");
    Objects.requireNonNull(pass, "pass");
    String creds = user + ":" + pass;
    String b64 = Base64.getEncoder().encodeToString(creds.getBytes(StandardCharsets.ISO_8859_1));
    return new Auth() {
      @Override
      public void applyToReq(Req req) {
        req.setHeader("Authorization", "Basic " + b64); // replace
      }

      @Override
      public String toString() {
        return "Auth(basic ****)";
      }
    };
  }

  static Auth headerAuth(String headerName, String value) {
    Objects.requireNonNull(headerName, "headerName");
    Objects.requireNonNull(value, "value");
    return new Auth() {
      @Override
      public void applyToReq(Req req) {
        req.setHeader(headerName, value); // replace by default
      }

      @Override
      public String toString() {
        return "Auth(header " + headerName + " ****)";
      }
    };
  }

  static Auth queryAuth(String key, String value) {
    Objects.requireNonNull(key, "key");
    Objects.requireNonNull(value, "value");
    Query q = qpReplace(key, value); // replace semantics for that key
    return new Auth() {
      @Override
      public URI applyToUri(URI uri) {
        return q.apply(uri);
      }

      @Override
      public String toString() {
        return "Auth(query " + key + "=****)";
      }
    };
  }

  static Auth chain(Auth... auths) {
    Objects.requireNonNull(auths, "auths");
    return new Auth() {
      @Override
      public URI applyToUri(URI uri) {
        URI u = uri;
        for (Auth a : auths) if (a != null) u = a.applyToUri(u);
        return u;
      }

      @Override
      public void applyToReq(Req req) {
        for (Auth a : auths) if (a != null) a.applyToReq(req);
      }

      @Override
      public String toString() {
        return "Auth(chain)";
      }
    };
  }
}
