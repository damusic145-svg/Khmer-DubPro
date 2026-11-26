package com.example

import com.example.data.local.DialogueSegmentEntity
import com.example.data.model.AgeGroup
import com.example.data.model.SpeakerGender
import com.example.data.repository.KhmerVoiceCatalog
import com.example.media.AudioExtractor
import com.example.media.VideoExporter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import kotlin.math.sin

class DubbingPipelineUnitTest {

    @Test
    fun `voice catalog matches gender and age accurately`() {
        val childVoice = KhmerVoiceCatalog.matchVoice(SpeakerGender.MALE, AgeGroup.CHILD)
        assertEquals("kh_child_bright", childVoice.id)

        val femaleElder = KhmerVoiceCatalog.matchVoice(SpeakerGender.FEMALE, AgeGroup.ELDERLY)
        assertEquals("kh_female_elder", femaleElder.id)

        val maleLead = KhmerVoiceCatalog.matchVoice(SpeakerGender.MALE, AgeGroup.ADULT)
        assertEquals("kh_male_hero", maleLead.id)
    }

    @Test
    fun `wav write and read roundtrip preserves sample count and rate`() {
        val tempWav = File.createTempFile("test_tone", ".wav")
        tempWav.deleteOnExit()

        val rate = 24000
        val samples = ShortArray(rate) { i ->
            (sin(2.0 * Math.PI * 440.0 * i / rate) * 16000).toInt().toShort()
        }
        AudioExtractor.writeWavFile(tempWav, samples, rate, 1)

        val readRate = AudioExtractor.readWavSampleRate(tempWav)
        val readSamples = AudioExtractor.readWavSamples(tempWav)
        val bars = AudioExtractor.computeWaveformBars(readSamples, 40)

        assertEquals(rate, readRate)
        assertEquals(samples.size, readSamples.size)
        assertEquals(40, bars.size)
        assertTrue(bars.all { it in 0.05f..1.0f })
    }

    @Test
    fun `srt writer outputs bilingual Khmer and original cues`() {
        val tempSrt = File.createTempFile("test_subs", ".srt")
        tempSrt.deleteOnExit()

        val segments = listOf(
            DialogueSegmentEntity(
                projectId = 1L,
                characterId = 1L,
                speakerTag = "SPEAKER_1",
                segmentIndex = 0,
                startMs = 1250L,
                endMs = 3800L,
                originalLanguage = "English",
                originalText = "Welcome to Phnom Penh!",
                khmerText = "សូមស្វាគមន៍មកកាន់ភ្នំពេញ!"
            )
        )
        VideoExporter.writeSrtFile(tempSrt, segments, bilingual = true)
        val content = tempSrt.readText(Charsets.UTF_8)

        assertTrue(content.contains("00:00:01,250 --> 00:00:03,800"))
        assertTrue(content.contains("សូមស្វាគមន៍មកកាន់ភ្នំពេញ!"))
        assertTrue(content.contains("Welcome to Phnom Penh!"))
    }
}
