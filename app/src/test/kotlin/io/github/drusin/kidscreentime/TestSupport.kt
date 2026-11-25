package io.github.drusin.kidscreentime

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.job
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.junit.rules.TestWatcher
import org.junit.runner.Description

/**
 * Installs the test `Dispatchers.Main` once per JVM and never resets it. A `setMain`/`resetMain` pair per test races with
 * coroutines of an earlier test that still dispatch to Main (a `viewModelScope` still resuming from a Room or HTTP thread):
 * "Dispatchers.Main is used concurrently with setting it". Nothing writes the Main dispatcher after the first call, so
 * there is nothing to race with. Use as `@get:Rule val main = MainDispatcherRule()` instead of `setMain`/`resetMain`.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class MainDispatcherRule : TestWatcher() {
    private val viewModels = java.util.concurrent.CopyOnWriteArrayList<ViewModel>()

    override fun starting(description: Description) = TestMain.install()

    /**
     * Registers a ViewModel built by the test. Its `viewModelScope` is cancelled and joined at the end of the test
     * (or by [cancelViewModels]), so a coroutine like the last sync of `AlarmViewModel.stop` cannot run on into the next
     * test, or into a closed Room database, and report an uncaught exception there.
     */
    fun <T : ViewModel> track(viewModel: T): T = viewModel.also { viewModels += it }

    /** Call before closing what the ViewModels use (the Room database). */
    fun cancelViewModels() = runBlocking { viewModels.forEach { it.viewModelScope.coroutineContext.job.cancelAndJoin() } }

    /** The end of the test: ViewModels first, then every `TestContainer` (its scope and its Room database), never the other way round. */
    override fun finished(description: Description) {
        cancelViewModels()
        io.github.drusin.kidscreentime.ui.OpenTestContainers.closeAll()
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
private object TestMain {
    init {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    fun install() = Unit
}

/** Failure bound for [waitFor]. It is only reached when the condition is wrong: success returns at once, so it costs nothing. */
const val WAIT_LIMIT_MILLIS = 30_000L

/**
 * Waits in real time until [condition] holds, for background work that really runs on other threads (Room, the HTTP round
 * trip, the Alarmkern observer). Always polls on `Dispatchers.Default`, so it is real time inside `runTest` and
 * `testApplication` alike. The failure names [what]. Never use it to wait for the passing of time: advance the `ManualClock`.
 * Only wait for a state that stays true once reached (a planned alarm that a later event replaces is not one): assert
 * moments that pass at once with a direct call (`replan()`, `trigger()`), then wait only for what follows from it.
 */
suspend fun waitFor(what: String = "Bedingung", condition: suspend () -> Boolean) {
    try {
        withContext(Dispatchers.Default) { withTimeout(WAIT_LIMIT_MILLIS) { while (!condition()) delay(10) } }
    } catch (e: kotlinx.coroutines.TimeoutCancellationException) {
        throw AssertionError("Nach ${WAIT_LIMIT_MILLIS} ms nicht eingetreten: $what", e)
    }
}
