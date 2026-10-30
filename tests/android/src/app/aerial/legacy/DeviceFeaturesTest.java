package app.aerial.legacy;
import android.content.*;
import android.os.SystemClock;
import android.test.InstrumentationTestCase;
import java.lang.reflect.*;
import java.net.*;
import java.io.*;
import java.util.*;

/** Targeted device tests: preserve settings, never invoke HDMI or change networking. */
public final class DeviceFeaturesTest extends InstrumentationTestCase {
  static MainActivity running; MainActivity activity; HomeView home;
  private void ui(Runnable r) { getInstrumentation().runOnMainSync(r); }
  private void launch() throws Exception {
    if(running==null || running.isFinishing()) running=(MainActivity)getInstrumentation().startActivitySync(new Intent(getInstrumentation().getTargetContext(),MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
    activity=running;
    Field f=MainActivity.class.getDeclaredField("home");f.setAccessible(true);home=(HomeView)f.get(activity);
    SystemClock.sleep(1200);
  }
  public void testBingAndPhotoWallpapers() throws Exception {
    launch();
    final String source=home.ui.wallpapers.source(); final int preset=home.ui.preset();
    File photo=home.ui.wallpapers.image("photo"); byte[] original=photo.exists()?read(photo):null;
    try {
      android.graphics.Bitmap sample=android.graphics.Bitmap.createBitmap(64,32,android.graphics.Bitmap.Config.ARGB_8888);
      sample.eraseColor(0xff38a098);ByteArrayOutputStream data=new ByteArrayOutputStream();sample.compress(android.graphics.Bitmap.CompressFormat.PNG,100,data);sample.recycle();
      home.ui.wallpapers.upload(data.toByteArray());assertEquals("photo",home.ui.wallpapers.source());
      android.graphics.Bitmap crop=home.ui.wallpapers.render(preset,960,540);assertEquals(960,crop.getWidth());assertEquals(540,crop.getHeight());int color=crop.getPixel(480,270);assertTrue(Math.abs(android.graphics.Color.red(color)-56)<=8);assertTrue(Math.abs(android.graphics.Color.green(color)-160)<=8);assertTrue(Math.abs(android.graphics.Color.blue(color)-152)<=8);crop.recycle();
      byte[] valid=read(photo);
      try {home.ui.wallpapers.upload("not an image".getBytes("UTF-8"));fail("Malformed upload accepted");}catch(IOException expected){}
      assertTrue("Invalid upload preserves last good photo",Arrays.equals(valid,read(photo)));
      home.ui.wallpapers.select("bing",preset);home.ui.wallpapers.refresh(true);
      for(int n=0;n<900 && home.ui.wallpapers.fetching();n++)SystemClock.sleep(100);
      assertFalse("Bing download completed",home.ui.wallpapers.fetching());
      assertTrue("Real API18 HTTPS Bing fetch: "+home.ui.wallpapers.status(),home.ui.wallpapers.image("bing").length()>1000);
      assertTrue("Photo attribution is retained",home.ui.wallpapers.caption().length()>0);
      android.graphics.Bitmap bing=home.ui.wallpapers.render(preset,960,540);assertNotNull(bing);bing.recycle();
      android.util.Log.i("AerialDeviceTest","Bing HTTPS image and photo crop/invalid upload verified");
    }finally {
      if(original==null)photo.delete();else {FileOutputStream out=new FileOutputStream(photo);out.write(original);out.close();}
      home.ui.wallpapers.select(source,preset);
    }
  }
  private static byte[] read(File file) throws IOException {
    FileInputStream in=new FileInputStream(file);try{ByteArrayOutputStream out=new ByteArrayOutputStream();byte[] b=new byte[8192];int n;while((n=in.read(b))!=-1)out.write(b,0,n);return out.toByteArray();}finally{in.close();}
  }
  public void testWallpaperAndLanSetup() throws Exception {
    launch(); final Map<String,?> saved=home.ui.prefs.getAll();
    try {
      ui(new Runnable(){public void run(){home.resetHome();home.ui.mode=DesktopUi.NONE;home.ui.chromeFocused=false;}});
      SystemClock.sleep(500);
      ui(new Runnable(){public void run(){
        android.graphics.Bitmap[] frames=new android.graphics.Bitmap[3];
        for(int mode=0;mode<3;mode++) {
          home.ui.prefs.edit().putInt("name_mode",mode).commit();
          frames[mode]=android.graphics.Bitmap.createBitmap(home.getWidth(),home.getHeight(),android.graphics.Bitmap.Config.ARGB_8888);
          home.draw(new android.graphics.Canvas(frames[mode]));
        }
        assertFalse("First-row selected label is visibly drawn with the glass shelf",frames[1].sameAs(frames[2]));
        assertFalse("All labels differ from selected-only",frames[0].sameAs(frames[1]));
        for(android.graphics.Bitmap frame:frames)frame.recycle();
        home.ui.prefs.edit().remove("name_mode").putBoolean("names",true).commit();assertEquals(0,home.ui.nameMode());
        home.ui.prefs.edit().putBoolean("names",false).commit();assertEquals(1,home.ui.nameMode());
        for(int mode=0;mode<3;mode++) {home.ui.prefs.edit().putInt("name_mode",mode).commit();assertEquals(mode!=2,home.ui.showName(true));assertEquals(mode==0,home.ui.showName(false));}
      }});
      assertEquals(NetworkStatus.read(activity),home.ui.networkKind);
      assertEquals(AerialGlyphs.LAN,NetworkStatus.glyph(NetworkStatus.ETHERNET));
      ui(new Runnable(){public void run(){home.ui.phone.show();}});
      String base=home.ui.phone.address();assertNotNull(base);
      JSONObjectResult state=get(base+"api/state"); assertEquals(200,state.code);assertTrue(state.text.contains("preset"));
      assertEquals(404,get("http://127.0.0.1:7681/wrong/api/state").code);
      HttpURLConnection forbidden=(HttpURLConnection)new URL(base+"api/settings").openConnection();forbidden.setRequestMethod("POST");forbidden.setDoOutput(true);forbidden.getOutputStream().write("{}".getBytes("UTF-8"));assertEquals(403,forbidden.getResponseCode());forbidden.disconnect();
      HttpURLConnection change=(HttpURLConnection)new URL(base+"api/settings").openConnection();change.setRequestMethod("POST");change.setRequestProperty("X-Aerial-Request","1");change.setDoOutput(true);change.getOutputStream().write("{\"names\":true}".getBytes("UTF-8"));assertEquals(200,change.getResponseCode());change.disconnect();assertTrue(home.ui.names());
      ui(new Runnable(){public void run(){home.ui.phone.stop();}});
      try {get(base+"api/state");fail("Setup listener must close");}catch(IOException expected){}
      try {
        Class<?> type=Class.forName("letv.desktop.DesktopManager");Object manager=type.getMethod("get").invoke(null);
        String[] tags=(String[])type.getMethod("getEnabledDesktopTags").invoke(manager);
        assertNotNull("EUI signal desktop enumeration initialized",tags);assertTrue(Arrays.asList(tags).contains("signal"));
        android.util.Log.i("AerialDeviceTest","EUI desktop tags initialized");
      }catch(ClassNotFoundException genericAndroid){}
    }finally {
      ui(new Runnable(){public void run(){home.ui.phone.stop();android.content.SharedPreferences.Editor e=home.ui.prefs.edit().clear();for(Map.Entry<String,?> v:saved.entrySet()){Object o=v.getValue();String k=v.getKey();if(o instanceof String)e.putString(k,(String)o);else if(o instanceof Boolean)e.putBoolean(k,(Boolean)o);else if(o instanceof Integer)e.putInt(k,(Integer)o);else if(o instanceof Long)e.putLong(k,(Long)o);else if(o instanceof Float)e.putFloat(k,(Float)o);}e.commit();home.resetHome();}});
    }
  }
  private static final class JSONObjectResult {int code;String text;}
  private static JSONObjectResult get(String url) throws IOException {
    HttpURLConnection c=(HttpURLConnection)new URL(url).openConnection();c.setConnectTimeout(2500);c.setReadTimeout(5000);
    try {JSONObjectResult r=new JSONObjectResult();r.code=c.getResponseCode();InputStream in=r.code==200?c.getInputStream():c.getErrorStream();ByteArrayOutputStream out=new ByteArrayOutputStream();if(in!=null){byte[] b=new byte[1024];int n;while((n=in.read(b))!=-1)out.write(b,0,n);in.close();}r.text=new String(out.toByteArray(),"UTF-8");return r;}finally{c.disconnect();}
  }
}
