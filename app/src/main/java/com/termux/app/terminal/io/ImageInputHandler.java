package com.termux.app.terminal.io;

import android.app.Activity;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Environment;
import android.provider.MediaStore;
import android.provider.OpenableColumns;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.FileProvider;

import com.termux.shared.logger.Logger;
import com.termux.terminal.TerminalSession;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class ImageInputHandler {

    public static final int REQUEST_IMAGE_PICK = 101;
    public static final int REQUEST_IMAGE_CAPTURE = 102;
    public static final int REQUEST_IMAGE_FILE = 103;

    private static final String LOG_TAG = "ImageInputHandler";
    private static final String IMAGE_DIR = ".termux/images";

    private final Activity mActivity;
    private TerminalSession mPendingSession;
    private Uri mCameraTempUri;
    private File mCameraTempFile;

    public ImageInputHandler(@NonNull Activity activity) {
        mActivity = activity;
    }

    /** Launch gallery image picker. */
    public void pickImage(@NonNull TerminalSession session) {
        mPendingSession = session;
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.setType("image/*");
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, false);
        mActivity.startActivityForResult(intent, REQUEST_IMAGE_PICK);
    }

    /** Launch camera capture. */
    public void captureImage(@NonNull TerminalSession session) {
        mPendingSession = session;

        String fileName = "IMG_" + new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date()) + ".jpg";
        File cacheDir = new File(mActivity.getCacheDir(), "camera_images");
        if (!cacheDir.exists()) cacheDir.mkdirs();
        mCameraTempFile = new File(cacheDir, fileName);
        mCameraTempUri = FileProvider.getUriForFile(mActivity,
            mActivity.getPackageName() + ".fileprovider", mCameraTempFile);

        Intent intent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        intent.putExtra(MediaStore.EXTRA_OUTPUT, mCameraTempUri);
        intent.addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            | Intent.FLAG_GRANT_READ_URI_PERMISSION);
        if (intent.resolveActivity(mActivity.getPackageManager()) != null) {
            mActivity.startActivityForResult(intent, REQUEST_IMAGE_CAPTURE);
        } else {
            Logger.showToast(mActivity, "No camera app available", true);
        }
    }

    /** Launch generic file picker. */
    public void pickFile(@NonNull TerminalSession session) {
        mPendingSession = session;
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.setType("*/*");
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, false);
        mActivity.startActivityForResult(intent, REQUEST_IMAGE_FILE);
    }

    /** Handle activity result. Returns the path injected, or null if failed. */
    @Nullable
    public String handleActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        if (resultCode != Activity.RESULT_OK) {
            cleanup();
            return null;
        }

        String path = null;

        if (requestCode == REQUEST_IMAGE_CAPTURE) {
            // Camera wrote directly to mCameraTempFile via EXTRA_OUTPUT
            path = copyFileToImageDir(mCameraTempFile);
        } else if ((requestCode == REQUEST_IMAGE_PICK || requestCode == REQUEST_IMAGE_FILE) && data != null) {
            Uri uri = data.getData();
            if (uri != null) {
                path = copyUriToImageDir(uri);
            }
        }

        if (path != null && mPendingSession != null && mPendingSession.isRunning()) {
            mPendingSession.write(path);
        }

        cleanup();
        return path;
    }

    /** Copy a content:// URI to the images directory. */
    @Nullable
    private String copyUriToImageDir(Uri uri) {
        try {
            File imageDir = getImageDir();
            if (imageDir == null) return null;

            String originalName = getFileName(uri);
            if (originalName == null) {
                originalName = "file_" + System.currentTimeMillis();
            }

            File dest = uniqueFile(imageDir, originalName);

            try (InputStream in = mActivity.getContentResolver().openInputStream(uri);
                 FileOutputStream out = new FileOutputStream(dest)) {
                if (in == null) return null;
                byte[] buf = new byte[8192];
                int len;
                while ((len = in.read(buf)) > 0) {
                    out.write(buf, 0, len);
                }
            }

            return dest.getAbsolutePath();
        } catch (Exception e) {
            Logger.logStackTraceWithMessage(LOG_TAG, "Failed to copy from URI", e);
            Logger.showToast(mActivity, "Failed to save file: " + e.getMessage(), true);
            return null;
        }
    }

    /** Copy a File (e.g. from camera cache) to the images directory. */
    @Nullable
    private String copyFileToImageDir(File source) {
        if (source == null || !source.exists()) return null;
        try {
            File imageDir = getImageDir();
            if (imageDir == null) return null;

            File dest = uniqueFile(imageDir, source.getName());

            try (FileInputStream in = new FileInputStream(source);
                 FileOutputStream out = new FileOutputStream(dest)) {
                byte[] buf = new byte[8192];
                int len;
                while ((len = in.read(buf)) > 0) {
                    out.write(buf, 0, len);
                }
            }

            // Delete the temp cache file
            source.delete();

            return dest.getAbsolutePath();
        } catch (Exception e) {
            Logger.logStackTraceWithMessage(LOG_TAG, "Failed to copy camera file", e);
            return null;
        }
    }

    @Nullable
    private String getFileName(Uri uri) {
        String name = null;
        try (Cursor cursor = mActivity.getContentResolver().query(uri,
                new String[]{OpenableColumns.DISPLAY_NAME}, null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) {
                int idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                if (idx >= 0) name = cursor.getString(idx);
            }
        } catch (Exception ignored) {}
        if (name == null) {
            name = uri.getLastPathSegment();
        }
        return name;
    }

    @Nullable
    private File getImageDir() {
        File termuxHome = new File(Environment.getDataDirectory(), "data/com.termux/files/home");
        if (!termuxHome.exists()) {
            File dir = new File(mActivity.getFilesDir(), "images");
            if (!dir.exists() && !dir.mkdirs()) return null;
            return dir;
        }
        File imageDir = new File(termuxHome, IMAGE_DIR);
        if (!imageDir.exists() && !imageDir.mkdirs()) return null;
        return imageDir;
    }

    private static File uniqueFile(File dir, String name) {
        File dest = new File(dir, name);
        if (!dest.exists()) return dest;
        String base = name.replaceFirst("(\\.[^.]+)$", "");
        String ext = name.contains(".") ? name.substring(name.lastIndexOf('.')) : "";
        return new File(dir, base + "_" + System.currentTimeMillis() + ext);
    }

    private void cleanup() {
        mPendingSession = null;
        mCameraTempUri = null;
        mCameraTempFile = null;
    }
}
