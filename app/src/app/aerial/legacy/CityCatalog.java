package app.aerial.legacy;

import java.io.*;
import java.util.*;
import java.util.regex.Pattern;

/** Aerial's GeoNames city list, used before online geocoding to resolve Chinese names reliably. */
final class CityCatalog {
  private static final Pattern SUFFIX = Pattern.compile("(壮族自治区|回族自治区|维吾尔自治区|特别行政区|自治区|自治州|自治县|地区|新区|省|市|县|区|盟|旗)$");
  private static final class Entry {
    final WeatherData.Place place;
    final String[] aliases;
    Entry(String[] c) {
      place = new WeatherData.Place(c[0], c[2], Double.parseDouble(c[3]), Double.parseDouble(c[4]));
      aliases = c[1].split(",");
    }
  }
  private final List<Entry> entries = new ArrayList<Entry>();
  CityCatalog(InputStream stream) throws IOException {
    BufferedReader in = new BufferedReader(new InputStreamReader(stream, "UTF-8"));
    try {
      String line;
      while ((line = in.readLine()) != null) {
        if (line.isEmpty() || line.startsWith("#")) continue;
        String[] c = line.split("\t", -1);
        if (c.length >= 7) entries.add(new Entry(c));
      }
    } finally { in.close(); }
  }
  List<WeatherData.Place> search(String typed) {
    String q = typed.replaceAll("\\s", "").replaceFirst("^中国", "");
    List<WeatherData.Place> result = new ArrayList<WeatherData.Place>();
    if (!q.matches("[\\u4e00-\\u9fff]+")) return result;
    // Exact names precede aliases; preserve GeoNames city-before-county ordering.
    for (int pass = 0; pass < 2; pass++) for (Entry e : entries) {
      WeatherData.Place p = e.place;
      String local = q.startsWith(p.region) && !p.region.isEmpty()
          ? shortName(q.substring(p.region.length()).replaceFirst("^(省|市|壮族自治区|回族自治区|维吾尔自治区|自治区)", "")) : "";
      boolean match = pass == 0 ? p.name.equals(shortName(q)) || p.name.equals(local)
          : Arrays.asList(e.aliases).contains(shortName(q)) || (!local.isEmpty() && Arrays.asList(e.aliases).contains(local));
      if (match && !result.contains(p)) result.add(p);
      if (result.size() == 10) return result;
    }
    return result;
  }
  private static String shortName(String name) {
    String trimmed = SUFFIX.matcher(name).replaceFirst("");
    return trimmed.length() >= 2 ? trimmed : name;
  }
}
