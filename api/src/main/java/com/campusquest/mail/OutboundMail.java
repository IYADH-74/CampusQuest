package com.campusquest.mail;

/** A rendered email. {@code html} is built from escaped values only (see {@link MailTemplates}). */
public record OutboundMail(String to, String subject, String text, String html) {}
