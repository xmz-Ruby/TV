package com.github.tvbox.osc.player.exo;

import android.content.Context;
import android.util.AttributeSet;

import androidx.annotation.Nullable;
import androidx.media3.common.Player;
import androidx.media3.common.VideoSize;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.ui.AspectRatioFrameLayout;
import androidx.media3.ui.PlayerView;

/**
 * PlayerView 子类：让缩放档位 "16:9"/"4:3" 真正强制显示比例，与 IJK 行为一致。
 *
 * <p>背景：EXO 的画面形状完全由 {@code videoAspectRatio = width * pixelWidthHeightRatio / height}
 * 决定（见 PlayerView.updateAspectRatio）。原生 PlayerView 的 "16:9"/"4:3" 档位实为
 * FIXED_WIDTH/FIXED_HEIGHT，仍依赖解码器上报的比例，并不强制比例。本类在
 * {@link #onContentAspectRatioChanged} 处把 "16:9"/"4:3" 档位直接覆盖为 16f/9f、4f/3f，
 * 作为遇到比例异常视频时的手动修正手段；Default/Fill/Zoom 行为与原生完全一致。
 */
@UnstableApi
public class CustomExoView extends PlayerView {

    /** select_scale 下标：Default(0) / 16:9(1) / 4:3(2) / Fill(3) / Zoom(4) */
    private int scale;
    @Nullable
    private AspectRatioFrameLayout frame;

    public CustomExoView(Context context) {
        super(context);
    }

    public CustomExoView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
    }

    public CustomExoView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    /** 替代直接调用 setResizeMode：先按档位设置基础 resize mode，再立即重算一次让强制比例生效。 */
    public void setScale(int scale) {
        this.scale = scale;
        setResizeMode(toResizeMode(scale));
        reapplyAspectRatio();
    }

    private int toResizeMode(int scale) {
        switch (scale) {
            case 3:
                return AspectRatioFrameLayout.RESIZE_MODE_FILL;
            case 4:
                return AspectRatioFrameLayout.RESIZE_MODE_ZOOM;
            // Default(0)/16:9(1)/4:3(2) 均以 FIT 摆放，16:9/4:3 的强制比例由 adjust() 提供
            default:
                return AspectRatioFrameLayout.RESIZE_MODE_FIT;
        }
    }

    @Override
    protected void onContentAspectRatioChanged(@Nullable AspectRatioFrameLayout contentFrame, float aspectRatio) {
        this.frame = contentFrame;
        super.onContentAspectRatioChanged(contentFrame, adjust(aspectRatio));
    }

    /** 仅在存在真实视频尺寸时覆盖比例，避免影响封面/图片（artwork/image 也会复用该回调）。 */
    private float adjust(float aspectRatio) {
        Player player = getPlayer();
        if (player == null) return aspectRatio;
        VideoSize size = player.getVideoSize();
        if (size.width == 0 || size.height == 0) return aspectRatio;
        switch (scale) {
            case 1:
                return 16f / 9f;
            case 2:
                return 4f / 3f;
            // Default/Fill/Zoom 保持解码器上报的比例，与原生 PlayerView 一致
            default:
                return aspectRatio;
        }
    }

    /** 手动切档时立即按当前视频尺寸复算一次，无需等待下一次 onVideoSizeChanged。 */
    private void reapplyAspectRatio() {
        if (frame == null) return;
        Player player = getPlayer();
        if (player == null) return;
        VideoSize size = player.getVideoSize();
        float aspectRatio = (size.width == 0 || size.height == 0) ? 0 : (size.width * size.pixelWidthHeightRatio) / size.height;
        onContentAspectRatioChanged(frame, aspectRatio);
    }
}
