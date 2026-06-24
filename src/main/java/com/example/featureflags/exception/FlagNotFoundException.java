package com.example.featureflags.exception;

public class FlagNotFoundException extends RuntimeException {
    public FlagNotFoundException(Long id) {
        super("Feature flag not found with id: " + id);
    }

    public FlagNotFoundException(String name) {
        super("Feature flag not found with name: " + name);
    }
}
