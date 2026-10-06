package com.tradingsentinel.ai;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.PixelFormat;
import android.media.Image;
import android.media.ImageReader;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.os.Bundle;
import android.util.Base64;
import android.webkit.*;
import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;

public class MainActivity extends Activity {
 private static final int REQ_CAPTURE=7001;
 private WebView webView; private MediaProjection projection; private ImageReader reader;
 private android.hardware.display.VirtualDisplay virtualDisplay; private boolean capturing=false;
 @Override public void onCreate(Bundle b){super.onCreate(b); webView=new WebView(this);setContentView(webView);
  WebSettings s=webView.getSettings();s.setJavaScriptEnabled(true);s.setDomStorageEnabled(true);s.setAllowFileAccess(true);s.setAllowContentAccess(true);
  s.setMediaPlaybackRequiresUserGesture(false);webView.setWebViewClient(new WebViewClient());webView.setWebChromeClient(new WebChromeClient());
  webView.addJavascriptInterface(new AndroidBridge(),"AndroidCapture");webView.loadUrl("file:///android_asset/index.html");}
 private void requestCapture(){Intent i=((MediaProjectionManager)getSystemService(Context.MEDIA_PROJECTION_SERVICE)).createScreenCaptureIntent();startActivityForResult(i,REQ_CAPTURE);}
 private void startCapture(int resultCode,Intent data){
  try{startForegroundService(new Intent(this,CaptureService.class));}catch(Exception ignored){}
  projection=((MediaProjectionManager)getSystemService(Context.MEDIA_PROJECTION_SERVICE)).getMediaProjection(resultCode,data); if(projection==null)return;
  android.util.DisplayMetrics dm=getResources().getDisplayMetrics();int sw=dm.widthPixels,sh=dm.heightPixels,dpi=dm.densityDpi;
  int w=Math.min(sw,900),h=Math.max(1,Math.round(sh*(w/(float)sw))); reader=ImageReader.newInstance(w,h,PixelFormat.RGBA_8888,2);
  reader.setOnImageAvailableListener(r->{if(!capturing)return;Image image=null;try{image=r.acquireLatestImage();if(image==null)return;
   Image.Plane p=image.getPlanes()[0];ByteBuffer buf=p.getBuffer();int ps=p.getPixelStride(),rs=p.getRowStride();int pad=rs-ps*image.getWidth();
   Bitmap full=Bitmap.createBitmap(image.getWidth()+pad/ps,image.getHeight(),Bitmap.Config.ARGB_8888);full.copyPixelsFromBuffer(buf);
   Bitmap frame=Bitmap.createBitmap(full,0,0,image.getWidth(),image.getHeight());full.recycle();ByteArrayOutputStream out=new ByteArrayOutputStream();
   frame.compress(Bitmap.CompressFormat.JPEG,65,out);frame.recycle();String b64=Base64.encodeToString(out.toByteArray(),Base64.NO_WRAP);
   String js="window.nativeScreenFrame&&window.nativeScreenFrame('data:image/jpeg;base64,"+b64+"')";runOnUiThread(()->webView.evaluateJavascript(js,null));
  }catch(Exception ignored){}finally{if(image!=null)image.close();}},null);
  virtualDisplay=projection.createVirtualDisplay("TradingSentinelAI",w,h,dpi,android.hardware.display.DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,reader.getSurface(),null,null);
  capturing=true;webView.evaluateJavascript("window.nativeCaptureStarted&&window.nativeCaptureStarted()",null);
 }
 private void stopCapture(){capturing=false;if(virtualDisplay!=null){virtualDisplay.release();virtualDisplay=null;}if(reader!=null){reader.close();reader=null;}if(projection!=null){projection.stop();projection=null;}stopService(new Intent(this,CaptureService.class));if(webView!=null)webView.evaluateJavascript("window.nativeCaptureStopped&&window.nativeCaptureStopped()",null);}
 @Override protected void onActivityResult(int r,int code,Intent d){super.onActivityResult(r,code,d);if(r==REQ_CAPTURE){if(code==RESULT_OK&&d!=null)startCapture(code,d);else webView.evaluateJavascript("window.nativeCaptureDenied&&window.nativeCaptureDenied()",null);}}
 public class AndroidBridge{@JavascriptInterface public void startScreenCapture(){runOnUiThread(MainActivity.this::requestCapture);}@JavascriptInterface public void stopScreenCapture(){runOnUiThread(MainActivity.this::stopCapture);}}
 @Override protected void onDestroy(){stopCapture();if(webView!=null){webView.loadUrl("about:blank");webView.destroy();}super.onDestroy();}
}