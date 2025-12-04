package com.github.tvbox.osc.player.custom;

import android.content.res.Resources;
import android.text.TextUtils;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.media3.common.C;
import androidx.media3.common.Format;
import androidx.media3.common.MimeTypes;
import androidx.media3.common.util.Util;
import androidx.media3.ui.R;

import com.github.tvbox.osc.App;

import java.util.Locale;

import tv.danmaku.ijk.media.player.misc.ITrackInfo;

public class TrackNameProvider {

    private final Resources resources;

    public TrackNameProvider() {
        this.resources = App.get().getResources();
    }

    public String getTrackName(@NonNull Format format) {
        String trackName;
        int trackType = inferPrimaryTrackType(format);
        if (trackType == C.TRACK_TYPE_VIDEO) {
            trackName = joinWithSeparator(buildRoleString(format), buildResolutionString(format), buildBitrateString(format));
        } else if (trackType == C.TRACK_TYPE_AUDIO) {
            // 对于音轨,优先使用 label
            String label = buildLabelString(format);

            // 打印音频轨道详细信息 - Format 对象完整转储
            android.util.Log.d("TrackNameProvider", "========== Format 对象完整信息 ==========");
            android.util.Log.d("TrackNameProvider", "Format.toString(): " + format.toString());
            android.util.Log.d("TrackNameProvider", "");

            android.util.Log.d("TrackNameProvider", "【基本标识】");
            android.util.Log.d("TrackNameProvider", "  id: " + format.id);
            android.util.Log.d("TrackNameProvider", "  label: " + format.label);
            android.util.Log.d("TrackNameProvider", "  language: " + format.language);
            android.util.Log.d("TrackNameProvider", "  containerMimeType: " + format.containerMimeType);
            android.util.Log.d("TrackNameProvider", "  sampleMimeType: " + format.sampleMimeType);
            android.util.Log.d("TrackNameProvider", "  codecs: " + format.codecs);

            android.util.Log.d("TrackNameProvider", "【比特率信息】");
            android.util.Log.d("TrackNameProvider", "  bitrate: " + format.bitrate + (format.bitrate > 0 ? " (" + (format.bitrate / 1000) + " kbps)" : ""));
            android.util.Log.d("TrackNameProvider", "  averageBitrate: " + format.averageBitrate + (format.averageBitrate > 0 ? " (" + (format.averageBitrate / 1000) + " kbps)" : ""));
            android.util.Log.d("TrackNameProvider", "  peakBitrate: " + format.peakBitrate + (format.peakBitrate > 0 ? " (" + (format.peakBitrate / 1000) + " kbps)" : ""));

            android.util.Log.d("TrackNameProvider", "【音频参数】");
            android.util.Log.d("TrackNameProvider", "  channelCount: " + format.channelCount);
            android.util.Log.d("TrackNameProvider", "  sampleRate: " + format.sampleRate + (format.sampleRate > 0 ? " Hz" : ""));
            android.util.Log.d("TrackNameProvider", "  pcmEncoding: " + format.pcmEncoding);
            android.util.Log.d("TrackNameProvider", "  encoderDelay: " + format.encoderDelay);
            android.util.Log.d("TrackNameProvider", "  encoderPadding: " + format.encoderPadding);

            android.util.Log.d("TrackNameProvider", "【视频参数】");
            android.util.Log.d("TrackNameProvider", "  width: " + format.width);
            android.util.Log.d("TrackNameProvider", "  height: " + format.height);
            android.util.Log.d("TrackNameProvider", "  frameRate: " + format.frameRate);
            android.util.Log.d("TrackNameProvider", "  rotationDegrees: " + format.rotationDegrees);
            android.util.Log.d("TrackNameProvider", "  pixelWidthHeightRatio: " + format.pixelWidthHeightRatio);

            android.util.Log.d("TrackNameProvider", "【颜色信息】");
            android.util.Log.d("TrackNameProvider", "  colorInfo: " + format.colorInfo);

            android.util.Log.d("TrackNameProvider", "【标志位】");
            android.util.Log.d("TrackNameProvider", "  selectionFlags: " + format.selectionFlags);
            if ((format.selectionFlags & androidx.media3.common.C.SELECTION_FLAG_DEFAULT) != 0)
                android.util.Log.d("TrackNameProvider", "    - DEFAULT (默认)");
            if ((format.selectionFlags & androidx.media3.common.C.SELECTION_FLAG_FORCED) != 0)
                android.util.Log.d("TrackNameProvider", "    - FORCED (强制)");
            if ((format.selectionFlags & androidx.media3.common.C.SELECTION_FLAG_AUTOSELECT) != 0)
                android.util.Log.d("TrackNameProvider", "    - AUTOSELECT (自动选择)");

            android.util.Log.d("TrackNameProvider", "  roleFlags: " + format.roleFlags);
            if ((format.roleFlags & androidx.media3.common.C.ROLE_FLAG_MAIN) != 0)
                android.util.Log.d("TrackNameProvider", "    - MAIN (主音轨)");
            if ((format.roleFlags & androidx.media3.common.C.ROLE_FLAG_ALTERNATE) != 0)
                android.util.Log.d("TrackNameProvider", "    - ALTERNATE (备用)");
            if ((format.roleFlags & androidx.media3.common.C.ROLE_FLAG_SUPPLEMENTARY) != 0)
                android.util.Log.d("TrackNameProvider", "    - SUPPLEMENTARY (补充)");
            if ((format.roleFlags & androidx.media3.common.C.ROLE_FLAG_COMMENTARY) != 0)
                android.util.Log.d("TrackNameProvider", "    - COMMENTARY (评论)");
            if ((format.roleFlags & androidx.media3.common.C.ROLE_FLAG_DUB) != 0)
                android.util.Log.d("TrackNameProvider", "    - DUB (配音)");
            if ((format.roleFlags & androidx.media3.common.C.ROLE_FLAG_EMERGENCY) != 0)
                android.util.Log.d("TrackNameProvider", "    - EMERGENCY (紧急)");
            if ((format.roleFlags & androidx.media3.common.C.ROLE_FLAG_CAPTION) != 0)
                android.util.Log.d("TrackNameProvider", "    - CAPTION (字幕)");
            if ((format.roleFlags & androidx.media3.common.C.ROLE_FLAG_SUBTITLE) != 0)
                android.util.Log.d("TrackNameProvider", "    - SUBTITLE (副标题)");
            if ((format.roleFlags & androidx.media3.common.C.ROLE_FLAG_SIGN) != 0)
                android.util.Log.d("TrackNameProvider", "    - SIGN (手语)");
            if ((format.roleFlags & androidx.media3.common.C.ROLE_FLAG_DESCRIBES_VIDEO) != 0)
                android.util.Log.d("TrackNameProvider", "    - DESCRIBES_VIDEO (视频描述)");
            if ((format.roleFlags & androidx.media3.common.C.ROLE_FLAG_DESCRIBES_MUSIC_AND_SOUND) != 0)
                android.util.Log.d("TrackNameProvider", "    - DESCRIBES_MUSIC_AND_SOUND (音乐声音描述)");
            if ((format.roleFlags & androidx.media3.common.C.ROLE_FLAG_ENHANCED_DIALOG_INTELLIGIBILITY) != 0)
                android.util.Log.d("TrackNameProvider", "    - ENHANCED_DIALOG_INTELLIGIBILITY (增强对话清晰度)");
            if ((format.roleFlags & androidx.media3.common.C.ROLE_FLAG_TRANSCRIBES_DIALOG) != 0)
                android.util.Log.d("TrackNameProvider", "    - TRANSCRIBES_DIALOG (对话转录)");
            if ((format.roleFlags & androidx.media3.common.C.ROLE_FLAG_EASY_TO_READ) != 0)
                android.util.Log.d("TrackNameProvider", "    - EASY_TO_READ (易读)");
            if ((format.roleFlags & androidx.media3.common.C.ROLE_FLAG_TRICK_PLAY) != 0)
                android.util.Log.d("TrackNameProvider", "    - TRICK_PLAY (特技播放)");

            android.util.Log.d("TrackNameProvider", "【元数据】");
            android.util.Log.d("TrackNameProvider", "  metadata: " + (format.metadata != null ? format.metadata.length() + " 项" : "null"));
            if (format.metadata != null && format.metadata.length() > 0) {
                for (int i = 0; i < format.metadata.length(); i++) {
                    android.util.Log.d("TrackNameProvider", "    [" + i + "] " + format.metadata.get(i).toString());
                }
            }

            android.util.Log.d("TrackNameProvider", "【初始化数据】");
            android.util.Log.d("TrackNameProvider", "  initializationData: " + (format.initializationData != null ? format.initializationData.size() + " 块" : "null"));
            if (format.initializationData != null && !format.initializationData.isEmpty()) {
                for (int i = 0; i < format.initializationData.size(); i++) {
                    byte[] data = format.initializationData.get(i);
                    android.util.Log.d("TrackNameProvider", "    [" + i + "] " + data.length + " bytes");
                }
            }

            android.util.Log.d("TrackNameProvider", "【DRM 信息】");
            android.util.Log.d("TrackNameProvider", "  drmInitData: " + format.drmInitData);
            if (format.drmInitData != null) {
                android.util.Log.d("TrackNameProvider", "    schemeType: " + format.drmInitData.schemeType);
                android.util.Log.d("TrackNameProvider", "    schemeDataCount: " + format.drmInitData.schemeDataCount);
            }

            android.util.Log.d("TrackNameProvider", "【其他字段】");
            android.util.Log.d("TrackNameProvider", "  maxInputSize: " + format.maxInputSize);
            android.util.Log.d("TrackNameProvider", "  subsampleOffsetUs: " + format.subsampleOffsetUs);
            android.util.Log.d("TrackNameProvider", "  accessibilityChannel: " + format.accessibilityChannel);
            android.util.Log.d("TrackNameProvider", "  cryptoType: " + format.cryptoType);
            android.util.Log.d("TrackNameProvider", "  tileCountHorizontal: " + format.tileCountHorizontal);
            android.util.Log.d("TrackNameProvider", "  tileCountVertical: " + format.tileCountVertical);

            android.util.Log.d("TrackNameProvider", "");
            android.util.Log.d("TrackNameProvider", "【处理结果】");
            android.util.Log.d("TrackNameProvider", "  是否详细 label: " + isDetailedLabel(label));

            if (!TextUtils.isEmpty(label) && isDetailedLabel(label)) {
                // label 包含详细信息,简化处理
                trackName = simplifyDetailedLabel(label);
                android.util.Log.d("TrackNameProvider", "简化后显示: " + trackName);
            } else {
                // label 简单或为空,添加技术信息
                String languageOrLabel = buildLanguageOrLabelString(format);
                trackName = joinWithSeparator(languageOrLabel, buildAudioChannelString(format), buildBitrateString(format));
                android.util.Log.d("TrackNameProvider", "组合后显示: " + trackName);
            }
            android.util.Log.d("TrackNameProvider", "==================================");
        } else {
            trackName = joinWithSeparator(buildLanguageString(format), buildLabelString(format));
        }
        return TextUtils.isEmpty(trackName) ? resources.getString(R.string.exo_track_unknown) : joinWithSeparator(trackName, buildFrameRateString(format.frameRate), buildMimeString(format));
    }

