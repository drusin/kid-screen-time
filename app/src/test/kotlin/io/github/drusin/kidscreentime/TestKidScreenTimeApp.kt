package io.github.drusin.kidscreentime

/**
 * The Application of every Robolectric test (`robolectric.properties`). The real one starts a process-long alarm observer
 * on a real container (real Room file, shared `ShadowAlarmManager` and `AlarmMemory` preferences) that races with the
 * alarm of the container under test. Tests that want the observer start it on their own container and scope.
 */
class TestKidScreenTimeApp : KidScreenTimeApp() {
    override fun observeAlarm() = Unit
}
