package com.gabriellpa.sabadaco.playlist;

public enum PlaylistScope {
    /** Do usuário, só no servidor onde foi criada (padrão). */
    GUILD,
    /** Do usuário, em qualquer servidor. */
    GLOBAL,
    /**
     * Do servidor: o dono é o próprio servidor ({@code ownerId == guildId}), todo mundo dele vê e toca,
     * e só admins do bot criam, editam e apagam.
     */
    SERVER
}
