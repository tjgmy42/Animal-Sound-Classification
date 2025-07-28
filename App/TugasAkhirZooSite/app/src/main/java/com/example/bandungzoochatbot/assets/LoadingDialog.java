package com.example.bandungzoochatbot.assets;

import android.app.Activity;
import android.app.AlertDialog;
import android.view.LayoutInflater;

import com.example.bandungzoochatbot.R;

public class LoadingDialog {
    private Activity activity;
    private AlertDialog dialog;

    public LoadingDialog(Activity myActivity) {
        activity = myActivity;
    }

    public void startLoadingDialog() {
        try {
            // Cek apakah activity masih aktif
            if (activity != null && !activity.isFinishing() && !activity.isDestroyed()) {
                // Tutup dialog yang sudah ada sebelumnya (jika ada)
                if (dialog != null && dialog.isShowing()) {
                    dialog.dismiss();
                }

                AlertDialog.Builder builder = new AlertDialog.Builder(activity);
                LayoutInflater inflater = activity.getLayoutInflater();
                builder.setView(inflater.inflate(R.layout.loading_dialog, null));
                builder.setCancelable(false); // Ubah menjadi false agar tidak bisa dibatalkan dengan back button

                dialog = builder.create();
                dialog.show();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void dismissDialog() {
        try {
            if (dialog != null && dialog.isShowing()) {
                dialog.dismiss();
                dialog = null;
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // Method tambahan untuk cek status dialog
    public boolean isShowing() {
        return dialog != null && dialog.isShowing();
    }
}