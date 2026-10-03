package com.gabriellpa.sabadaco.discord.interaction;

/**
 * Publicado pelo {@link InteractionRouter} ao fim de cada comando ou componente.
 * Quem quiser estatísticas (ex.: o painel admin) escuta este evento sem acoplar no router.
 *
 * @param name    ex.: {@code play}, {@code playlist move}, {@code player:pause}
 * @param outcome {@code success}, {@code user_error} ou {@code error}
 */
public record InteractionExecuted(long userId, String name, boolean component, String outcome, long durationNanos) {

    public boolean failed() {
        return !"success".equals(outcome);
    }
}
