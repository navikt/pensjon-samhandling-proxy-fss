package no.nav.pensjon.samhandling.fssproxy.controllers

import jakarta.servlet.http.HttpServletRequest
import no.nav.pensjon.samhandling.fssproxy.service.PenService
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("folketrygdbeholdning")
class FolketrygdbeholdningController(penService: PenService) : Controller(penService) {

    @GetMapping(path = ["beregning"])
    fun beregnFolketrygdbeholdning(request: HttpServletRequest) = super.get(request)
}