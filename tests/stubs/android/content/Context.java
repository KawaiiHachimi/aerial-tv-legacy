package android.content;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
/** Host-only preference/context stand-in; never compiled into the APK. */
public class Context {
  public static final int MODE_PRIVATE=0;
  private final Map<String,SharedPreferences> files=new ConcurrentHashMap<String,SharedPreferences>();
  public Context getApplicationContext(){return this;}
  public String getString(int id){return Integer.toString(id);}
  public SharedPreferences getSharedPreferences(String name,int mode){
    if(!files.containsKey(name))files.put(name,new MemoryPreferences());return files.get(name);
  }
  private static final class MemoryPreferences implements SharedPreferences {
    final Map<String,Object> data=new ConcurrentHashMap<String,Object>();
    public Map<String,?> getAll(){return new HashMap<String,Object>(data);}
    public String getString(String k,String d){return data.containsKey(k)?(String)data.get(k):d;}
    @SuppressWarnings("unchecked") public Set<String> getStringSet(String k,Set<String>d){return data.containsKey(k)?(Set<String>)data.get(k):d;}
    public int getInt(String k,int d){return data.containsKey(k)?(Integer)data.get(k):d;}
    public long getLong(String k,long d){return data.containsKey(k)?(Long)data.get(k):d;}
    public float getFloat(String k,float d){return data.containsKey(k)?(Float)data.get(k):d;}
    public boolean getBoolean(String k,boolean d){return data.containsKey(k)?(Boolean)data.get(k):d;}
    public boolean contains(String k){return data.containsKey(k);}
    public void registerOnSharedPreferenceChangeListener(OnSharedPreferenceChangeListener l){}
    public void unregisterOnSharedPreferenceChangeListener(OnSharedPreferenceChangeListener l){}
    public Editor edit(){return new MemoryEditor();}
    private final class MemoryEditor implements Editor {
      final Map<String,Object> changes=new HashMap<String,Object>();boolean clear;
      public Editor putString(String k,String v){changes.put(k,v);return this;}
      public Editor putStringSet(String k,Set<String>v){changes.put(k,v);return this;}
      public Editor putInt(String k,int v){changes.put(k,v);return this;}
      public Editor putLong(String k,long v){changes.put(k,v);return this;}
      public Editor putFloat(String k,float v){changes.put(k,v);return this;}
      public Editor putBoolean(String k,boolean v){changes.put(k,v);return this;}
      public Editor remove(String k){changes.put(k,null);return this;}
      public Editor clear(){clear=true;return this;}
      public boolean commit(){if(clear)data.clear();for(Map.Entry<String,Object> e:changes.entrySet())if(e.getValue()==null)data.remove(e.getKey());else data.put(e.getKey(),e.getValue());return true;}
      public void apply(){commit();}
    }
  }
}
