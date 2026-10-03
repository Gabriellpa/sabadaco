package com.gabriellpa.sabadaco.discord.interaction;

/**
 * customId de componentes no formato {@code prefixo:acao[:payload]}.
 */
public record CustomId(String prefix, String action, String payload) {

    private static final String SEPARATOR = ":";

    public static String of(String prefix, String action) {
        return prefix + SEPARATOR + action;
    }

    public static String of(String prefix, String action, Object payload) {
        return prefix + SEPARATOR + action + SEPARATOR + payload;
    }

    public static CustomId parse(String id) {
        var parts = id.split(SEPARATOR, 3);
        return new CustomId(parts[0], parts.length > 1 ? parts[1] : "", parts.length > 2 ? parts[2] : "");
    }
}
