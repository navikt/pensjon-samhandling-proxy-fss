package no.nav.pensjon.samhandling.fssproxy

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication
class PensjonPenProxyFssApplication

fun main(args: Array<String>) {
    runApplication<PensjonPenProxyFssApplication>(*args)
}