    public String getTrackName(ITrackInfo trackInfo) {
        String trackName;
        int trackType = trackInfo.getTrackType();
        if (trackType == C.TRACK_TYPE_VIDEO) {
            trackName = joinWithSeparator(buildResolutionString(trackInfo.getWidth(), trackInfo.getHeight()), buildBitrateString(trackInfo.getBitrate()));
        } else if (trackType == C.TRACK_TYPE_AUDIO) {
            trackName = joinWithSeparator(buildLanguageString(trackInfo.getLanguage()), buildAudioChannelString(trackInfo.getChannelCount()), buildBitrateString(trackInfo.getBitrate()));
        } else {
            trackName = joinWithSeparator(buildLanguageString(trackInfo.getLanguage()));
        }
        return TextUtils.isEmpty(trackName) ? resources.getString(R.string.exo_track_unknown) : joinWithSeparator(trackName, buildFrameRateString(trackInfo.getFps()), buildMimeString(trackInfo.getMimeType()));
    }

    private String buildResolutionString(Format format) {
        return buildResolutionString(format.width, format.height);
    }

    private String buildResolutionString(int width, int height) {
        return width == Format.NO_VALUE || height == Format.NO_VALUE ? "" : resources.getString(R.string.exo_track_resolution, width, height);
    }

