package app.aerial.legacy;

import android.content.*;
import android.graphics.*;
import android.graphics.drawable.Drawable;
import android.os.SystemClock;
import android.test.InstrumentationTestCase;
import java.io.*;
import java.lang.reflect.*;

/** Real API18 Canvas tests; generated glyph fixtures are not claimed to be third-party APK logos. */
public final class CardArtTest extends InstrumentationTestCase {
  public void testSystemStylesAndGenericIcons() throws Exception {
    Context context=getInstrumentation().getTargetContext();
    SystemTiles.Kind[] kinds=SystemTiles.Kind.values();
    Bitmap sheet=Bitmap.createBitmap(1200,820,Bitmap.Config.ARGB_8888);Canvas out=new Canvas(sheet);out.drawColor(0xFF13141A);
    Paint labels=new Paint(Paint.ANTI_ALIAS_FLAG);labels.setColor(Color.WHITE);labels.setTextSize(16);labels.setTypeface(AppFonts.regular(context));
    int index=0;
    for(SystemTiles.Kind kind:kinds) {
      Bitmap tile=Bitmap.createBitmap(264,148,Bitmap.Config.ARGB_8888);
      assertTrue(SystemTiles.draw(new Canvas(tile),kind,264,148));
      assertEquals("Unadorned corners have identical solid color",tile.getPixel(2,2),tile.getPixel(261,145));
      int white=0,yellow=0;
      for(int y=0;y<148;y++)for(int x=0;x<264;x++) {
        int c=tile.getPixel(x,y);assertTrue("Old Skia antialiased symbols keep the tile opaque",Color.alpha(c)>=250);
        if(Color.red(c)>245&&Color.green(c)>245&&Color.blue(c)>245)white++;
        if(Color.red(c)>230&&Color.green(c)>150&&Color.blue(c)<80)yellow++;
      }
      assertTrue(kind+" draws a visible symbol",white>100);
      if(kind==SystemTiles.Kind.WEATHER)assertTrue("Weather retains the yellow sun",yellow>20);
      int x=24+(index%4)*294,y=24+(index/4)*196;out.drawBitmap(tile,x,y,null);out.drawText(kind.name(),x,y+172,labels);
      tile.recycle();index++;
    }
    FileOutputStream f=context.openFileOutput("system-cards-preview.png",0);sheet.compress(Bitmap.CompressFormat.PNG,100,f);f.close();sheet.recycle();
    assertEquals(SystemTiles.Kind.SETTINGS,SystemTiles.of("com.stv.t2.globalsetting"));
    assertEquals(SystemTiles.Kind.STORE,SystemTiles.of("com.tcl.appmarket2"));
    assertNull("Unrelated apps must keep their own art",SystemTiles.of("example.user.player"));
    Bitmap icon=Bitmap.createBitmap(192,192,Bitmap.Config.ARGB_8888);Canvas c=new Canvas(icon);Paint p=new Paint(3);
    p.setColor(0xFF204080);c.drawRoundRect(new RectF(8,8,184,184),28,28,p);
    p.setColor(Color.RED);c.drawRect(57,64,91,128,p);p.setColor(Color.GREEN);c.drawRect(101,64,135,128,p);
    Bitmap color=Bitmap.createBitmap(352,198,Bitmap.Config.ARGB_8888);
    AppTileArt.draw(new Canvas(color),new android.graphics.drawable.BitmapDrawable(context.getResources(),icon),352,198);
    int red=0,green=0;long sumX=0;
    for(int y=0;y<198;y++)for(int x=0;x<352;x++) {int v=color.getPixel(x,y);if(Color.red(v)>210&&Color.green(v)<40){red++;sumX+=x;}if(Color.green(v)>210&&Color.red(v)<40){green++;sumX+=x;}}
    assertTrue("Original icon colors survive generic composition",red>300&&green>300);
    assertTrue("Logo remains centered",Math.abs(sumX/(float)(red+green)-176)<4);
    // Blank and transparent images must render a complete fallback background without crashing.
    Bitmap blank=Bitmap.createBitmap(64,64,Bitmap.Config.ARGB_8888);
    AppTileArt.draw(new Canvas(color),new android.graphics.drawable.BitmapDrawable(context.getResources(),blank),352,198);
    assertTrue(Color.alpha(color.getPixel(0,0))>=250);
    color.recycle();blank.recycle();icon.recycle();
  }
  public void testBingSelectionHasCachedPreview() throws Exception {
    final MainActivity activity=(MainActivity)getInstrumentation().startActivitySync(new Intent(getInstrumentation().getTargetContext(),MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
    Field field=MainActivity.class.getDeclaredField("home");field.setAccessible(true);final HomeView home=(HomeView)field.get(activity);
    final Method open=DesktopUi.class.getDeclaredMethod("open",int.class,int.class);open.setAccessible(true);
    final Method load=DesktopUi.class.getDeclaredMethod("loadThumbs");load.setAccessible(true);
    getInstrumentation().runOnMainSync(new Runnable(){public void run(){try{open.invoke(home.ui,DesktopUi.WALLPAPERS,Gradients.COUNT);load.invoke(home.ui);}catch(Exception e){throw new RuntimeException(e);}}});
    Field daily=DesktopUi.class.getDeclaredField("dailyThumb");daily.setAccessible(true);
    for(int n=0;n<150&&daily.get(home.ui)==null;n++)SystemClock.sleep(100);
    Bitmap preview=(Bitmap)daily.get(home.ui);assertNotNull("Cached Bing image appears on the TV selection card",preview);
    assertTrue(preview.getWidth()>0&&preview.getHeight()>0);
    SystemClock.sleep(450);
    getInstrumentation().runOnMainSync(new Runnable(){public void run(){try{
      Bitmap shot=Bitmap.createBitmap(home.getWidth(),home.getHeight(),Bitmap.Config.ARGB_8888);home.draw(new Canvas(shot));
      FileOutputStream out=activity.openFileOutput("wallpaper-preview.png",0);shot.compress(Bitmap.CompressFormat.PNG,100,out);out.close();shot.recycle();
      home.resetHome();
    }catch(Exception e){throw new RuntimeException(e);}}});
  }
}
