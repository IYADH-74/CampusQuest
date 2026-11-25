package com.campusquest.support;

import static org.awaitility.Awaitility.await;

import com.campusquest.mail.MailGateway;
import com.campusquest.mail.OutboundMail;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Captures outbound mail instead of sending it. Mail is async, so lookups poll. */
public class RecordingMailGateway implements MailGateway {

    private static final Pattern CODE = Pattern.compile("verification code is: (\\d{6})");
    private static final Pattern LINK_TOKEN = Pattern.compile("token=([A-Za-z0-9_\\-%]+)");

    private final List<OutboundMail> sent = new CopyOnWriteArrayList<>();

    @Override
    public void send(OutboundMail mail) {
        sent.add(mail);
    }

    public OutboundMail awaitMail(String to, String subjectFragment) {
        return await().atMost(Duration.ofSeconds(10)).until(() -> latest(to, subjectFragment), Optional::isPresent).get();
    }

    public List<OutboundMail> sentTo(String to) {
        return sent.stream().filter(m -> m.to().equals(to)).toList();
    }

    public String awaitVerificationCode(String to) {
        return extract(CODE, awaitMail(to, "verification code").text());
    }

    public String awaitLinkToken(String to, String subjectFragment) {
        return extract(LINK_TOKEN, awaitMail(to, subjectFragment).text());
    }

    private Optional<OutboundMail> latest(String to, String subjectFragment) {
        return sent.reversed().stream()
                .filter(m -> m.to().equals(to) && m.subject().contains(subjectFragment))
                .findFirst();
    }

    private static String extract(Pattern pattern, String text) {
        Matcher matcher = pattern.matcher(text);
        if (!matcher.find()) {
            throw new AssertionError("Pattern " + pattern + " not found in mail: " + text);
        }
        return java.net.URLDecoder.decode(matcher.group(1), java.nio.charset.StandardCharsets.UTF_8);
    }
}
