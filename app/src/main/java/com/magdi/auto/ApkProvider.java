package com.magdi.auto;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.provider.OpenableColumns;

import java.io.File;
import java.io.FileNotFoundException;

public final class ApkProvider extends ContentProvider {
    static final String MIME_TYPE = "application/vnd.android.package-archive";

    static Uri uri(String packageName, Releases.App app) {
        return new Uri.Builder().scheme("content").authority(packageName + ".downloads")
                .appendPath(app.packageName + ".apk").build();
    }

    @Override public boolean onCreate() { return true; }

    @Override public String getType(Uri uri) {
        return apk(uri) == null ? null : MIME_TYPE;
    }

    @Override public ParcelFileDescriptor openFile(Uri uri, String mode) throws FileNotFoundException {
        File file = apk(uri);
        if (!"r".equals(mode) || file == null) throw new FileNotFoundException();
        return ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY);
    }

    @Override public Cursor query(Uri uri, String[] projection, String selection,
                                  String[] selectionArgs, String sortOrder) {
        File file = apk(uri);
        if (file == null || !file.isFile()) return null;
        String[] columns = projection == null
                ? new String[] {OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE} : projection;
        MatrixCursor cursor = new MatrixCursor(columns, 1);
        Object[] values = new Object[columns.length];
        for (int i = 0; i < columns.length; i++) {
            if (OpenableColumns.DISPLAY_NAME.equals(columns[i])) values[i] = file.getName();
            if (OpenableColumns.SIZE.equals(columns[i])) values[i] = file.length();
        }
        cursor.addRow(values);
        return cursor;
    }

    private File apk(Uri uri) {
        if (uri.getPathSegments().size() != 1) return null;
        String filename = uri.getLastPathSegment();
        for (Releases.App app : Releases.App.values()) {
            if ((app.packageName + ".apk").equals(filename)) {
                return new File(new File(getContext().getCacheDir(), "releases"), filename);
            }
        }
        return null;
    }

    @Override public Uri insert(Uri uri, ContentValues values) { throw new UnsupportedOperationException(); }
    @Override public int delete(Uri uri, String selection, String[] args) { throw new UnsupportedOperationException(); }
    @Override public int update(Uri uri, ContentValues values, String selection, String[] args) {
        throw new UnsupportedOperationException();
    }
}
