package com.github.tvbox.osc.ui.adapter;

import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.github.tvbox.osc.databinding.AdapterAudioChannelBinding;

public class AudioChannelAdapter extends RecyclerView.Adapter<AudioChannelAdapter.ViewHolder> {

    private final OnClickListener mListener;
    private final int[] mItems;
    private int select;

    public AudioChannelAdapter(OnClickListener listener) {
        this.mItems = new int[]{2, 4, 6, 8, 16};
        this.mListener = listener;
    }

    public void setSelect(int select) {
        this.select = select;
    }

    public int getSelect() {
        return select;
    }

    public int getItemCount() {
        return mItems.length;
    }

    public int getItem(int position) {
        return mItems[position];
    }

    public interface OnClickListener {

        void onItemClick(int item);
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new ViewHolder(AdapterAudioChannelBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        int item = mItems[position];
        holder.binding.text.setText(String.valueOf(item));
        holder.binding.text.setActivated(select == position);
        holder.binding.text.setOnClickListener(v -> mListener.onItemClick(item));
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {

        private final AdapterAudioChannelBinding binding;

        public ViewHolder(@NonNull AdapterAudioChannelBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }
    }
}
