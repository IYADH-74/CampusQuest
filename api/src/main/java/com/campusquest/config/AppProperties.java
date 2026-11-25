package com.campusquest.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.net.URI;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties("campusquest")
public record AppProperties(
        @NotNull URI publicBaseUrl,
        @Valid @DefaultValue Mail mail,
        @DefaultValue Bootstrap bootstrap,
        @DefaultValue Dev dev) {

    public record Mail(@NotBlank @DefaultValue("CampusQuest <no-reply@campusquest.local>") String from) {}

    public record Bootstrap(String adminEmail) {}

    public record Dev(boolean seed) {}

    /** Builds a link into the web client, e.g. {@code /reset-password?token=...}. */
    public String link(String pathAndQuery) {
        String base = publicBaseUrl.toString();
        return (base.endsWith("/") ? base.substring(0, base.length() - 1) : base) + pathAndQuery;
    }
}
