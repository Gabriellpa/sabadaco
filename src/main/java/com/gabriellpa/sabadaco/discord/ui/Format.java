package com.gabriellpa.sabadaco.discord.ui;

import com.gabriellpa.sabadaco.music.TrackSummary;

import java.util.Locale;

/**
 * Formatação de textos exibidos no Discord (e reaproveitada pelo painel admin).
 */
public final class Format {

    private static final int BAR_WIDTH = 16;
    private static final Locale PT_BR = Locale.of("pt", "BR");

    private Format() {
    }

    public static String duration(long millis) {
        long totalSeconds = Math.max(0, millis) / 1000;
        long hours = totalSeconds / 3600;
        long minutes = (totalSeconds % 3600) / 60;
        long seconds = totalSeconds % 60;
        return hours > 0 ? "%d:%02d:%02d".formatted(hours, minutes, seconds) : "%d:%02d".formatted(minutes, seconds);
    }

    public static String trackDuration(TrackSummary track) {
        return track.stream() ? "🔴 ao vivo" : duration(track.durationMs());
    }

    public static String progressBar(long positionMs, long lengthMs) {
        if (lengthMs <= 0) {
            return "▬".repeat(BAR_WIDTH);
        }
        int marker = (int) Math.min(BAR_WIDTH - 1, positionMs * BAR_WIDTH / lengthMs);
        return "▬".repeat(marker) + "🔘" + "▬".repeat(BAR_WIDTH - 1 - marker);
    }

    public static String bytes(long bytes) {
        if (bytes < 1024) {
            return bytes + " B";
        }
        if (bytes < 1024 * 1024) {
            return String.format(PT_BR, "%.1f KB", bytes / 1024.0);
        }
        return String.format(PT_BR, "%.2f MB", bytes / (1024.0 * 1024));
    }

    public static String truncate(String text, int max) {
        if (text == null) {
            return "";
        }
        return text.length() <= max ? text : text.substring(0, max - 1) + "…";
    }

    /** Link markdown com título escapado e sem preview. */
    public static String link(TrackSummary track, int maxTitle) {
        var title = escape(truncate(track.title(), maxTitle));
        return track.uri() == null ? title : "[" + title + "](<" + track.uri() + ">)";
    }

    public static String escape(String text) {
        return text == null ? "" : text.replaceAll("([\\\\*_~`|\\[\\]>])", "\\\\$1");
    }

    public static String mention(long userId) {
        return userId == 0 ? "alguém" : "<@" + userId + ">";
    }
}
