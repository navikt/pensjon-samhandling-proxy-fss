package no.nav.pensjon.samhandling.fssproxy.annotations

import org.springframework.boot.actuate.autoconfigure.security.servlet.ManagementWebSecurityAutoConfiguration
import org.springframework.boot.autoconfigure.EnableAutoConfiguration
import org.springframework.boot.autoconfigure.security.oauth2.client.servlet.OAuth2ClientAutoConfiguration
import org.springframework.boot.autoconfigure.security.oauth2.resource.servlet.OAuth2ResourceServerAutoConfiguration
import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration
import org.springframework.test.context.ActiveProfiles

@ActiveProfiles("disable-sec") //skipper SecurityConfig og OAuth2ClientConfig
@EnableAutoConfiguration //ekskluderer noen autoconfig-klasser
    (exclude = [ SecurityAutoConfiguration::class, OAuth2ClientAutoConfiguration::class, OAuth2ResourceServerAutoConfiguration::class, ManagementWebSecurityAutoConfiguration::class ])
annotation class SecurityDisabled