    private String buildBitrateString(Format format) {
        return buildBitrateString(format.bitrate);
    }

    private String buildBitrateString(int bitrate) {
        return bitrate <= 0 ? "" : resources.getString(R.string.exo_track_bitrate, bitrate / 1000000f);
    }

    private String buildAudioChannelString(Format format) {
        return buildAudioChannelString(format.channelCount);
    }

    private String buildAudioChannelString(int channelCount) {
        if (channelCount < 1) return "";
        switch (channelCount) {
            case 1:
                return resources.getString(R.string.exo_track_mono);
            case 2:
                return resources.getString(R.string.exo_track_stereo);
            case 6:
            case 7:
                return resources.getString(R.string.exo_track_surround_5_point_1);
            case 8:
                return resources.getString(R.string.exo_track_surround_7_point_1);
            default:
                return resources.getString(R.string.exo_track_surround);
        }
    }

    /**
     * 判断 label 是否已经包含详细的技术信息
     * 如果包含编解码器名称、比特率、声道数等信息,则认为是详细 label
     */
    private boolean isDetailedLabel(String label) {
        if (TextUtils.isEmpty(label)) return false;
        String lowerLabel = label.toLowerCase();

        // 检查是否包含编解码器关键词
        boolean hasCodec = lowerLabel.contains("dolby") || lowerLabel.contains("dts") ||
                          lowerLabel.contains("truehd") || lowerLabel.contains("atmos") ||
                          lowerLabel.contains("ac3") || lowerLabel.contains("eac3") ||
                          lowerLabel.contains("aac") || lowerLabel.contains("mp3") ||
                          lowerLabel.contains("flac") || lowerLabel.contains("opus");

        // 检查是否包含比特率信息
        boolean hasBitrate = lowerLabel.contains("kbps") || lowerLabel.contains("mbps") ||
                            lowerLabel.contains("kb/s") || lowerLabel.contains("mb/s") ||
                            lowerLabel.contains("@");

        // 检查是否包含声道信息
        boolean hasChannel = lowerLabel.contains("5.1") || lowerLabel.contains("7.1") ||
                            lowerLabel.contains("stereo") || lowerLabel.contains("mono") ||
                            lowerLabel.contains("surround");

        // 如果包含编解码器或比特率信息,认为是详细 label
        return hasCodec || hasBitrate || hasChannel;
    }

