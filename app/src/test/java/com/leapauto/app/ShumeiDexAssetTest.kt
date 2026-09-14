package com.leapauto.app

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.MessageDigest
import java.util.zip.Adler32

class ShumeiDexAssetTest {
    private val asset = listOf(File("src/main/assets/shumei.dex"), File("app/src/main/assets/shumei.dex"))
        .firstOrNull { it.isFile } ?: error("Packaged Shumei DEX asset is missing")

    @Test
    fun `packaged dex has a valid header signature and checksum`() {
        val bytes = asset.readBytes()
        val header = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        assertArrayEquals(byteArrayOf('d'.code.toByte(), 'e'.code.toByte(), 'x'.code.toByte(), 10), bytes.copyOfRange(0, 4))
        assertEquals(bytes.size, header.getInt(32))
        val signature = MessageDigest.getInstance("SHA-1").digest(bytes.copyOfRange(32, bytes.size))
        assertArrayEquals(signature, bytes.copyOfRange(12, 32))
        val checksum = Adler32().apply { update(bytes, 12, bytes.size - 12) }.value
        assertEquals(checksum, header.getInt(8).toLong() and 0xffffffffL)
    }

    @Test
    fun `every non platform dependency in packaged dex has a definition`() {
        val tables = DexTypeTables(asset.readBytes())
        val platformPrefixes = listOf("Landroid/", "Ljava/", "Ljavax/", "Ldalvik/", "Lorg/json/", "Lorg/xml/", "Lorg/w3c/")
        val missing = tables.types.map { it.trimStart('[') }.filter { type ->
            type.startsWith("L") && platformPrefixes.none(type::startsWith) && type !in tables.definitions
        }
        assertTrue("Unresolved SDK dependencies: $missing", missing.isEmpty())
        assertTrue("Lcom/ishumei/smantifraud/SmAntiFraud;" in tables.definitions)
        assertTrue("Lcom/ishumei/smantifraud/SmAntiFraud\$SmOption;" in tables.definitions)
        assertTrue("Lcom/ishumei/smantifraud/SmAntiFraud\$IDeviceIdCallback;" in tables.definitions)
    }

    @Test
    fun `shared outline helpers cannot collide with app R8 default package classes`() {
        val tables = DexTypeTables(asset.readBytes())
        val helpers = setOf("ic3", "jc3", "q10", "ue1", "uu1", "w", "w50")
        val prefix = "Lcom/leapauto/security/shumei/compat/"
        assertEquals(helpers.map { "$prefix$it;" }.toSet(), tables.definitions.filter { it.startsWith(prefix) }.toSet())
        helpers.forEach { assertFalse("Unrelocated helper: $it", "L$it;" in tables.types) }
        assertEquals(175, tables.definitions.count { it.startsWith("Lcom/ishumei/smantifraud/") })
    }

    private class DexTypeTables(private val bytes: ByteArray) {
        private val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        val types = List(buffer.getInt(64)) { index ->
            val stringIndex = buffer.getInt(buffer.getInt(68) + index * 4)
            stringAt(buffer.getInt(buffer.getInt(60) + stringIndex * 4))
        }
        val definitions = List(buffer.getInt(96)) { index ->
            types[buffer.getInt(buffer.getInt(100) + index * 32)]
        }.toSet()

        private fun stringAt(offset: Int): String {
            var start = offset
            while ((bytes[start++].toInt() and 0x80) != 0) { }
            var end = start
            while (bytes[end].toInt() != 0) end++
            return String(bytes, start, end - start, Charsets.UTF_8)
        }
    }
}
