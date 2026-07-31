package com.maik205.shoumeiplayer.player

import java.io.File
import kotlin.io.path.createTempDirectory
import kotlin.test.assertEquals
import kotlin.test.assertNull
import org.junit.Test

class SystemCaBundleTest {
    @Test
    fun `combines system certificates in stable order`() {
        val root = createTempDirectory("system-ca-").toFile()
        try {
            val source = File(root, "cacerts").apply { mkdir() }
            File(source, "b.0").writeText("CERT-B")
            File(source, "a.0").writeText("CERT-A")
            File(source, "ignored-directory").mkdir()
            val destination = File(root, "bundle.pem")

            val result = createSystemCaBundle(source, destination)

            assertEquals(destination, result)
            assertEquals("CERT-A\nCERT-B\n", destination.readText())
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun `returns null when system trust store is unavailable`() {
        val root = createTempDirectory("system-ca-").toFile()
        try {
            assertNull(createSystemCaBundle(File(root, "missing"), File(root, "bundle.pem")))
        } finally {
            root.deleteRecursively()
        }
    }
}
