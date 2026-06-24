package com.example.featureflags.service;

import com.example.featureflags.dto.CreateFlagRequest;
import com.example.featureflags.dto.EvaluateResponse;
import com.example.featureflags.dto.FlagResponse;
import com.example.featureflags.dto.UpdateFlagRequest;

import java.util.List;

public interface FeatureFlagService {
    FlagResponse create(CreateFlagRequest request);

    List<FlagResponse> findAll();

    FlagResponse findById(Long id);

    FlagResponse update(Long id, UpdateFlagRequest request);

    void delete(Long id);

    EvaluateResponse evaluate(String name);
}
