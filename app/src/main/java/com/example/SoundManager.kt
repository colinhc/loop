package com.example

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.media.ToneGenerator
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.sin

class SoundManager {

    companion object {
        const val SOUND_NONE = "None"
        const val SOUND_BEEP_LOW = "Low Beep"
        const val SOUND_BEEP_MED = "Med Beep"
        const val SOUND_BEEP_HIGH = "High Beep"
        const val SOUND_DOUBLE_BEEP = "Double Beep"
        const val SOUND_SWEEP_UP = "Sweep Up"
        const val SOUND_SIREN = "Siren Alert"
        const val SOUND_TICK = "Wood Tick"
        const val SOUND_TRIPLE_BURST = "Triple Burst"

        val START_SOUNDS = listOf(SOUND_NONE, SOUND_BEEP_MED, SOUND_BEEP_HIGH, SOUND_DOUBLE_BEEP, SOUND_TICK)
        val END_SOUNDS = listOf(SOUND_NONE, SOUND_BEEP_LOW, SOUND_SWEEP_UP, SOUND_BEEP_HIGH, SOUND_DOUBLE_BEEP)
    }

    suspend fun playSound(type: String, durationMs: Int = 200) {
        if (type == SOUND_NONE) return
        withContext(Dispatchers.Default) {
            try {
                val sampleRate = 44100
                when (type) {
                    SOUND_BEEP_LOW -> playTone(330f, durationMs, sampleRate)
                    SOUND_BEEP_MED -> playTone(660f, durationMs, sampleRate)
                    SOUND_BEEP_HIGH -> playTone(1320f, durationMs, sampleRate)
                    SOUND_DOUBLE_BEEP -> {
                        if (durationMs > 200) {
                            val activeDur = (durationMs * 0.4).toInt()
                            val sleepDur = (durationMs * 0.2).toInt()
                            playTone(1000f, activeDur, sampleRate)
                            Thread.sleep(sleepDur.toLong())
                            playTone(1000f, activeDur, sampleRate)
                        } else {
                            playTone(1000f, 80, sampleRate)
                            Thread.sleep(60)
                            playTone(1000f, 80, sampleRate)
                        }
                    }
                    SOUND_SWEEP_UP -> playSweep(440f, 1500f, durationMs, sampleRate)
                    SOUND_SIREN -> playSiren(400, sampleRate)
                    SOUND_TICK -> playTick(sampleRate)
                    SOUND_TRIPLE_BURST -> {
                        playTone(1320f, 80, sampleRate)
                        Thread.sleep(60)
                        playTone(1320f, 80, sampleRate)
                        Thread.sleep(60)
                        playTone(1320f, 80, sampleRate)
                    }
                    else -> playTone(880f, durationMs, sampleRate)
                }
            } catch (e: Exception) {
                Log.e("SoundManager", "Error playing sound: $type", e)
            }
        }
    }

    private fun playTone(freq: Float, durationMs: Int, sampleRate: Int) {
        val numSamples = (durationMs * sampleRate / 1000)
        val samples = FloatArray(numSamples)
        for (i in 0 until numSamples) {
            val angle = 2.0 * Math.PI * i / (sampleRate / freq)
            samples[i] = sin(angle).toFloat()
            // Apply smoothing envelope
            val fadeOutIndex = numSamples - (sampleRate * 0.015).toInt() // last 15ms
            if (i > fadeOutIndex) {
                val fraction = (numSamples - i).toFloat() / (numSamples - fadeOutIndex)
                samples[i] *= fraction
            }
            if (i < sampleRate * 0.008) {
                samples[i] *= (i.toFloat() / (sampleRate * 0.008f))
            }
        }
        writeToAudioTrack(samples, sampleRate)
    }

    private fun playSweep(startFreq: Float, endFreq: Float, durationMs: Int, sampleRate: Int) {
        val numSamples = (durationMs * sampleRate / 1000)
        val samples = FloatArray(numSamples)
        for (i in 0 until numSamples) {
            val progress = i.toFloat() / numSamples
            // Linear frequency interpolation
            val freq = startFreq + (endFreq - startFreq) * progress
            val angle = 2.0 * Math.PI * freq * i / sampleRate
            samples[i] = sin(angle).toFloat()

            // Envelope
            val fadeOutIndex = numSamples - (sampleRate * 0.02).toInt()
            if (i > fadeOutIndex) {
                samples[i] *= (numSamples - i).toFloat() / (numSamples - fadeOutIndex)
            }
            if (i < sampleRate * 0.01) {
                samples[i] *= (i.toFloat() / (sampleRate * 0.01f))
            }
        }
        writeToAudioTrack(samples, sampleRate)
    }

