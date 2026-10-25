package app.aerial.legacy;

import org.json.*;
import java.io.*;
import java.net.URLEncoder;
import java.util.*;

/** Open-Meteo protocol, independent of Activity/lifecycle so parsing and transport can be tested. */
final class WeatherApi {
  interface Transport { String getText(String url) throws Exception; }
  interface Assets { InputStream open(String path) throws IOException; }
  private final Transport http;
  private final Assets assets;
  private CityCatalog cities;
  WeatherApi(Transport http, Assets assets) { this.http = http; this.assets = assets; }
  WeatherData.Place locate() throws Exception {
    // Omitting an address asks for the connection's public exit location. Do not request/store the IP itself.
    return parseLocation(http.getText("https://ipwho.is/?fields=success,message,city,region,latitude,longitude&lang=zh-CN"));
  }
  static WeatherData.Place parseLocation(String text) throws Exception {
    JSONObject value = new JSONObject(text);
    if (!value.getBoolean("success")) throw new IOException("Network location unavailable");
    return new WeatherData.Place(value.getString("city"), value.optString("region"),
        value.getDouble("latitude"), value.getDouble("longitude"));
  }
  List<WeatherData.Place> search(String name) throws Exception {
    name = name.trim();
    if (name.isEmpty() || name.length() > 80) throw new IllegalArgumentException("Invalid city name");
    synchronized (this) { if (cities == null) cities = new CityCatalog(assets.open("cities-cn.tsv")); }
    List<WeatherData.Place> local = cities.search(name);
    if (!local.isEmpty()) return local;
    return parsePlaces(http.getText("https://geocoding-api.open-meteo.com/v1/search?name="
        + URLEncoder.encode(name, "UTF-8") + "&count=10&language=zh&format=json"));
  }
  WeatherData.Reading fetch(WeatherData.Place city, long now) throws Exception {
    return parseReading(http.getText("https://api.open-meteo.com/v1/forecast?latitude=" + city.latitude
        + "&longitude=" + city.longitude + "&current=temperature_2m,weather_code,is_day"
        + "&daily=temperature_2m_max,temperature_2m_min&forecast_days=1&timezone=auto"), now);
  }
  static WeatherData.Reading parseReading(String text, long now) throws JSONException {
    JSONObject root = new JSONObject(text), current = root.getJSONObject("current");
    if (!"°C".equals(root.getJSONObject("current_units").getString("temperature_2m")))
      throw new JSONException("Unexpected temperature units");
    if (current.isNull("temperature_2m") || current.isNull("weather_code")) throw new JSONException("Missing current weather");
    double code = current.getDouble("weather_code");
    if (!WeatherData.finite(code) || code != (int) code) throw new JSONException("Invalid weather code");
    JSONObject daily = root.optJSONObject("daily");
    return new WeatherData.Reading(current.getDouble("temperature_2m"), (int) code,
        current.optInt("is_day", 1) == 1, daily(daily, "temperature_2m_max"), daily(daily, "temperature_2m_min"), now);
  }
  private static Double daily(JSONObject object, String key) {
    JSONArray array = object == null ? null : object.optJSONArray(key);
    return array == null || array.length() == 0 || array.isNull(0) ? null : array.optDouble(0);
  }
  static List<WeatherData.Place> parsePlaces(String text) throws JSONException {
    JSONArray values = new JSONObject(text).optJSONArray("results");
    List<WeatherData.Place> result = new ArrayList<WeatherData.Place>();
    if (values == null) return result;
    for (int pass = 0; pass < 2; pass++) for (int i = 0; i < values.length() && result.size() < 10; i++) {
      JSONObject v = values.getJSONObject(i);
      boolean city = v.optString("feature_code").startsWith("PPLA") || v.optString("feature_code").equals("PPLC");
      if (city != (pass == 0)) continue;
      try {
        result.add(new WeatherData.Place(v.getString("name"), v.optString("admin1") + " · " + v.optString("country"),
            v.getDouble("latitude"), v.getDouble("longitude")));
      } catch (IllegalArgumentException invalidPlace) { /* Skip malformed results, preserve other candidates. */ }
    }
    return result;
  }
}
