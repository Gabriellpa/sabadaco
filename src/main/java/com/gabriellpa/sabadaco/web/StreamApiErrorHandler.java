package com.gabriellpa.sabadaco.web;

import com.gabriellpa.sabadaco.UserFacingException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;

/** Erros de regra viram 403 com a mensagem em JSON, que a página da stream mostra para a pessoa. */
@RestControllerAdvice(basePackageClasses = StreamApiErrorHandler.class)
public class StreamApiErrorHandler {

    @ExceptionHandler(UserFacingException.class)
    public ResponseEntity<Map<String, String>> userError(UserFacingException error) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", error.getMessage()));
    }
}
