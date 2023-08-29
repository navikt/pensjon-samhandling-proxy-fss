package no.nav.pensjon.samhandling.fssproxy

import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.test.util.TestSocketUtils
import org.springframework.test.web.client.MockRestServiceServer
import org.springframework.test.web.client.match.MockRestRequestMatchers
import org.springframework.test.web.client.response.MockRestResponseCreators
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders
import org.springframework.test.web.servlet.result.MockMvcResultMatchers
import org.springframework.web.client.RestTemplate
import java.net.URI
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.*


@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
class AzureAdResourceServerTest(@Autowired private val mockMvc: MockMvc, @Autowired restTemplate: RestTemplate, @Value("\${PEN_URL}") private val penUrl: String) {
    private val mockRestServiceServer = MockRestServiceServer.bindTo(restTemplate).build()

    companion object {
        private val azureAdServerPort = TestSocketUtils.findAvailableTcpPort()

        private val azureAdIssuer = "http://localhost:$azureAdServerPort/"
        private val azureAdTokenEndpoint = "http://localhost:$azureAdServerPort/token"
        private val trustedAzureAdAuthenticationServer =
            OAuthAuthenticationServerMock(issuer = azureAdIssuer, port = azureAdServerPort, startHttpServer = true)
        private val notTrustedAzureAdAuthenticationServer =
            OAuthAuthenticationServerMock(issuer = azureAdIssuer, startHttpServer = false)

        @JvmStatic
        @DynamicPropertySource
        fun dynamicProperties(registry: DynamicPropertyRegistry) {
            registry.add("AZURE_OPENID_CONFIG_ISSUER") { azureAdIssuer }
            registry.add("PEN_URL") { "http://pen.nav.no/pen/springapi" }
            registry.add("azure.accepted.audience") { "pensjon-samhandling-proxy-fss" }
            registry.add("AZURE_OPENID_CONFIG_TOKEN_ENDPOINT") { azureAdTokenEndpoint }
            registry.add("AZURE_OPENID_CONFIG_JWKS_URI") { "test" }
            registry.add("PEN_SCOPE") { "test" }
            registry.add("AZURE_APP_CLIENT_ID") { "test" }
            registry.add("AZURE_APP_CLIENT_SECRET") { "test" }

        }

        @JvmStatic
        @AfterAll
        fun afterAll() {
            trustedAzureAdAuthenticationServer.stopHttpServer()
        }
    }

