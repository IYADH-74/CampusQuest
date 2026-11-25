package com.campusquest.user;

import com.campusquest.auth.token.OneTimeTokenService;
import com.campusquest.auth.token.TokenPurpose;
import com.campusquest.common.Text;
import com.campusquest.config.AppProperties;
import com.campusquest.mail.Mailer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * Creates the very first admin from {@code BOOTSTRAP_ADMIN_EMAIL} when no admin exists yet. The account is
 * invited, not given a password: the owner of that mailbox sets one through the normal invite link.
 */
@Component
public class AdminBootstrap implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(AdminBootstrap.class);

    private final AppProperties properties;
    private final UserRepository users;
    private final OneTimeTokenService oneTimeTokens;
    private final Mailer mailer;

    public AdminBootstrap(AppProperties properties, UserRepository users, OneTimeTokenService oneTimeTokens,
            Mailer mailer) {
        this.properties = properties;
        this.users = users;
        this.oneTimeTokens = oneTimeTokens;
        this.mailer = mailer;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        String email = Text.normalizeEmail(properties.bootstrap().adminEmail());
        if (!StringUtils.hasText(email)) {
            return;
        }
        if (users.countByRoleAndStatus(Role.ADMIN, AccountStatus.ACTIVE) > 0
                || users.countByRoleAndStatus(Role.ADMIN, AccountStatus.INVITED) > 0) {
            return;
        }
        if (users.existsByEmail(email)) {
            log.warn("Bootstrap admin email is already used by a non-admin account; not creating an admin");
            return;
        }
        User admin = users.save(User.invited(email, "Campus", "Admin", Role.ADMIN));
        mailer.sendInvite(admin, oneTimeTokens.issueLinkToken(admin, TokenPurpose.ACCOUNT_INVITE));
        log.info("Bootstrap admin invited. Check the configured mailbox for the invite link");
    }
}
