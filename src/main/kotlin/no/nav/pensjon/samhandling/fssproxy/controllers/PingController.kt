package no.nav.pensjon.samhandling.fssproxy.controllers

import org.springframework.beans.factory.annotation.Value
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.client.RestTemplate
import org.springframework.web.util.UriComponentsBuilder

@RestController
@RequestMapping("ping")
class PingController(
    @Value("\${PEN_URL}") private val penUrl : String,
    private val restTemplate: RestTemplate
) {
    @GetMapping
    fun ping() = "pong"

    @GetMapping("deep")
    fun deepPing() =
        restTemplate.getForObject(
            UriComponentsBuilder.fromUriString("$penUrl/ping").build().toUri(),
            String::class.java)
}