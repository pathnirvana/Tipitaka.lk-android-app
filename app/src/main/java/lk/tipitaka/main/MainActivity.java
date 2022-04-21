package lk.tipitaka.main;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.KeyEvent;
import android.webkit.ConsoleMessage;
import android.webkit.WebChromeClient;
import android.webkit.WebView;
import android.webkit.WebViewClient;

public class MainActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // my code below
        WebViewClient client = new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, String url) {
                try {
                    java.net.URL givenUrl = new java.net.URL(url);
                    if (givenUrl.toString().contains("youtube") || givenUrl.toString().contains("pitaka.lk")) {
                        Intent intent = new Intent(Intent.ACTION_VIEW, android.net.Uri.parse(url));
                        startActivity(intent);
                        return true;
                    }
                } catch (java.net.MalformedURLException e) {}
                return false; // open index/static pages links in the webview itself
            }
//            @Override
//            public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
//                return assetLoader.shouldInterceptRequest(request.getUrl());
//            }
        };

        WebView myWebView = (WebView) findViewById(R.id.mainWebView);
        myWebView.setWebChromeClient(new WebChromeClient() { // console messages from the js webpage
            @Override
            public boolean onConsoleMessage(ConsoleMessage consoleMessage) {
                Log.i("LOG_TAG", consoleMessage.message() + " -- From line " +
                        consoleMessage.lineNumber() + " of " + consoleMessage.sourceId());
                return true;
            }
        });

        android.webkit.WebSettings webSettings = myWebView.getSettings();
        webSettings.setJavaScriptEnabled(true);
        webSettings.setDomStorageEnabled(true); // for localStorage
        webSettings.setAllowFileAccessFromFileURLs(true);
        webSettings.setAllowUniversalAccessFromFileURLs(true);
        webSettings.setLayoutAlgorithm(android.webkit.WebSettings.LayoutAlgorithm.SINGLE_COLUMN);
        webSettings.setBuiltInZoomControls(true);
        webSettings.setDisplayZoomControls(false);
        myWebView.addJavascriptInterface(new WebAppInterface(this, myWebView), "Android");
        myWebView.setWebViewClient(client);

        String webviewLoadUrl = "file:///android_asset/index.html";
        Log.e("LOG_TAG", "webview Url : " + webviewLoadUrl);
        myWebView.loadUrl(webviewLoadUrl);
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        WebView myWebView = (WebView) findViewById(R.id.mainWebView);
        // Check if the key event was the Back button and if there's history
        if ((keyCode == KeyEvent.KEYCODE_BACK) && myWebView.canGoBack()) {
            myWebView.goBack();
            return true;
        }
        // If it wasn't the Back key or there's no web page history, bubble up to the default
        // system behavior (probably exit the activity)
        return super.onKeyDown(keyCode, event);
    }
}