    /**
     * 简化详细的 label
     * 例如: "English Dolby TrueHD/Atmos Audio 7.1 @ 7810 kbps" -> "英语 7.1 TrueHD"
     * 例如: "Mandarin DTS-HD Master Audio 7.1 @ 4657 kbps 台配" -> "国语 7.1 DTS-HD 台配"
     */
    private String simplifyDetailedLabel(String label) {
        if (TextUtils.isEmpty(label)) return label;

        // 提取语言部分并转换
        String language = extractAndConvertLanguage(label);

        // 提取声道信息
        String channel = extractChannel(label);

        // 提取编解码器信息
        String codec = extractCodec(label);

        // 提取额外标注(如"台配"、"粤语"等)
        String extra = extractExtraInfo(label);

        // 组合简化后的信息
        return joinWithSeparator(language, channel, codec, extra);
    }

    /**
     * 从 label 中提取并转换语言
     */
    private String extractAndConvertLanguage(String label) {
        String lowerLabel = label.toLowerCase();

        // 优先检查特定方言,避免被通用的 "Chinese" 匹配
        if (lowerLabel.startsWith("cantonese") || lowerLabel.contains("cantonese") ||
            lowerLabel.contains("yue chinese") || lowerLabel.startsWith("yue ")) {
            return "粤语";
        } else if (lowerLabel.contains("台配") || lowerLabel.contains("台湾")) {
            return "台语";
        } else if (lowerLabel.contains("粤语") || lowerLabel.contains("粤")) {
            return "粤语";
        } else if (lowerLabel.contains("国配") || lowerLabel.contains("国语")) {
            return "国语";
        }

        // 通用语言检查
        if (lowerLabel.startsWith("english") || lowerLabel.contains("english")) {
            return "英语";
        } else if (lowerLabel.startsWith("mandarin") || lowerLabel.contains("mandarin") ||
                   lowerLabel.startsWith("chinese") || lowerLabel.contains("chinese")) {
            return "国语";  // 默认为国语
        } else if (lowerLabel.startsWith("japanese") || lowerLabel.contains("japanese")) {
            return "日语";
        } else if (lowerLabel.startsWith("korean") || lowerLabel.contains("korean")) {
            return "韩语";
        } else if (lowerLabel.startsWith("french") || lowerLabel.contains("french")) {
            return "法语";
        } else if (lowerLabel.startsWith("german") || lowerLabel.contains("german")) {
            return "德语";
        } else if (lowerLabel.startsWith("spanish") || lowerLabel.contains("spanish")) {
            return "西语";
        }

        return "";
    }

