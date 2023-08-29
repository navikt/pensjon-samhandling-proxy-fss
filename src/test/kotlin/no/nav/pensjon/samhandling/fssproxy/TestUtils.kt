package no.nav.pensjon.samhandling.fssproxy

import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.boot.web.client.RestTemplateBuilder
import org.springframework.context.annotation.Bean
import org.springframework.http.HttpMethod
import org.springframework.web.client.RestTemplate
import org.springframework.web.util.DefaultUriBuilderFactory

data class ClientCredentialsAuthorization(var method: HttpMethod, var path: String, var roles: List<String>)
data class OnBehalfOfAuthorization(var method: HttpMethod, var path: String, var scopes: List<String>, var groups: List<String>)
data class MaskinportenAuthorization(var method: HttpMethod, var path: String, var scopes: List<String>)

@TestConfiguration
class RestTemplateTestConfig(@Value("\${PEN_URL}") private val penUrl: String) {
    @Bean("restTemplate")
    fun restTemplateWithoutOAuthInterceptor(): RestTemplate {
        return RestTemplateBuilder()
            .uriTemplateHandler(DefaultUriBuilderFactory(penUrl))
            .build()
    }
}