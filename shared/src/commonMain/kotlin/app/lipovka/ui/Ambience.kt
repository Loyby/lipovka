package app.lipovka.ui

import app.lipovka.compat.*
import kotlin.concurrent.Volatile

import app.lipovka.domain.Env
import app.lipovka.domain.Phase
import app.lipovka.domain.Sky
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.sin
import kotlin.math.tanh

/**
 * The quiet sounds of the post office while the app is open: the wall clock ticking in step with its pendulum,
 * and what the hour and weather bring through the walls — rain, a thunderstorm, the wind, birds on a summer
 * morning, crickets on a warm evening, the stove crackling on a cold one.
 *
 * The weather and the stove are real recordings in assets/ambience (mono, 32 kHz, collected by the Blanket project):
 * - rain: "Rain" by alex36917 (freesound.org/s/524605), CC BY;
 * - storm: "Thunderstorm" by Digifish music (freesound.org/s/41739), CC BY;
 * - wind: by felix.blume (freesound.org/s/217506), CC0;
 * - forest: "Forest birds" by kvgarlic (freesound.org/s/156826), CC0;
 * - night: "Crickets Chirping At Night" by Lisa Redfern (soundbible.com/2083), public domain.
 * The clock and the stove are made up here: the clock so each beat falls exactly at the end of the pendulum's
 * swing, the stove as a low breath of fire with the wood crackling.
 *
 * Everything is mixed into one stream; each layer fades in and out on its own, so a change of weather or of
 * room doesn't stop the rest.
 */
object Ambience {
    private const val TAG = "Ambience"
    private const val RATE = 32000
    private const val BLOCK = 1024

    /** Milliseconds between the clock's beats; the pendulum is at the end of its swing on each. */
    const val BEAT_MS = 600
    private const val BEAT = RATE * BEAT_MS / 1000

    /** A layer, its recording in assets/ambience (none for the clock; the stove's is made here) and how loud it sits in the room. */
    enum class Layer(val file: String?, val gain: Float) {
        CLOCK(null, .06f),
        RAIN("rain", 1.6f),
        STORM("storm", 1.6f),
        WIND("wind", 1.3f),
        BIRDS("forest", .9f),
        CRICKETS("night", 1.1f),
        STOVE("stove", .55f),
    }

