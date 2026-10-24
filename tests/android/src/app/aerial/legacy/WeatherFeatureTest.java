package app.aerial.legacy;

import android.content.*;
import android.content.res.Configuration;
import android.graphics.*;
import android.os.SystemClock;
import android.test.InstrumentationTestCase;
import java.io.*;
import java.lang.reflect.Field;
import java.net.*;
import java.util.*;
import org.json.*;

/** Local API18 emulator check. Test city is an explicit fixture, never device location. */
public final class WeatherFeatureTest extends InstrumentationTestCase {
  public void testHttpsAndPhoneWeatherFlow() throws Exception {
    final Context context=getInstrumentation().getTargetContext();
    WeatherApi.Assets assets=new WeatherApi.Assets(){public InputStream open(String path)throws IOException{return context.getAssets().open(path);}};
    WeatherApi api=new WeatherApi(new LegacyHttp(assets),assets);
    WeatherData.Place city=api.search("杭州").get(0);
    assertEquals("浙江",city.region);
    assertFalse("Real native TLS geocoder",api.search("London").isEmpty());
    WeatherData.Reading forecast=api.fetch(city,System.currentTimeMillis());
    assertTrue("Real API18 Conscrypt weather HTTPS",forecast.fresh(System.currentTimeMillis()));
    assertNotNull(forecast.high);assertNotNull(forecast.low);
    final MainActivity activity=(MainActivity)getInstrumentation().startActivitySync(new Intent(context,MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
    Field f=MainActivity.class.getDeclaredField("home");f.setAccessible(true);final HomeView home=(HomeView)f.get(activity);
    for(int n=0;n<150&&home.selectedItem()==null;n++)SystemClock.sleep(100);
    Field prepared=HomeView.class.getDeclaredField("prepared");prepared.setAccessible(true);
    for(int n=0;n<150&&!prepared.getBoolean(home);n++)SystemClock.sleep(100);
    getInstrumentation().runOnMainSync(new Runnable(){public void run(){home.ui.phone.show();}});
    String base=home.ui.phone.address();assertNotNull(base);
    JSONObject result=post(base+"api/weather/search",new JSONObject().put("name","杭州"));
    JSONObject selected=result.getJSONArray("cities").getJSONObject(0);
    JSONObject state=post(base+"api/weather",selected.put("enabled",true));
    assertEquals("杭州",state.getJSONObject("weather").getString("city"));
    for(int n=0;n<600&&home.ui.weather.reading()==null;n++)SystemClock.sleep(100);
    assertNotNull("Phone selection triggers forecast",home.ui.weather.reading());
    assertEquals("杭州",home.ui.weather.state().getString("city"));
    state=post(base+"api/weather",new JSONObject().put("enabled",false));
    assertFalse(state.getJSONObject("weather").getBoolean("enabled"));assertNull(home.ui.weather.reading());
    post(base+"api/weather",new JSONObject().put("enabled",true));
    for(int n=0;n<600&&home.ui.weather.reading()==null;n++)SystemClock.sleep(100);
    assertNotNull(home.ui.weather.reading());
    final Configuration original=new Configuration(activity.getResources().getConfiguration());
    final Locale locale=Locale.getDefault();
    try {
      getInstrumentation().runOnMainSync(new Runnable(){public void run(){
        home.ui.phone.stop();home.resetHome();
        Configuration zh=new Configuration(original);zh.locale=Locale.SIMPLIFIED_CHINESE;Locale.setDefault(zh.locale);
        activity.getResources().updateConfiguration(zh,activity.getResources().getDisplayMetrics());
      }});
      SystemClock.sleep(500);
      render(activity,home,"weather-corner.png",false);render(activity,home,"weather-large.png",true);
    }finally{
      getInstrumentation().runOnMainSync(new Runnable(){public void run(){
        Locale.setDefault(locale);activity.getResources().updateConfiguration(original,activity.getResources().getDisplayMetrics());
        home.ui.prefs.edit().putBoolean("large_clock",false).apply();home.resetHome();
      }});
    }
    android.util.Log.i("AerialWeatherTest","Native API18 HTTPS, phone city selection, enable/disable, cache and both clock renders passed");
    if ("1".equals(((android.test.InstrumentationTestRunner)getInstrumentation()).getArguments().getString("phone_preview"))) {
      getInstrumentation().runOnMainSync(new Runnable(){public void run(){home.ui.phone.show();}});
      FileOutputStream out=activity.openFileOutput("phone-preview-url.txt",0);
      out.write(home.ui.phone.address().getBytes("UTF-8"));out.close();
      SystemClock.sleep(180000); // Optional local browser QA; normal test runs finish immediately.
      getInstrumentation().runOnMainSync(new Runnable(){public void run(){home.ui.phone.stop();}});
    }
  }
  private void render(final MainActivity activity,final HomeView home,final String name,final boolean large){
    getInstrumentation().runOnMainSync(new Runnable(){public void run(){try{
      home.ui.prefs.edit().putBoolean("large_clock",large).apply();
      java.lang.reflect.Method clock=HomeView.class.getDeclaredMethod("updateClock");clock.setAccessible(true);clock.invoke(home);
      Bitmap bitmap=Bitmap.createBitmap(home.getWidth(),home.getHeight(),Bitmap.Config.ARGB_8888);
      home.draw(new Canvas(bitmap));FileOutputStream out=activity.openFileOutput(name,0);bitmap.compress(Bitmap.CompressFormat.PNG,100,out);out.close();bitmap.recycle();
    }catch(Exception e){throw new RuntimeException(e);}}});
  }
  private static JSONObject post(String url,JSONObject data)throws Exception {
    HttpURLConnection c=(HttpURLConnection)new URL(url).openConnection();c.setConnectTimeout(5000);c.setReadTimeout(25000);
    c.setRequestMethod("POST");c.setRequestProperty("X-Aerial-Request","1");c.setDoOutput(true);
    byte[] bytes=data.toString().getBytes("UTF-8");c.setFixedLengthStreamingMode(bytes.length);
    try{
      OutputStream out=c.getOutputStream();out.write(bytes);out.close();
      if(c.getResponseCode()!=200)throw new IOException("HTTP "+c.getResponseCode());
      InputStream in=c.getInputStream();ByteArrayOutputStream body=new ByteArrayOutputStream();byte[] b=new byte[4096];int n;
      try{while((n=in.read(b))!=-1)body.write(b,0,n);}finally{in.close();}
      return new JSONObject(new String(body.toByteArray(),"UTF-8"));
    }finally{c.disconnect();}
  }
}
