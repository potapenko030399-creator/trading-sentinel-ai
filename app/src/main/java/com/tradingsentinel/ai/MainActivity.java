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
import android.os.Handler;
import android.os.Looper;
import android.util.Base64;
import android.util.DisplayMetrics;
import android.webkit.JavascriptInterface;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;

public class MainActivity extends Activity {
    private static final int REQ_CAPTURE = 7001;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private WebView webView;
    private MediaProjection projection;
    private ImageReader reader;
    private android.hardware.display.VirtualDisplay virtualDisplay;
    private boolean capturing = false;
    private long lastFrameMs = 0;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        webView = new WebView(this);
        setContentView(webView);
        WebSettings s = webView.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setAllowFileAccess(true);
        s.setAllowContentAccess(true);
        s.setMediaPlaybackRequiresUserGesture(false);
        webView.setWebViewClient(new WebViewClient());
        webView.setWebChromeClient(new WebChromeClient());
        webView.addJavascriptInterface(new AndroidBridge(), "AndroidCapture");
        webView.loadUrl("file:///android_asset/index.html");
    }

    private void requestCapture() {
        try {
            MediaProjectionManager mgr=(MediaProjectionManager)getSystemService(Context.MEDIA_PROJECTION_SERVICE);
            if(mgr!=null) startActivityForResult(mgr.createScreenCaptureIntent(),REQ_CAPTURE);
        } catch(Exception e) {
            notifyJs("window.nativeCaptureDenied&&window.nativeCaptureDenied()");
        }
    }

    private void startCapture(int resultCode, Intent data) {
        stopCapture();
        try {
            MediaProjectionManager mgr=(MediaProjectionManager)getSystemService(Context.MEDIA_PROJECTION_SERVICE);
            projection=mgr==null?null:mgr.getMediaProjection(resultCode,data);
            if(projection==null){notifyJs("window.nativeCaptureDenied&&window.nativeCaptureDenied()");return;}
            try { startForegroundService(new Intent(this,CaptureService.class)); } catch(Exception ignored) {}

            DisplayMetrics dm=getResources().getDisplayMetrics();
            int sw=Math.max(320,dm.widthPixels), sh=Math.max(320,dm.heightPixels), dpi=dm.densityDpi;
            int w=Math.min(sw,720), h=Math.max(320,Math.round(sh*(w/(float)sw)));
            reader=ImageReader.newInstance(w,h,PixelFormat.RGBA_8888,2);
            reader.setOnImageAvailableListener(r->{
                long now=System.currentTimeMillis();
                if(!capturing || now-lastFrameMs<220)return;
                lastFrameMs=now;
                Image image=null;
                try{
                    image=r.acquireLatestImage(); if(image==null)return;
                    Image.Plane p=image.getPlanes()[0]; ByteBuffer buf=p.getBuffer();
                    int ps=p.getPixelStride(), rs=p.getRowStride();
                    int paddedWidth=image.getWidth()+Math.max(0,rs-ps*image.getWidth())/Math.max(1,ps);
                    Bitmap full=Bitmap.createBitmap(paddedWidth,image.getHeight(),Bitmap.Config.ARGB_8888);
                    full.copyPixelsFromBuffer(buf);
                    Bitmap frame=Bitmap.createBitmap(full,0,0,image.getWidth(),image.getHeight());
                    full.recycle();
                    ByteArrayOutputStream out=new ByteArrayOutputStream(180000);
                    frame.compress(Bitmap.CompressFormat.JPEG,55,out); frame.recycle();
                    String b64=Base64.encodeToString(out.toByteArray(),Base64.NO_WRAP);
                    notifyJs("window.nativeScreenFrame&&window.nativeScreenFrame('data:image/jpeg;base64,"+b64+"')");
                } catch(Exception ignored) {
                } finally { if(image!=null) image.close(); }
            },mainHandler);
            virtualDisplay=projection.createVirtualDisplay("TradingSentinelAI",w,h,dpi,
                    android.hardware.display.DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                    reader.getSurface(),null,mainHandler);
            capturing=true;
            notifyJs("window.nativeCaptureStarted&&window.nativeCaptureStarted()");
        } catch(Exception e) {
            capturing=false; cleanupProjection();
            notifyJs("window.nativeCaptureDenied&&window.nativeCaptureDenied()");
        }
    }

    private void notifyJs(String script) {
        mainHandler.post(()->{ if(webView!=null && !isFinishing()) webView.evaluateJavascript(script,null); });
    }

    private void cleanupProjection() {
        if(virtualDisplay!=null){try{virtualDisplay.release();}catch(Exception ignored){} virtualDisplay=null;}
        if(reader!=null){try{reader.close();}catch(Exception ignored){} reader=null;}
        if(projection!=null){try{projection.stop();}catch(Exception ignored){} projection=null;}
    }

    private void stopCapture() {
        capturing=false; cleanupProjection();
        try{stopService(new Intent(this,CaptureService.class));}catch(Exception ignored){}
        if(webView!=null)notifyJs("window.nativeCaptureStopped&&window.nativeCaptureStopped()");
    }

    @Override protected void onActivityResult(int requestCode,int resultCode,Intent data) {
        super.onActivityResult(requestCode,resultCode,data);
        if(requestCode==REQ_CAPTURE){
            if(resultCode==RESULT_OK && data!=null)startCapture(resultCode,data);
            else notifyJs("window.nativeCaptureDenied&&window.nativeCaptureDenied()");
        }
    }

    public class AndroidBridge {
        @JavascriptInterface public void startScreenCapture(){mainHandler.post(MainActivity.this::requestCapture);}
        @JavascriptInterface public void stopScreenCapture(){mainHandler.post(MainActivity.this::stopCapture);}
    }

    @Override protected void onDestroy() {
        stopCapture();
        if(webView!=null){webView.loadUrl("about:blank");webView.destroy();webView=null;}
        super.onDestroy();
    }
}