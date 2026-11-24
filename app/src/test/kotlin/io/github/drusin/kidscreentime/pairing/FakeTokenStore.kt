package io.github.drusin.kidscreentime.pairing

/** Test replacement for the Android Keystore (Robolectric has none). */
class FakeTokenStore(private var token: String? = null) : TokenStore {
    override fun load() = token
    override fun save(token: String) { this.token = token }
    override fun clear() { token = null }
}
