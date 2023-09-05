package no.nav.pensjon.samhandling.fssproxy

import no.nav.pensjon.samhandling.fssproxy.annotations.SecurityDisabled
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.test.web.client.MockRestServiceServer
import org.springframework.test.web.client.match.MockRestRequestMatchers
import org.springframework.test.web.client.response.MockRestResponseCreators
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders
import org.springframework.test.web.servlet.result.MockMvcResultMatchers
import org.springframework.web.client.RestTemplate
import java.net.URI
import java.util.stream.Stream

@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    classes = [Application::class]
)
@AutoConfigureMockMvc
//@Import(RestTemplateTestConfig::class)
@SecurityDisabled
class ProxyMappingTest(@Autowired private val mockMvc: MockMvc, @Autowired restTemplate: RestTemplate,
                       @Value("\${PEN_URL}") private val penUrl: String) {
    private val mockRestServiceServer = MockRestServiceServer.bindTo(restTemplate).build()
    private val requestBody = "test request body"
    private val responseBody = "test response body"
    private val customRequestHeaderName = "customRequestHeader"
    private val customRequestHeaderValue = "request header value"
    private val notForwardedHeader = "notFwdHeader"

    companion object {
        @JvmStatic
        @DynamicPropertySource
        fun dynamicProperties(registry: DynamicPropertyRegistry) {
            registry.add("PEN_URL") { "http://pen.nav.no/pen/springapi" }
        }

        @JvmStatic
        private fun getPaths() = Stream.of(
            "folketrygdbeholdning/beregning"
        )

        @JvmStatic
        private fun postPaths() = Stream.of("afpprivat/resultat")
    }

    @ParameterizedTest
    @MethodSource("getPaths")
    fun `test gets, successful request is forwarded with correct method, header and status - body, headers and status is returned correct`(path: String) {
        mockRestServiceServer.expect(MockRestRequestMatchers.requestTo(URI("$penUrl/${path}")))
            .andExpect(MockRestRequestMatchers.method(HttpMethod.GET))
            .andExpect(MockRestRequestMatchers.header(customRequestHeaderName, customRequestHeaderValue))
            .andExpect(MockRestRequestMatchers.headerDoesNotExist(HttpHeaders.AUTHORIZATION))
            .andRespond(
                MockRestResponseCreators
                    .withStatus(HttpStatus.OK)
                    .header(HttpHeaders.CONTENT_TYPE, MediaType.TEXT_PLAIN_VALUE)
                    .header(notForwardedHeader, "test")
                    .body(responseBody))

        this.mockMvc
            .perform(MockMvcRequestBuilders.get("/$path")
                .header(customRequestHeaderName, customRequestHeaderValue)
                .header(HttpHeaders.AUTHORIZATION, "Bearer xyz"))
            .andExpect(MockMvcResultMatchers.status().isOk)
            .andExpect(MockMvcResultMatchers.header().stringValues(HttpHeaders.CONTENT_TYPE, MediaType.TEXT_PLAIN_VALUE))
            .andExpect(MockMvcResultMatchers.header().doesNotExist(notForwardedHeader))
            .andExpect(MockMvcResultMatchers.content().string(responseBody))

        mockRestServiceServer.verify()
    }

    @ParameterizedTest
    @MethodSource("getPaths")
    fun `test gets, client error from pen is returned as it is`(path: String) {
        mockRestServiceServer.expect(MockRestRequestMatchers.requestTo(URI("$penUrl/${path}")))
            .andExpect(MockRestRequestMatchers.method(HttpMethod.GET))
            .andRespond(MockRestResponseCreators.withStatus(HttpStatus.NOT_FOUND))

        this.mockMvc
            .perform(MockMvcRequestBuilders.get("/$path"))
            .andExpect(MockMvcResultMatchers.status().isNotFound)

        mockRestServiceServer.verify()
    }


    @ParameterizedTest
    @MethodSource("getPaths")
    fun `test gets, 5xx with body from pen gives internal server error with no body`(path: String) {
        mockRestServiceServer.expect(MockRestRequestMatchers.requestTo(URI("$penUrl/${path}")))
            .andExpect(MockRestRequestMatchers.method(HttpMethod.GET))
            .andRespond(MockRestResponseCreators.withStatus(HttpStatus.SERVICE_UNAVAILABLE).body(responseBody))

        this.mockMvc
            .perform(MockMvcRequestBuilders.get("/$path"))
            .andExpect(MockMvcResultMatchers.status().isInternalServerError)
            .andExpect(MockMvcResultMatchers.content().string(""))

        mockRestServiceServer.verify()
    }

    @Test
    fun `test header case does not matter`() {
        mockRestServiceServer.expect(MockRestRequestMatchers.requestTo(URI("$penUrl/folketrygdbeholdning/beregning")))
            .andExpect(MockRestRequestMatchers.method(HttpMethod.GET))
            .andExpect(MockRestRequestMatchers.header("Test-Custom", "test"))
            .andRespond(
                MockRestResponseCreators
                    .withStatus(HttpStatus.OK)
                    .header("cOnTeNt-TyPe", MediaType.TEXT_PLAIN_VALUE)
                    .body(responseBody))

        this.mockMvc
            .perform(MockMvcRequestBuilders.get("/folketrygdbeholdning/beregning")
                .header("tEsT-cUsToM", "test"))
            .andExpect(MockMvcResultMatchers.status().isOk)
            .andExpect(MockMvcResultMatchers.header().stringValues(HttpHeaders.CONTENT_TYPE, MediaType.TEXT_PLAIN_VALUE))
            .andExpect(MockMvcResultMatchers.content().string(responseBody))

        mockRestServiceServer.verify()
    }
}