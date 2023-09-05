package no.nav.pensjon.samhandling.fssproxy

import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.test.util.TestSocketUtils
import org.springframework.test.web.client.MockRestServiceServer
import org.springframework.test.web.client.match.MockRestRequestMatchers
import org.springframework.test.web.client.response.MockRestResponseCreators
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders
import org.springframework.test.web.servlet.result.MockMvcResultMatchers
import org.springframework.web.client.RestTemplate
import java.net.URI
import java.util.stream.Stream


@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT
)
@AutoConfigureMockMvc
//@Import(RestTemplateTestConfig::class)
class EndpointAuthorizationTest(
    @Autowired private val mockMvc: MockMvc, @Autowired restTemplate: RestTemplate,
    @Value("\${PEN_URL}") private val penUrl: String
) {
    private val mockPen = MockRestServiceServer.bindTo(restTemplate).build()

    companion object {
        private val azureAdServerPort = TestSocketUtils.findAvailableTcpPort()
        private val maskinportenServerPort = TestSocketUtils.findAvailableTcpPort()

        private val azureAdIssuer = "http://localhost:$azureAdServerPort/"
        private val trustedAzureAdAuthenticationServer =
            OAuthAuthenticationServerMock(issuer = azureAdIssuer, port = azureAdServerPort, startHttpServer = true)

        private val maskinportenIssuer = "http://localhost:$maskinportenServerPort/"
        private val trustedMaskinportenAuthenticationServer =
            OAuthAuthenticationServerMock(
                issuer = maskinportenIssuer,
                port = maskinportenServerPort,
                startHttpServer = true
            )

        private const val PEN_SCOPE = "correct_pen_scope"

        @JvmStatic
        @DynamicPropertySource
        fun dynamicProperties(registry: DynamicPropertyRegistry) {
            registry.add("MASKINPORTEN_ISSUER") { maskinportenIssuer }
            registry.add("AZURE_OPENID_CONFIG_ISSUER") { azureAdIssuer }
            registry.add("PEN_URL") { "http://pen.nav.no/pen/springapi" }
            //registry.add("azure.accepted.audience") { "pensjon-samhandling-proxy-fss" }
            registry.add("AZURE_OPENID_CONFIG_TOKEN_ENDPOINT") { "test" }
            registry.add("AZURE_OPENID_CONFIG_JWKS_URI") { "test" }
            registry.add("PEN_SCOPE") { PEN_SCOPE }
            registry.add("AZURE_APP_CLIENT_ID") { "test" }
            registry.add("AZURE_APP_CLIENT_SECRET") { "test" }

        }

        @JvmStatic
        @AfterAll
        fun afterAll() {
            trustedAzureAdAuthenticationServer.stopHttpServer()
            trustedMaskinportenAuthenticationServer.stopHttpServer()
        }

        @JvmStatic
        private fun azureAdClientCredentials() = Stream.of(
            ClientCredentialsAuthorization(HttpMethod.GET, "afpprivat/sokere", listOf("afp-privat")),
            ClientCredentialsAuthorization(HttpMethod.GET, "afpprivat/uforeperioder", listOf("afp-privat")),
            ClientCredentialsAuthorization(HttpMethod.GET, "afpprivat/vedtak", listOf("afp-privat")),
            ClientCredentialsAuthorization(HttpMethod.POST, "afpprivat/resultat", listOf("afp-privat")),
            ClientCredentialsAuthorization(HttpMethod.GET, "aap/uforeperioder", listOf("aap")),
            ClientCredentialsAuthorization(HttpMethod.GET, "aap/vedtak/1", listOf("aap")),
            ClientCredentialsAuthorization(
                HttpMethod.GET,
                "folketrygdbeholdning/beregning",
                listOf("folketrygdbeholdning")
            ),

            )

        @JvmStatic
        private fun maskinporten() = Stream.of(
            MaskinportenAuthorization(HttpMethod.GET, "vedtak/ytelse", listOf("nav:pensjon/v1/ytelse")),
            MaskinportenAuthorization(
                HttpMethod.GET,
                "vedtak/ytelsehistorikk",
                listOf("nav:pensjon/v1/ytelsehistorikk")
            ),

            )
    }


    //Client credentials
    @Test
    fun `test azure endpoint authorization with correct role, is ok`() {
        mockPen.setupOkResponse(HttpMethod.GET, "folketrygdbeholdning/beregning")

        mockMvc.perform(
            requestBuilder(
                method = HttpMethod.GET,
                path = "folketrygdbeholdning/beregning",
                createAzureAdAuthHeader(audience = "pensjon-samhandling-proxy-fss", scopes = listOf(PEN_SCOPE))
            )
        ).andExpect(MockMvcResultMatchers.status().isOk)
    }

    @Test
    fun `test azure endpoint authorization with wrong role, is forbidden`() {
        mockMvc.perform(
            requestBuilder(
                method = HttpMethod.GET,
                path = "folketrygdbeholdning/beregning",
                createAzureAdAuthHeader(audience = "pensjon-samhandling-proxy-fss", scopes = listOf("wrong_scope"))
            )
        ).andExpect(MockMvcResultMatchers.status().isForbidden)
    }

    //OBO
    @ParameterizedTest
    @MethodSource("azureAdOnBehalfOf")
    fun `test azure endpoint authorization with correct scope and group, is ok`(authorization: OnBehalfOfAuthorization) {
        mockPen.setupOkResponse(authorization.method, authorization.path)

        this.mockMvc
            .perform(
                requestBuilder(
                    method = authorization.method, path = authorization.path, createAzureAdAuthHeader(
                        audience = "pensjon-samhandling-proxy-fss", scopes = authorization.scopes,
                        groups = authorization.groups
                    )
                )
            )
            .andExpect(MockMvcResultMatchers.status().isOk)
    }

    @ParameterizedTest
    @MethodSource("azureAdOnBehalfOf")
    fun `test azure endpoint authorization with wrong scope but correct group, is forbidden`(authorization: OnBehalfOfAuthorization) {
        this.mockMvc
            .perform(
                requestBuilder(
                    method = authorization.method,
                    path = authorization.path,
                    createAzureAdAuthHeader(
                        audience = "pensjon-samhandling-proxy-fss",
                        scopes = listOf("very-wrong-scope"),
                        groups = authorization.groups
                    )
                )
            )
            .andExpect(MockMvcResultMatchers.status().isForbidden)
    }

    @ParameterizedTest
    @MethodSource("azureAdOnBehalfOf")
    fun `test azure endpoint authorization with correct scope but wrong group, is forbidden`(authorization: OnBehalfOfAuthorization) {
        this.mockMvc
            .perform(
                requestBuilder(
                    method = authorization.method,
                    path = authorization.path,
                    createAzureAdAuthHeader(
                        audience = "pensjon-samhandling-proxy-fss",
                        scopes = authorization.scopes,
                        groups = listOf("very-wrong-group")
                    )
                )
            )
            .andExpect(MockMvcResultMatchers.status().isForbidden)
    }


    //Maskinporten
    @ParameterizedTest
    @MethodSource("maskinporten")
    fun `test maskinporten endpoint authorization with correct scope is ok`(authorization: MaskinportenAuthorization) {
        mockPen.setupOkResponse(authorization.method, authorization.path)

        this.mockMvc
            .perform(
                requestBuilder(
                    method = authorization.method,
                    path = authorization.path,
                    createMaskinportenAuthHeader(scopes = authorization.scopes)
                )
            )
            .andExpect(MockMvcResultMatchers.status().isOk)
    }

    @ParameterizedTest
    @MethodSource("maskinporten")
    fun `test maskinporten endpoint authorization with wrong scope is forbidden`(authorization: MaskinportenAuthorization) {
        this.mockMvc
            .perform(
                requestBuilder(
                    method = authorization.method,
                    path = authorization.path,
                    createMaskinportenAuthHeader(scopes = listOf("very-wrong-scope"))
                )
            )
            .andExpect(MockMvcResultMatchers.status().isForbidden)
    }


    private fun MockRestServiceServer.setupOkResponse(method: HttpMethod, path: String) {
        this.apply {
            this.expect(MockRestRequestMatchers.requestTo(URI("$penUrl/$path")))
                .andExpect(MockRestRequestMatchers.method(method))
                .andRespond(
                    MockRestResponseCreators.withStatus(HttpStatus.OK)
                )
        }
    }

    private fun requestBuilder(method: HttpMethod, path: String, authHeader: String): MockHttpServletRequestBuilder {
        return MockMvcRequestBuilders.request(method, "/$path")
            .content(if (method == HttpMethod.GET) "" else "test")
            .header("Authorization", authHeader)
    }

    private fun createAzureAdAuthHeader(
        roles: List<String> = emptyList(),
        audience: String,
        scopes: List<String> = emptyList(),
        groups: List<String> = emptyList()
    ) =
        "Bearer ${
            trustedAzureAdAuthenticationServer.generateClientJWT(
                audience = audience, roles = roles, scopes = scopes, groups = groups
            )
        }"

    private fun createMaskinportenAuthHeader(
        roles: List<String> = emptyList(),
        scopes: List<String> = emptyList(),
        groups: List<String> = emptyList()
    ) =
        "Bearer ${
            trustedMaskinportenAuthenticationServer.generateClientJWT(
                roles = roles, scopes = scopes, groups = groups
            )
        }"
}