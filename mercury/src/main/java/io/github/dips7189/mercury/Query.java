package io.github.dips7189.mercury;

import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public interface Query {
  URI apply(URI base);

  static Query qp(String... pairs) {
    var items = pairsToItems(pairs);
    return base -> appendQuery(base, items);
  }

  static Query qp(Map<String, String> params) {
    Objects.requireNonNull(params, "params");
    return qpReplace(params);
  }

  static Query qpReplace(Map<String, String> params) {
    Objects.requireNonNull(params, "params");

    List<Map.Entry<String, String>> items = new ArrayList<>(params.size());
    for (var e : params.entrySet()) {
      items.add(Map.entry(
          Objects.requireNonNull(e.getKey(), "query key is null"),
          e.getValue()
      ));
    }

    Set<String> keys = params.keySet();
    return base -> replaceThenAppend(base, keys, items);
  }

  static Query qpReplace(String... pairs) {
    var items = pairsToItems(pairs);
    var keys = keysFromPairs(pairs);
    return base -> replaceThenAppend(base, keys, items);
  }

  static Query qpReplace(Set<String> keysToRemove, String... pairs) {
    Objects.requireNonNull(keysToRemove, "keysToRemove");
    var items = pairsToItems(pairs);
    return base -> replaceThenAppend(base, keysToRemove, items);
  }

  private static List<Map.Entry<String, String>> pairsToItems(String... pairs) {
    if (pairs == null) throw new IllegalArgumentException("pairs is null");
    if ((pairs.length & 1) != 0) {
      throw new IllegalArgumentException("qp requires even number of strings: k1,v1,k2,v2...");
    }
    List<Map.Entry<String, String>> items = new ArrayList<>(pairs.length / 2);
    for (int i = 0; i < pairs.length; i += 2) {
      String k = Objects.requireNonNull(pairs[i], "query key is null");
      String v = pairs[i + 1]; // policy: allow null => k=
      items.add(Map.entry(k, v));
    }
    return items;
  }

  private static URI replaceThenAppend(URI base, Set<String> keysToRemove, List<Map.Entry<String, String>> items) {
    String fragment = base.getRawFragment();
    String existing = base.getRawQuery();
    String filtered = removeKeysFromRawQuery(existing, keysToRemove);

    URI cleaned;
    try {
      cleaned = new URI(
          base.getScheme(),
          base.getRawAuthority(),
          base.getRawPath(),
          filtered,     // already raw
          fragment
      );
    } catch (java.net.URISyntaxException impossible) {
      throw new IllegalStateException("BUG: invalid URI built from raw components", impossible);
    }

    return appendQuery(cleaned, items);
  }

  private static Set<String> keysFromPairs(String... pairs) {
    if ((pairs.length & 1) != 0) {
      throw new IllegalArgumentException("qp requires even number of strings: k1,v1,k2,v2...");
    }

    Set<String> keys = new HashSet<>();
    for (int i = 0; i < pairs.length; i += 2) {
      keys.add(Objects.requireNonNull(pairs[i], "query key is null"));
    }

    return keys;
  }

  private static String removeKeysFromRawQuery(String rawQuery, Set<String> keysToRemove) {
    if (rawQuery == null || rawQuery.isEmpty()) return rawQuery;

    StringBuilder out = new StringBuilder(rawQuery.length());
    int start = 0;

    while (start <= rawQuery.length()) {
      int amp = rawQuery.indexOf('&', start);
      String part;
      int next;
      if (amp == -1) {
        part = rawQuery.substring(start);
        next = rawQuery.length() + 1;
      } else {
        part = rawQuery.substring(start, amp);
        next = amp + 1;
      }

      if (!part.isEmpty()) {
        int eq = part.indexOf('=');
        String rawKey = (eq == -1) ? part : part.substring(0, eq);

        String key = decodePercent(rawKey);
        if (!keysToRemove.contains(key)) {
          if (out.length() > 0) out.append('&');
          out.append(part); // keep raw part exactly as-is
        }
      }

      start = next;
      if (amp == -1) break;
    }

    return out.length() == 0 ? null : out.toString();
  }

  private static String decodePercent(String s) {
    // only handles %HH sequences; leaves '+' untouched (important!)
    int n = s.length();
    byte[] buf = new byte[n];
    int bi = 0;

    for (int i = 0; i < n; ) {
      char c = s.charAt(i);
      if (c == '%' && i + 2 < n) {
        int hi = Character.digit(s.charAt(i + 1), 16);
        int lo = Character.digit(s.charAt(i + 2), 16);
        if (hi >= 0 && lo >= 0) {
          buf[bi++] = (byte) ((hi << 4) + lo);
          i += 3;
          continue;
        }
      }
      // ASCII char passthrough (good enough for typical keys)
      buf[bi++] = (byte) c;
      i++;
    }

    return new String(buf, 0, bi, java.nio.charset.StandardCharsets.UTF_8);
  }

  private static URI appendQuery(URI base, List<Map.Entry<String, String>> items) {
    String existing = base.getRawQuery();
    String fragment = base.getRawFragment();

    StringBuilder q = new StringBuilder();
    if (existing != null && !existing.isEmpty()) q.append(existing);

    for (var e : items) {
      if (q.length() > 0) q.append('&');

      String k = encodeQueryComponent(e.getKey());
      String v = e.getValue();

      // Policy: null => key=   (least surprising)
      q.append(k).append('=');
      if (v != null) q.append(encodeQueryComponent(v));
    }

    try {
      return new URI(
          base.getScheme(),
          base.getRawAuthority(),
          base.getRawPath(),
          q.isEmpty() ? null : q.toString(),
          fragment
      );
    } catch (URISyntaxException impossible) {
      throw new IllegalStateException(impossible);
    }
  }

  private static String encodeQueryComponent(String s) {
    if (s == null) {
      throw new IllegalArgumentException("null query component");
    }

    byte[] bytes = s.getBytes(StandardCharsets.UTF_8);
    var out = new StringBuilder(bytes.length * 3);

    for (byte b : bytes) {
      int c = b & 0xff;
      boolean unreserved =
          (c >= 'a' && c <= 'z') ||
              (c >= 'A' && c <= 'Z') ||
              (c >= '0' && c <= '9') ||
              c == '-' || c == '.' || c == '_' || c == '~';

      if (unreserved) {
        out.append((char) c);
      } else {
        out.append('%');
        out.append(Character.toUpperCase(Character.forDigit((c >>> 4) & 0xF, 16)));
        out.append(Character.toUpperCase(Character.forDigit(c & 0xF, 16)));
      }
    }

    return out.toString();
  }
}