package com.cradle.iitc_mobile;

import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.net.http.SslError;
import android.os.Environment;
import android.preference.PreferenceManager;
import android.util.Log;
import android.webkit.SslErrorHandler;
import android.webkit.WebResourceResponse;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import java.io.ByteArrayInputStream;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

public class IITC_WebViewClient extends WebViewClient {

    private static final ByteArrayInputStream STYLE = new ByteArrayInputStream(
            "body, #dashboard_container, #map_canvas { background: #000 !important; }"
                    .getBytes());
    private static final ByteArrayInputStream EMPTY = new ByteArrayInputStream(
            "".getBytes());

    private String mIitcPath;
    private boolean mIitcInjected = false;
    private final IITC_Mobile mIitc;
    private final IITC_TileManager mTileManager;

    public IITC_WebViewClient(IITC_Mobile iitc) {
        this.mIitc = iitc;
        this.mTileManager = new IITC_TileManager(mIitc);
        this.mIitcPath = Environment.getExternalStorageDirectory().getPath() + "/IITC_Mobile/";
    }

    // TODO use somewhere else:
    // Toast.makeText(mIitc, "File " + mIitcPath +
    // "dev/total-conversion-build.user.js not found. " +
    // "Disable developer mode or add iitc files to the dev folder.",
    // Toast.LENGTH_LONG).show();

    // enable https
    @Override
    public void onReceivedSslError(WebView view, SslErrorHandler handler, SslError error) {
        handler.proceed();
    }

    @Override
    public void onPageFinished(WebView view, String url) {
        if (url.startsWith("http://www.ingress.com/intel")
                || url.startsWith("https://www.ingress.com/intel")) {
            if (mIitcInjected) return;
            Log.d("iitcm", "injecting iitc..");
            loadScripts((IITC_WebView) view);
            mIitcInjected = true;
        }
        super.onPageFinished(view, url);
    }

    private void loadScripts(IITC_WebView view) {
        List<String> scripts = new LinkedList<String>();

        scripts.add("script/total-conversion-build.user.js");

        // get the plugin preferences
        SharedPreferences sharedPref = PreferenceManager.getDefaultSharedPreferences(mIitc);
        Map<String, ?> all_prefs = sharedPref.getAll();

        // iterate through all plugins
        for (Map.Entry<String, ?> entry : all_prefs.entrySet()) {
            String plugin = entry.getKey();
            if (plugin.endsWith(".user.js") && entry.getValue().toString().equals("true")) {
                if (plugin.startsWith(mIitcPath)) {
                    scripts.add("user-plugin" + plugin);
                } else {
                    scripts.add("script/plugins/" + plugin);
                }
            }
        }

        // inject the user location script if enabled in settings
        if (Integer.parseInt(sharedPref.getString("pref_user_location_mode", "0")) != 0) {
            scripts.add("script/user-location.user.js");
        }

        String js = "(function(){['" + join(scripts, "','") + "'].forEach(function(src) {" +
                "var script = document.createElement('script');script.src = 'iitcm://'+src;" +
                "(document.body || document.head || document.documentElement).appendChild(script);" +
                "});})();";

        view.loadJS(js);
    }

    static public String join(List<String> list, String conjunction)
    {
        StringBuilder sb = new StringBuilder();
        boolean first = true;
        for (String item : list)
        {
            if (first)
                first = false;
            else
                sb.append(conjunction);
            sb.append(item);
        }
        return sb.toString();
    }

    /**
     * this method is called automatically when the Google login form is opened.
     */
    @Override
    public void onReceivedLoginRequest(WebView view, String realm, String account, String args) {
        Log.d("iitcm", "Login requested: " + realm + " " + account + " " + args);
        mIitcInjected = false;
        // ((IITC_Mobile) mContext).onReceivedLoginRequest(this, view, realm, account, args);
    }

