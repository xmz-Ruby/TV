package com.github.tvbox.osc.utils;

import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.util.Log;

import com.android.cast.dlna.dmc.control.DeviceControl;
import com.android.cast.dlna.dmc.control.ServiceActionCallback;

import org.fourthline.cling.controlpoint.ControlPoint;
import org.fourthline.cling.model.action.ActionInvocation;
import org.fourthline.cling.model.message.UpnpResponse;
import org.fourthline.cling.model.meta.Service;
import org.fourthline.cling.support.avtransport.callback.SetAVTransportURI;

import java.lang.reflect.Field;
import java.util.Locale;

import kotlin.Unit;

public class CastDlna {

    private static final String TAG = "CastDlna";
    private static final Handler HANDLER = new Handler(Looper.getMainLooper());

    public static void setAVTransportURI(DeviceControl control, String url, String title, String format, ServiceActionCallback<Unit> callback) {
        try {
            Object avTransport = getField(control, "avTransportService");
            ControlPoint controlPoint = (ControlPoint) getField(avTransport, "controlPoint");
            Service<?, ?> service = (Service<?, ?>) getField(avTransport, "service");
            String metadata = createMetadata(url, title, getMimeType(format, url));
            controlPoint.execute(new SetAVTransportURI(service, url, metadata) {
                @Override
                public void success(ActionInvocation invocation) {
                    notifySuccess(callback);
                }

                @Override
                public void failure(ActionInvocation invocation, UpnpResponse operation, String defaultMsg) {
                    notifyFailure(callback, TextUtils.isEmpty(defaultMsg) ? "Error" : defaultMsg);
                }
            });
        } catch (Throwable e) {
            Log.w(TAG, "Use default setAVTransportURI fallback", e);
            control.setAVTransportURI(url, title, callback);
        }
    }

    private static Object getField(Object target, String name) throws Exception {
        Class<?> clazz = target.getClass();
        while (clazz != null) {
            try {
                Field field = clazz.getDeclaredField(name);
                field.setAccessible(true);
                return field.get(target);
            } catch (NoSuchFieldException ignored) {
                clazz = clazz.getSuperclass();
            }
        }
        throw new NoSuchFieldException(name);
    }

    private static String createMetadata(String url, String title, String mimeType) {
        String safeTitle = escapeXml(title);
        String safeUrl = escapeXml(url);
        String safeMimeType = escapeXml(mimeType);
        return "<?xml version=\"1.0\"?><DIDL-Lite xmlns=\"urn:schemas-upnp-org:metadata-1-0/DIDL-Lite/\" xmlns:dc=\"http://purl.org/dc/elements/1.1/\" xmlns:upnp=\"urn:schemas-upnp-org:metadata-1-0/upnp/\">" +
                "<item id=\"" + safeTitle + "\" parentID=\"-1\" restricted=\"1\">" +
                "<dc:title>" + safeTitle + "</dc:title>" +
                "<upnp:class>object.item.videoItem</upnp:class>" +
                "<res protocolInfo=\"http-get:*:" + safeMimeType + ":*;DLNA.ORG_OP=01;\">" + safeUrl + "</res>" +
                "</item></DIDL-Lite>";
    }

    private static String getMimeType(String format, String url) {
        String value = TextUtils.isEmpty(format) ? "" : format.toLowerCase(Locale.ROOT);
        String lowerUrl = TextUtils.isEmpty(url) ? "" : url.toLowerCase(Locale.ROOT);
        String source = value + " " + lowerUrl;
        if (source.contains("dash") || source.contains(".mpd")) return "application/dash+xml";
        if (source.contains("mpegurl") || source.contains("m3u8")) return "application/vnd.apple.mpegurl";
        if (source.contains("mp2t") || source.contains(".ts")) return "video/mp2t";
        if (source.contains("webm") || source.contains(".webm")) return "video/webm";
        if (source.contains("matroska") || source.contains(".mkv")) return "video/x-matroska";
        if (source.contains("mp4") || source.contains(".mp4")) return "video/mp4";
        return "video/mp4";
    }

    private static String escapeXml(String value) {
        if (value == null) return "";
        return value.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&apos;");
    }

    private static void notifySuccess(ServiceActionCallback<Unit> callback) {
        if (callback == null) return;
        HANDLER.post(() -> callback.onSuccess(Unit.INSTANCE));
    }

    private static void notifyFailure(ServiceActionCallback<Unit> callback, String message) {
        if (callback == null) return;
        HANDLER.post(() -> callback.onFailure(message));
    }
}
