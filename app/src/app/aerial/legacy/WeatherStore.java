package app.aerial.legacy;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.util.Log;
import org.json.*;
import java.util.*;
import java.util.concurrent.*;

/** One bounded worker; refreshed by Home's existing minute tick, never a background service. */
final class WeatherStore {
  private static final int NONE = Integer.MIN_VALUE;
  private final Context context;
  private final SharedPreferences prefs;
  private final WeatherApi api;
  private final Handler main;
  private final Runnable changed;
  private final ExecutorService worker = Executors.newSingleThreadExecutor();
  private Future<?> task;
  private volatile WeatherData.Reading cached;
  private boolean active, closed, running, failed;
  private int generation;
  private long lastAttempt;
  WeatherStore(Context c, Handler handler, Runnable changed, WeatherApi api) {
    context = c.getApplicationContext(); prefs = context.getSharedPreferences("weather-v1", Context.MODE_PRIVATE);
    main = handler; this.changed = changed; this.api = api;
    try {
      if (prefs.contains("fetched_at")) cached = new WeatherData.Reading(prefs.getInt("temperature", 0),
          prefs.getInt("code", -1), prefs.getBoolean("day", true), optional("high"), optional("low"), prefs.getLong("fetched_at", 0));
    } catch (RuntimeException invalidCache) { cached = null; }
  }
  private Double optional(String key) { int v = prefs.getInt(key, NONE); return v == NONE ? null : (double) v; }
  boolean enabled() { return prefs.getBoolean("enabled", false); }
  WeatherData.Place place() {
    try {
      return new WeatherData.Place(prefs.getString("city", ""), prefs.getString("region", ""),
          Double.parseDouble(prefs.getString("latitude", "")), Double.parseDouble(prefs.getString("longitude", "")));
    } catch (RuntimeException absent) { return null; }
  }
  WeatherData.Reading reading() {
    WeatherData.Reading value = cached;
    return enabled() && value != null && value.fresh(System.currentTimeMillis()) ? value : null;
  }
  synchronized boolean cachedAfterFailure() { return failed && reading() != null; }
  synchronized void onShown() { if (!closed) { active = true; refresh(false); } }
  synchronized void onHidden() {
    active = false; generation++;
    if (task != null && running) { task.cancel(true); lastAttempt = 0; }
    running = false;
  }
  synchronized void close() { onHidden(); closed = true; worker.shutdownNow(); }
  private void notifyChanged() {
    main.post(new Runnable() { public void run() {
      synchronized (WeatherStore.this) { if (closed || !active) return; }
      changed.run();
    }});
  }
  synchronized void refresh(boolean force) {
    final WeatherData.Place city = place();
    if (closed || !active || !enabled() || city == null || running) return;
    if (!force && !WeatherData.due(System.currentTimeMillis(), lastAttempt, failed)) return;
    if (force && lastAttempt > 0 && System.currentTimeMillis() - lastAttempt < 10000) return;
    lastAttempt = System.currentTimeMillis(); running = true;
    final int request = generation;
    task = worker.submit(new Runnable() { public void run() {
      try {
        WeatherData.Reading result = api.fetch(city, System.currentTimeMillis());
        synchronized (WeatherStore.this) {
          if (closed || !active || request != generation) return;
          cached = result; failed = false;
          prefs.edit().putInt("temperature", result.celsius).putInt("code", result.code).putBoolean("day", result.day)
              .putInt("high", result.high == null ? NONE : result.high).putInt("low", result.low == null ? NONE : result.low)
              .putLong("fetched_at", result.fetchedAt).apply();
        }
      } catch (Exception unavailable) {
        synchronized (WeatherStore.this) {
          if (closed || request != generation) return;
          failed = true;
        }
        Log.w("AerialWeather", "Weather request failed", unavailable);
      } finally {
        synchronized (WeatherStore.this) { if (request == generation) running = false; }
        notifyChanged();
      }
    }});
    notifyChanged();
  }
  List<WeatherData.Place> search(String name) throws Exception { return api.search(name); }
  synchronized void configure(JSONObject values) throws JSONException {
    if (closed || !active) throw new IllegalStateException("Home closed");
    WeatherData.Place selected = null;
    if (values.has("latitude") || values.has("longitude")) selected = new WeatherData.Place(values.getString("name"),
        values.optString("region"), values.getDouble("latitude"), values.getDouble("longitude"));
    WeatherData.Place previous = place();
    boolean newCity = selected != null && (previous == null || !selected.name.equals(previous.name)
        || selected.latitude != previous.latitude || selected.longitude != previous.longitude);
    generation++;
    if (task != null) task.cancel(true);
    running = false; lastAttempt = 0; failed = false;
    SharedPreferences.Editor edit = prefs.edit();
    if (selected != null) edit.putString("city", selected.name).putString("region", selected.region)
        .putString("latitude", Double.toString(selected.latitude)).putString("longitude", Double.toString(selected.longitude));
    if (newCity) { cached = null; edit.remove("fetched_at"); }
    if (values.has("enabled")) edit.putBoolean("enabled", values.getBoolean("enabled"));
    else if (selected != null) edit.putBoolean("enabled", true);
    edit.apply(); refresh(true); notifyChanged();
  }
  synchronized JSONObject state() throws JSONException {
    WeatherData.Place city = place(); WeatherData.Reading value = reading();
    String status = !enabled() ? "disabled" : city == null ? "choose_city" : running ? "loading"
        : failed ? (value == null ? "unavailable" : "cached") : value == null ? "stale" : "ok";
    JSONObject data = new JSONObject().put("enabled", enabled()).put("city", city == null ? "" : city.name)
        .put("region", city == null ? "" : city.region).put("status", status).put("provider", "Open-Meteo");
    if (value != null) data.put("temperature", value.celsius).put("kind", context.getString(kindLabel(value.kind())))
        .put("high", value.high == null ? JSONObject.NULL : value.high).put("low", value.low == null ? JSONObject.NULL : value.low)
        .put("fetched_at", value.fetchedAt);
    return data;
  }
  static JSONObject cityJson(WeatherData.Place city) throws JSONException {
    return new JSONObject().put("name", city.name).put("region", city.region)
        .put("latitude", city.latitude).put("longitude", city.longitude);
  }
  static int kindLabel(WeatherData.Kind kind) {
    int[] ids = {R.string.weather_clear, R.string.weather_partly_cloudy, R.string.weather_cloudy, R.string.weather_fog,
        R.string.weather_rain, R.string.weather_snow, R.string.weather_thunder, R.string.weather_unknown};
    return ids[kind.ordinal()];
  }
}
