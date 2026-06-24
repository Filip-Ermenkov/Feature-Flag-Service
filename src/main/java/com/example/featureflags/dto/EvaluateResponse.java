package com.example.featureflags.dto;

public record EvaluateResponse(
        String name,
        boolean enabled
) {}
