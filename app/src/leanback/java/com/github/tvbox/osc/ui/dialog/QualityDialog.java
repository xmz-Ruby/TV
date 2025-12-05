package com.github.tvbox.osc.ui.dialog;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.viewbinding.ViewBinding;

import com.github.tvbox.osc.bean.Result;
import com.github.tvbox.osc.databinding.DialogQualityBinding;
import com.github.tvbox.osc.ui.adapter.QualityDialogAdapter;
import com.github.tvbox.osc.ui.custom.SpaceItemDecoration;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;

public final class QualityDialog extends BaseDialog implements QualityDialogAdapter.OnClickListener {

    private final QualityDialogAdapter adapter;
    private DialogQualityBinding binding;
    private Listener listener;
    private Result result;

    public static QualityDialog create() {
        return new QualityDialog();
    }

    public QualityDialog() {
        this.adapter = new QualityDialogAdapter(this);
    }

    public QualityDialog result(Result result) {
        this.result = result;
        return this;
    }

    public void show(FragmentActivity activity) {
        for (Fragment f : activity.getSupportFragmentManager().getFragments()) if (f instanceof BottomSheetDialogFragment) return;
        show(activity.getSupportFragmentManager(), null);
        this.listener = (Listener) activity;
    }

    @Override
    protected ViewBinding getBinding(@NonNull LayoutInflater inflater, @Nullable ViewGroup container) {
        return binding = DialogQualityBinding.inflate(inflater, container, false);
    }

    @Override
    protected void initView() {
        binding.recycler.setHasFixedSize(true);
        if (result != null) {
            adapter.addAll(result);
            binding.recycler.setAdapter(adapter);
            binding.recycler.addItemDecoration(new SpaceItemDecoration(1, 16));
            binding.recycler.post(() -> binding.recycler.scrollToPosition(result.getUrl().getPosition()));
        }
        binding.recycler.setVisibility(adapter.getItemCount() == 0 ? View.GONE : View.VISIBLE);
    }

    @Override
    protected void initEvent() {
    }

    @Override
    public void onItemClick(Result result) {
        if (listener != null) listener.onQualityClick(result);
        dismiss();
    }

    public interface Listener {
        void onQualityClick(Result result);
    }
}
