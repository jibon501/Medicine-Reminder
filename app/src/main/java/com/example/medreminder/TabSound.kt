package com.example.medreminder

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.AudioFormat
import android.media.AudioTrack
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin

// ট্যাব অ্যানিমেশনের সাথে মেলানো ছোট সাউন্ড। কোনো ফাইল লাগে না, কোডেই বানানো হয়
object TabSound {
    private const val RATE = 22050
    private val cache = HashMap<Int, ShortArray>()
    private var last: AudioTrack? = null

    @Synchronized
    // ফোন সাইলেন্ট বা ভাইব্রেশন মোডে থাকলে true
    fun isMuted(ctx: Context): Boolean {
        val am = ctx.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return false
        return am.ringerMode != AudioManager.RINGER_MODE_NORMAL
    }

    // volume: ০.০ (নীরব) থেকে ১.০ (ফুল)। ডিফল্ট হালকা (০.২৫)। ফোন সাইলেন্ট/ভাইব্রেশনে বাজে না
    fun play(ctx: Context, style: Int, volume: Float = 0.25f) {
        if (volume <= 0f || isMuted(ctx)) return
        try {
            val pcm = cache.getOrPut(style) { synth(style) }
            last?.let { try { it.stop(); it.release() } catch (_: Exception) {} }
            val bytes = pcm.size * 2
            val t = AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate(RATE)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .build()
                )
                .setBufferSizeInBytes(bytes)
                .setTransferMode(AudioTrack.MODE_STATIC)
                .build()
            t.write(pcm, 0, pcm.size)
            t.setVolume(volume.coerceIn(0f, 1f))
            t.play()
            last = t
        } catch (_: Exception) {
        }
    }

    private fun synth(style: Int): ShortArray = when (style) {
        1 -> spin()
        2 -> whoosh()
        3 -> boing()
        4 -> flip()
        5 -> twang()
        else -> zoom()
    }

    private fun buf(sec: Float) = FloatArray((RATE * sec).toInt())

    private fun toPcm(f: FloatArray, gain: Float = 0.35f): ShortArray =
        ShortArray(f.size) { (f[it].coerceIn(-1f, 1f) * gain * 32767f).toInt().toShort() }

    private fun hann(t: Float, dur: Float) = sin(PI.toFloat() * t / dur)

    // ০: জুম আউট (সুর নামে) → জুম ইন (সুর ওঠে)
    private fun zoom(): ShortArray {
        val dur = 0.45f
        val b = buf(dur)
        var ph = 0.0
        for (i in b.indices) {
            val t = i / RATE.toFloat()
            val f = if (t < dur / 2) 900f - 500f * (t / (dur / 2)) else 400f + 650f * ((t - dur / 2) / (dur / 2))
            ph += 2 * PI * f / RATE
            b[i] = sin(ph).toFloat() * hann(t, dur)
        }
        return toPcm(b)
    }

    // ১: ঘুরতে ঘুরতে (কাঁপা সুর, ধীরে ওপরে ওঠে)
    private fun spin(): ShortArray {
        val dur = 0.65f
        val b = buf(dur)
        var ph = 0.0
        for (i in b.indices) {
            val t = i / RATE.toFloat()
            val f = 420f + 500f * t / dur + 140f * sin(2 * PI.toFloat() * 9f * t)
            ph += 2 * PI * f / RATE
            b[i] = sin(ph).toFloat() * hann(t, dur)
        }
        return toPcm(b)
    }

    // ২: সোজা লাইনে (হুশ শব্দ)
    private fun whoosh(): ShortArray {
        val dur = 0.38f
        val b = buf(dur)
        val rnd = java.util.Random(7)
        var y = 0f
        for (i in b.indices) {
            val t = i / RATE.toFloat()
            val a = 0.04f + 0.4f * hann(t, dur)
            y += a * ((rnd.nextFloat() * 2f - 1f) - y)
            b[i] = y * 3f * hann(t, dur)
        }
        return toPcm(b, 0.45f)
    }

    // ৩: লাফিয়ে (বয়িং)
    private fun boing(): ShortArray {
        val dur = 0.8f
        val b = buf(dur)
        var ph = 0.0
        for (i in b.indices) {
            val t = i / RATE.toFloat()
            val f = 230f + 260f * exp(-3f * t) * (1f + sin(2 * PI.toFloat() * 7f * t))
            ph += 2 * PI * f / RATE
            val env = (1f - exp(-t * 200f)) * exp(-3.5f * t)
            b[i] = sin(ph).toFloat() * env
        }
        return toPcm(b)
    }

    // ৪: ফ্লিপ (তাস উল্টানোর দুটো খট শব্দ)
    private fun flip(): ShortArray {
        val dur = 0.65f
        val b = buf(dur)
        val rnd = java.util.Random(3)
        for (i in b.indices) {
            val t = i / RATE.toFloat()
            val e1 = exp(-t / 0.012f)
            val e2 = if (t >= 0.3f) exp(-(t - 0.3f) / 0.012f) else 0f
            val n = rnd.nextFloat() * 2f - 1f
            val tone = sin(2 * PI.toFloat() * 1700f * t)
            b[i] = (n * 0.8f + tone * 0.4f) * (e1 + e2)
        }
        return toPcm(b)
    }

    // ৫: রাবারের টান (টুং করে নামা সুর)
    private fun twang(): ShortArray {
        val dur = 0.5f
        val b = buf(dur)
        var ph = 0.0
        for (i in b.indices) {
            val t = i / RATE.toFloat()
            val f = 200f + 650f * exp(-4f * t) + 25f * sin(2 * PI.toFloat() * 30f * t)
            ph += 2 * PI * f / RATE
            val env = (1f - exp(-t * 300f)) * exp(-5f * t)
            b[i] = (sin(ph).toFloat() + 0.3f * sin(2 * ph).toFloat()) * env
        }
        return toPcm(b)
    }
}
