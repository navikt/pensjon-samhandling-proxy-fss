package no.nav.pensjon.samhandling.fssproxy.config.security

import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.web.SecurityFilterChain


@Configuration
@EnableMethodSecurity
@Profile("!disable-sec")
class SecurityConfig(
    @Value("\${PEN_SCOPE}") private val penScope: String
) {

    @Bean
    fun filterChain(
        http: HttpSecurity
    ): SecurityFilterChain {
        return http
            .authorizeHttpRequests {
                it.requestMatchers(
                    "/actuator/health/liveness",
                    "/actuator/health/readiness",
                    "/actuator/prometheus",
                    "/ping"
                ).permitAll()
                    .anyRequest().hasAuthority("SCOPE_$penScope")
            }.oauth2ResourceServer { it.jwt {  }}.build()
    }
}