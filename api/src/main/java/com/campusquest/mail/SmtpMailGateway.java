package com.campusquest.mail;

import com.campusquest.config.AppProperties;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import java.nio.charset.StandardCharsets;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;

@Component
public class SmtpMailGateway implements MailGateway {

    private final JavaMailSender sender;
    private final String from;

    public SmtpMailGateway(JavaMailSender sender, AppProperties properties) {
        this.sender = sender;
        this.from = properties.mail().from();
    }

    @Override
    public void send(OutboundMail mail) {
        try {
            MimeMessage message = sender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, StandardCharsets.UTF_8.name());
            helper.setFrom(from);
            helper.setTo(mail.to());
            helper.setSubject(mail.subject());
            helper.setText(mail.text(), mail.html());
            sender.send(message);
        } catch (MessagingException e) {
            throw new IllegalStateException("Could not build email", e);
        }
    }
}
