package com.example.featureflags.service;

import com.example.featureflags.dto.CreateFlagRequest;
import com.example.featureflags.dto.EvaluateResponse;
import com.example.featureflags.dto.FlagResponse;
import com.example.featureflags.dto.UpdateFlagRequest;
import com.example.featureflags.exception.DuplicateFlagNameException;
import com.example.featureflags.exception.FlagNotFoundException;
import com.example.featureflags.model.FeatureFlag;
import com.example.featureflags.repository.FeatureFlagRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional(readOnly = true)
public class FeatureFlagServiceImpl implements FeatureFlagService {

    private final FeatureFlagRepository repository;

    public FeatureFlagServiceImpl(FeatureFlagRepository repository) {
        this.repository = repository;
    }

    @Override
    @Transactional
    public FlagResponse create(CreateFlagRequest request) {
        if (repository.existsByName(request.name())) {
            throw new DuplicateFlagNameException(request.name());
        }
        FeatureFlag saved = repository.save(
                new FeatureFlag(request.name(), request.description(), request.enabled()));
        return FlagResponse.from(saved);
    }

    @Override
    public List<FlagResponse> findAll() {
        return repository.findAll().stream()
                .map(FlagResponse::from)
                .toList();
    }

    @Override
    public FlagResponse findById(Long id) {
        return repository.findById(id)
                .map(FlagResponse::from)
                .orElseThrow(() -> new FlagNotFoundException(id));
    }

    @Override
    @Transactional
    public FlagResponse update(Long id, UpdateFlagRequest request) {
        FeatureFlag flag = repository.findById(id)
                .orElseThrow(() -> new FlagNotFoundException(id));

        if (request.name() != null && !request.name().equals(flag.getName())) {
            if (repository.existsByName(request.name())) {
                throw new DuplicateFlagNameException(request.name());
            }
            flag.setName(request.name());
        }
        if (request.description() != null) {
            flag.setDescription(request.description());
        }
        if (request.enabled() != null) {
            flag.setEnabled(request.enabled());
        }

        return FlagResponse.from(repository.save(flag));
    }

    @Override
    @Transactional
    public void delete(Long id) {
        if (!repository.existsById(id)) {
            throw new FlagNotFoundException(id);
        }
        repository.deleteById(id);
    }

    @Override
    public EvaluateResponse evaluate(String name) {
        FeatureFlag flag = repository.findByName(name)
                .orElseThrow(() -> new FlagNotFoundException(name));
        return new EvaluateResponse(flag.getName(), flag.isEnabled());
    }
}
