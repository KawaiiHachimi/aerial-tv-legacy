package app.aerial.legacy;

import android.content.Context;
import android.os.Handler;
import org.json.*;
import java.io.*;
import java.net.URL;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import javax.net.ssl.*;

/** Deterministic protocol/cache/lifecycle tests; optional --live probes use desktop TLS. */
public final class WeatherTest {
  private static int checks;
  private static final long NOW=1700000000000L;
  private static String fixture(double temp){return "{\"current_units\":{\"temperature_2m\":\"°C\"},\"current\":{\"temperature_2m\":"+temp+",\"weather_code\":2,\"is_day\":0},\"daily\":{\"temperature_2m_max\":[24.7],\"temperature_2m_min\":[null]}}";}
  private static void check(boolean value,String message){checks++;if(!value)throw new AssertionError(message);}
  private static void rejects(Runnable body){try{body.run();throw new AssertionError("Invalid input accepted");}catch(IllegalArgumentException expected){checks++;}}
  private static WeatherApi.Assets assets(final File root){return new WeatherApi.Assets(){public InputStream open(String path)throws IOException{return new FileInputStream(new File(root,path));}};}
  private static void await(java.util.concurrent.Callable<Boolean> test,String message)throws Exception{
    for(int n=0;n<300;n++){if(test.call()){checks++;return;}Thread.sleep(10);}throw new AssertionError(message);
  }
  public static void main(String[] args)throws Exception{
    final File root=new File(args[0]);
    check(WeatherData.kindOf(0)==WeatherData.Kind.CLEAR,"clear");check(WeatherData.kindOf(2)==WeatherData.Kind.PARTLY_CLOUDY,"partly cloudy");
    for(int code:new int[]{51,55,61,67,80,82})check(WeatherData.kindOf(code)==WeatherData.Kind.RAIN,"rain "+code);
    for(int code:new int[]{71,77,85,86})check(WeatherData.kindOf(code)==WeatherData.Kind.SNOW,"snow "+code);
    for(int code:new int[]{95,96,99})check(WeatherData.kindOf(code)==WeatherData.Kind.THUNDER,"thunder "+code);
    check(WeatherData.kindOf(900)==WeatherData.Kind.UNKNOWN,"unknown must not become rain");
    WeatherData.Reading reading=WeatherApi.parseReading(fixture(-3.5),NOW);check(reading.celsius==-3&&!reading.day,"negative temp / night");
    check(reading.high==25&&reading.low==null,"nullable daily values");check(reading.fresh(NOW+WeatherData.MAX_AGE-1),"cached reading within boundary");
    check(!reading.fresh(NOW+WeatherData.MAX_AGE)&&!reading.fresh(NOW-1),"expired and future readings hidden");
    check(WeatherData.due(NOW+WeatherData.HOUR,NOW,false)&&!WeatherData.due(NOW+WeatherData.HOUR-1,NOW,false),"hourly refresh");
    check(WeatherData.due(NOW+WeatherData.RETRY,NOW,true)&&!WeatherData.due(NOW+WeatherData.RETRY-1,NOW,true),"failed request backoff");
    rejects(new Runnable(){public void run(){new WeatherData.Place("x","",91,0);}});
    rejects(new Runnable(){public void run(){new WeatherData.Reading(Double.NaN,0,true,null,null,NOW);}});
    rejects(new Runnable(){public void run(){new WeatherData.Reading(20,0,true,5.0,15.0,NOW);}});
    for(String invalid:new String[]{"{}","{\"current_units\":{\"temperature_2m\":\"°F\"},\"current\":{\"temperature_2m\":80,\"weather_code\":0}}",fixture(20).replace("\"weather_code\":2","\"weather_code\":null")}){
      try{WeatherApi.parseReading(invalid,NOW);throw new AssertionError("Malformed weather accepted");}catch(JSONException expected){checks++;}
    }
    List<WeatherData.Place> places=WeatherApi.parsePlaces("{\"results\":[{\"name\":\"village\",\"latitude\":1,\"longitude\":2,\"feature_code\":\"PPL\"},{\"name\":\"city\",\"latitude\":3,\"longitude\":4,\"feature_code\":\"PPLA\"}]}");
    check(places.size()==2&&places.get(0).name.equals("city"),"cities before villages");check(WeatherApi.parsePlaces("{}").isEmpty(),"empty geocoder result");
    CityCatalog cities=new CityCatalog(assets(root).open("cities-cn.tsv"));
    for(String city:new String[]{"上海","上海市","中国上海","沪"})check(cities.search(city).get(0).name.equals("上海"),"city alias "+city);
    check(cities.search("河南商丘市").get(0).region.equals("河南"),"Chinese province-qualified city");check(cities.search("London").isEmpty(),"foreign search falls back online");
    for(String invalid:new String[]{"http://api.open-meteo.com/v1/forecast","https://api.open-meteo.com.evil.test/","https://user@api.open-meteo.com/","https://api.open-meteo.com:8443/"}){
      try{LegacyHttp.group(new URL(invalid));throw new AssertionError("Untrusted URL accepted");}catch(IOException expected){checks++;}
    }
    lifecycle(root);
    boolean probeLive=args.length>1&&args[1].equals("--live");
    if(probeLive)live(root);
    System.out.println("Weather: "+checks+" checks passed"+(probeLive?" (including live HTTPS probes)":""));
  }
  private static void lifecycle(File root)throws Exception{
    final AtomicInteger calls=new AtomicInteger(),notifications=new AtomicInteger();
    final CountDownLatch firstStarted=new CountDownLatch(1),releaseFirst=new CountDownLatch(1);
    final AtomicBoolean fail=new AtomicBoolean(false);
    WeatherApi.Transport transport=new WeatherApi.Transport(){public String getText(String url)throws Exception{
      int call=calls.incrementAndGet();
      if(call==1){firstStarted.countDown();while(releaseFirst.getCount()>0)try{releaseFirst.await();}catch(InterruptedException deliberateSlowIo){}}
      if(fail.get())throw new IOException("offline fixture");return fixture(call==1?11:22);
    }};
    final Context context=new Context();final WeatherStore store=new WeatherStore(context,new Handler(),new Runnable(){public void run(){notifications.incrementAndGet();}},new WeatherApi(transport,assets(root)));
    try{
      store.onShown();check(calls.get()==0,"unconfigured weather does not query location or forecast");
      store.configure(new JSONObject("{\"name\":\"First\",\"latitude\":1,\"longitude\":2}"));check(firstStarted.await(2,TimeUnit.SECONDS),"first forecast started");
      store.configure(new JSONObject("{\"name\":\"Second\",\"latitude\":3,\"longitude\":4}"));check(store.reading()==null,"old city's cache removed immediately");
      releaseFirst.countDown();await(new Callable<Boolean>(){public Boolean call(){return store.reading()!=null;}},"second forecast completed");
      check(store.place().name.equals("Second")&&store.reading().celsius==22,"late first-city response cannot overwrite second city");
      fail.set(true);store.configure(new JSONObject("{\"enabled\":true}"));
      await(new Callable<Boolean>(){public Boolean call()throws Exception{return store.state().getString("status").equals("cached");}},"offline cached status");
      check(store.reading().celsius==22,"network failure preserves last good reading");
      store.onHidden();int before=calls.get();store.refresh(true);Thread.sleep(60);check(calls.get()==before,"hidden Home does not refresh");
      final WeatherStore restored=new WeatherStore(context,new Handler(),new Runnable(){public void run(){}},new WeatherApi(transport,assets(root)));
      try{check(restored.reading()!=null&&restored.reading().celsius==22,"cache survives process recreation");}finally{restored.close();}
      context.getSharedPreferences("weather-v1",0).edit().putLong("fetched_at",System.currentTimeMillis()-WeatherData.MAX_AGE).commit();
      WeatherStore expired=new WeatherStore(context,new Handler(),new Runnable(){public void run(){}},new WeatherApi(transport,assets(root)));
      try{check(expired.reading()==null,"expired cache is hidden after recreation");}finally{expired.close();}
      store.onShown();store.configure(new JSONObject("{\"enabled\":false}"));check(store.reading()==null&&!store.enabled(),"weather can be disabled without losing city");
    }finally{releaseFirst.countDown();store.close();}
  }
  private static void live(final File root)throws Exception{
    LegacyHttp http=new LegacyHttp(assets(root)){protected SSLContext newTlsContext()throws Exception{return SSLContext.getInstance("TLS");}};
    WeatherApi api=new WeatherApi(http,assets(root));
    List<WeatherData.Place> cn=api.search("杭州"),foreign=api.search("London");check(!cn.isEmpty()&&!foreign.isEmpty(),"live local/online city search");
    WeatherData.Reading current=api.fetch(cn.get(0),System.currentTimeMillis());check(current.fresh(System.currentTimeMillis()),"live forecast via bundled weather CA roots");
    check(!http.getText("https://cn.bing.com/HPImageArchive.aspx?format=js&idx=0&n=1&mkt=zh-CN").isEmpty(),"Bing regression via separate CA roots");
    try{http.get("https://api.open-meteo.com/v1/forecast?latitude=30&longitude=120&current=temperature_2m",8);throw new AssertionError("Unbounded response");}catch(IOException expected){checks++;}
    final WeatherApi.Assets real=assets(root);
    LegacyHttp wrongRoots=new LegacyHttp(new WeatherApi.Assets(){public InputStream open(String path)throws IOException{return real.open("bing-ca/Microsoft-RSA-Root-2017.crt");}}){protected SSLContext newTlsContext()throws Exception{return SSLContext.getInstance("TLS");}};
    try{new WeatherApi(wrongRoots,real).fetch(cn.get(0),System.currentTimeMillis());throw new AssertionError("Untrusted certificate accepted");}catch(SSLException expected){checks++;}
    System.out.println("Live protocol/TLS probes passed on the host JVM; API18 device TLS is a separate check.");
  }
}
