package com.gabriellpa.sabadaco.discord.interaction;

import java.util.List;

/**
 * Ajuda de um comando, exibida pelo {@code /help}. Cada comando descreve a si mesmo, então um
 * comando novo já aparece no {@code /help} sem mexer em mais nada.
 *
 * @param summary  o que o comando faz, em uma linha
 * @param examples exemplos prontos para copiar
 * @param tips     observações (regras, limites, atalhos)
 */
public record CommandHelp(Category category, String summary, List<Example> examples, List<String> tips) {

    public enum Category {
        MUSIC("🎵 Música"),
        CONTROLS("🎛️ Fila e controles"),
        PLAYLIST("💾 Playlists"),
        OTHER("✨ Outros");

        private final String label;

        Category(String label) {
            this.label = label;
        }

        public String label() {
            return label;
        }
    }

    /**
     * @param command     o comando como o usuário digita, ex.: {@code /play kasino sabadaço}
     * @param explanation o que acontece
     */
    public record Example(String command, String explanation) {
    }

    public static CommandHelp of(Category category, String summary, List<Example> examples, String... tips) {
        return new CommandHelp(category, summary, examples, List.of(tips));
    }

    public static Example example(String command, String explanation) {
        return new Example(command, explanation);
    }
}
