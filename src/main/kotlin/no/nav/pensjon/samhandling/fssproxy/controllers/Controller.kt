package no.nav.pensjon.samhandling.fssproxy.controllers

import jakarta.servlet.http.HttpServletRequest
import no.nav.pensjon.samhandling.fssproxy.service.PenClientException
import no.nav.pensjon.samhandling.fssproxy.service.PenResponse
import no.nav.pensjon.samhandling.fssproxy.service.PenServerException
import no.nav.pensjon.samhandling.fssproxy.service.PenService
import org.slf4j.LoggerFactory
import org.springframework.http.HttpHeaders
import org.springframework.http.ResponseEntity
import org.springframework.http.ResponseEntity.BodyBuilder
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.context.request.WebRequest
import java.util.*

open class Controller(private val penService: PenService) {
    open fun get(request: HttpServletRequest) =
        penService.get("${request.requestURI}${request.queryStringOrEmpty()}", request.requestHeaders())
            .let { response ->
                ResponseEntity.status(response.statusCode).addResponseHeaders(response).body(response.body)
            }

    open fun post(request: HttpServletRequest, @RequestBody body: String) =
        penService.post(
            path = "${request.requestURI}${request.queryStringOrEmpty()}",
            headers = request.requestHeaders(),
            body = body)
            .let { response ->
                ResponseEntity.status(response.statusCode).addResponseHeaders(response).body(response.body)
            }

    private fun BodyBuilder.addResponseHeaders(response: PenResponse): BodyBuilder =
        this.apply {
            includedResponseHeaders.forEach { includedHeader ->
                response.headers[includedHeader]?.let { listOfHeaders ->
                    this.header(includedHeader, *listOfHeaders.toTypedArray())
                }
            }
        }

    private fun HttpServletRequest.queryStringOrEmpty() = this.queryString?.let { "?$it" } ?: ""

    private fun HttpServletRequest.requestHeaders() =
        this.headerNames.toList()
            .filter { !excludedRequestHeaders.contains(it.lowercase(locale)) }
            .associateWith { this.getHeaders(it).toList() }

    @ExceptionHandler(PenClientException::class)
    fun handleProxyClientErrorException(e: PenClientException, request: WebRequest): ResponseEntity<Any> {
        logger.info("${e.statusCode} fra pen: ${if(e.body.trim().isNotEmpty()) e.body else "ingen body"}")
        return ResponseEntity.status(e.statusCode).body(e.body)
    }

    @ExceptionHandler(PenServerException::class)
    fun handleProxyServerErrorException(e: PenServerException, request: WebRequest): ResponseEntity<Any> {
        logger.warn("${e.statusCode} fra pen: ${if(e.body.trim().isNotEmpty()) e.body else "ingen body"}")
        return ResponseEntity.internalServerError().build()
    }

    companion object {
        private val logger = LoggerFactory.getLogger(Controller::class.java)
        private val locale = Locale.getDefault()
        private val excludedRequestHeaders = listOf(
            HttpHeaders.ACCEPT.lowercase(locale),
            HttpHeaders.AUTHORIZATION.lowercase(locale),
            HttpHeaders.HOST.lowercase(locale),
            HttpHeaders.USER_AGENT.lowercase(locale),
            HttpHeaders.CONTENT_LENGTH.lowercase(locale),
        )
        private val includedResponseHeaders = listOf(
            HttpHeaders.CONTENT_TYPE
        )
    }
}