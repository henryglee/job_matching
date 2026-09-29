package com.jobmatching.demo.controller;

import com.jobmatching.demo.dto.AiUsageReport;
import com.jobmatching.demo.entity.CandidateInfo;
import com.jobmatching.demo.repository.CandidateInfoRepository;
import com.jobmatching.demo.service.AiUsageMonitor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * AI usage per candidate since the server started, for the Admin "AI Usage" page: chat
 * messages, drafts, what-if scores, model tokens, moderation blocks, limit hits and failures.
 * Counts only; no message content is ever kept. Admin only. No app.py equivalent.
 */
@RestController
@PreAuthorize("hasRole('Admin')")
public class AiUsageController {

    private final AiUsageMonitor aiUsageMonitor;
    private final CandidateInfoRepository candidateInfoRepository;

    public AiUsageController(AiUsageMonitor aiUsageMonitor, CandidateInfoRepository candidateInfoRepository) {
        this.aiUsageMonitor = aiUsageMonitor;
        this.candidateInfoRepository = candidateInfoRepository;
    }

    @GetMapping("/api/admin/ai-usage")
    public AiUsageReport aiUsage() {
        return aiUsageMonitor.report(ids -> {
            Map<UUID, String> names = new HashMap<>();
            for (CandidateInfo candidate : candidateInfoRepository.findAllById(ids)) {
                names.put(candidate.getId(), candidate.getCandidateName());
            }
            return names;
        });
    }
}
