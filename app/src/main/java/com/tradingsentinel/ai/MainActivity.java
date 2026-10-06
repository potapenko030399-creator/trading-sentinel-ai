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
import android.webkit.JavascriptInterface;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.webkit.WebChromeClient;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;

public class MainActivity extends Activity {
    private static final int REQ_CAPTURE = 7001;
    private WebView webView;
    private MediaProjection projection;
    private ImageReader reader;
    private android.hardware.display.VirtualDisplay virtualDisplay;
    private boolean capturing = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        webView = new WebView(this);
        setContentView(webView);

        WebSettings s = webView.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setAllowFileAccess(true);
        s.setAllowContentAccess(true);
        s.setMediaPlaybackRequiresUserGesture(false);
        s.setBuiltInZoomControls(false);
        s.setDisplayZoomControls(false);
        webView.setWebViewClient(new WebViewClient());
        webView.setWebChromeClient(new WebChromeClient());
        webView.addJavascriptInterface(new AndroidBridge(), "AndroidCapture");
        webView.loadUrl("file:///android_asset/index.html");
    }

    private void requestCapture() {
        Intent i = ((MediaProjectionManager)getSystemService(Context.MEDIA_PROJECTION_SERVICE))
                .createScreenCaptureIntent();
        startActivityForResult(i, REQ_CAPTURE);
    }

    private void startCapture(int resultCode, Intent data) {
        try {
            Intent service = new Intent(this, CaptureService.class);
            startForegroundService(service);
        } catch (Exception ignored) {}

        MediaProjectionManager mpm = (MediaProjectionManager)getSystemService(Context.MEDIA_PROJECTION_SERVICE);
        projection = mpm.getMediaProjection(resultCode, data);
        if (projection == null) return;

        android.util.DisplayMetrics dm = getResources().getDisplayMetrics();
        int screenW = dm.widthPixels;
        int screenH = dm.heightPixels;
        int density = dm.densityDpi;

        int w = Math.min(screenW, 900);
        int h = Math.max(1, Math.round(screenH * (w / (float)screenW)));

        reader = ImageReader.newInstance(w, h, PixelFormat.RGBA_8888, 2);
        reader.setOnImageAvailableListener(r -> {
            if (!capturing) return;
            Image image = null;
            try {
                image = r.acquireLatestImage();
                if (image == null) return;
                Image.Plane p = image.getPlanes()[0];
                ByteBuffer buf = p.getBuffer();
                int pixelStride = p.getPixelStride();
                int rowStride = p.getRowStride();
                int rowPadding = rowStride - pixelStride * image.getWidth();
                Bitmap full = Bitmap.createBitmap(
                        image.getWidth() + rowPadding / pixelStride,
                        image.getHeight(),
                        Bitmap.Config.ARGB_8888);
                full.copyPixelsFromBuffer(buf);
                Bitmap frame = Bitmap.createBitmap(full, 0, 0, image.getWidth(), image.getHeight());
                full.recycle();

                ByteArrayOutputStream out = new ByteArrayOutputStream();
                frame.compress(Bitmap.CompressFormat.JPEG, 72, out);
                frame.recycle();
                String b64 = Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP);
                String js = "window.nativeScreenFrame && window.nativeScreenFrame('data:image/jpeg;base64," + b64 + "')";
                runOnUiThread(() -> webView.evaluateJavascript(js, null));
            } catch (Exception ignored) {
            } finally {
                if (image != null) image.close();
            }
        }, null);

        virtualDisplay = projection.createVirtualDisplay(
                "TradingSentinelAI",
                w, h, density,
                android.hardware.display.DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                reader.getSurface(), null, null);
        capturing = true;
        webView.evaluateJavascript("window.nativeCaptureStarted && window.nativeCaptureStarted()", null);
    }

    private void stopCapture() {
        capturing = false;
        if (virtualDisplay != null) { virtualDisplay.release(); virtualDisplay = null; }
        if (reader != null) { reader.close(); reader = null; }
        if (projection != null) { projection.stop(); projection = null; }
        stopService(new Intent(this, CaptureService.class));
        if (webView != null) webView.evaluateJavascript("window.nativeCaptureStopped && window.nativeCaptureStopped()", null);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQ_CAPTURE) {
            if (resultCode == RESULT_OK && data != null) startCapture(resultCode, data);
            else webView.evaluateJavascript("window.nativeCaptureDenied && window.nativeCaptureDenied()", null);
        }
    }

    public class AndroidBridge {
        @JavascriptInterface public void startScreenCapture() { runOnUiThread(MainActivity.this::requestCapture); }
        @JavascriptInterface public void stopScreenCapture() { runOnUiThread(MainActivity.this::stopCapture); }
    }

    @Override
    protected void onDestroy() {
        stopCapture();
        if (webView != null) { webView.loadUrl("about:blank"); webView.destroy(); }
        super.onDestroy();
    }
}
