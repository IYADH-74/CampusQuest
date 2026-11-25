package com.campusquest.mail;

import org.springframework.web.util.HtmlUtils;

/**
 * Plain-text + HTML bodies. Every interpolated value is HTML-escaped (legacy v2 injected the raw first name
 * into the HTML; see LV-10).
 */
final class MailTemplates {

    private MailTemplates() {}

    static OutboundMail verificationCode(String to, String firstName, String code, long ttlMinutes) {
        String text = """
                Hi %s,

                Your CampusQuest verification code is: %s

                It expires in %d minutes. If you did not create an account, you can ignore this email.
                """.formatted(firstName, code, ttlMinutes);
        String html = layout("Verify your email",
                "<p>Hi " + esc(firstName) + ",</p>"
                        + "<p>Your CampusQuest verification code is:</p>"
                        + "<p style=\"font-size:28px;font-weight:700;letter-spacing:6px\">" + esc(code) + "</p>"
                        + "<p>It expires in " + ttlMinutes + " minutes. If you did not create an account, ignore this email.</p>");
        return new OutboundMail(to, "Your CampusQuest verification code", text, html);
    }

    static OutboundMail accountAlreadyExists(String to, String firstName, String resetLink) {
        String text = """
                Hi %s,

                Someone tried to create a CampusQuest account with this email address, but you already have one.
                If that was you, sign in instead, or reset your password here: %s

                If it wasn't you, no action is needed. Your account is safe.
                """.formatted(firstName, resetLink);
        String html = layout("You already have an account",
                "<p>Hi " + esc(firstName) + ",</p>"
                        + "<p>Someone tried to create a CampusQuest account with this email address, but you already have one.</p>"
                        + "<p>If that was you, sign in instead or <a href=\"" + esc(resetLink) + "\">reset your password</a>.</p>"
                        + "<p>If it wasn't you, no action is needed.</p>");
        return new OutboundMail(to, "You already have a CampusQuest account", text, html);
    }

    static OutboundMail passwordReset(String to, String firstName, String link, long ttlMinutes) {
        String text = """
                Hi %s,

                Reset your CampusQuest password with this link (valid for %d minutes, single use):
                %s

                If you did not ask for this, ignore this email. Your password stays unchanged.
                """.formatted(firstName, ttlMinutes, link);
        String html = layout("Reset your password",
                "<p>Hi " + esc(firstName) + ",</p>"
                        + "<p><a href=\"" + esc(link) + "\">Reset your CampusQuest password</a> "
                        + "(valid for " + ttlMinutes + " minutes, single use).</p>"
                        + "<p>If you did not ask for this, ignore this email.</p>");
        return new OutboundMail(to, "Reset your CampusQuest password", text, html);
    }

    static OutboundMail invite(String to, String firstName, String role, String link, long ttlHours) {
        String text = """
                Hi %s,

                You have been invited to CampusQuest as %s.
                Choose your password here (valid for %d hours, single use):
                %s
                """.formatted(firstName, role, ttlHours, link);
        String html = layout("You're invited",
                "<p>Hi " + esc(firstName) + ",</p>"
                        + "<p>You have been invited to CampusQuest as <b>" + esc(role) + "</b>.</p>"
                        + "<p><a href=\"" + esc(link) + "\">Choose your password</a> "
                        + "(valid for " + ttlHours + " hours, single use).</p>");
        return new OutboundMail(to, "Your CampusQuest invitation", text, html);
    }

    private static String layout(String title, String body) {
        return "<!doctype html><html><body style=\"font-family:system-ui,sans-serif;background:#f5f5f7;padding:24px\">"
                + "<div style=\"max-width:520px;margin:auto;background:#fff;border-radius:12px;padding:24px\">"
                + "<h2 style=\"margin-top:0\">" + esc(title) + "</h2>" + body
                + "</div></body></html>";
    }

    private static String esc(String value) {
        return HtmlUtils.htmlEscape(value == null ? "" : value);
    }
}
