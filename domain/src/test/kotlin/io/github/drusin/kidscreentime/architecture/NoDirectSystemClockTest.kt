package io.github.drusin.kidscreentime.architecture

import com.lemonappdev.konsist.api.Konsist
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Architecture test: production code in every module must not call the system clock directly.
 * Time comes from the injected WallClock and MonotonicClock. Only the real clock implementations
 * listed in [ALLOWED_FILES] may use the system clock.
 *
 * The repo root comes from the system property `kidscreentime.repoRoot` (set in the root build.gradle.kts).
 */
class NoDirectSystemClockTest {

    @Test
    fun `Produktivcode ruft die Systemuhr nicht direkt auf`() {
        val violations = productionFiles()
            .filterNot { file -> ALLOWED_FILES.any { file.path.replace('\\', '/').endsWith(it) } }
            .flatMap { file -> findSystemClockCalls(file.text).map { "${file.path}: $it" } }

        assertTrue(
            violations.isEmpty(),
            "Direkte Systemuhr-Aufrufe im Produktivcode (stattdessen WallClock/MonotonicClock injizieren):\n" +
                violations.joinToString("\n"),
        )
    }

    @Test
    fun `Der Test sieht den Produktivcode aller vier Module`() {
        val paths = productionFiles().map { it.path.replace('\\', '/') }
        MODULES.forEach { module ->
            assertTrue(paths.any { "/$module/src/main/" in it }, "Keine Produktivdateien in Modul $module gefunden")
        }
    }

    @Test
    fun `Erlaubte Dateien existieren noch`() {
        ALLOWED_FILES.forEach { allowed ->
            assertTrue(File(repoRoot, allowed).exists(), "Ausnahme in der Allow-List zeigt auf eine fehlende Datei: $allowed")
        }
    }

    @Test
    fun `Regel erkennt typische Systemuhr-Aufrufe`() {
        val calls = listOf(
            "System.currentTimeMillis()",
            "System.nanoTime()",
            "Instant.now()",
            "LocalDate.now(zone)",
            "LocalDateTime.now()",
            "ZonedDateTime.now()",
            "Clock.System.now()",
            "Clock.systemUTC()",
            "SystemClock.elapsedRealtime()",
            "TimeSource.Monotonic.markNow()",
            "ZoneId.systemDefault()",
            "TimeZone.currentSystemDefault()",
            "Calendar.getInstance()",
            "java.util.Date()",
            "measureTime { }",
        )
        calls.forEach { call ->
            assertEquals(1, findSystemClockCalls("val x = $call").size, "Nicht erkannt: $call")
        }
    }

    @Test
    fun `Regel ignoriert Kommentare und injizierte Uhren`() {
        val source = """
            // System.currentTimeMillis() steht nur im Kommentar
            /** Instant.now() ebenfalls */
            /* Clock.System.now() */
            val now = clock.now()
            val elapsed = monotonic.elapsed()
        """.trimIndent()

        assertEquals(emptyList(), findSystemClockCalls(source))
    }

    private fun productionFiles() =
        MODULES.flatMap { module ->
            listOf("src/main/kotlin", "src/main/java").map { "$module/$it" }
        }
            .filter { File(repoRoot, it).isDirectory }
            // Konsist resolves relative paths against the project root it finds itself.
            .flatMap { Konsist.scopeFromDirectory(it).files }

    private companion object {
        val repoRoot = File(checkNotNull(System.getProperty("kidscreentime.repoRoot")) { "System property kidscreentime.repoRoot fehlt" })

        val MODULES = listOf("domain", "api", "app", "server")

        /** The real clock implementations. Keep this list as short as possible. */
        val ALLOWED_FILES = listOf(
            "domain/src/main/kotlin/io/github/drusin/kidscreentime/domain/clock/SystemClocks.kt",
            "app/src/main/kotlin/io/github/drusin/kidscreentime/clock/AndroidMonotonicClock.kt",
        )

        val FORBIDDEN = listOf(
            Regex("""\bSystem\s*\.\s*(currentTimeMillis|nanoTime)\b"""),
            Regex("""\b(Instant|LocalDate|LocalDateTime|LocalTime|OffsetDateTime|ZonedDateTime|Year|YearMonth|MonthDay)\s*\.\s*now\b"""),
            Regex("""\bClock\s*\.\s*(System|systemUTC|systemDefaultZone|systemDefault)\b"""),
            Regex("""\bSystemClock\b"""),
            Regex("""\bTimeSource\s*\.\s*Monotonic\b"""),
            Regex("""\b(measureTime|measureTimedValue)\b"""),
            Regex("""\bZoneId\s*\.\s*systemDefault\b"""),
            Regex("""\bTimeZone\s*\.\s*(currentSystemDefault|getDefault)\b"""),
            Regex("""\bCalendar\s*\.\s*getInstance\b"""),
            Regex("""\bDate\s*\(\s*\)"""),
        )

        private val BLOCK_COMMENT = Regex("""/\*.*?\*/""", RegexOption.DOT_MATCHES_ALL)
        private val LINE_COMMENT = Regex("""//[^\n]*""")

        fun findSystemClockCalls(source: String): List<String> {
            val code = source.replace(BLOCK_COMMENT, "").replace(LINE_COMMENT, "")
            return FORBIDDEN.flatMap { rule -> rule.findAll(code).map { it.value }.toList() }
        }
    }
}
