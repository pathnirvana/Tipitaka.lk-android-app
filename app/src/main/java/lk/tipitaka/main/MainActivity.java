package lk.tipitaka.main;

import android.content.Intent;
import android.content.pm.ApplicationInfo;
import androidx.activity.OnBackPressedCallback;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;
import android.os.Bundle;
import android.util.Log;
import android.webkit.ConsoleMessage;
import android.webkit.WebChromeClient;
import android.webkit.WebView;
import android.webkit.WebViewClient;

public class MainActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        setupEdgeToEdge();

        final WebView myWebView = (WebView) findViewById(R.id.mainWebView);
        if ((getApplicationInfo().flags & ApplicationInfo.FLAG_DEBUGGABLE) != 0) {
            WebView.setWebContentsDebuggingEnabled(true); // chrome://inspect for debug builds
        }

        // back goes back in the webview history (KEYCODE_BACK is not delivered with predictive back from API 36)
        final OnBackPressedCallback backCallback = new OnBackPressedCallback(false) {
            @Override
            public void handleOnBackPressed() {
                myWebView.goBack();
            }
        };
        getOnBackPressedDispatcher().addCallback(this, backCallback);

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

            @Override
            public void doUpdateVisitedHistory(WebView view, String url, boolean isReload) {
                // when there is no history left the system back closes the app (with the predictive animation)
                backCallback.setEnabled(view.canGoBack());
            }
        };

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
        // recitation audio starts after its timings are loaded (async) - old WebViews no longer count that as the user's tap
        webSettings.setMediaPlaybackRequiresUserGesture(false);
        webSettings.setAllowFileAccess(true); // local BJT scans (file://) - the default is false from API 30
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

    /** API 35+ draws behind the status/navigation bars - pad the content by the bars, the cutout and the keyboard */
    private void setupEdgeToEdge() {
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        WindowInsetsControllerCompat controller = WindowCompat.getInsetsController(getWindow(), getWindow().getDecorView());
        controller.setAppearanceLightStatusBars(false); // white icons on the primary color
        controller.setAppearanceLightNavigationBars(false);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.root), (v, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars()
                    | WindowInsetsCompat.Type.displayCutout() | WindowInsetsCompat.Type.ime());
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom);
            return WindowInsetsCompat.CONSUMED;
        });
    }
}
