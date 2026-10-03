package com.gabriellpa.sabadaco;

/**
 * Erro de regra de negócio cuja mensagem pode ser mostrada diretamente ao usuário (Discord ou painel admin).
 */
public class UserFacingException extends RuntimeException {

    public UserFacingException(String message) {
        super(message);
    }
}
