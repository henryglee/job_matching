package com.jobmatching.demo.dto;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record CandidateIdRequest(@NotNull UUID candidateId) {
}
