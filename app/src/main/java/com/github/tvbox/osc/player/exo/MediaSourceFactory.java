package com.github.tvbox.osc.player.exo;

import android.net.Uri;

import androidx.annotation.NonNull;
import androidx.media3.common.C;
import androidx.media3.common.MediaItem;
import androidx.media3.common.MimeTypes;
import androidx.media3.datasource.DataSource;
import androidx.media3.datasource.DefaultDataSource;
import androidx.media3.datasource.HttpDataSource;
import androidx.media3.datasource.cache.Cache;
import androidx.media3.datasource.cache.CacheDataSink;
import androidx.media3.datasource.cache.CacheDataSource;
import androidx.media3.datasource.okhttp.OkHttpDataSource;
import androidx.media3.exoplayer.drm.DrmSessionManagerProvider;
import androidx.media3.exoplayer.source.ConcatenatingMediaSource2;
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory;
import androidx.media3.exoplayer.source.MediaSource;
import androidx.media3.exoplayer.upstream.LoadErrorHandlingPolicy;
import androidx.media3.extractor.DefaultExtractorsFactory;
import androidx.media3.extractor.ExtractorsFactory;
import androidx.media3.extractor.ts.DefaultTsPayloadReaderFactory;
import androidx.media3.extractor.ts.TsExtractor;

