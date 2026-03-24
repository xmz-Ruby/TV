package com.github.tvbox.osc.ui.dialog;

import android.app.Activity;
import android.view.LayoutInflater;

import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;

import com.github.tvbox.osc.databinding.DialogTransmitActionBinding;
import com.github.tvbox.osc.utils.FileChooser;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

public class TransmitActionDialog {

    private final Fragment fragment;
    private DialogTransmitActionBinding binding;
    private AlertDialog dialog;


    public static TransmitActionDialog create(Fragment fragment) {
        return new TransmitActionDialog(fragment);
    }

    public TransmitActionDialog(Fragment fragment) {
        this.fragment = fragment;
        init(fragment.getActivity());
    }

    private void init(Activity activity) {
        this.binding = DialogTransmitActionBinding.inflate(LayoutInflater.from(activity));
        this.dialog = new MaterialAlertDialogBuilder(activity).setView(binding.getRoot()).create();
        initEvent();
    }

    private void initEvent() {
        this.binding.apk.setOnClickListener(v-> pushApk());
        this.binding.vodConfig.setOnClickListener(v-> pushVodConfig());
    }

    public void show() {
        setDialog();
    }

    private void setDialog() {
        dialog.getWindow().setDimAmount(0);
        dialog.show();
    }

    private void pushApk() {
        FileChooser.from(fragment).type(FileChooser.TYPE_APK).show();
        dialog.dismiss();
    }

    private void pushVodConfig() {
        TransmitDialog.create().vodConfig().show(fragment);
        dialog.dismiss();
    }

}
