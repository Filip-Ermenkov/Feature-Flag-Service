package com.example.featureflags.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CreateFlagRequest(

        @NotBlank(message = "name must not be blank")
        @Size(max = 255, message = "name must not exceed 255 characters")
        String name,

        String description,

        @NotNull(message = "enabled must not be null")
        Boolean enabled
) {}
