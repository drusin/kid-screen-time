package io.github.drusin.kidscreentime.server

import io.github.drusin.kidscreentime.api.DeviceRole
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.html.respondHtml
import io.ktor.server.request.receiveParameters
import io.ktor.server.response.header
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import java.time.Instant
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.html.FormMethod
import kotlinx.html.HTML
import kotlinx.html.InputType
import kotlinx.html.body
import kotlinx.html.code
import kotlinx.html.form
import kotlinx.html.h1
import kotlinx.html.head
import kotlinx.html.input
import kotlinx.html.label
import kotlinx.html.lang
import kotlinx.html.meta
import kotlinx.html.p
import kotlinx.html.style
import kotlinx.html.title

const val ADMIN_PATH = "/admin"
const val ADMIN_PARENT_CODE_PATH = "/admin/parent-code"

/**
 * The Admin-Webformular (kotlinx.html, no JS). Installed only on the admin port. It asks for the Admin-Passwort
 * on every submit (no session, no cookie) and can do exactly one thing: create a Kopplungscode for a Elterngerät
 * (Bootstrap and Recovery).
 */
fun Route.adminRoutes(services: ServerServices) {
    get(ADMIN_PATH) {
        call.respondHtml { adminPage { passwordForm(error = null) } }
    }

    post(ADMIN_PARENT_CODE_PATH) {
        if (services.rateLimiter.isBlocked()) {
            call.respondHtml(HttpStatusCode.TooManyRequests) {
                adminPage { passwordForm("Zu viele Fehlversuche. Bitte in einer Minute erneut versuchen.") }
            }
            return@post
        }
        val password = call.receiveParameters()["password"].orEmpty()
        if (!secretsEqual(password, services.config.adminPassword)) {
            services.rateLimiter.recordFailure()
            call.respondHtml(HttpStatusCode.Unauthorized) { adminPage { passwordForm("Falsches Passwort.") } }
            return@post
        }
        val created = services.pairing.createCode(DeviceRole.PARENT)
        val validUntil = DateTimeFormatter.ofPattern("HH:mm", Locale.GERMANY)
            .withZone(services.config.familyZone)
            .format(Instant.ofEpochMilli(created.expiresAtEpochMillis))
        call.response.header(HttpHeaders.CacheControl, "no-store")
        call.respondHtml {
            adminPage {
                p { +"Kopplungscode für ein Elterngerät:" }
                p { code { style = "font-size: 2em"; +created.code } }
                p { +"Gültig bis $validUntil Uhr, einmal verwendbar." }
                p { +"Server-URL für die App: ${services.config.publicServerUrl}" }
            }
        }
    }
}

private fun HTML.adminPage(content: kotlinx.html.FlowContent.() -> Unit) {
    lang = "de"
    head {
        meta(charset = "utf-8")
        meta(name = "viewport", content = "width=device-width, initial-scale=1")
        title("Bildschirmzeit Admin")
    }
    body {
        h1 { +"Bildschirmzeit Admin" }
        content()
    }
}

private fun kotlinx.html.FlowContent.passwordForm(error: String?) {
    if (error != null) p { +error }
    form(action = ADMIN_PARENT_CODE_PATH, method = FormMethod.post) {
        p {
            label { +"Admin-Passwort " }
            input(type = InputType.password, name = "password") { required = true }
        }
        input(type = InputType.submit) { value = "Kopplungscode für Elterngerät erzeugen" }
    }
}
