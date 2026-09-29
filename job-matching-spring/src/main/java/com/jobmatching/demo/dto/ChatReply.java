package com.jobmatching.demo.dto;

import java.util.List;

public record ChatReply(
        String reply,
        List<DraftProposal> draftProposals
) {
}
