package app.aerial.legacy;

import android.graphics.*;
import android.graphics.drawable.Drawable;

/** One generic icon card for apps without a TV banner. Never guesses or rewrites a brand logo. */
final class AppTileArt {
  static void draw(Canvas out,Drawable icon,int w,int h) {
    final int sample=256;
    Bitmap image=Bitmap.createBitmap(sample,sample,Bitmap.Config.ARGB_8888);
    Rect old=new Rect(icon.getBounds());icon.setBounds(0,0,sample,sample);
    try { icon.draw(new Canvas(image)); } finally { icon.setBounds(old); }
    int[] pixels=new int[sample*sample];image.getPixels(pixels,0,sample,0,0,sample,sample);
    Rect visible=new Rect(sample,sample,0,0);
    double red=0,green=0,blue=0,total=0;float[] hsv=new float[3];
    for(int y=0;y<sample;y++)for(int x=0;x<sample;x++) {
      int c=pixels[y*sample+x];if(Color.alpha(c)<32)continue;
      visible.left=Math.min(visible.left,x);visible.top=Math.min(visible.top,y);
      visible.right=Math.max(visible.right,x+1);visible.bottom=Math.max(visible.bottom,y+1);
      if(Color.alpha(c)<128)continue;
      Color.colorToHSV(c,hsv);double weight=hsv[1]*hsv[2]+.04;
      red+=Color.red(c)*weight;green+=Color.green(c)*weight;blue+=Color.blue(c)*weight;total+=weight;
    }
    int tint=total==0?0xFF818696:Color.rgb((int)(red/total),(int)(green/total),(int)(blue/total));
    Color.colorToHSV(tint,hsv);
    float max=Math.max(Color.red(tint),Math.max(Color.green(tint),Color.blue(tint)))/255f;
    float min=Math.min(Color.red(tint),Math.min(Color.green(tint),Color.blue(tint)))/255f;
    float light=(max+min)/2f, saturation=max==min?0:(max-min)/(1-Math.abs(2*light-1));
    int background=0xFF545458;
    if(saturation>=.18f) {
      // Aerial tileFromTint: midpoint lightness .48, saturation clamped to .5..85.
      saturation=Math.max(.5f,Math.min(.85f,saturation));
      hsv[1]=2*saturation/(1+saturation);hsv[2]=.48f*(1+saturation);
      background=Color.HSVToColor(hsv);
    }
    SystemTiles.background(out,w,h,background);
    if(!visible.isEmpty()) {
      float scale=Math.min(h*.58f/visible.height(),w*.62f/visible.width());
      float width=visible.width()*scale,height=visible.height()*scale;
      RectF target=new RectF((w-width)/2,(h-height)/2,(w+width)/2,(h+height)/2);
      out.save();
      // Mask square phone icons consistently; preserve transparent shapes and all original colors.
      if(Color.alpha(image.getPixel(visible.left,visible.top))>200
          && Color.alpha(image.getPixel(visible.right-1,visible.bottom-1))>200) {
        Path clip=new Path();float radius=Math.min(width,height)*.2f;
        clip.addRoundRect(target,radius,radius,Path.Direction.CW);out.clipPath(clip);
      }
      out.drawBitmap(image,visible,target,new Paint(Paint.ANTI_ALIAS_FLAG|Paint.FILTER_BITMAP_FLAG));out.restore();
    }
    image.recycle();
  }
}
