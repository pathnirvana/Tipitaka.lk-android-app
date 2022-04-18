package lk.tipitaka.main;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.os.Environment;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.util.Log;
import android.webkit.JavascriptInterface;
import android.webkit.WebView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static android.database.Cursor.FIELD_TYPE_FLOAT;
import static android.database.Cursor.FIELD_TYPE_INTEGER;
import static android.database.Cursor.FIELD_TYPE_STRING;

public class WebAppInterface {
    Context mContext;
    Activity activity;
    WebView webView;
    Map<String, DatabaseHelper> openedDbs;
    Map<String, String> runAsyncResults = new ConcurrentHashMap<>();
    JSONObject dbVersions;
    String dbRootPath = "static/db/";
    String bjtBooksFolder = "", bjtImageExt = "";

    /** Instantiate the interface and set the context */
    WebAppInterface(Activity act, WebView wv) {
        openedDbs = new HashMap<>();
        mContext = act.getApplicationContext();
        activity = act;
        webView = wv;
        this.initBjtParams();
    }

    /** Show a toast from the web page - not used for now since JS toast looks better */
    @JavascriptInterface
    public void showToast(String toast) {
        Toast.makeText(mContext, toast, Toast.LENGTH_SHORT).show();
    }

    @JavascriptInterface
    public void runAsync(final String rand, final String funcName, final String jsonParams) {
            final WebAppInterface wai = this;
            new Thread() {
                @Override public void run() {
                    try {
                        final JSONObject params = new JSONObject(jsonParams);
                        String result = (String) wai.getClass().getMethod(funcName, JSONObject.class).invoke(wai, params);
                        wai.jsResolve(rand, true, result);
                    } catch (InvocationTargetException ite) { // exceptions inside the funcName function
                        wai.jsResolve(rand, false, ite.getCause().toString());
                    } catch (Exception e) {
                        wai.jsResolve(rand, false, e.toString());
                    }
                }
            }.start();
    }
    private void jsResolve(String rand, boolean isSuccess, String result) { // notify that result is ready
        runAsyncResults.put(rand, result);
        final String url = "javascript:" + rand + ".callback(" + isSuccess + ")";
        Log.i("LOG_TAG", "calling js method with url " + url);
        webView.post(new Runnable() {
            @Override
            public void run() {
                webView.loadUrl(url);
            }
        });
    }
    @JavascriptInterface
    public String runAsyncResult(String rand) { // returns the result from runAsync to JS
        String result = runAsyncResults.get(rand);
        runAsyncResults.remove(rand);
        return result;
    }

    public String openDbs(JSONObject jsonVersions) throws IOException {
        Log.i("LOG_TAG", "db versions initialized string " + jsonVersions);
        dbVersions = jsonVersions;
        Iterator<String> types = dbVersions.keys();
        while(types.hasNext()) {
            String dbPath = getDbPath(types.next());
            String vDbName = getVersionedDbName(dbPath);
            openedDbs.put(vDbName, new DatabaseHelper(mContext, vDbName, dbPath));
        }
        return "Initialized"; // not really needed
    }

    public String runSqliteQuery(JSONObject params) throws JSONException {
        String type = params.getString("type"), sql = params.getString("sql");
        String vDbName = getVersionedDbName(getDbPath(type));
        DatabaseHelper dbHelper = openedDbs.get(vDbName);
        return cursorToJson(dbHelper.runQuery(sql, null));
    }

    //@JavascriptInterface
    //public String all(String vDbName, String sql, String[] params) {
    //    DatabaseHelper dbHelper = openedDbs.get(vDbName);
    //    return cursorToJson(dbHelper.runQuery(sql, params));
    //}

    private String getDbPath(String type) {
        return dbRootPath + type + ".db";
    }

    private String getVersionedDbName(String dbPath) {
        File fDbPath = new File(dbPath);
        int dbVersion = 0;
        try {
            String dbName = fDbPath.getName().split("\\.")[0];
            dbVersion = dbVersions.getInt(dbName);
        } catch (JSONException ex) {}
        return "" + dbVersion + "@" + fDbPath.getName();
    }

