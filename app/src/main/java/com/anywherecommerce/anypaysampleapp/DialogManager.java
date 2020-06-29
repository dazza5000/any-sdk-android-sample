package com.anywherecommerce.anypaysampleapp;

import android.app.AlertDialog;
import android.content.Context;
import android.view.LayoutInflater;

import java.util.zip.Inflater;

/**
 * Created by ankit on 28/12/17.
 */

public class DialogManager {

    private AlertDialog progressDialog;

    public void showDialog(Context context, String title, String message, AlertDialog.OnDismissListener dismissListener) {
        AlertDialog success = new AlertDialog.Builder(context).create();
        success.setTitle(title);
        success.setMessage(message);
        success.setOnDismissListener(dismissListener);
        success.show();
    }

    public void showProgressDialog(Context context, String message) {
        progressDialog = new AlertDialog.Builder(context, R.style.alert_dialog).create();
        progressDialog.setTitle(message);
        progressDialog.show();
    }

    public void hideProgressDialog() {
        if (progressDialog != null)
            progressDialog.dismiss();
    }

}
