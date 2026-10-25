package app.aerial.legacy;

import java.io.*;
import java.net.*;
import java.security.KeyStore;
import java.security.cert.CertificateFactory;
import java.util.HashMap;
import java.util.Map;
import javax.net.ssl.*;

/** Small, bounded HTTPS client shared by wallpaper and weather; public roots stay host-scoped. */
class LegacyHttp implements WeatherApi.Transport {
  private final WeatherApi.Assets assets;
  private final Map<String, SSLSocketFactory> factories = new HashMap<String, SSLSocketFactory>();
  LegacyHttp(WeatherApi.Assets assets) { this.assets = assets; }
  public String getText(String url) throws Exception { return new String(get(url, 256 * 1024), "UTF-8"); }
  byte[] get(String address, int limit) throws Exception {
    URL url = new URL(address);
    String group = group(url);
    HttpsURLConnection c = (HttpsURLConnection) url.openConnection();
    c.setSSLSocketFactory(factory(group));
    c.setConnectTimeout(10000); c.setReadTimeout(12000); c.setInstanceFollowRedirects(false);
    c.setRequestProperty("User-Agent", "Aerial-TV-Legacy/1.5");
    try {
      if (c.getResponseCode() != 200) throw new IOException("HTTP " + c.getResponseCode());
      if (c.getContentLength() > limit) throw new IOException("Response too large");
      InputStream in = c.getInputStream();
      try {
        ByteArrayOutputStream out = new ByteArrayOutputStream(); byte[] buffer = new byte[8192]; int n;
        while ((n = in.read(buffer)) != -1) {
          if (Thread.currentThread().isInterrupted()) throw new InterruptedIOException();
          if (out.size() + n > limit) throw new IOException("Response too large");
          out.write(buffer, 0, n);
        }
        return out.toByteArray();
      } finally { in.close(); }
    } finally { c.disconnect(); }
  }
  static String group(URL url) throws IOException {
    if (!"https".equals(url.getProtocol()) || (url.getPort() != -1 && url.getPort() != 443)
        || url.getUserInfo() != null) throw new IOException("HTTPS required");
    if ("cn.bing.com".equals(url.getHost())) return "bing";
    if ("api.open-meteo.com".equals(url.getHost()) || "geocoding-api.open-meteo.com".equals(url.getHost())) return "weather";
    if ("ipwho.is".equals(url.getHost())) return "location";
    throw new IOException("Unknown data source");
  }
  private synchronized SSLSocketFactory factory(String group) throws Exception {
    SSLSocketFactory result = factories.get(group);
    if (result != null) return result;
    String[] names = "bing".equals(group)
        ? new String[]{"Microsoft-RSA-Root-2017.crt", "Microsoft-ECC-Root-2017.crt", "DigiCert-Global-Root-G2.crt"}
        : "location".equals(group) ? new String[]{"GTS-Root-R4.crt"}
        : new String[]{"ISRG-Root-X1.crt", "ISRG-Root-X2.crt"};
    KeyStore roots = KeyStore.getInstance(KeyStore.getDefaultType()); roots.load(null, null);
    CertificateFactory certificates = CertificateFactory.getInstance("X.509");
    for (String name : names) {
      InputStream in = assets.open(group + "-ca/" + name);
      try { roots.setCertificateEntry(name, certificates.generateCertificate(in)); } finally { in.close(); }
    }
    TrustManagerFactory trust = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm()); trust.init(roots);
    SSLContext tls;
    try {
      tls = newTlsContext();
    } catch (LinkageError unavailable) {
      // API18 preinstalled APKs need their native library in /system/lib. Keep the
      // desktop usable if a firmware integrator omitted it or supplied the wrong ABI.
      throw new IOException("HTTPS native library unavailable", unavailable);
    }
    tls.init(null, trust.getTrustManagers(), null);
    result = tls.getSocketFactory(); factories.put(group, result); return result;
  }
  // Overridden only by host tests, where Android's native Conscrypt cannot load on macOS.
  protected SSLContext newTlsContext() throws Exception { return SSLContext.getInstance("TLS", org.conscrypt.Conscrypt.newProvider()); }
}
