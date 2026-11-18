package app.lipovka.ui

import app.lipovka.IosPlatform
import app.lipovka.Platform
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.get
import kotlinx.cinterop.set
import kotlinx.cinterop.usePinned
import platform.AVFAudio.AVAudioEngine
import platform.AVFAudio.AVAudioFile
import platform.AVFAudio.AVAudioFormat
import platform.AVFAudio.AVAudioPCMBuffer
import platform.AVFAudio.AVAudioPlayer
import platform.AVFAudio.AVAudioPlayerNode
import platform.AVFAudio.AVAudioSession
import platform.AVFAudio.AVAudioSessionCategoryAmbient
import platform.AVFAudio.setActive
import platform.Foundation.NSData
import platform.Foundation.NSURL
import platform.Foundation.create
import platform.UIKit.UIImpactFeedbackGenerator
import platform.UIKit.UIImpactFeedbackStyle
import platform.darwin.DISPATCH_TIME_NOW
import platform.darwin.dispatch_after
import platform.darwin.dispatch_get_main_queue
import platform.darwin.dispatch_semaphore_create
import platform.darwin.dispatch_semaphore_signal
import platform.darwin.dispatch_semaphore_wait
import platform.darwin.dispatch_time

/** The app's sounds sit under whatever else is playing and fall silent with the ring/silent switch. */
private object Session {
    private var ready = false
    fun ensure() {
        if (ready) return
        ready = true
        runCatching {
            val s = AVAudioSession.sharedInstance()
            s.setCategory(AVAudioSessionCategoryAmbient, error = null)
            s.setActive(true, error = null)
        }
    }
}

/** How many blocks may wait in the player at once: enough to ride out a hiccup, few enough to keep changes quick. */
private const val QUEUED = 4L

actual class PcmStream actual constructor(private val rate: Int, private val block: Int) {
    private var engine: AVAudioEngine? = null
    private var node: AVAudioPlayerNode? = null
    private var format: AVAudioFormat? = null
    private val slots = dispatch_semaphore_create(QUEUED)

    actual fun start(): Boolean {
        Session.ensure()
        val e = AVAudioEngine()
        val n = AVAudioPlayerNode()
        val f = AVAudioFormat(standardFormatWithSampleRate = rate.toDouble(), channels = 1u)
        e.attachNode(n)
        e.connect(n, to = e.mainMixerNode, format = f)
        if (!e.startAndReturnError(null)) return false
        n.play()
        engine = e
        node = n
        format = f
        return true
    }

    actual fun write(data: ShortArray, n: Int): Boolean {
        val player = node ?: return false
        val f = format ?: return false
        // a stopped engine (a call, another app taking the sound) never hands a slot back: give up after a while
        if (dispatch_semaphore_wait(slots, dispatch_time(DISPATCH_TIME_NOW, 2_000_000_000L)) != 0L) return false
        val buf = AVAudioPCMBuffer(pCMFormat = f, frameCapacity = n.toUInt()) ?: return false
        buf.frameLength = n.toUInt()
        val ch = buf.floatChannelData?.get(0) ?: return false
        for (i in 0 until n) ch[i] = data[i] / 32768f
        player.scheduleBuffer(buf, completionHandler = { dispatch_semaphore_signal(slots) })
        return engine?.running == true
    }

    actual fun playedMs(): Long? {
        val player = node ?: return null
        val at = player.lastRenderTime ?: return null
        val t = player.playerTimeForNodeTime(at) ?: return null
        return t.sampleTime * 1000 / rate
    }

    actual fun close() {
        runCatching { node?.stop() }
        runCatching { engine?.stop() }
        node = null
        engine = null
    }
}

/** A WAV file in memory around 16-bit mono [pcm]. */
private fun wav(rate: Int, pcm: ShortArray): ByteArray {
    val data = pcm.size * 2
    val out = ByteArray(44 + data)
    fun str(at: Int, s: String) = s.forEachIndexed { i, c -> out[at + i] = c.code.toByte() }
    fun int(at: Int, v: Int) { for (k in 0 until 4) out[at + k] = (v shr (8 * k)).toByte() }
    fun short(at: Int, v: Int) { out[at] = v.toByte(); out[at + 1] = (v shr 8).toByte() }
    str(0, "RIFF"); int(4, 36 + data); str(8, "WAVE"); str(12, "fmt "); int(16, 16); short(20, 1); short(22, 1)
    int(24, rate); int(28, rate * 2); short(32, 2); short(34, 16); str(36, "data"); int(40, data)
    for (i in pcm.indices) short(44 + i * 2, pcm[i].toInt())
    return out
}

private fun ByteArray.toNSData(): NSData = usePinned { NSData.create(bytes = it.addressOf(0), length = size.toULong()) }

actual object Clips {
    private val players = HashMap<String, AVAudioPlayer>()

    actual fun play(key: String, rate: Int, pcm: ShortArray, volume: Float) {
        Session.ensure()
        val p = players[key] ?: AVAudioPlayer(data = wav(rate, pcm).toNSData(), error = null).also {
            it.prepareToPlay()
            players[key] = it
        }
        p.stop()
        p.currentTime = 0.0
        p.volume = volume
        p.play()
    }

    actual fun forget(key: String) {
        players.remove(key)?.stop()
    }
}

actual object Haptics {
    private val tap by lazy { UIImpactFeedbackGenerator(style = UIImpactFeedbackStyle.UIImpactFeedbackStyleLight) }

    /** The Taptic Engine taps rather than hums: a soft tap at each step that's loud enough, as strong as it. */
    actual fun buzz(levels: IntArray, stepMs: Long) {
        tap.prepare()
        var last = -10
        for ((i, level) in levels.withIndex()) {
            // at most a tap every other step, so it's felt as a texture rather than a buzz
            if (level < 25 || i - last < 2) continue
            last = i
            val strength = (level / 255.0).coerceIn(.15, 1.0)
            dispatch_after(dispatch_time(DISPATCH_TIME_NOW, i * stepMs * 1_000_000L), dispatch_get_main_queue()) {
                tap.impactOccurredWithIntensity(strength)
            }
        }
    }
}

/** The recordings ship for the iPhone as AAC (ambience/<name>.m4a in the app bundle, 32 kHz mono, made from the Android .ogg files). */
actual fun decodeRecording(p: Platform, name: String, rate: Int): ShortArray? {
    val path = IosPlatform.bundlePath("ambience/$name.m4a") ?: return null
    val file = AVAudioFile(forReading = NSURL.fileURLWithPath(path), error = null)
    val f = file.processingFormat
    val frames = file.length
    if (frames <= 0) return null
    val buf = AVAudioPCMBuffer(pCMFormat = f, frameCapacity = frames.toUInt()) ?: return null
    if (!file.readIntoBuffer(buf, error = null)) return null
    val n = buf.frameLength.toInt()
    val ch = buf.floatChannelData?.get(0) ?: return null
    val src = FloatArray(n) { ch[it] }
    val from = f.sampleRate
    // the files are made at the mixer's rate; should one not be, it's stretched to fit
    val out = if (from.toInt() == rate) src else FloatArray((n * rate / from).toInt()) { i ->
        val x = i * from / rate
        val a = x.toInt().coerceAtMost(n - 1)
        val b = (a + 1).coerceAtMost(n - 1)
        val t = (x - a).toFloat()
        src[a] * (1 - t) + src[b] * t
    }
    return ShortArray(out.size) { (out[it].coerceIn(-1f, 1f) * 32767).toInt().toShort() }
}
