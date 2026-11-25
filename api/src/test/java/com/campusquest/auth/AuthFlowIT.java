package com.campusquest.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.campusquest.cohort.Cohort;
import com.campusquest.support.IntegrationTest;
import com.campusquest.user.Role;
import com.campusquest.user.User;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.PlainJWT;
import com.nimbusds.jwt.SignedJWT;
import jakarta.servlet.http.Cookie;
import java.security.KeyPairGenerator;
import java.time.Instant;
import java.util.Date;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

class AuthFlowIT extends IntegrationTest {

    private static final Pattern REFRESH_COOKIE = Pattern.compile("cq_refresh=([^;]*)");

    @Test
    void signupVerifyLoginRefreshAndReuseDetection() throws Exception {
        Cohort cohort = cohort();
        String email = email();

        register(email, cohort).andExpect(status().isAccepted());
        String code = mail.awaitVerificationCode(email);

        // Not verified yet: correct password reveals the state, nothing more.
        login(email, PASSWORD).andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("EMAIL_NOT_VERIFIED"));

        verify(email, code).andExpect(status().isNoContent());

        MvcResult loginResult = login(email, PASSWORD).andExpect(status().isOk())
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.user.role").value("STUDENT"))
                .andExpect(jsonPath("$.user.cohort.code").value(cohort.getCode()))
                .andExpect(jsonPath("$.refreshToken").doesNotExist())
                .andReturn();
        String setCookie = loginResult.getResponse().getHeader(HttpHeaders.SET_COOKIE);
        assertThat(setCookie).contains("HttpOnly", "SameSite=Strict", "Path=/api/v1/auth", "Secure");
        String access = jsonString(loginResult, "$.accessToken");
        String refresh1 = refreshCookie(loginResult);