    // read a file into a string
    // use the full path for File
    // if asset == true use the asset manager to open file
    // public String fileToString(String file, boolean asset) {
    // Scanner s = null;
    // String src = "";
    // if (!asset) {
    // File js_file = new File(file);
    // try {
    // s = new Scanner(js_file).useDelimiter("\\A");
    // } catch (FileNotFoundException e) {
    // e.printStackTrace();
    // Log.d("iitcm", "failed to parse file " + file);
    // return "false";
    // }
    // } else {
    // // load plugins from asset folder
    // AssetManager am = mIitc.getAssets();
    // try {
    // s = new Scanner(am.open(file)).useDelimiter("\\A");
    // } catch (IOException e) {
    // e.printStackTrace();
    // Log.d("iitcm", "failed to parse file assets/" + file);
    // return "false";
    // }
    // }
    //
    // if (s != null) {
    // src = s.hasNext() ? s.next() : "";
    // }
    // return src;
    // }

    // Check every external resource if it’s okay to load it and maybe replace
    // it
    // with our own content. This is used to block loading Niantic resources
    // which aren’t required and to inject IITC early into the site.
    // via http://stackoverflow.com/a/8274881/1684530
    @Override
    public WebResourceResponse shouldInterceptRequest(final WebView view, String url) {
        // if any tiles are requested, handle it with IITC_TileManager
        if (url.matches(".*tile.*jpg.*") // mapquest tiles | ovi tiles
                || url.matches(".*tile.*png.*") // cloudmade tiles
                || url.matches(".*mts.*googleapis.*smartmaps") // google tiles
                || url.matches(".*khms.*googleapis.*") // google satellite tiles
                || url.matches(".*tile.*jpeg.*") // bing tiles
                || url.matches(".*maps.*yandex.*tiles.*") // yandex maps
        ) {
            try {
                return mTileManager.getTile(url);
            } catch (Exception e) {
                e.printStackTrace();
                return super.shouldInterceptRequest(view, url);
            }
        } else if (url.contains("/css/common.css")) {
            return new WebResourceResponse("text/css", "UTF-8", STYLE);
            // } else if (url.contains("gen_dashboard.js")) {
            // // define initialize function to get rid of JS ReferenceError on intel page's 'onLoad'
            // String gen_dashboard_replacement = "window.initialize = function() {}";
            // return new WebResourceResponse("text/javascript", "UTF-8",
            // new ByteArrayInputStream(gen_dashboard_replacement.getBytes()));
        } else if (url.contains("/css/ap_icons.css")
                || url.contains("/css/map_icons.css")
                || url.contains("/css/common.css")
                || url.contains("/css/misc_icons.css")
                || url.contains("/css/style_full.css")
                || url.contains("/css/style_mobile.css")
                || url.contains("/css/portalrender.css")
                || url.contains("/css/portalrender_mobile.css")
                || url.contains("js/analytics.js")
                || url.contains("google-analytics.com/ga.js")) {
            return new WebResourceResponse("text/plain", "UTF-8", EMPTY);
        } else if (url.startsWith("iitcm:")) {
            return mIitc.getFileManager().getResponse(url);
        } else {
            return super.shouldInterceptRequest(view, url);
        }
    }

    // start non-ingress-intel-urls in another app...
    @Override
    public boolean shouldOverrideUrlLoading(WebView view, String url) {
        if (url.contains("ingress.com") || url.contains("appengine.google.com")) {
            // reload iitc if a poslink is clicked inside the app
            if (url.contains("intel?ll=")
                    || (url.contains("latE6") && url.contains("lngE6"))) {
                Log.d("iitcm",
                        "should be an internal clicked position link...reload script for: "
                                + url);
                mIitc.loadUrl(url);
            }
            return false;
        } else {
            Log.d("iitcm",
                    "no ingress intel link, start external app to load url: "
                            + url);
            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
            mIitc.startActivity(intent);
            return true;
        }
    }
}
