package no.nav.pensjon.samhandling.fssproxy.config

import ch.qos.logback.access.tomcat.LogbackValve
import org.springframework.boot.web.embedded.tomcat.TomcatServletWebServerFactory
import org.springframework.boot.web.server.WebServerFactoryCustomizer
import org.springframework.stereotype.Component

@Component
class TomcatAccessLogCustomizer : WebServerFactoryCustomizer<TomcatServletWebServerFactory> {
    override fun customize(factory: TomcatServletWebServerFactory) = factory.addEngineValves(
        LogbackValve().apply {
            name = "Logback Access"
            filename = "logback-access.xml"
        }
    )
}
