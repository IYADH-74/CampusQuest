package com.campusquest.mail;

/** Transport abstraction, so tests can capture mail and production can swap SMTP for an API provider. */
public interface MailGateway {

    void send(OutboundMail mail);
}
