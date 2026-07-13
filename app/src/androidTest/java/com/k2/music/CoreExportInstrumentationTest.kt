package com.k2.music

import android.graphics.Color
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.k2.music.ui.gateway.DefaultExportGateway
import com.k2.music.ui.gateway.ExportRequestUi
import com.k2.music.ui.gateway.ExportScopeUi
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CoreExportInstrumentationTest {
    @Test
    fun customVoicingsAreIncludedByTheComposeExportGateway() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val repository = ChordRepository()
        val store = CustomVoicingStore(context)
        val id = "device-export-custom"
        store.delete(id)
        try {
            store.save(
                CustomVoicing(
                    id,
                    "C",
                    "设备导出自定义指法",
                    intArrayOf(-1, 3, 5, 5, 5, 3),
                    intArrayOf(0, 1, 3, 3, 3, 1),
                    3,
                    "设备回归",
                    System.currentTimeMillis(),
                ),
            )
            val chord = checkNotNull(repository.find("C").chord)
            val gateway = DefaultExportGateway(
                context,
                repository,
                store,
                UserChordStore(context, repository),
            )

            assertEquals(
                chord.voicings.size + 1,
                gateway.count(ExportRequestUi(ExportScopeUi.CHORD_ALL, listOf("C"))),
            )
        } finally {
            store.delete(id)
        }
    }

    @Test
    fun exportedInfoTextKeepsItsLeftMargin() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val chord = checkNotNull(ChordRepository().find("C").chord)
        val bitmap = VoicingImageExporter.render(context, chord, chord.voicings[0])
        try {
            assertFalse(
                "Info text leaked into the left crop margin",
                containsDarkPixel(bitmap, 0, 80, 1640, 1750),
            )
            assertTrue(
                "Info heading was not rendered in its intended content area",
                containsDarkPixel(bitmap, 100, 650, 1640, 1750),
            )
        } finally {
            bitmap.recycle()
        }
    }

    private fun containsDarkPixel(
        bitmap: android.graphics.Bitmap,
        left: Int,
        right: Int,
        top: Int,
        bottom: Int,
    ): Boolean {
        val safeRight = minOf(bitmap.width, right)
        val safeBottom = minOf(bitmap.height, bottom)
        for (y in maxOf(0, top) until safeBottom step 2) {
            for (x in maxOf(0, left) until safeRight step 2) {
                val color = bitmap.getPixel(x, y)
                if (Color.red(color) < 100 && Color.green(color) < 100 && Color.blue(color) < 100) {
                    return true
                }
            }
        }
        return false
    }
}
