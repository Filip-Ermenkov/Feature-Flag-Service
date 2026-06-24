package com.example.featureflags.dto;

import com.example.featureflags.model.FeatureFlag;

import java.time.Instant;

public record FlagResponse(
        Long id,
        String name,
        String description,
        boolean enabled,
        Instant createdAt,
        Instant updatedAt
) {
    public static FlagResponse from(FeatureFlag flag) {
        return new FlagResponse(
                flag.getId(),
                flag.getName(),
                flag.getDescription(),
                flag.isEnabled(),
                flag.getCreatedAt(),
                flag.getUpdatedAt()
        );
    }
}
