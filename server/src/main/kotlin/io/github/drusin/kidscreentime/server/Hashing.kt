package io.github.drusin.kidscreentime.server

import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64

/** SHA-256 of secrets we store (Geräte-Token, Kopplungscodes). The inputs are random, so no salt or slow hash is needed. */
internal fun sha256Hex(value: String): String =
    MessageDigest.getInstance("SHA-256").digest(value.toByteArray()).joinToString("") { "%02x".format(it) }

/** Constant-time comparison of two secrets. */
internal fun secretsEqual(a: String, b: String): Boolean =
    MessageDigest.isEqual(sha256Hex(a).toByteArray(), sha256Hex(b).toByteArray())

/** A random 256-bit Geräte-Token, URL-safe Base64 without padding. */
internal fun newDeviceToken(random: SecureRandom): String =
    ByteArray(32).also(random::nextBytes).let { Base64.getUrlEncoder().withoutPadding().encodeToString(it) }
