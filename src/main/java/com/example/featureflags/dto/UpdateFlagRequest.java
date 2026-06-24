package com.example.featureflags.dto;

import jakarta.validation.constraints.Size;

public record UpdateFlagRequest(

        @Size(min = 1, max = 255, message = "name must be between 1 and 255 characters")
        String name,

        String description,

        Boolean enabled
) {}
