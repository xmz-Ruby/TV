package com.github.tvbox.osc.ui.adapter;

import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.github.tvbox.osc.bean.Result;
import com.github.tvbox.osc.databinding.AdapterQualityDialogBinding;

public class QualityDialogAdapter extends RecyclerView.Adapter<QualityDialogAdapter.ViewHolder> {

    private final OnClickListener mListener;
    private Result mResult;
    private int position;

    public QualityDialogAdapter(OnClickListener listener) {
        this.mListener = listener;
        this.mResult = Result.empty();
    }

    public interface OnClickListener {

        void onItemClick(Result result);
    }

    public void addAll(Result result) {
        mResult = result;
        notifyDataSetChanged();
    }

    public int getPosition() {
        return position;
    }

    public void setPosition(int position) {
        this.position = position;
    }

    public Result getResult() {
        return mResult;
    }

    @Override
    public int getItemCount() {
        return mResult.getUrl().getValues().size();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new ViewHolder(AdapterQualityDialogBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        holder.binding.text.setText(mResult.getUrl().n(position));
        holder.binding.text.setOnClickListener(v -> onItemClick(position));
        holder.binding.text.setActivated(mResult.getUrl().getPosition() == position);
    }

    private void onItemClick(int position) {
        this.position = position;
        mResult.getUrl().set(position);
        mListener.onItemClick(mResult);
        notifyItemRangeChanged(0, getItemCount());
    }

    static class ViewHolder extends RecyclerView.ViewHolder {

        private final AdapterQualityDialogBinding binding;

        ViewHolder(@NonNull AdapterQualityDialogBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }
    }
}