    /**
     * 从 label 中提取声道信息
     */
    private String extractChannel(String label) {
        if (label.contains("7.1")) return "7.1";
        if (label.contains("5.1")) return "5.1";
        if (label.contains("2.0") || label.toLowerCase().contains("stereo")) return "立体声";
        if (label.toLowerCase().contains("mono")) return "单声道";
        return "";
    }

    /**
     * 从 label 中提取编解码器信息
     */
    private String extractCodec(String label) {
        String lowerLabel = label.toLowerCase();

        if (lowerLabel.contains("truehd")) return "TrueHD";
        if (lowerLabel.contains("atmos")) return "Atmos";
        if (lowerLabel.contains("dts-hd") || lowerLabel.contains("dts hd")) return "DTS-HD";
        if (lowerLabel.contains("dts-x") || lowerLabel.contains("dts:x")) return "DTS:X";
        if (lowerLabel.contains("dts")) return "DTS";
        if (lowerLabel.contains("dolby digital")) return "DD";
        if (lowerLabel.contains("eac3") || lowerLabel.contains("e-ac-3")) return "EAC3";
        if (lowerLabel.contains("ac3") || lowerLabel.contains("ac-3")) return "AC3";
        if (lowerLabel.contains("aac")) return "AAC";
        if (lowerLabel.contains("flac")) return "FLAC";
        if (lowerLabel.contains("opus")) return "Opus";

        return "";
    }

    /**
     * 从 label 中提取额外信息(如"台配"、"国配"、"[Original]"等)
     */
    private String extractExtraInfo(String label) {
        // 提取中文标注
        if (label.contains("台配")) return "台配";
        if (label.contains("国配")) return "国配";
        if (label.contains("粤语") && !label.toLowerCase().startsWith("cantonese") && !label.toLowerCase().contains("yue")) return "粤语";
        if (label.contains("原声")) return "原声";

        // 提取英文标注
        if (label.contains("[Original]") || label.contains("(Original)")) return "原声";
        if (label.contains("[Commentary]") || label.contains("(Commentary)")) return "评论音轨";
        if (label.contains("[Descriptive]") || label.contains("(Descriptive)")) return "描述音轨";

        return "";
    }

    private String buildLanguageOrLabelString(Format format) {
        // 优先使用 label,因为它包含更详细的语言信息(如粤语、国语、台配等)
        String label = buildLabelString(format);
        if (!TextUtils.isEmpty(label)) {
            // 尝试从 label 中提取并转换语言
            String convertedLanguage = extractAndConvertLanguage(label);
            if (!TextUtils.isEmpty(convertedLanguage)) {
                // 如果成功转换,使用转换后的语言
                // 同时保留 label 中的额外信息(如 [Original])
                String extra = extractExtraInfo(label);
                if (!TextUtils.isEmpty(extra)) {
                    return joinWithSeparator(convertedLanguage, extra);
                }
                return convertedLanguage;
            }
            // 如果无法转换,直接使用原始 label
            return label;
        }
        // label 为空,使用语言代码和角色信息
        String languageAndRole = joinWithSeparator(buildLanguageString(format), buildRoleString(format));
        return languageAndRole;
    }

