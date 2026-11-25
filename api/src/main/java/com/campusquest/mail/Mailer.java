package com.campusquest.mail;

import com.campusquest.config.AppProperties;
import com.campusquest.config.SecuritySettings;
import com.campusquest.user.User;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Queues transactional emails. Mail goes out asynchronously and only after the surrounding transaction commits,
 * so a rolled-back request never sends anything, and SMTP latency can't be used as a timing oracle.
 */
@Component
public class Mailer {

    private static final Logger log = LoggerFactory.getLogger(Mailer.class);

    /** Internal event; carries an already-rendered message. */
    public record MailRequested(OutboundMail mail) {}

    private final ApplicationEventPublisher events;
    private final MailGateway gateway;
    private final AppProperties app;
    private final SecuritySettings security;

    public Mailer(ApplicationEventPublisher events, MailGateway gateway, AppProperties app, SecuritySettings security) {
        this.events = events;
        this.gateway = gateway;
        this.app = app;
        this.security = security;
    }

    public void sendVerificationCode(User user, String code) {
        publish(MailTemplates.verificationCode(user.getEmail(), user.getFirstName(), code,
                security.verification().codeTtl().toMinutes()));
    }

    public void sendAccountAlreadyExists(User user) {
        publish(MailTemplates.accountAlreadyExists(user.getEmail(), user.getFirstName(), app.link("/forgot-password")));
    }

    public void sendPasswordReset(User user, String rawToken) {
        publish(MailTemplates.passwordReset(user.getEmail(), user.getFirstName(),
                app.link("/reset-password?token=" + encode(rawToken)), security.passwordResetTtl().toMinutes()));
    }

    public void sendInvite(User user, String rawToken) {
        publish(MailTemplates.invite(user.getEmail(), user.getFirstName(), user.getRole().name().toLowerCase(),
                app.link("/accept-invite?token=" + encode(rawToken)), security.inviteTtl().toHours()));
    }

    private void publish(OutboundMail mail) {
        events.publishEvent(new MailRequested(mail));
    }

    @Async
    @TransactionalEventListener(fallbackExecution = true)
    public void deliver(MailRequested event) {
        try {
            gateway.send(event.mail());
        } catch (RuntimeException e) {
            // Never log the body: it contains one-time secrets.
            log.warn("Failed to deliver email '{}': {}", event.mail().subject(), e.getClass().getSimpleName());
        }
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