    @Test
    fun `correctly signed jwt with random role accessing endpoint without specific role has access`() {
        mockRestServiceServer.expect(MockRestRequestMatchers.requestTo(URI("$penUrl/ping")))
            .andExpect(MockRestRequestMatchers.method(HttpMethod.GET))
            .andRespond(MockRestResponseCreators.withStatus(HttpStatus.OK))

        this.mockMvc
            .perform(
                MockMvcRequestBuilders.get("/ping/deep").header(
                    "Authorization", "Bearer ${
                        trustedAzureAdAuthenticationServer.generateClientJWT(
                            audience = "pensjon-samhandling-proxy-fss", roles = listOf("test"))
                    }"
                )
            )
            .andExpect(MockMvcResultMatchers.status().isOk)
    }

    @Test
    fun `correctly signed jwt with matching role has access`() {
        mockRestServiceServer.expect(MockRestRequestMatchers.requestTo(URI("$penUrl/folketrygdbeholdning/beregning")))
            .andExpect(MockRestRequestMatchers.method(HttpMethod.GET))
            .andRespond(MockRestResponseCreators.withStatus(HttpStatus.OK))

        this.mockMvc
            .perform(
                MockMvcRequestBuilders.get("/folketrygdbeholdning/beregning").header(
                    "Authorization", "Bearer ${
                        trustedAzureAdAuthenticationServer.generateClientJWT(
                            audience = "pensjon-samhandling-proxy-fss", roles = listOf("folketrygdbeholdning"))
                    }"
                )
            )
            .andExpect(MockMvcResultMatchers.status().isOk)
    }


    @Test
    fun `correctly signed jwt with no matching role is forbidden`() {
        this.mockMvc
            .perform(
                MockMvcRequestBuilders.get("/folketrygdbeholdning/beregning").header(
                    "Authorization", "Bearer ${
                        trustedAzureAdAuthenticationServer.generateClientJWT(
                            audience = "pensjon-samhandling-proxy-fss", roles = listOf("no-match"))
                    }"
                )
            )
            .andExpect(MockMvcResultMatchers.status().isForbidden)
    }

    @Test
    fun `correctly signed jwt with matching scope but no matching group is forbidden`() {
        this.mockMvc
            .perform(
                MockMvcRequestBuilders.get("/folketrygdbeholdning/beregning").header(
                    "Authorization", "Bearer ${
                        trustedAzureAdAuthenticationServer.generateClientJWT(
                            audience = "pensjon-samhandling-proxy-fss", scopes = listOf("samhandling"), groups = listOf("no-match"))
                    }"
                )
            )
            .andExpect(MockMvcResultMatchers.status().isForbidden)
    }

    @Test
    fun `correctly signed jwt with no matching role or scope is forbidden`() {
        this.mockMvc
            .perform(
                MockMvcRequestBuilders.get("/folketrygdbeholdning/beregning").header(
                    "Authorization", "Bearer ${
                        trustedAzureAdAuthenticationServer.generateClientJWT(
                            audience = "pensjon-samhandling-proxy-fss", scopes = listOf("no-match"), 
                            groups = listOf("no-match"))
                    }"
                )
            )
            .andExpect(MockMvcResultMatchers.status().isForbidden)
    }

    @Test
    fun `correctly signed jwt with no role or scope is forbidden`() {
        this.mockMvc
            .perform(
                MockMvcRequestBuilders.get("/folketrygdbeholdning/beregning").header(
                    "Authorization", "Bearer ${
                        trustedAzureAdAuthenticationServer.generateClientJWT(
                            audience = "pensjon-samhandling-proxy-fss")
                    }"
                )
            )
            .andExpect(MockMvcResultMatchers.status().isForbidden)
    }

    @Test
    fun `correctly signed jwt without audience is unauthorized`() {
        this.mockMvc
            .perform(
                MockMvcRequestBuilders.get("/ping").header(
                    "Authorization", "Bearer ${
                        trustedAzureAdAuthenticationServer.generateClientJWT(
                            audience = "", roles = listOf())
                    }"
                )
            )
            .andExpect(MockMvcResultMatchers.status().isUnauthorized)
    }

    @Test
    fun `jwt with correct issuer and audience, but signed with untrusted private key, is unauthorized`() {
        this.mockMvc
            .perform(
                MockMvcRequestBuilders.get("/ping").header(
                    "Authorization", "Bearer ${
                        notTrustedAzureAdAuthenticationServer.generateClientJWT(
                            audience = "pensjon-pen-proxy-fss", roles = listOf())
                    }"
                )
            )
            .andExpect(MockMvcResultMatchers.status().isUnauthorized)
    }

    @Test
    fun `correctly signed jwt that has expired is unauthorized`() {
        this.mockMvc
            .perform(
                MockMvcRequestBuilders.get("/ping").header(
                    "Authorization", "Bearer ${
                        trustedAzureAdAuthenticationServer.generateClientJWT(
                            audience = "pensjon-pen-proxy-fss", roles = listOf(), expiration = Date(0)
                        )
                    }"
                )
            )
            .andExpect(MockMvcResultMatchers.status().isUnauthorized)
    }

    @Test
    fun `correctly signed jwt with not before time in the future is unauthorized`() {
        this.mockMvc
            .perform(
                MockMvcRequestBuilders.get("/ping").header(
                    "Authorization", "Bearer ${
                        trustedAzureAdAuthenticationServer.generateClientJWT(
                            audience = "pensjon-pen-proxy-fss", roles = listOf(), notBeforeTime = inAnHour()
                        )
                    }"
                )
            )
            .andExpect(MockMvcResultMatchers.status().isUnauthorized)
    }

    private fun inAnHour() = Date.from(LocalDateTime.now().plusHours(1).atZone(ZoneId.systemDefault()).toInstant())
}