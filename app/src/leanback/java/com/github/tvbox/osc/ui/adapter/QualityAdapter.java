package com.github.tvbox.osc.ui.adapter;

import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.github.tvbox.osc.bean.Result;
import com.github.tvbox.osc.databinding.AdapterQualityBinding;

public class QualityAdapter extends RecyclerView.Adapter<QualityAdapter.ViewHolder> {

    private final OnClickListener mListener;
    private int nextFocusDown;
    private Result mResult;
    private int position;

    public QualityAdapter(OnClickListener listener) {
        this.mListener = listener;
        this.mResult = Result.empty();
    }

    public interface OnClickListener {

        void onItemClick(Result result);
    }

    public void setNextFocusDown(int nextFocusDown) {
        this.nextFocusDown = nextFocusDown;
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

    public void addAll(Result result) {
        mResult = result;
        notifyDataSetChanged();
    }

    @Override
    public int getItemCount() {
        return mResult.getUrl().getValues().isEmpty() ? 0 : 1;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new ViewHolder(AdapterQualityBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        holder.binding.text.setNextFocusDownId(nextFocusDown);
        int currentPosition = mResult.getUrl().getPosition();
        String qualityText = mResult.getUrl().n(currentPosition) + " - 点击切换画质";
        holder.binding.text.setText(qualityText);
        holder.binding.text.setOnClickListener(v -> onItemClick());
        holder.binding.text.setActivated(true);
    }

    private void onItemClick() {
        mListener.onItemClick(mResult);
    }

    static class ViewHolder extends RecyclerView.ViewHolder {

        private final AdapterQualityBinding binding;

        ViewHolder(@NonNull AdapterQualityBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }
    }
}