        get(access, "/api/v1/me").andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(email))
                .andExpect(jsonPath("$.progression.level").value(0))
                .andExpect(jsonPath("$.progression.challengeSlotsMax").value(1));

        MvcResult rotated = refresh(refresh1).andExpect(status().isOk()).andReturn();
        String refresh2 = refreshCookie(rotated);
        assertThat(refresh2).isNotBlank().isNotEqualTo(refresh1);

        // Replaying a rotated token is treated as theft: the whole family dies, including the fresh token.
        refresh(refresh1).andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_REFRESH_TOKEN"));
        refresh(refresh2).andExpect(status().isUnauthorized());
    }

    @Test
    void logoutEndsTheSession() throws Exception {
        User user = user(Role.STUDENT);
        MvcResult result = login(user.getEmail(), PASSWORD).andExpect(status().isOk()).andReturn();
        String refresh = refreshCookie(result);

        mvc.perform(cookieRequest("/api/v1/auth/logout", refresh, true))
                .andExpect(status().isNoContent())
                .andExpect(header().string(HttpHeaders.SET_COOKIE, org.hamcrest.Matchers.containsString("Max-Age=0")));
        refresh(refresh).andExpect(status().isUnauthorized());
    }

    @Test
    void registeringAnExistingEmailLooksExactlyLikeANewSignup() throws Exception {
        Cohort cohort = cohort();
        User existing = student(cohort);

        String fresh = body(register(email(), cohort).andExpect(status().isAccepted()));
        String duplicate = body(register(existing.getEmail(), cohort).andExpect(status().isAccepted()));

        assertThat(duplicate).isEqualTo(fresh);
        assertThat(mail.awaitMail(existing.getEmail(), "already have").text()).contains("already have one");
    }

    @Test
    void verificationCodesBurnAfterTooManyWrongGuesses() throws Exception {
        Cohort cohort = cohort();
        String email = email();
        register(email, cohort).andExpect(status().isAccepted());
        String code = mail.awaitVerificationCode(email);
        String wrong = code.equals("000000") ? "111111" : "000000";

        for (int i = 0; i < 5; i++) {
            verify(email, wrong).andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_CODE"));
        }
        verify(email, code).andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_CODE"));
    }

    @Test
    void verificationErrorsDoNotRevealUnknownEmails() throws Exception {
        String unknown = body(verify(email(), "123456").andExpect(status().isBadRequest()));
        Cohort cohort = cohort();
        String email = email();
        register(email, cohort).andExpect(status().isAccepted());
        mail.awaitVerificationCode(email);
        String known = body(verify(email, "999999").andExpect(status().isBadRequest()));
        assertThat(jsonOf(known, "$.code")).isEqualTo(jsonOf(unknown, "$.code"));
        assertThat(jsonOf(known, "$.detail")).isEqualTo(jsonOf(unknown, "$.detail"));
    }

    @Test
    void loginErrorsAreIdenticalForUnknownAccountsAndWrongPasswords() throws Exception {
        User user = user(Role.TEACHER);
        String unknown = body(login(email(), PASSWORD).andExpect(status().isUnauthorized()));
        String wrongPassword = body(login(user.getEmail(), "not-the-password-123").andExpect(status().isUnauthorized()));
        assertThat(jsonOf(unknown, "$.code")).isEqualTo("INVALID_CREDENTIALS").isEqualTo(jsonOf(wrongPassword, "$.code"));
        assertThat(jsonOf(unknown, "$.detail")).isEqualTo(jsonOf(wrongPassword, "$.detail"));
    }

    @Test
    void repeatedLoginFailuresAreThrottledPerAccount() throws Exception {
        User user = user(Role.STUDENT);
        for (int i = 0; i < 5; i++) {
            login(user.getEmail(), "wrong-password-" + i).andExpect(status().isUnauthorized());
        }
        // Even the right password is refused until the window passes; a new IP doesn't help.
        login(user.getEmail(), PASSWORD).andExpect(status().isTooManyRequests())
                .andExpect(header().exists(HttpHeaders.RETRY_AFTER))
                .andExpect(jsonPath("$.code").value("RATE_LIMITED"));
    }

    @Test
    void passwordResetRevokesEveryExistingSession() throws Exception {
        User user = user(Role.STAFF);
        MvcResult session = login(user.getEmail(), PASSWORD).andExpect(status().isOk()).andReturn();
        String access = jsonString(session, "$.accessToken");
        String refresh = refreshCookie(session);

        post(null, "/api/v1/auth/password/forgot", "{\"email\":\"" + user.getEmail() + "\"}")
                .andExpect(status().isAccepted());
        String resetToken = mail.awaitLinkToken(user.getEmail(), "Reset your");
        String newPassword = "a-brand-new-passphrase";
        post(null, "/api/v1/auth/password/reset",
                "{\"token\":\"" + resetToken + "\",\"newPassword\":\"" + newPassword + "\"}")
                .andExpect(status().isNoContent());

        get(access, "/api/v1/me").andExpect(status().isUnauthorized());
        refresh(refresh).andExpect(status().isUnauthorized());
        login(user.getEmail(), PASSWORD).andExpect(status().isUnauthorized());
        login(user.getEmail(), newPassword).andExpect(status().isOk());
        // Single use.
        post(null, "/api/v1/auth/password/reset",
                "{\"token\":\"" + resetToken + "\",\"newPassword\":\"yet-another-passphrase\"}")
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_TOKEN"));
    }

    @Test
    void invitedUsersChooseTheirOwnPassword() throws Exception {
        String admin = token(user(Role.ADMIN));
        String email = email();
        String created = body(post(admin, "/api/v1/admin/users", """
                {"email":"%s","firstName":"Grace","lastName":"Hopper","role":"TEACHER","subject":"Compilers"}
                """.formatted(email)).andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("INVITED")));
        assertThat(created).doesNotContainIgnoringCase("password").doesNotContain("$argon2");

        login(email, PASSWORD).andExpect(status().isUnauthorized());
        String inviteToken = mail.awaitLinkToken(email, "invitation");
        post(null, "/api/v1/auth/password/reset",
                "{\"token\":\"" + inviteToken + "\",\"newPassword\":\"grace-chooses-this-one\"}")
                .andExpect(status().isNoContent());
        login(email, "grace-chooses-this-one").andExpect(status().isOk())
                .andExpect(jsonPath("$.user.role").value("TEACHER"))
                .andExpect(jsonPath("$.user.subject").value("Compilers"));
    }

    @Test
    void disablingAnAccountLocksItOutImmediately() throws Exception {
        User student = user(Role.STUDENT);
        String studentToken = token(student);
        get(studentToken, "/api/v1/me").andExpect(status().isOk());

        post(token(user(Role.ADMIN)), "/api/v1/admin/users/{id}/disable", null, student.getId())
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("DISABLED"));

        get(studentToken, "/api/v1/me").andExpect(status().isUnauthorized());
        login(student.getEmail(), PASSWORD).andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCOUNT_DISABLED"));
    }

    @Test
    void forgedUnsignedAndMalformedTokensAreRejected() throws Exception {
        User user = user(Role.ADMIN);
        JWTClaimsSet claims = new JWTClaimsSet.Builder()
                .issuer("campusquest").audience("campusquest-api").subject(user.getId().toString())
                .claim("tv", 0).claim("role", "ADMIN")
                .issueTime(new Date()).expirationTime(Date.from(Instant.now().plusSeconds(600))).build();

        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        SignedJWT foreign = new SignedJWT(new JWSHeader(JWSAlgorithm.RS256), claims);
        foreign.sign(new RSASSASigner(generator.generateKeyPair().getPrivate()));

        for (String forged : new String[] {foreign.serialize(), new PlainJWT(claims).serialize(), "not.a.jwt"}) {
            get(forged, "/api/v1/admin/users").andExpect(status().isUnauthorized())
                    .andExpect(header().exists(HttpHeaders.WWW_AUTHENTICATE));
        }
    }

    @Test
    void cookieEndpointsRequireTheCsrfHeader() throws Exception {
        User user = user(Role.STUDENT);
        mvc.perform(MockMvcRequestBuilders.post("/api/v1/auth/login").with(fromIp(randomIp()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + user.getEmail() + "\",\"password\":\"" + PASSWORD + "\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("CSRF_HEADER_REQUIRED"));
        mvc.perform(cookieRequest("/api/v1/auth/refresh", "anything", false))
                .andExpect(status().isForbidden());
    }

    @Test
    void unknownFieldsAreRejectedSoClientsCannotMassAssign() throws Exception {
        Cohort cohort = cohort();
        post(null, "/api/v1/auth/register", """
                {"email":"%s","password":"%s","firstName":"Eve","lastName":"Il","cohortId":"%s","role":"ADMIN"}
                """.formatted(email(), PASSWORD, cohort.getId()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MALFORMED_REQUEST"));
    }

    @Test
    void weakPasswordsAreRefused() throws Exception {
        Cohort cohort = cohort();
        post(null, "/api/v1/auth/register", """
                {"email":"%s","password":"short","firstName":"Weak","lastName":"Pass","cohortId":"%s"}
                """.formatted(email(), cohort.getId()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("WEAK_PASSWORD"));
    }

    // --- helpers ----------------------------------------------------------------------------------

    private ResultActions register(String email, Cohort cohort) throws Exception {
        return post(null, "/api/v1/auth/register", """
                {"email":"%s","password":"%s","firstName":"Lina","lastName":"Haddad","cohortId":"%s"}
                """.formatted(email, PASSWORD, cohort.getId()));
    }

    private ResultActions verify(String email, String code) throws Exception {
        return post(null, "/api/v1/auth/verify-email", "{\"email\":\"" + email + "\",\"code\":\"" + code + "\"}");
    }

    private ResultActions login(String email, String password) throws Exception {
        return mvc.perform(prepare(MockMvcRequestBuilders.post("/api/v1/auth/login"), null,
                "{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}")
                .header("X-Requested-With", "XMLHttpRequest"));
    }

    private ResultActions refresh(String refreshToken) throws Exception {
        return mvc.perform(cookieRequest("/api/v1/auth/refresh", refreshToken, true));
    }

    private MockHttpServletRequestBuilder cookieRequest(String url, String refreshToken, boolean withCsrfHeader) {
        MockHttpServletRequestBuilder request = MockMvcRequestBuilders.post(url).with(fromIp(randomIp()))
                .cookie(new Cookie("cq_refresh", refreshToken));
        return withCsrfHeader ? request.header("X-Requested-With", "XMLHttpRequest") : request;
    }

    private static String refreshCookie(MvcResult result) {
        Matcher matcher = REFRESH_COOKIE.matcher(String.valueOf(result.getResponse().getHeader(HttpHeaders.SET_COOKIE)));
        assertThat(matcher.find()).as("refresh cookie set").isTrue();
        return matcher.group(1);
    }

    private static String jsonString(MvcResult result, String path) throws Exception {
        return jsonOf(result.getResponse().getContentAsString(), path);
    }

    private static String jsonOf(String json, String path) {
        Object value = com.jayway.jsonpath.JsonPath.read(json, path);
        return value == null ? null : value.toString();
    }
}
