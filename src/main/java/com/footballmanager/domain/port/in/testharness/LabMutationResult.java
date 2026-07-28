package com.footballmanager.domain.port.in.testharness;

import com.footballmanager.application.service.domain.TeamStyle;

import java.util.List;
import java.util.Map;

public record LabMutationResult(
    String labKey,
    String message,
    Map<String, Object> details
) {}
