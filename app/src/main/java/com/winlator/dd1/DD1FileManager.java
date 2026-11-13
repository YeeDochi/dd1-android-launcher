package com.winlator.dd1;

import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Environment;
import android.provider.DocumentsContract;
import android.widget.Toast;

import com.winlator.R;

import java.io.File;

// Opens a folder in phone storage in the phone's own file manager. There is no
// standard way to do that: Samsung's My Files takes a start path, and elsewhere
// the system Files app opens a document URI for the directory. Failing both,
// the path is shown so it can be found by hand.
public final class DD1FileManager {
    private DD1FileManager() {}

    public static void open(Context context, File dir) {
        String path = dir.getPath();
        try {
            context.startActivity(new Intent("samsung.myfiles.intent.action.LAUNCH_MY_FILES")
                .setPackage("com.sec.android.app.myfiles")
                .putExtra("samsung.myfiles.intent.extra.START_PATH", path)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            return;
        }
        catch (ActivityNotFoundException notSamsung) {}
        String storage = Environment.getExternalStorageDirectory().getPath();
        String inside = path.startsWith(storage + "/") ? path.substring(storage.length() + 1) : path;
        Uri uri = DocumentsContract.buildDocumentUri(
            "com.android.externalstorage.documents", "primary:" + inside);
        try {
            context.startActivity(new Intent(Intent.ACTION_VIEW)
                .setDataAndType(uri, DocumentsContract.Document.MIME_TYPE_DIR)
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_ACTIVITY_NEW_TASK));
        }
        catch (ActivityNotFoundException noFiles) {
            Toast.makeText(context, context.getString(R.string.dd1_game_files_find, path),
                Toast.LENGTH_LONG).show();
        }
    }
}
