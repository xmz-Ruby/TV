package com.github.tvbox.osc.ui.dialog;

import android.view.LayoutInflater;

import androidx.fragment.app.FragmentActivity;

import com.github.tvbox.osc.bean.Result;
import com.github.tvbox.osc.databinding.DialogQualityListBinding;
import com.github.tvbox.osc.ui.adapter.QualityDialogAdapter;
import com.google.android.material.bottomsheet.BottomSheetDialog;

public class QualityListDialog {

    private final FragmentActivity activity;
    private DialogQualityListBinding binding;
    private Result result;
    private QualityDialogAdapter adapter;
    private BottomSheetDialog dialog;
    private OnClickListener onClickListener;

    public static QualityListDialog create(FragmentActivity activity) {
        return new QualityListDialog(activity);
    }

    public interface OnClickListener {
        void onItemClick(Result result);
    }

    public QualityListDialog(FragmentActivity activity) {
        this.activity = activity;
    }

    public QualityListDialog result(Result result) {
        this.result = result;
        return this;
    }

    public QualityListDialog setOnClickListener(OnClickListener listener) {
        this.onClickListener = listener;
        return this;
    }

    public BottomSheetDialog show() {
        initDialog();
        initView();
        return dialog;
    }

    private void initDialog() {
        binding = DialogQualityListBinding.inflate(LayoutInflater.from(activity));
        dialog = new BottomSheetDialog(activity);
        dialog.setContentView(binding.getRoot());
        dialog.show();
    }

    private void initView() {
        setRecyclerView();
        setQuality();
    }

    private void setRecyclerView() {
        binding.recycler.setHasFixedSize(true);
        binding.recycler.setItemAnimator(null);
        binding.recycler.setAdapter(adapter = new QualityDialogAdapter(new QualityDialogAdapter.OnClickListener() {
            @Override
            public void onItemClick(Result result) {
                adapter.setPosition(result.getUrl().getPosition());
                adapter.notifyDataSetChanged();
                if (onClickListener != null) {
                    onClickListener.onItemClick(result);
                }
                dialog.dismiss();
            }
        }));
        binding.recycler.addItemDecoration(new com.github.tvbox.osc.ui.custom.SpaceItemDecoration(1, 16));
    }

    private void setQuality() {
        if (result != null) {
            adapter.addAll(result);
            binding.recycler.scrollToPosition(result.getUrl().getPosition());
        }
    }
}