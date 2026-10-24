package app.aerial.legacy;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.media.AudioManager;
import android.content.Context;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import io.nayuki.qrcodegen.QrCode;
import org.json.JSONObject;
import java.io.*;
import java.net.*;
import java.security.SecureRandom;
import java.util.*;
import java.util.concurrent.*;

/** Short lived, token protected LAN setup; no remote commands or external filesystem access. */
final class PhoneSetup {
  private final DesktopUi ui;
  private ServerSocket server;
  private ExecutorService workers;
  private volatile boolean stopped = true;
  private String token, address;
  private AlertDialog dialog;
  private final Runnable expire = new Runnable() { public void run() { stop(); } };
  PhoneSetup(DesktopUi ui) { this.ui=ui; }
  void show() {
    try {
      start();
      LinearLayout box = new LinearLayout(ui.home.getContext()); box.setOrientation(LinearLayout.VERTICAL);
      box.setPadding(32,16,32,16);
      TextView hint = new TextView(ui.home.getContext()); hint.setText(ui.s(R.string.phone_hint));
      hint.setTextSize(17); box.addView(hint);
      ImageView image = new ImageView(ui.home.getContext()); image.setImageBitmap(qr(address));
      box.addView(image,new LinearLayout.LayoutParams(-1,260));
      TextView link = new TextView(ui.home.getContext()); link.setText(address); link.setTextSize(16);
      link.setGravity(17); box.addView(link);
      dialog = new AlertDialog.Builder(ui.home.getContext()).setTitle(R.string.phone_setup)
          .setView(box).setPositiveButton(android.R.string.ok,null).create();
      dialog.setOnDismissListener(new DialogInterface.OnDismissListener() {
        public void onDismiss(DialogInterface ignored) { ui.handler.removeCallbacks(expire); ui.handler.postDelayed(expire,5*60000L); }
      });
      ui.handler.removeCallbacks(expire); ui.handler.postDelayed(expire,15*60000L);
      dialog.show(); AppFonts.apply(dialog.getWindow().getDecorView());
    } catch (IOException failed) {
      AppFonts.toast(ui.home.getContext(),R.string.phone_unavailable,1).show(); stop();
    }
  }
  String address() { return address; }
  private synchronized void start() throws IOException {
    if (!stopped) return;
    String ip=null;
    Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
    while (interfaces!=null && interfaces.hasMoreElements()) {
      NetworkInterface net=interfaces.nextElement();
      Enumeration<InetAddress> addresses=net.getInetAddresses();
      while(addresses.hasMoreElements()) {
        InetAddress a=addresses.nextElement();
        if (a instanceof Inet4Address && !a.isLoopbackAddress() && !a.isLinkLocalAddress()) { ip=a.getHostAddress(); break; }
      }
      if (ip!=null) break;
    }
    if (ip==null) throw new IOException("No local network");
    byte[] random=new byte[16]; new SecureRandom().nextBytes(random);
    StringBuilder key=new StringBuilder(); for(byte b:random) key.append(String.format(Locale.US,"%02x",b&255));
    token=key.toString(); server=new ServerSocket(); server.setReuseAddress(true);
    server.bind(new InetSocketAddress(7681));
    address="http://"+ip+":7681/"+token+"/";
    workers=new ThreadPoolExecutor(2,2,15,TimeUnit.SECONDS,new ArrayBlockingQueue<Runnable>(16));
    stopped=false;
    new Thread(new Runnable() { public void run() {
      while(!stopped) {
        try {
          final Socket socket=server.accept(); socket.setSoTimeout(15000);
          if (!socket.getInetAddress().isSiteLocalAddress() && !socket.getInetAddress().isLoopbackAddress()) {socket.close();continue;}
          try { workers.execute(new Runnable() { public void run() { serve(socket); } }); }
          catch (RejectedExecutionException busy) { socket.close(); }
        } catch (IOException done) { break; }
      }
    } },"aerial-phone").start();
  }
  synchronized void stop() {
    stopped=true; ui.handler.removeCallbacks(expire);
    if (dialog!=null && dialog.isShowing()) dialog.dismiss();
    ui.handler.removeCallbacks(expire);
    if(server!=null) try { server.close(); } catch(IOException ignored) { }
    if(workers!=null) workers.shutdownNow();
  }
  private void serve(Socket socket) {
    try {
      InputStream in=new BufferedInputStream(socket.getInputStream());
      String request=line(in); String[] parts=request.split(" ");
      if(parts.length!=3 || (!parts[0].equals("GET") && !parts[0].equals("POST"))) { reply(socket,400,"text/plain","Invalid request".getBytes("UTF-8"));return; }
      Map<String,String> headers=new HashMap<String,String>();
      int total=0; String h;
      while(!(h=line(in)).isEmpty()) { total+=h.length(); if(total>8192) throw new IOException("Headers too large"); int colon=h.indexOf(':'); if(colon<=0) throw new IOException("Invalid header"); String name=h.substring(0,colon).trim().toLowerCase(Locale.US);if(headers.containsKey(name))throw new IOException("Duplicate header");headers.put(name,h.substring(colon+1).trim()); }
      if(!parts[1].startsWith("/"+token+"/")) { reply(socket,404,"text/plain",new byte[0]);return; }
      String path=parts[1].substring(token.length()+1);
      boolean post=parts[0].equals("POST");
      if(headers.containsKey("transfer-encoding"))throw new IOException("Chunked requests unsupported");
      if(post && !"1".equals(headers.get("x-aerial-request"))) { reply(socket,403,"text/plain",new byte[0]);return; }
      String origin=headers.get("origin");
      if(post && origin!=null && !origin.equals("http://"+headers.get("host"))) {reply(socket,403,"text/plain",new byte[0]);return;}
      int size=Integer.parseInt(headers.containsKey("content-length")?headers.get("content-length"):"0");
      int limit=path.equals("/api/upload")?8*1024*1024:8192;
      if(size<0 || size>limit) {reply(socket,413,"text/plain","File too large (8 MB maximum)".getBytes("UTF-8"));return;}
      byte[] body=new byte[size];int at=0,n;while(at<size && (n=in.read(body,at,size-at))>0)at+=n;
      if(at!=size)throw new EOFException();
      if(!post && path.equals("/")) { reply(socket,200,"text/html; charset=utf-8",asset());return; }
      if (!post && path.startsWith("/api/preview?")) {
        Map<String,String> query = new HashMap<String,String>();
        for(String part:path.substring(path.indexOf('?')+1).split("&")) { String[] pair=part.split("=",2); if(pair.length==2)query.put(pair[0],pair[1]); }
        android.graphics.Bitmap image;
        String source=query.get("source");
        if("builtin".equals(source)) {
          int preset=Integer.parseInt(query.get("preset")); if(preset<0||preset>=Gradients.COUNT)throw new IOException("Unknown preset");
          image=Gradients.render(preset,240,135);
        } else if("bing".equals(source)||"photo".equals(source)) image=ui.wallpapers.preview(source);
        else throw new IOException("Unknown source");
        if(image==null){reply(socket,404,"text/plain",new byte[0]);return;}
        ByteArrayOutputStream bytes=new ByteArrayOutputStream(); image.compress(Bitmap.CompressFormat.JPEG,80,bytes);image.recycle();
        reply(socket,200,"image/jpeg",bytes.toByteArray());return;
      }
      if(!post && path.equals("/api/state")) { reply(socket,200,"application/json; charset=utf-8",state().toString().getBytes("UTF-8"));return; }
      if(!post || !path.startsWith("/api/")) {reply(socket,404,"text/plain",new byte[0]);return;}
      final JSONObject values=path.equals("/api/upload")?new JSONObject():new JSONObject(new String(body,"UTF-8"));
      if (path.equals("/api/weather/search")) {
        try {
          org.json.JSONArray cities = new org.json.JSONArray();
          for (WeatherData.Place city : ui.weather.search(values.getString("name"))) cities.put(WeatherStore.cityJson(city));
          reply(socket, 200, "application/json; charset=utf-8", new JSONObject().put("cities", cities).toString().getBytes("UTF-8"));
        } catch (Exception unavailable) {
          reply(socket, 502, "application/json; charset=utf-8", new JSONObject()
              .put("message", "暂时无法查找城市，请检查网络后重试 / City search unavailable").toString().getBytes("UTF-8"));
        }
        return;
      }
      if (path.equals("/api/weather")) ui.weather.configure(values);
      else if (path.equals("/api/weather/refresh")) ui.weather.refresh(true);
      else if(path.equals("/api/upload")) ui.wallpapers.upload(body);
      else if(path.equals("/api/refresh")) ui.wallpapers.refresh(true);
      else if(path.equals("/api/wallpaper")) ui.wallpapers.select(values.getString("source"),values.optInt("preset",ui.preset()));
      else if(path.equals("/api/settings")) {
        final CountDownLatch done=new CountDownLatch(1);
        ui.handler.post(new Runnable() { public void run() {
          try {
            android.content.SharedPreferences.Editor edit=ui.prefs.edit();
            for(String key:new String[]{"names","contrast","plate","large_clock","sounds"})
              if(values.has(key))edit.putBoolean(key,values.optBoolean(key));
            if(values.has("idle")) {int value=values.optInt("idle",2);if(value==0||value==1||value==2||value==5||value==10)edit.putInt("idle",value);}
            if (values.has("name_mode")) edit.putInt("name_mode", Math.max(0, Math.min(2, values.optInt("name_mode", 1))));
            else if (values.has("names")) edit.putInt("name_mode", values.optBoolean("names") ? 0 : 1);
            edit.apply();ui.resetIdle();ui.home.invalidate();
          } finally { done.countDown(); }
        } });
        if(!done.await(5,TimeUnit.SECONDS))throw new IOException("TV is busy");
      } else {reply(socket,404,"text/plain",new byte[0]);return;}
      reply(socket,200,"application/json; charset=utf-8",state().toString().getBytes("UTF-8"));
    } catch(Exception invalid) {
      try { reply(socket,400,"text/plain; charset=utf-8","无法应用设置，请检查输入或在电视重新打开手机配置 / Check the input or reopen setup on TV.".getBytes("UTF-8")); } catch(IOException ignored) { }
    } finally { try { socket.close(); } catch(IOException ignored) { } }
  }
  private JSONObject state() throws Exception {
    JSONObject state=new JSONObject().put("source",ui.wallpapers.source()).put("preset",ui.preset())
        .put("photo",ui.wallpapers.hasPhoto()).put("caption",ui.wallpapers.caption())
        .put("bing_stamp",ui.wallpapers.image("bing").lastModified())
        .put("busy",ui.wallpapers.fetching()).put("status",ui.wallpapers.status());
    for(String key:new String[]{"names","contrast","plate","large_clock","sounds"})
      state.put(key,ui.prefs.getBoolean(key,key.equals("contrast")||key.equals("plate")));
    state.put("idle",ui.prefs.getInt("idle",2)).put("name_mode",ui.nameMode()).put("weather",ui.weather.state());
    return state;
  }
  private byte[] asset() throws IOException {
    InputStream in=ui.home.getContext().getAssets().open("phone.html");
    try {ByteArrayOutputStream out=new ByteArrayOutputStream();byte[] b=new byte[4096];int n;while((n=in.read(b))!=-1)out.write(b,0,n);return out.toByteArray();}finally{in.close();}
  }
  private static String line(InputStream in) throws IOException {
    StringBuilder s=new StringBuilder();int b;while((b=in.read())!=-1){if(b==10)return s.toString().replace("\r","");if(s.length()>4096)throw new IOException("Line too long");s.append((char)b);}throw new EOFException();
  }
  private static void reply(Socket socket,int code,String type,byte[] bytes) throws IOException {
    OutputStream out=socket.getOutputStream();
    String h="HTTP/1.1 "+code+" Response\r\nContent-Type: "+type+"\r\nContent-Length: "+bytes.length+
        "\r\nConnection: close\r\nCache-Control: no-store\r\nX-Content-Type-Options: nosniff\r\nReferrer-Policy: no-referrer\r\nContent-Security-Policy: default-src 'self'; script-src 'unsafe-inline'; style-src 'unsafe-inline'; connect-src 'self'; img-src 'self' data:; frame-ancestors 'none'; base-uri 'none'\r\n\r\n";
    out.write(h.getBytes("UTF-8"));out.write(bytes);out.flush();
  }
  private static Bitmap qr(String value) {
    QrCode qr=QrCode.encodeText(value,QrCode.Ecc.MEDIUM);int size=qr.size+8;
    Bitmap image=Bitmap.createBitmap(size*5,size*5,Bitmap.Config.ARGB_8888);
    int[] pixels=new int[size*size*25];
    for(int y=0;y<size*5;y++)for(int x=0;x<size*5;x++)pixels[y*size*5+x]=qr.getModule(x/5-4,y/5-4)?Color.BLACK:Color.WHITE;
    image.setPixels(pixels,0,size*5,0,0,size*5,size*5);return image;
  }
}
