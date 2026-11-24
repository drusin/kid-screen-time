# 01: Gerüst und CI-Gate

**What to build:** Ein lauffähiges Monorepo, auf dem alle weiteren Tickets aufbauen. Ein Gradle-Build mit den Modulen `domain`, `api`, `app` und `server`, wie in der [Spezifikation v1](../../screen-time/issues/15-spezifikation-v1.md) (Abschnitt „Gesamtarchitektur“) beschrieben. Wanduhr und monotone Uhr werden überall injiziert. Ein Architekturtest verbietet direkte Systemuhr-Aufrufe im Produktivcode. GitHub Actions führt bei jedem Push auf `main` und bei jedem PR alle JVM-Tests und Android Lint aus.

**Blocked by:** None (can start immediately)

**Status:** ready-for-agent

- [ ] `domain` und `api` sind reines Kotlin ohne Android- oder Server-Abhängigkeit, `api` nutzt kotlinx.serialization.
- [ ] `app` ist eine Compose-App (Material 3, Dynamic Color, `minSdk 33`, Package `io.github.drusin.kidscreentime`, nur Deutsch, Texte als String-Ressourcen). Der Debug-Build hat das Suffix `.debug` und heißt „Bildschirmzeit Dev“.
- [ ] `server` ist ein Ktor-Server, der als Fat-JAR baut.
- [ ] Es gibt Abstraktionen für Wanduhr und monotone Uhr, die sich im Test steuern lassen.
- [ ] Der Architekturtest (z. B. Konsist) schlägt fehl, sobald Produktivcode die Systemuhr direkt aufruft.
- [ ] Der CI-Workflow läuft bei Push auf `main` und bei PRs und ist grün.