    private static String cursorToJson(Cursor cursor) {
        JSONArray resultSet = new JSONArray();

        cursor.moveToFirst();
        while (!cursor.isAfterLast()) {

            int totalColumn = cursor.getColumnCount();
            JSONObject rowObject = new JSONObject();

            for (int i = 0; i < totalColumn; i++) {
                if (cursor.getColumnName(i) != null) {
                    try {
                        switch(cursor.getType(i)) {
                            case FIELD_TYPE_INTEGER:
                                rowObject.put(cursor.getColumnName(i), cursor.getInt(i));
                                break;
                            case FIELD_TYPE_FLOAT:
                                rowObject.put(cursor.getColumnName(i), cursor.getDouble(i));
                                break;
                            case FIELD_TYPE_STRING:
                                rowObject.put(cursor.getColumnName(i), cursor.getString(i));
                                break;
                            default: //NULL or BLOB put empty
                                rowObject.put(cursor.getColumnName(i), "");
                        }
                    } catch (Exception e) {
                        Log.e("LOG_TAG", e.getMessage());
                    }
                }
            }

            resultSet.put(rowObject);
            cursor.moveToNext();
        }

        cursor.close();
        Log.d("LOG_TAG", "resultSet size " + resultSet.length());
        try {
            return resultSet.toString(2);
        } catch (JSONException ex) {
            return ex.toString();
        }
    }

    @JavascriptInterface
    public String getBjtParams() {
        if (bjtBooksFolder.isEmpty())
            return "";
        return bjtBooksFolder + "|" + bjtImageExt;
    }
    private boolean initBjtParams() {
        //Context context = this.getApplicationContext();
        if (ContextCompat.checkSelfPermission(mContext, Manifest.permission.READ_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED) {

            ActivityCompat.requestPermissions(activity,
                    new String[]{Manifest.permission.READ_EXTERNAL_STORAGE},
                    101);
        }

        String[] sdPaths = this.getExtSdCardDataPaths(mContext);
        Log.e("LOG_TAG", "secondary storage path = " +  java.util.Arrays.toString(sdPaths));
        File[] paths = new File[sdPaths.length*2 + 2];
        paths[0] = new File(Environment.getExternalStoragePublicDirectory(
                Environment.DIRECTORY_PICTURES), "bjt_newbooks");
        paths[1] = new File(Environment.getExternalStoragePublicDirectory(
                Environment.DIRECTORY_PICTURES), "bjt_books");
        for (int i = 0; i < sdPaths.length; i++) {
            paths[2 + i*2] = new File(sdPaths[i], "Pictures/bjt_newbooks");
            paths[2 + i*2 + 1] = new File(sdPaths[i], "Pictures/bjt_books");
        }

        String[] files = new String[2];
        files[0] = "10/DN1_Page_001.jpg";
        files[1] = "10/DN1_Page_001.png";

        for (File p : paths) {
            for (String f : files) {
                File filePublic = new File(p, f);
                if (filePublic.exists()) {
                    Log.e("LOG_TAG", "found file " + filePublic.getPath());
                    bjtBooksFolder = "file://" + p.getPath();
                    bjtImageExt = f.substring(f.length() - 3);
                    return true;
                    //return "books_folder=" + "file://" + p.getPath() +
                    //        "/&image_extension=" + f.substring(f.length() - 3);
                }
            }
        }
        Log.e("LOG_TAG", "Non of the files exist. Loading remotely.");
        return false;
        //return "load_books_remote=1";
    }

    private String[] getExtSdCardDataPaths(Context context) {
        List<String> paths = new ArrayList<String>();
        for (File file : context.getExternalFilesDirs("external")) {
            if (file != null) {
                int index = file.getAbsolutePath().lastIndexOf("/Android/data");
                if (index >= 0) {
                    String path = file.getAbsolutePath().substring(0, index);
                    try {
                        path = new File(path).getCanonicalPath();
                    } catch (IOException e) {
                        // Keep non-canonical path.
                    }
                    paths.add(path);
                }
            }
        }
        if (paths.isEmpty()) paths.add("/storage/sdcard1");
        return paths.toArray(new String[0]);
    }
}
