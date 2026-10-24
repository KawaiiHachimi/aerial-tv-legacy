package app.aerial.legacy;

/** Pure model and cache policy; shared by the API18 UI and host tests. */
final class WeatherData {
  static final long HOUR = 3600000L, RETRY = 600000L, MAX_AGE = 3 * HOUR;
  enum Kind { CLEAR, PARTLY_CLOUDY, CLOUDY, FOG, RAIN, SNOW, THUNDER, UNKNOWN }
  static final class Place {
    final String name, region;
    final double latitude, longitude;
    Place(String name, String region, double latitude, double longitude) {
      if (name == null || name.trim().isEmpty() || name.length() > 80
          || !finite(latitude) || !finite(longitude) || Math.abs(latitude) > 90 || Math.abs(longitude) > 180)
        throw new IllegalArgumentException("Invalid weather city");
      this.name = name.trim(); this.region = region == null ? "" : region;
      this.latitude = latitude; this.longitude = longitude;
    }
  }
  static final class Reading {
    final int celsius, code;
    final boolean day;
    final Integer high, low;
    final long fetchedAt;
    Reading(double temperature, int code, boolean day, Double high, Double low, long fetchedAt) {
      if (!finite(temperature) || temperature < -100 || temperature > 80 || fetchedAt <= 0)
        throw new IllegalArgumentException("Invalid weather reading");
      this.celsius = (int) Math.round(temperature); this.code = code; this.day = day;
      this.high = optional(high); this.low = optional(low); this.fetchedAt = fetchedAt;
      if (this.high != null && this.low != null && this.high < this.low)
        throw new IllegalArgumentException("Invalid daily range");
    }
    boolean fresh(long now) { return now >= fetchedAt && now - fetchedAt < MAX_AGE; }
    Kind kind() { return kindOf(code); }
  }
  static Kind kindOf(int code) {
    if (code == 0) return Kind.CLEAR;
    if (code == 1 || code == 2) return Kind.PARTLY_CLOUDY;
    if (code == 3) return Kind.CLOUDY;
    if (code == 45 || code == 48) return Kind.FOG;
    if ((code >= 71 && code <= 77) || code == 85 || code == 86) return Kind.SNOW;
    if (code == 95 || code == 96 || code == 99) return Kind.THUNDER;
    if ((code >= 51 && code <= 67) || (code >= 80 && code <= 82)) return Kind.RAIN;
    return Kind.UNKNOWN;
  }
  static boolean due(long now, long lastAttempt, boolean failed) {
    return lastAttempt == 0 || now < lastAttempt || now - lastAttempt >= (failed ? RETRY : HOUR);
  }
  static boolean finite(double value) { return !Double.isNaN(value) && !Double.isInfinite(value); }
  private static Integer optional(Double value) {
    return value != null && finite(value) && value >= -100 && value <= 80 ? (int) Math.round(value) : null;
  }
}