    private String buildLabelString(Format format) {
        return TextUtils.isEmpty(format.label) ? "" : format.label;
    }

    private String buildLanguageString(Format format) {
        return buildLanguageString(format.language);
    }

    private String buildLanguageString(@Nullable String language) {
        if (TextUtils.isEmpty(language) || C.LANGUAGE_UNDETERMINED.equals(language)) return "";

        // 特殊处理中文方言代码
        // 参考: https://en.wikipedia.org/wiki/IETF_language_tag
        if (language.startsWith("zh-") || language.startsWith("yue") || language.startsWith("cmn")) {
            return getChineseDialectName(language);
        }

        Locale languageLocale = Util.SDK_INT >= 21 ? Locale.forLanguageTag(language) : new Locale(language);
        Locale displayLocale = Util.getDefaultDisplayLocale();
        String languageName = languageLocale.getDisplayName(displayLocale);
        if (TextUtils.isEmpty(languageName)) return "";
        try {
            int firstCodePointLength = languageName.offsetByCodePoints(0, 1);
            return languageName.substring(0, firstCodePointLength).toUpperCase(displayLocale) + languageName.substring(firstCodePointLength);
        } catch (IndexOutOfBoundsException e) {
            return languageName;
        }
    }

    /**
     * 获取中文方言的显示名称
     * 支持常见的中文方言 ISO 639-3 代码和 IETF 语言标签
     */
    private String getChineseDialectName(String language) {
        // 标准化处理
        String lang = language.toLowerCase();

        // IETF 语言标签格式: zh-CN (简体中文), zh-TW (繁体中文), zh-HK (香港粤语)
        if (lang.equals("zh-cn") || lang.equals("cmn") || lang.equals("cmn-hans")) {
            return "国语";
        } else if (lang.equals("zh-tw") || lang.equals("cmn-hant")) {
            return "台语";
        } else if (lang.equals("zh-hk") || lang.equals("yue") || lang.equals("yue-hk")) {
            return "粤语";
        } else if (lang.equals("zh-mo")) {
            return "粤语";  // 澳门也使用粤语
        } else if (lang.equals("zh-sg")) {
            return "国语";  // 新加坡使用普通话
        } else if (lang.startsWith("zh")) {
            return "中文";
        }

        // 如果无法识别,返回原始语言代码
        return language;
    }

    private String buildRoleString(Format format) {
        String roles = "";
        if ((format.roleFlags & C.ROLE_FLAG_ALTERNATE) != 0) roles = resources.getString(R.string.exo_track_role_alternate);
        if ((format.roleFlags & C.ROLE_FLAG_SUPPLEMENTARY) != 0) roles = joinWithSeparator(roles, resources.getString(R.string.exo_track_role_supplementary));
        if ((format.roleFlags & C.ROLE_FLAG_COMMENTARY) != 0) roles = joinWithSeparator(roles, resources.getString(R.string.exo_track_role_commentary));
        if ((format.roleFlags & (C.ROLE_FLAG_CAPTION | C.ROLE_FLAG_DESCRIBES_MUSIC_AND_SOUND)) != 0) roles = joinWithSeparator(roles, resources.getString(R.string.exo_track_role_closed_captions));
        return roles;
    }

    private String joinWithSeparator(String... items) {
        String itemList = "";
        for (String item : items) {
            if (item.length() > 0) {
                if (TextUtils.isEmpty(itemList)) {
                    itemList = item;
                } else {
                    itemList = resources.getString(R.string.exo_item_list, itemList, item);
                }
            }
        }
        return itemList;
    }