    private fun playSiren(durationMs: Int, sampleRate: Int) {
        val numSamples = (durationMs * sampleRate / 1000)
        val samples = FloatArray(numSamples)
        for (i in 0 until numSamples) {
            val progress = i.toFloat() / numSamples
            // Frequency sweeps up and down fast
            val freq = 700f + 500f * sin(2.0 * Math.PI * 8.0 * progress).toFloat()
            val angle = 2.0 * Math.PI * freq * i / sampleRate
            samples[i] = sin(angle).toFloat()

            // Envelope
            val fadeOutIndex = numSamples - (sampleRate * 0.02).toInt()
            if (i > fadeOutIndex) {
                samples[i] *= (numSamples - i).toFloat() / (numSamples - fadeOutIndex)
            }
        }
        writeToAudioTrack(samples, sampleRate)
    }

    private fun playTick(sampleRate: Int) {
        // High frequency, extremely short dampening sine wave for clean wooden woodblock click tick
        val durationMs = 15
        val numSamples = (durationMs * sampleRate / 1000)
        val samples = FloatArray(numSamples)
        for (i in 0 until numSamples) {
            val progress = i.toFloat() / numSamples
            val angle = 2.0 * Math.PI * 1800f * i / sampleRate
            samples[i] = sin(angle).toFloat() * (1f - progress) * (1f - progress)
        }
        writeToAudioTrack(samples, sampleRate)
    }

    private fun playVictoryFanfare(sampleRate: Int) {
        playTone(523.25f, 100, sampleRate)
        playTone(659.25f, 100, sampleRate)
        playTone(783.99f, 100, sampleRate)
        playTone(1046.50f, 150, sampleRate)
    }

    private fun playCelestialChimes(sampleRate: Int) {
        val durationMs = 500
        val numSamples = (durationMs * sampleRate / 1000)
        val samples = FloatArray(numSamples)
        for (i in 0 until numSamples) {
            val progress = i.toFloat() / numSamples
            val angle1 = 2.0 * Math.PI * 1200f * i / sampleRate
            val angle2 = 2.0 * Math.PI * 1500f * i / sampleRate
            val angle3 = 2.0 * Math.PI * 1800f * i / sampleRate
            val envelope = (1f - progress) * (1f - progress)
            samples[i] = (sin(angle1) * 0.4f + sin(angle2) * 0.3f + sin(angle3) * 0.3f).toFloat() * envelope
        }
        writeToAudioTrack(samples, sampleRate)
    }

    private fun playTrill(sampleRate: Int) {
        val durationMs = 500
        val numSamples = (durationMs * sampleRate / 1000)
        val samples = FloatArray(numSamples)
        for (i in 0 until numSamples) {
            val progress = i.toFloat() / numSamples
            val isA = (i / (sampleRate * 0.045f).toInt()) % 2 == 0
            val freq = if (isA) 880f else 1174.66f
            val angle = 2.0 * Math.PI * freq * i / sampleRate
            val envelope = 1f - progress
            samples[i] = sin(angle).toFloat() * envelope
        }
        writeToAudioTrack(samples, sampleRate)
    }

    private fun playBubblingSynth(sampleRate: Int) {
        val durationMs = 500
        val numSamples = (durationMs * sampleRate / 1000)
        val samples = FloatArray(numSamples)
        for (i in 0 until numSamples) {
            val progress = i.toFloat() / numSamples
            val step = (progress * 6).toInt()
            val freq = 440f + step * 160f
            val angle = 2.0 * Math.PI * freq * i / sampleRate
            val envelope = 1f - progress
            samples[i] = sin(angle).toFloat() * envelope
        }
        writeToAudioTrack(samples, sampleRate)
    }

    private fun writeToAudioTrack(samples: FloatArray, sampleRate: Int) {
        try {
            val audioAttributes = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()

            val audioTrack = AudioTrack.Builder()
                .setAudioAttributes(audioAttributes)
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_FLOAT)
                        .setSampleRate(sampleRate)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .build()
                )
                .setTransferMode(AudioTrack.MODE_STATIC)
                .setBufferSizeInBytes(samples.size * 4)
                .build()

            audioTrack.write(samples, 0, samples.size, AudioTrack.WRITE_BLOCKING)
            audioTrack.play()

            val playDuration = (samples.size * 1000L) / sampleRate
            Thread.sleep(playDuration + 15)

            audioTrack.stop()
            audioTrack.release()
        } catch (e: Exception) {
            Log.e("SoundManager", "Failed to write raw track", e)
        }
    }
}
