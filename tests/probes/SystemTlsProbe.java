package app.aerial.legacy;

import java.io.*;
import java.util.zip.ZipFile;

/** API18 app_process probe: native lookup must work through /system/lib, without an APK update. */
public final class SystemTlsProbe {
  public static void main(String[] args) throws Exception {
    final ZipFile apk = new ZipFile(args[0]);
    try {
      WeatherApi.Assets assets = new WeatherApi.Assets() {
        public InputStream open(String path) throws IOException {
          return apk.getInputStream(apk.getEntry("assets/" + path));
        }
      };
      WeatherApi api = new WeatherApi(new LegacyHttp(assets), assets);
      if (args.length > 1 && "--location".equals(args[1])) {
        WeatherData.Place city = api.locate();
        if (!api.fetch(city, System.currentTimeMillis()).fresh(System.currentTimeMillis()))
          throw new AssertionError("Missing automatic-location forecast");
        System.out.println("IP city lookup and matching forecast passed; no IP address logged");
      }
      WeatherData.Reading reading = api.fetch(api.search("杭州").get(0), System.currentTimeMillis());
      if (!reading.fresh(System.currentTimeMillis())) throw new AssertionError("Stale forecast");
      String bing = new LegacyHttp(assets).getText("https://cn.bing.com/HPImageArchive.aspx?format=js&idx=0&n=1&mkt=zh-CN");
      if (!bing.contains("images")) throw new AssertionError("Missing Bing response");
      System.out.println("API18 system-library lookup, weather TLS and Bing TLS passed");
    } finally { apk.close(); }
  }
}
