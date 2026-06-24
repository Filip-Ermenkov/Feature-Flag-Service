package com.example.featureflags.exception;

public class DuplicateFlagNameException extends RuntimeException {
    public DuplicateFlagNameException(String name) {
        super("A feature flag with name '" + name + "' already exists");
    }
}
