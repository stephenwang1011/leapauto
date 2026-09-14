package com.leapauto.app

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class CarModel3DManagerTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun `sanitizeKey replaces invalid characters with underscore`() {
        assertEquals("3D_702ef381_d7ed_49cd_8d69_fad6f5f9f397", CarModel3DManager.sanitizeKey("3D-702ef381-d7ed-49cd-8d69-fad6f5f9f397"))
        assertEquals("test_model_v1", CarModel3DManager.sanitizeKey("test/model:v1"))
        assertEquals("abc123XYZ", CarModel3DManager.sanitizeKey("abc123XYZ"))
    }

    @Test
    fun `isModelReady returns false for non-existent or incomplete directory`() {
        assertFalse(CarModel3DManager.isModelReady(tempFolder.root, ""))
        assertFalse(CarModel3DManager.isModelReady(tempFolder.root, "not_exist_key"))

        val dir = File(tempFolder.root, "CarModel3D/test_key")
        dir.mkdirs()
        assertFalse(File(dir, ".ready").exists())

        // Only .ready but no index.html
        File(dir, ".ready").createNewFile()
        assertFalse(File(dir, "index.html").exists())
    }

    @Test
    fun `vehicle picture meta extracts h5Key and modelParam correctly`() {
        val json = JSONObject(
            """
            {
              "h5Key": "3D-702ef381-d7ed-49cd-8d69-fad6f5f9f397",
              "srcKey": "3D-d3e0fbce-0755-441e-8381-7512fd49bc70",
              "shareBindUrl": "https://cdn.leapmotor.com/car.png",
              "modelParam": {
                "carType": "C16",
                "year": 2026,
                "carTypeCode": "630激光雷达智尊版 6座",
                "colorCode": 3
              }
            }
            """.trimIndent()
        )
        val meta = VehiclePictureMeta(
            pictureKey = "key_1",
            shareBindUrl = "https://cdn.leapmotor.com/car.png",
            rawData = json
        )

        assertEquals("3D-702ef381-d7ed-49cd-8d69-fad6f5f9f397", meta.h5Key)
        assertEquals("3D-d3e0fbce-0755-441e-8381-7512fd49bc70", meta.srcKey)
        assertNotNull(meta.modelParam)
        assertEquals("C16", meta.modelParam?.optString("carType"))
        assertEquals(2026, meta.modelParam?.optInt("year"))
        assertEquals(3, meta.modelParam?.optInt("colorCode"))
    }

    @Test
    fun `dual package merge extraction creates merged folder with index html and android assets`() {
        val h5ZipFile = tempFolder.newFile("h5_runtime.zip")
        ZipOutputStream(FileOutputStream(h5ZipFile)).use { zos ->
            zos.putNextEntry(ZipEntry("index.html"))
            zos.write("<html><head><title>3D</title></head></html>".toByteArray())
            zos.closeEntry()

            zos.putNextEntry(ZipEntry("js/three.js"))
            zos.write("// ThreeJS".toByteArray())
            zos.closeEntry()
        }

        val srcZipFile = tempFolder.newFile("src_assets.zip")
        ZipOutputStream(FileOutputStream(srcZipFile)).use { zos ->
            zos.putNextEntry(ZipEntry("android/C16/model.gltf"))
            zos.write("{}".toByteArray())
            zos.closeEntry()

            zos.putNextEntry(ZipEntry("android/C16/texture.png"))
            zos.write(byteArrayOf(1, 2, 3))
            zos.closeEntry()
        }

        val destDir = tempFolder.newFolder("CarModel3D", "merged_model")

        // 1. 解压 H5 包
        java.util.zip.ZipInputStream(h5ZipFile.inputStream()).use { zis ->
            while (true) {
                val entry = zis.nextEntry ?: break
                val file = File(destDir, entry.name)
                if (entry.isDirectory) {
                    file.mkdirs()
                } else {
                    file.parentFile?.mkdirs()
                    FileOutputStream(file).use { fos -> zis.copyTo(fos) }
                }
                zis.closeEntry()
            }
        }

        // 2. 合并解压 Src 素材包至同一目录
        java.util.zip.ZipInputStream(srcZipFile.inputStream()).use { zis ->
            while (true) {
                val entry = zis.nextEntry ?: break
                val file = File(destDir, entry.name)
                if (entry.isDirectory) {
                    file.mkdirs()
                } else {
                    file.parentFile?.mkdirs()
                    FileOutputStream(file).use { fos -> zis.copyTo(fos) }
                }
                zis.closeEntry()
            }
        }
        File(destDir, ".ready").createNewFile()

        assertTrue(File(destDir, ".ready").exists())
        assertTrue(File(destDir, "index.html").exists())
        assertTrue(File(destDir, "js/three.js").exists())
        assertTrue(File(destDir, "android/C16/model.gltf").exists())
        assertTrue(File(destDir, "android/C16/texture.png").exists())
        assertTrue(CarModel3DManager.isModelReady(tempFolder.root.resolve("CarModel3D"), "merged_model"))
    }

    @Test
    fun `zip extraction creates valid structure with index html and ready marker`() {
        val zipFile = tempFolder.newFile("test_model.zip")
        ZipOutputStream(FileOutputStream(zipFile)).use { zos ->
            zos.putNextEntry(ZipEntry("index.html"))
            zos.write("<html><body>3D Car Model</body></html>".toByteArray())
            zos.closeEntry()

            zos.putNextEntry(ZipEntry("model/car.gltf"))
            zos.write("{}".toByteArray())
            zos.closeEntry()
        }

        val destDir = tempFolder.newFolder("dest_model")
        java.util.zip.ZipInputStream(zipFile.inputStream()).use { zis ->
            while (true) {
                val entry = zis.nextEntry ?: break
                val file = File(destDir, entry.name)
                if (entry.isDirectory) {
                    file.mkdirs()
                } else {
                    file.parentFile?.mkdirs()
                    FileOutputStream(file).use { fos ->
                        zis.copyTo(fos)
                    }
                }
                zis.closeEntry()
            }
        }
        File(destDir, ".ready").createNewFile()

        assertTrue(File(destDir, ".ready").exists())
        assertTrue(File(destDir, "index.html").exists())
        assertTrue(File(destDir, "model/car.gltf").exists())
        assertEquals("<html><body>3D Car Model</body></html>", File(destDir, "index.html").readText())
    }
}