    /** What can be heard from inside the post office in this weather at this hour. */
    fun layers(e: Env): Set<Layer> = buildSet {
        add(Layer.CLOCK)
        val dark = e.phase == Phase.EVENING || e.phase == Phase.NIGHT || e.phase == Phase.DUSK
        // the storm's recording has its own rain
        if (e.sky == Sky.STORM) add(Layer.STORM) else if (e.sky.rain) add(Layer.RAIN)
        if (e.wind >= 20 || (e.sky.snow && e.wind >= 10)) add(Layer.WIND)
        if (!dark && !e.sky.rain && e.month in 4..8) add(Layer.BIRDS)
        if (dark && !e.sky.rain && e.month in 6..8 && e.temp >= 12) add(Layer.CRICKETS)
        if (dark && (e.temp <= 8 || e.month !in 5..9)) add(Layer.STOVE)
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val pcm = HashMap<Layer, ShortArray>()
    private val loading = HashSet<Layer>()

    @Volatile private var want: Set<Layer> = emptySet()

    /** How loud it all plays, 0..1, as set in the settings; changes glide in over a block or two. */
    @Volatile var volume = 1f
    @Volatile private var track: PcmStream? = null
    private var thread: Job? = null

    /** Plays [layers] from now on, each fading in or out on its own; empty fades everything away. */
    fun play(context: Ctx, layers: Set<Layer>) {
        val app = context.container.platform
        want = layers
        for (l in layers) {
            val file = l.file ?: continue
            synchronized(pcm) {
                if (l !in pcm && loading.add(l)) scope.launch {
                    val p = runCatching { if (l == Layer.STOVE) stove() else decodeRecording(app, file, RATE) }.onFailure { Log.w(TAG, "load $file", it) }.getOrNull()
                    synchronized(pcm) { loading -= l; if (p != null && p.size > RATE * 4) pcm[l] = p }
                }
            }
        }
        synchronized(this) {
            if (layers.isNotEmpty() && thread == null) thread = scope.launch { run() }
        }
    }

    fun stop(context: Ctx) = play(context, emptySet())

    /**
     * Where the ticking clock is right now, in ms of its own time (a beat sounds at every whole [BEAT_MS]),
     * so the pendulum in the picture can swing in step with what's heard; null when it isn't ticking.
     */
    fun clockMs(): Long? {
        if (Layer.CLOCK !in want) return null
        val t = track ?: return null
        return runCatching { t.playedMs() }.getOrNull()
    }

    // ------------------------------------------------------------------ the stream

    /** One recording as it plays: how loud it is now, and where it is. */
    private class Voice(val layer: Layer, val rnd: Random) {
        var level = 0f
        var start = 0
        var len = 0
        var i = 0
        var next = 0
    }

    private fun run() {
        val t = runCatching { PcmStream(RATE, BLOCK).takeIf { it.start() } }.onFailure { Log.w(TAG, "track", it) }.getOrNull()
        if (t == null) { synchronized(this) { thread = null }; return }
        val voices = Layer.entries.map { Voice(it, Random(it.ordinal * 7919L + System.nanoTime())) }
        val out = ShortArray(BLOCK)
        val mix = FloatArray(BLOCK)
        var frame = 0L
        var quiet = 0
        var gain = volume * volume
        track = t
        try {
            while (true) {
                val w = want
                mix.fill(0f)
                var any = false
                for (v in voices) {
                    val p = if (v.layer.file == null) null else synchronized(pcm) { pcm[v.layer] }
                    if (v.layer.file != null && p == null) continue
                    // about two seconds to come in, one to go
                    val from = v.level
                    v.level = (from + if (v.layer in w) BLOCK / (RATE * 2f) else -BLOCK / RATE.toFloat()).coerceIn(0f, 1f)
                    if (from == 0f && v.level == 0f) continue
                    any = true
                    for (k in 0 until BLOCK) {
                        val g = (from + (v.level - from) * k / BLOCK) * v.layer.gain
                        mix[k] += g * if (p == null) clock(frame + k) else recording(v, p)
                    }
                }
                // the ear hears loudness roughly as the square of the amplitude, so the bar's steps feel even
                val to = volume * volume
                for (k in 0 until BLOCK) {
                    val g = gain + (to - gain) * k / BLOCK
                    out[k] = (tanh(mix[k].toDouble()) * 32767 * .9 * g).toInt().toShort()
                }
                gain = to
                if (!t.write(out, BLOCK)) break
                frame += BLOCK
                // wound down: everything faded and nothing wanted for half a second
                quiet = if (!any && w.isEmpty()) quiet + 1 else 0
                if (quiet > RATE / BLOCK / 2) synchronized(this) { if (want.isEmpty()) { thread = null; return } }
            }
            synchronized(this) { thread = null }
        } catch (e: Exception) {
            Log.w(TAG, "stream", e)
            synchronized(this) { thread = null }
        } finally {
            track = null
            runCatching { t.close() }
        }
    }

    /**
     * The next sample of a recording, played as stretches of 8–20 s from random places, each fading into the
     * next over 1.5 s, so neither a loop point nor a repeat can be heard.
     */
    private fun recording(v: Voice, p: ShortArray): Float {
        val xf = minOf(RATE * 3 / 2, p.size / 4)
        if (v.len == 0) { v.start = pick(v, p); v.len = segment(v, p, v.start); v.next = pick(v, p); v.i = 0 }
        val fadeAt = v.len - xf
        var s = p[v.start + v.i] / 32768f
        if (v.i >= fadeAt) {
            val t = (v.i - fadeAt).toFloat() / xf
            s = s * cos(t * PI.toFloat() / 2) + p[v.next + (v.i - fadeAt)] / 32768f * sin(t * PI.toFloat() / 2)
        }
        if (++v.i >= v.len) {
            // the next stretch already began under the fade; it carries on from there
            v.start = v.next
            v.len = segment(v, p, v.start)
            v.next = pick(v, p)
            v.i = xf
        }
        return s
    }

    /** A place to start a stretch, leaving room for at least a short one after it. */
    private fun pick(v: Voice, p: ShortArray): Int = v.rnd.nextInt(maxOf(1, p.size - RATE * 4))

    private fun segment(v: Voice, p: ShortArray, start: Int): Int =
        (RATE * (8 + v.rnd.nextFloat() * 12)).toInt().coerceAtMost(p.size - start - 1)

    // ------------------------------------------------------------------ the clock

    /** The ходики's two sounds, a tick and a slightly lower tock: a small metal escapement in a wooden case. */
    private val beats: Array<FloatArray> by lazy {
        val r = Random(11)
        Array(2) { k ->
            val metal = if (k == 1) 1750.0 else 2150.0
            val click = RATE / 500
            val x = FloatArray(RATE / 15) { i ->
                val t = i.toDouble() / RATE
                (sin(2 * PI * metal * t) * exp(-t * 260) * .7 +
                    sin(2 * PI * 620 * t) * exp(-t * 90) * .45 +
                    (if (i < click) r.nextGaussian() * .5 * (1 - i / click.toDouble()) else 0.0)).toFloat()
            }
            val top = x.maxOf { abs(it) }
            FloatArray(x.size) { x[it] / top * if (k == 1) .8f else 1f }
        }
    }

    /** The clock at stream frame [f]: a beat starts at every whole [BEAT], tick and tock in turn. */
    private fun clock(f: Long): Float {
        val n = f / BEAT
        val i = (f - n * BEAT).toInt()
        val b = beats[(n and 1).toInt()]
        return if (i < b.size) b[i] else 0f
    }

    // ------------------------------------------------------------------ the stove

    /** Half a minute of the stove: a low breath of fire behind the door, the wood crackling, sometimes a sharp snap. */
    private fun stove(): ShortArray {
        val n = RATE * 30
        val r = Random(61)
        val x = FloatArray(n)
        // the fire's breath: noise under a gentle low-pass, scaled to stay under the crackle
        var y = 0.0
        val a = exp(-2 * PI * 260 / RATE)
        val bed = FloatArray(n) { y = (1 - a) * r.nextGaussian() + a * y; y.toFloat() }
        val top = bed.maxOf { abs(it) }
        for (i in 0 until n) x[i] = bed[i] / top * .35f
        var at = 0
        while (at < n) {
            // pops come in little clusters, as one log catches
            val big = r.nextDouble() < .08
            val loud = if (big) .9 + r.nextDouble() * .3 else .12 + r.nextDouble().let { it * it * it } * .6
            val len = RATE * (if (big) 8 else 1 + r.nextInt(3)) / 1000
            var last = 0.0
            for (i in 0 until len) {
                val g = r.nextGaussian()
                if (at + i < n) x[at + i] += ((g - last) * loud * exp(-i * 5.0 / len)).toFloat()
                last = g
            }
            at += (-kotlin.math.ln(1 - r.nextDouble()) * RATE / 7).toInt() + RATE / 200
        }
        val peak = x.maxOf { abs(it) }
        return ShortArray(n) { (x[it] / peak * 32767 * .9).toInt().toShort() }
    }

    /** The made-up layers as they play, [seconds] long, for listening to outside the app. */
    fun synth(layer: Layer, seconds: Int): ShortArray = when (layer) {
        Layer.CLOCK -> ShortArray(RATE * seconds) { (clock(it.toLong()) * 32767 * .9).toInt().toShort() }
        Layer.STOVE -> stove().copyOf(RATE * seconds)
        else -> ShortArray(0)
    }

    const val SAMPLE_RATE = RATE
}
