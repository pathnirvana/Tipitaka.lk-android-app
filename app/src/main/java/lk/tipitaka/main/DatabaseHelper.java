package lk.tipitaka.main;

import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import android.util.Log;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URL;
import java.util.zip.ZipInputStream;

public class DatabaseHelper extends SQLiteOpenHelper {
    private final Context context;
    private SQLiteDatabase db = null;

    private final String vDbName; //the extension may be .sqlite or .db
    private final String inDbAssetsPath;
    private final File outDbFile;

    public DatabaseHelper(Context context, String vDbName, String inDbPath) throws IOException {
        super(context, vDbName, null, 1);
        this.context = context;
        this.vDbName = vDbName;
        this.inDbAssetsPath = inDbPath;
        //Log.i("LOG_TAG", String.join(",", context.getAssets().list("")));

        this.outDbFile = context.getDatabasePath(vDbName);
        if (outDbFile.exists()) {
            Log.i("LOG_TAG", "Database exists " + vDbName);
            openDatabase();
        } else {
            Log.i("LOG_TAG", "Database doesn't exist " + vDbName);
            // try to delete any older versions before copying the new version
            deleteOldVersions(vDbName);

            getReadableDatabase();
            close(); // needed for android P (9)
            try {
                int copiedSize;
                // for now the apk size is less than 150mb hence all dbs put in assets
                //if (inDbAssetsPath.equals("static/db/dict-all.db")) {
                //    copiedSize = copyFromURL("https://tipitaka.lk/library/674");
                //} else {
                // a separate asset pack was created with the db files since the size with dbs exceeds 150mb
                // no need to copyFromURL anymore
                copiedSize = copyFromAssets();
                //}
                Log.i("LOG_TAG", "Copied db " + inDbAssetsPath + " of size " + copiedSize);
            } catch (IOException e) {
                Log.e("LOG_TAG", "Error copying db " + e);
                throw e;
            }
            openDatabase();
        }
    }

    private void openDatabase() {
        db = SQLiteDatabase.openDatabase(outDbFile.getPath(), null, SQLiteDatabase.OPEN_READONLY);
        Cursor c = db.rawQuery("SELECT name FROM sqlite_master WHERE type ='table' AND name NOT LIKE 'sqlite_%'", null);
        if (c.moveToFirst()) {
            while ( !c.isAfterLast() ) {
                Log.i("LOG_TAG","Table Name=> "+c.getString(0));
                c.moveToNext();
            }
        }
        c.close();
    }

    public Cursor runQuery(String sql, String[] params) {
        if (db == null) { // wait until the db copy is finished and db opened
            throw new Error("Please wait until db copy finished and search again.");
        }
        return db.rawQuery(sql, params);
    }

    private void deleteOldVersions(String vDbName) {
        String[] parts = vDbName.split("@");
        int dbVersion = Integer.parseInt(parts[0]);
        while (dbVersion > 0) {
            dbVersion--;
            File oldDbFile = context.getDatabasePath("" + dbVersion + "@" + parts[1]);
            if (oldDbFile.exists()) {
                Log.i("LOG_TAG", "Deleting old db " + oldDbFile.getPath());
                SQLiteDatabase.deleteDatabase(oldDbFile);
            }
        }
    }

    private int copyFromAssets() throws IOException {
        //Open your local db as the input stream
        InputStream assetsIn = context.getAssets().open(inDbAssetsPath);

        //Open the empty db as the output stream
        OutputStream databaseOut = new FileOutputStream(outDbFile);

        // transfer byte to input file to output file
        byte[] buffer = new byte[1024 * 128];
        int length, copiedSize = 0;
        while ((length = assetsIn.read(buffer)) > 0) {
            databaseOut.write(buffer, 0, length);
            copiedSize += length;
        }

        //Close the streams
        databaseOut.flush();
        databaseOut.close();
        assetsIn.close();
        return copiedSize;
    }

    private int copyFromURL(String urlStr) throws IOException {
        URL url = new URL(urlStr);
        ZipInputStream zis = new ZipInputStream(new BufferedInputStream(url.openStream()));
        OutputStream databaseOut = new FileOutputStream(outDbFile);
        int length, copiedSize = 0;
        byte[] buffer = new byte[8192];
        try {
            zis.getNextEntry(); // not sure if this is necessary
            while ((length = zis.read(buffer)) != -1) {
                databaseOut.write(buffer, 0, length);
                copiedSize += length;
            }
        } finally {
            zis.close();
            databaseOut.flush();
            databaseOut.close();
        }
        return copiedSize;
    }

    public synchronized void close() {
        if (db != null) {
            db.close();
        }
        super.close();
    }

    @Override
    public void onCreate(SQLiteDatabase sqLiteDatabase) {
    }

    @Override
    public void onUpgrade(SQLiteDatabase sqLiteDatabase, int i, int i1) {
        Log.i("LOG_TAG", "OnUpgrade called for " + this.vDbName + ", old version:" + i + ", new version:" + i1);
    }
    @Override
    public void onOpen(SQLiteDatabase db) {
        // needed for Android P (9) - otherwise the tables will not be accessible
        super.onOpen(db);
        db.disableWriteAheadLogging();
    }
}