package com.jobmatching.demo.service;

import com.jobmatching.demo.dto.JobMatchResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.ses.SesClient;
import software.amazon.awssdk.services.ses.model.Body;
import software.amazon.awssdk.services.ses.model.Content;
import software.amazon.awssdk.services.ses.model.Destination;
import software.amazon.awssdk.services.ses.model.Message;
import software.amazon.awssdk.services.ses.model.SendEmailRequest;

import java.util.List;

/**
 * Ports jobspy/email_service.py's SESEmailService — same subject lines and body text
 * so existing email templates/spam-filter rules don't need to change.
 */
@Service
public class EmailService {

    private static final Logger log = LoggerFactory.getLogger(EmailService.class);

    private final SesClient sesClient;
    private final String senderEmail;

    public EmailService(SesClient sesClient, @Value("${aws.ses.sender-email}") String senderEmail) {
        this.sesClient = sesClient;
        this.senderEmail = senderEmail;
    }

    public boolean sendJobMatches(String candidateName, String candidateEmail, List<JobMatchResult> matches) {
        if (matches.isEmpty()) {
            return false;
        }

        StringBuilder jobLines = new StringBuilder();
        int count = Math.min(matches.size(), 10);
        for (int i = 0; i < count; i++) {
            JobMatchResult match = matches.get(i);
            double score = Math.round(match.similarityScore() * 1000) / 10.0;
            String title = match.job().getTitle() != null ? match.job().getTitle() : "N/A";
            String company = match.job().getCompanyName() != null ? match.job().getCompanyName() : "N/A";
            String url = match.job().getJobUrl() != null ? match.job().getJobUrl() : "";
            jobLines.append("%d. %s at %s (%s%% match)%n   %s%n".formatted(i + 1, title, company, score, url));
        }

        String body = """
                Hi %s,

                We found %d job matches for you:

                %s
                Good luck with your applications!
                """.formatted(candidateName, matches.size(), jobLines);

        try {
            sendEmail(candidateEmail, "Your Job Matches - " + matches.size() + " found!", body);
            log.info("Sent match email to {}", candidateEmail);
            return true;
        } catch (Exception e) {
            log.error("SES send failed: {}", e.getMessage());
            throw e;
        }
    }

    public boolean sendVerificationCode(String email, String code) {
        String body = """
                Welcome to Beshton Job Matching Platform!

                Your email verification code is:

                    %s

                Enter this code on the verification page to activate your account. The code expires in 10 minutes.

                If you did not register for an account, you can safely ignore this email.
                """.formatted(code);
        try {
            sendEmail(email, "Verify Your Beshton Account", body);
            log.info("Sent verification email to {}", email);
            return true;
        } catch (Exception e) {
            log.error("SES verification send failed: {}", e.getMessage());
            return false;
        }
    }

    public boolean sendPasswordReset(String email, String resetUrl) {
        String body = """
                Hi,

                We received a request to reset your password for your Beshton account.

                Click the link below to set a new password (expires in 1 hour):

                %s

                If you didn't request this, you can safely ignore this email.
                """.formatted(resetUrl);
        try {
            sendEmail(email, "Reset Your Beshton Password", body);
            log.info("Sent password reset email to {}", email);
            return true;
        } catch (Exception e) {
            log.error("SES password reset send failed: {}", e.getMessage());
            return false;
        }
    }

    private void sendEmail(String to, String subject, String body) {
        sesClient.sendEmail(SendEmailRequest.builder()
                .source(senderEmail)
                .destination(Destination.builder().toAddresses(to).build())
                .message(Message.builder()
                        .subject(Content.builder().data(subject).build())
                        .body(Body.builder().text(Content.builder().data(body).build()).build())
                        .build())
                .build());
    }
}
