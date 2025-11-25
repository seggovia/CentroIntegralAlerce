package com.centroalerce.ui;

import android.app.DownloadManager;
import android.app.ProgressDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.text.TextUtils;
import android.util.Log;
import android.webkit.MimeTypeMap;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.centroalerce.gestion.utils.CustomToast;

/**
 * Helper reutilizable para mostrar un ProgressDialog y toasts personalizados
 * durante la descarga de adjuntos utilizando DownloadManager.
 */
public class AttachmentDownloadHelper {

    private static final String TAG = "AttachmentDownload";

    private final Fragment fragment;
    private ProgressDialog progressDialog;
    private BroadcastReceiver downloadReceiver;
    private Context receiverContext;
    private Context fallbackContext;
    private long downloadRequestId = -1L;

    public AttachmentDownloadHelper(@NonNull Fragment fragment) {
        this.fragment = fragment;
    }

    public void startDownload(@Nullable String nombreArchivo, @NonNull String url) {
        if (!fragment.isAdded()) return;

        if (TextUtils.isEmpty(url)) {
            CustomToast.showError(fragment.getContext(), "URL no disponible");
            return;
        }

        Context context = fragment.requireContext();
        try {
            DownloadManager dm = (DownloadManager) context.getSystemService(Context.DOWNLOAD_SERVICE);
            if (dm == null) {
                CustomToast.showError(context, "Servicio de descargas no disponible");
                return;
            }

            DownloadManager.Request request = new DownloadManager.Request(Uri.parse(url));
            request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
            request.setAllowedOverMetered(true);
            request.allowScanningByMediaScanner();

            String sanitizedName = sanitizeFileName(TextUtils.isEmpty(nombreArchivo) ? nombreDesdeUrl(url) : nombreArchivo, url);
            String mimeType = guessMimeType(sanitizedName, url);
            if (!TextUtils.isEmpty(mimeType)) {
                request.setMimeType(mimeType);
            }
            request.setTitle(sanitizedName);
            request.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, sanitizedName);

            showProgressDialog();
            fallbackContext = context.getApplicationContext();
            registerReceiver(context.getApplicationContext());
            downloadRequestId = dm.enqueue(request);
            Log.d(TAG, "Descarga encolada id=" + downloadRequestId + " | archivo=" + nombreArchivo);
        } catch (Exception e) {
            dismissProgressDialog();
            showError("No se pudo iniciar la descarga");
            Log.e(TAG, "Error iniciando descarga", e);
        }
    }

    public void cleanup() {
        unregisterReceiver();
        dismissProgressDialog();
        fallbackContext = null;
    }

    private void showProgressDialog() {
        if (!fragment.isAdded()) return;

        if (progressDialog == null) {
            progressDialog = new ProgressDialog(fragment.requireContext());
            progressDialog.setIndeterminate(true);
            progressDialog.setCancelable(false);
        }
        progressDialog.setMessage("Descargando archivo...");
        if (!progressDialog.isShowing()) {
            progressDialog.show();
        }
    }

    private void dismissProgressDialog() {
        if (progressDialog != null && progressDialog.isShowing()) {
            try {
                progressDialog.dismiss();
            } catch (Exception ignored) {}
        }
    }

    private void registerReceiver(Context context) {
        if (downloadReceiver != null) return;

        receiverContext = context;
        downloadReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context ctx, Intent intent) {
                if (!DownloadManager.ACTION_DOWNLOAD_COMPLETE.equals(intent.getAction())) return;

                long id = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1L);
                if (downloadRequestId == -1L || id != downloadRequestId) return;

                handleDownloadCompletion(ctx, id);
            }
        };
        IntentFilter filter = new IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            receiverContext.registerReceiver(downloadReceiver, filter, Context.RECEIVER_EXPORTED);
        } else {
            receiverContext.registerReceiver(downloadReceiver, filter);
        }
    }

    private void unregisterReceiver() {
        if (downloadReceiver != null && receiverContext != null) {
            try {
                receiverContext.unregisterReceiver(downloadReceiver);
            } catch (Exception ignored) {}
        }
        downloadReceiver = null;
        receiverContext = null;
    }

    private void handleDownloadCompletion(Context context, long downloadId) {
        dismissProgressDialog();

        DownloadManager dm = (DownloadManager) context.getSystemService(Context.DOWNLOAD_SERVICE);
        if (dm == null) {
            showError("No se pudo verificar la descarga");
            downloadRequestId = -1L;
            return;
        }

        DownloadManager.Query query = new DownloadManager.Query().setFilterById(downloadId);
        android.database.Cursor cursor = null;
        try {
            cursor = dm.query(query);
            if (cursor != null && cursor.moveToFirst()) {
                int statusIdx = cursor.getColumnIndex(DownloadManager.COLUMN_STATUS);
                int status = cursor.getInt(statusIdx);
                if (status == DownloadManager.STATUS_SUCCESSFUL) {
                    showSuccess("Archivo descargado correctamente");
                } else {
                    showError("Error al descargar el archivo");
                }
            } else {
                showError("Descarga finalizada sin información");
            }
        } catch (Exception e) {
            Log.w(TAG, "No se pudo verificar estado de descarga", e);
            showError("No se pudo verificar la descarga");
        } finally {
            if (cursor != null) {
                cursor.close();
            }
            downloadRequestId = -1L;
        }
    }

    private String nombreDesdeUrl(String url) {
        if (TextUtils.isEmpty(url)) return "archivo";
        int q = url.indexOf('?');
        String clean = q >= 0 ? url.substring(0, q) : url;
        int idx = clean.lastIndexOf('/');
        return idx >= 0 ? clean.substring(idx + 1) : clean;
    }

    private String sanitizeFileName(@Nullable String rawName, @NonNull String url) {
        String candidate = TextUtils.isEmpty(rawName) ? nombreDesdeUrl(url) : rawName;
        if (TextUtils.isEmpty(candidate)) candidate = "archivo";
        candidate = candidate.replace("\\", "_").replace("/", "_");
        candidate = candidate.replaceAll("[:*?\"<>|]", "_");
        candidate = candidate.trim();
        if (candidate.isEmpty()) candidate = "archivo";

        String ext = obtenerExtension(candidate);
        if (TextUtils.isEmpty(ext)) {
            String fromUrl = MimeTypeMap.getFileExtensionFromUrl(url);
            if (!TextUtils.isEmpty(fromUrl)) {
                candidate += "." + fromUrl;
            } else {
                candidate += ".bin";
            }
        }
        return candidate;
    }

    private String guessMimeType(String fileName, String url) {
        String ext = obtenerExtension(fileName);
        if (TextUtils.isEmpty(ext)) {
            ext = MimeTypeMap.getFileExtensionFromUrl(url);
        }
        if (TextUtils.isEmpty(ext)) return "";
        String mime = MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext.toLowerCase());
        return mime != null ? mime : "";
    }

    private String obtenerExtension(String fileName) {
        if (TextUtils.isEmpty(fileName)) return "";
        int dot = fileName.lastIndexOf('.');
        if (dot < 0 || dot == fileName.length() - 1) return "";
        return fileName.substring(dot + 1);
    }

    private Context getToastContext() {
        if (fragment.getContext() != null) {
            return fragment.getContext();
        }
        if (receiverContext != null) {
            return receiverContext;
        }
        return fallbackContext;
    }

    private void showSuccess(String message) {
        Context ctx = getToastContext();
        if (ctx != null) {
            CustomToast.showSuccess(ctx, message);
        }
    }

    private void showError(String message) {
        Context ctx = getToastContext();
        if (ctx != null) {
            CustomToast.showError(ctx, message);
        }
    }
}