import com.github.tvbox.osc.App;
import com.github.catvod.net.OkHttp;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public class MediaSourceFactory implements MediaSource.Factory {

    private final DefaultMediaSourceFactory cacheMediaSourceFactory;
    private final DefaultMediaSourceFactory directMediaSourceFactory;
    private HttpDataSource.Factory httpDataSourceFactory;
    private DataSource.Factory cacheDataSourceFactory;
    private DataSource.Factory directDataSourceFactory;
    private ExtractorsFactory extractorsFactory;

    public MediaSourceFactory() {
        cacheMediaSourceFactory = new DefaultMediaSourceFactory(getCacheDataSourceFactory(), getExtractorsFactory());
        directMediaSourceFactory = new DefaultMediaSourceFactory(getDirectDataSourceFactory(), getExtractorsFactory());
    }

    @NonNull
    @Override
    public MediaSource.Factory setDrmSessionManagerProvider(@NonNull DrmSessionManagerProvider drmSessionManagerProvider) {
        cacheMediaSourceFactory.setDrmSessionManagerProvider(drmSessionManagerProvider);
        directMediaSourceFactory.setDrmSessionManagerProvider(drmSessionManagerProvider);
        return this;
    }

    @NonNull
    @Override
    public MediaSource.Factory setLoadErrorHandlingPolicy(@NonNull LoadErrorHandlingPolicy loadErrorHandlingPolicy) {
        cacheMediaSourceFactory.setLoadErrorHandlingPolicy(loadErrorHandlingPolicy);
        directMediaSourceFactory.setLoadErrorHandlingPolicy(loadErrorHandlingPolicy);
        return this;
    }

    @NonNull
    @Override
    public @C.ContentType int[] getSupportedTypes() {
        return cacheMediaSourceFactory.getSupportedTypes();
    }

    @NonNull
    @Override
    public MediaSource createMediaSource(@NonNull MediaItem mediaItem) {
        mediaItem = setHeader(mediaItem);
        DefaultMediaSourceFactory mediaSourceFactory = shouldBypassCache(mediaItem) ? directMediaSourceFactory : cacheMediaSourceFactory;
        if (mediaItem.mediaId.contains("***") && mediaItem.mediaId.contains("|||")) {
            return createConcatenatingMediaSource(mediaSourceFactory, mediaItem);
        } else {
            return mediaSourceFactory.createMediaSource(mediaItem);
        }
    }

    private MediaItem setHeader(MediaItem mediaItem) {
        Map<String, String> headers = new HashMap<>();
        for (String key : mediaItem.requestMetadata.extras.keySet()) headers.put(key, mediaItem.requestMetadata.extras.get(key).toString());
        getHttpDataSourceFactory().setDefaultRequestProperties(headers);
        return mediaItem;
    }

    private MediaSource createConcatenatingMediaSource(DefaultMediaSourceFactory mediaSourceFactory, MediaItem mediaItem) {
        ConcatenatingMediaSource2.Builder builder = new ConcatenatingMediaSource2.Builder();
        for (String split : mediaItem.mediaId.split("\\*\\*\\*")) {
            String[] info = split.split("\\|\\|\\|");
            if (info.length >= 2) builder.add(mediaSourceFactory.createMediaSource(mediaItem.buildUpon().setUri(Uri.parse(info[0])).build()), Long.parseLong(info[1]));
        }
        return builder.build();
    }

    private ExtractorsFactory getExtractorsFactory() {
        if (extractorsFactory == null) extractorsFactory = new DefaultExtractorsFactory().setTsExtractorFlags(DefaultTsPayloadReaderFactory.FLAG_ENABLE_HDMV_DTS_AUDIO_STREAMS).setTsExtractorTimestampSearchBytes(TsExtractor.DEFAULT_TIMESTAMP_SEARCH_BYTES * 3);
        return extractorsFactory;
    }

    private DataSource.Factory getCacheDataSourceFactory() {
        if (cacheDataSourceFactory == null) cacheDataSourceFactory = buildCacheDataSource(getDirectDataSourceFactory());
        return cacheDataSourceFactory;
    }

    private DataSource.Factory getDirectDataSourceFactory() {
        if (directDataSourceFactory == null) directDataSourceFactory = new DefaultDataSource.Factory(App.get(), getHttpDataSourceFactory());
        return directDataSourceFactory;
    }

    private CacheDataSource.Factory buildCacheDataSource(DataSource.Factory upstreamFactory) {
        Cache cache = CacheManager.get().getCache();
        return new CacheDataSource.Factory()
                .setCache(cache)
                .setUpstreamDataSourceFactory(upstreamFactory)
                .setCacheWriteDataSinkFactory(new CacheDataSink.Factory().setCache(cache).setFragmentSize(CacheDataSink.DEFAULT_FRAGMENT_SIZE))
                .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR);
    }

    private HttpDataSource.Factory getHttpDataSourceFactory() {
        if (httpDataSourceFactory == null) httpDataSourceFactory = new OkHttpDataSource.Factory(OkHttp.client());
        return httpDataSourceFactory;
    }

    private boolean shouldBypassCache(MediaItem mediaItem) {
        if (MimeTypes.APPLICATION_M3U8.equals(getMimeType(mediaItem)) || isM3u8Uri(mediaItem) || isEmbyDirectStreamUri(mediaItem)) return true;
        Uri uri = mediaItem.localConfiguration != null ? mediaItem.localConfiguration.uri : null;
        if (uri == null) uri = mediaItem.requestMetadata.mediaUri;
        if (ExoUtil.isArmeabiV7aOnly()) {
            if (uri != null) {
                String scheme = uri.getScheme();
                if ("http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme)) return true;
            }
        }
        return false;
    }

    private String getMimeType(MediaItem mediaItem) {
        return mediaItem.localConfiguration == null ? null : mediaItem.localConfiguration.mimeType;
    }

    private boolean isM3u8Uri(MediaItem mediaItem) {
        Uri uri = mediaItem.requestMetadata.mediaUri;
        String raw = uri == null ? mediaItem.mediaId : uri.toString();
        if (raw == null || raw.isEmpty()) return false;
        String decoded = Uri.decode(raw).toLowerCase(Locale.US);
        return decoded.contains(".m3u8");
    }

    private boolean isEmbyDirectStreamUri(MediaItem mediaItem) {
        Uri uri = mediaItem.requestMetadata.mediaUri;
        String raw = uri == null ? mediaItem.mediaId : uri.toString();
        if (raw == null || raw.isEmpty()) return false;
        String decoded = Uri.decode(raw).toLowerCase(Locale.US);
        if (!decoded.contains("/emby/")) return false;
        return decoded.contains("/videos/") || decoded.contains("/audio/") || decoded.contains("mediasourceid=");
    }
}
