package no.nav.pensjon.samhandling.fssproxy.service

import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatusCode
import org.springframework.stereotype.Component
import org.springframework.web.client.HttpClientErrorException
import org.springframework.web.client.HttpServerErrorException
import org.springframework.web.client.RestTemplate
import org.springframework.web.util.UriComponentsBuilder

@Component
class PenService(
    private val restTemplate: RestTemplate,
    @Value("\${PEN_URL}") private val penUrl : String)
{
    fun get(path: String, headers: Map<String, List<String>>): PenResponse =
        try {
            restTemplate.exchange(
                UriComponentsBuilder.fromUriString("$penUrl$path").build().toUri(),
                HttpMethod.GET,
                HttpEntity<Unit>(headers.toHttpHeaders()),
                String::class.java
            ).let {
                PenResponse(it.statusCode, it.body, it.headers)
            }
        }
        catch(e: HttpClientErrorException) {
            throw PenClientException(e.responseBodyAsString, e.statusCode)
        }
        catch (e: HttpServerErrorException) {
            throw PenServerException(e.responseBodyAsString, e.statusCode)
        }

    fun post(path: String, headers: Map<String, List<String>>, body: String): PenResponse =
        try {
            restTemplate.exchange(
                UriComponentsBuilder.fromUriString("$penUrl$path").build().toUri(),
                HttpMethod.POST,
                HttpEntity<String>(body, headers.toHttpHeaders()),
                String::class.java
            ).let {
                PenResponse(it.statusCode, it.body, it.headers)
            }
        }
        catch(e: HttpClientErrorException) {
            throw PenClientException(e.responseBodyAsString, e.statusCode)
        }
        catch (e: HttpServerErrorException) {
            throw PenServerException(e.responseBodyAsString, e.statusCode)
        }

    private fun Map<String, List<String>>.toHttpHeaders() =
        HttpHeaders().also { httpHeaders ->
            this.entries.forEach {
                httpHeaders.addAll(it.key, it.value)
            }
    }
}

data class PenResponse(val statusCode: HttpStatusCode, val body: String?, val headers: HttpHeaders)
class PenClientException(val body: String, val statusCode: HttpStatusCode) : Exception(body)
class PenServerException(val body: String, val statusCode: HttpStatusCode) : Exception(body)