package app.lipovka.ui

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTimestamp
import android.media.AudioTrack
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.os.VibrationEffect
import android.os.Vibrator
import app.lipovka.AndroidPlatform
import app.lipovka.Platform
import java.nio.ByteOrder

private fun attributes() = AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_GAME).setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build()

private fun mono(rate: Int) = AudioFormat.Builder().setSampleRate(rate).setEncoding(AudioFormat.ENCODING_PCM_16BIT).setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build()

actual class PcmStream actual constructor(private val rate: Int, private val block: Int) {
    private var t: AudioTrack? = null

    actual fun start(): Boolean {
        val track = AudioTrack.Builder()
            .setAudioAttributes(attributes())
            .setAudioFormat(mono(rate))
            .setTransferMode(AudioTrack.MODE_STREAM)
            .setBufferSizeInBytes(block * 2 * 4)
            .build()
        track.play()
        t = track
        return true
    }

    actual fun write(data: ShortArray, n: Int): Boolean = (t?.write(data, 0, n) ?: -1) >= 0

    actual fun playedMs(): Long? {
        val track = t ?: return null
        val ts = AudioTimestamp()
        if (!track.getTimestamp(ts)) return null
        return (ts.framePosition + (System.nanoTime() - ts.nanoTime) * rate / 1_000_000_000L) * 1000 / rate
    }

    actual fun close() {
        val track = t ?: return
        t = null
        runCatching { track.stop() }
        track.release()
    }
}

actual object Clips {
    private val tracks = HashMap<String, AudioTrack>()

    actual fun play(key: String, rate: Int, pcm: ShortArray, volume: Float) {
        val t = tracks[key] ?: AudioTrack.Builder()
            .setAudioAttributes(attributes())
            .setAudioFormat(mono(rate))
            .setTransferMode(AudioTrack.MODE_STATIC)
            .setBufferSizeInBytes(pcm.size * 2)
            .build()
            .also { it.write(pcm, 0, pcm.size); tracks[key] = it }
        if (t.playState == AudioTrack.PLAYSTATE_PLAYING) t.stop()
        t.setVolume(volume)
        t.reloadStaticData()
        t.play()
    }

    actual fun forget(key: String) {
        tracks.remove(key)?.release()
    }
}

actual object Haptics {
    actual fun buzz(levels: IntArray, stepMs: Long) {
        val v = AndroidPlatform.app?.getSystemService(Vibrator::class.java) ?: return
        if (!v.hasVibrator() || levels.isEmpty()) return
        val timings = LongArray(levels.size * 2) { stepMs / 2 }
        val amps = IntArray(levels.size * 2) { if (it % 2 == 1) 0 else levels[it / 2].coerceIn(0, 255) }
        if (v.hasAmplitudeControl()) v.vibrate(VibrationEffect.createWaveform(timings, amps, -1))
        // Without soft steps a phone only buzzes full strength, so just a single short tremble.
        else v.vibrate(VibrationEffect.createOneShot(30, VibrationEffect.DEFAULT_AMPLITUDE))
    }
}

/** A whole recording as 16-bit mono PCM; a few megabytes, decoded once and kept while the app runs. */
actual fun decodeRecording(p: Platform, name: String, rate: Int): ShortArray? {
    val context: Context = (p as AndroidPlatform).context
    val fd = context.assets.openFd("ambience/$name.ogg")
    val ex = MediaExtractor()
    ex.setDataSource(fd.fileDescriptor, fd.startOffset, fd.length)
    fd.close()
    ex.selectTrack(0)
    val format = ex.getTrackFormat(0)
    val codec = MediaCodec.createDecoderByType(format.getString(MediaFormat.KEY_MIME)!!)
    codec.configure(format, null, null, 0)
    codec.start()
    val channels = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
    var out = ShortArray(rate * 60)
    var n = 0
    val info = MediaCodec.BufferInfo()
    var inputDone = false
    try {
        while (true) {
            if (!inputDone) {
                val i = codec.dequeueInputBuffer(10_000)
                if (i >= 0) {
                    val size = ex.readSampleData(codec.getInputBuffer(i)!!, 0)
                    if (size < 0) {
                        codec.queueInputBuffer(i, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                        inputDone = true
                    } else {
                        codec.queueInputBuffer(i, 0, size, ex.sampleTime, 0)
                        ex.advance()
                    }
                }
            }
            val o = codec.dequeueOutputBuffer(info, 10_000)
            if (o >= 0) {
                val buf = codec.getOutputBuffer(o)!!.order(ByteOrder.nativeOrder()).asShortBuffer()
                val frames = info.size / 2 / channels
                if (n + frames > out.size) out = out.copyOf(maxOf(out.size * 2, n + frames))
                for (f in 0 until frames) {
                    var acc = 0
                    for (c in 0 until channels) acc += buf.get(info.offset / 2 + f * channels + c)
                    out[n++] = (acc / channels).toShort()
                }
                codec.releaseOutputBuffer(o, false)
                if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) break
            }
        }
    } finally {
        codec.stop()
        codec.release()
        ex.release()
    }
    return out.copyOf(n)
}