    private int inferPrimaryTrackType(Format format) {
        int trackType = MimeTypes.getTrackType(format.sampleMimeType);
        if (trackType != C.TRACK_TYPE_UNKNOWN) return trackType;
        if (MimeTypes.getVideoMediaMimeType(format.codecs) != null) return C.TRACK_TYPE_VIDEO;
        if (MimeTypes.getAudioMediaMimeType(format.codecs) != null) return C.TRACK_TYPE_AUDIO;
        if (format.width != Format.NO_VALUE || format.height != Format.NO_VALUE) return C.TRACK_TYPE_VIDEO;
        if (format.channelCount != Format.NO_VALUE || format.sampleRate != Format.NO_VALUE) return C.TRACK_TYPE_AUDIO;
        return C.TRACK_TYPE_UNKNOWN;
    }

    private String buildFrameRateString(float frameRate) {
        if (frameRate <= 0) return "";
        return (int) Math.floor(frameRate) + "FPS";
    }

    private String buildMimeString(Format format) {
        if (format.sampleMimeType == null) return "";
        return buildMimeString(format.sampleMimeType);
    }

    private String buildMimeString(String mimeType) {
        switch (mimeType) {
            case MimeTypes.AUDIO_DTS:
                return "DTS";
            case MimeTypes.AUDIO_DTS_HD:
                return "DTS-HD";
            case MimeTypes.AUDIO_DTS_EXPRESS:
                return "DTS";
            case MimeTypes.AUDIO_TRUEHD:
                return "TrueHD";
            case MimeTypes.AUDIO_AC3:
                return "AC3";
            case MimeTypes.AUDIO_E_AC3:
                return "EAC3";
            case MimeTypes.AUDIO_E_AC3_JOC:
                return "EAC3";
            case MimeTypes.AUDIO_AC4:
                return "AC4";
            case MimeTypes.AUDIO_AAC:
                return "AAC";
            case MimeTypes.AUDIO_MPEG:
                return "MP3";
            case MimeTypes.AUDIO_MPEG_L2:
                return "MP2";
            case MimeTypes.AUDIO_VORBIS:
                return "Vorbis";
            case MimeTypes.AUDIO_OPUS:
                return "Opus";
            case MimeTypes.AUDIO_FLAC:
                return "FLAC";
            case MimeTypes.AUDIO_ALAC:
                return "ALAC";
            case MimeTypes.AUDIO_WAV:
                return "WAV";
            case MimeTypes.AUDIO_AMR:
            case MimeTypes.AUDIO_AMR_NB:
            case MimeTypes.AUDIO_AMR_WB:
                return "AMR";
            case MimeTypes.VIDEO_MP4:
                return "MP4";
            case MimeTypes.VIDEO_FLV:
                return "FLV";
            case MimeTypes.VIDEO_AV1:
                return "AV1";
            case MimeTypes.VIDEO_AVI:
                return "AVI";
            case MimeTypes.VIDEO_MPEG:
            case MimeTypes.VIDEO_MPEG2:
                return "MPEG";
            case MimeTypes.VIDEO_H263:
                return "H263";
            case MimeTypes.VIDEO_H264:
                return "H264";
            case MimeTypes.VIDEO_H265:
                return "H265";
            case MimeTypes.VIDEO_VP8:
                return "VP8";
            case MimeTypes.VIDEO_VP9:
                return "VP9";
            case MimeTypes.VIDEO_DOLBY_VISION:
                return "DV";
            case MimeTypes.APPLICATION_PGS:
                return "PGS";
            case MimeTypes.APPLICATION_SUBRIP:
                return "SRT";
            case MimeTypes.TEXT_SSA:
                return "SSA";
            case MimeTypes.TEXT_VTT:
                return "VTT";
            case MimeTypes.APPLICATION_TTML:
                return "TTML";
            case MimeTypes.APPLICATION_TX3G:
                return "TX3G";
            case MimeTypes.APPLICATION_DVBSUBS:
                return "DVB";
            case MimeTypes.APPLICATION_MEDIA3_CUES:
                return "CUES";
            default:
                return "";  // 不显示未知的编解码器
        }
    }
}
