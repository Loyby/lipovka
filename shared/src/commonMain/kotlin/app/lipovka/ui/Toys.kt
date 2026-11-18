package app.lipovka.ui

import app.lipovka.compat.*
import kotlin.concurrent.Volatile

import app.lipovka.Platform
import app.lipovka.ui.art.Toy
import kotlin.math.sqrt

/**
 * What the toys in the pictures sound and feel like when tapped. All are real recordings, kept short in assets/sounds:
 * - the cat's purr: "Misha purrs" by AstronautDown (freesound.org/s/376378), CC0;
 * - the dog's woof: "WoofSmall2" by Wdomino (freesound.org/s/517131), CC0, pitched up, softer and muffled;
 * - the kettle's whistle: "Whistling kettle" by Work With Sounds / Konrad Gutkowski (Wikimedia Commons), CC BY 4.0;
 * - the clock's strike: "clock chime" by gsb1039 (freesound.org/s/415061), CC0.
 * The calendar's sheet tearing off is made up here: a short crackle of noise, fibre by fibre.
 */
object Toys {
    private const val TAG = "Toys"

    /** Seconds between the clock's strikes; matches the art's strike ticks. */
    private const val STRIKE_GAP = 0.88

    /** A recording as it sits in res/raw: 16-bit mono PCM. */
    private class Clip(val rate: Int, val pcm: ShortArray)

    private val clips = HashMap<String, Clip>()

    /** One player per toy; the clock's is remade when the hour (its number of strikes) changes. */
    private var trackStrikes = 0

    /** How loud the toys sound, 0..1, as set in the settings; the buzz in the hand stays as it is. */
    @Volatile var volume = 1f

    fun play(context: Ctx, toy: Toy, strikes: Int = 0, tear: Boolean = true) {
        val app = context.container.platform
        when (toy) {
            Toy.CAT -> clip(app, "purr")?.let { sound(toy, it); buzz(app, follow(it)) }
            Toy.DOG -> clip(app, "dog")?.let { sound(toy, it); buzz(app, follow(it, 90)) }
            Toy.KETTLE -> clip(app, "kettle")?.let { sound(toy, it); buzz(app, follow(it, 45)) }
            Toy.CLOCK -> clip(app, "chime")?.let { strike ->
                if (trackStrikes != strikes) { Clips.forget(toy.name); trackStrikes = strikes }
                sound(toy, strikes(strike, strikes))
                buzz(app, knocks(strikes))
            }
            Toy.CALENDAR -> if (tear) rip.let { sound(toy, it); buzz(app, follow(it, 70)) } else buzz(app, intArrayOf(30))
        }
    }

    /** Reads a WAV's format and samples; each is small enough to keep in memory whole. */
    private fun clip(p: Platform, name: String): Clip? = clips[name] ?: runCatching {
        val bytes = p.asset("sounds/$name.wav") ?: error("no sounds/$name.wav")
        fun int(at: Int) = (bytes[at].toInt() and 0xff) or ((bytes[at + 1].toInt() and 0xff) shl 8) or
            ((bytes[at + 2].toInt() and 0xff) shl 16) or ((bytes[at + 3].toInt() and 0xff) shl 24)
        fun short(at: Int) = ((bytes[at].toInt() and 0xff) or (bytes[at + 1].toInt() shl 8)).toShort()
        var rate = 0
        var at = 12
        while (at + 8 <= bytes.size) {
            val id = bytes.decodeToString(at, at + 4)
            val size = int(at + 4)
            if (id == "fmt ") rate = int(at + 12)
            if (id == "data") {
                val n = minOf(size, bytes.size - at - 8) / 2
                return@runCatching Clip(rate, ShortArray(n) { short(at + 8 + it * 2) })
            }
            at += 8 + size + (size and 1)
        }
        error("no data in $name")
    }.onFailure { Log.w(TAG, "load", it) }.getOrNull()?.also { clips[name] = it }

    /** Paper tearing along the perforation: a burst of fine noise, its loudness jumping as each fibre gives. */
    private val rip: Clip by lazy {
        val rate = 22050
        val n = (rate * .34).toInt()
        val r = Random(7)
        var last = 0.0
        var fibre = 0.0
        Clip(rate, ShortArray(n) { i ->
            val t = i.toDouble() / n
            if (i % (rate / 90) == 0) fibre = .3 + r.nextDouble() * .7
            val noise = r.nextGaussian()
            // the difference of white noise leans it towards the hiss of paper rather than a rumble
            val hiss = noise - last
            last = noise
            val shape = minOf(t * 30, 1.0) * (1 - t) * (1 - t)
            (hiss * fibre * shape * 9000).coerceIn(-32767.0, 32767.0).toInt().toShort()
        })
    }

    /** The clock's strike rung [n] times, each ringing on under the next. */
    private fun strikes(one: Clip, n: Int): Clip {
        val gap = (one.rate * STRIKE_GAP).toInt()
        val mix = FloatArray(gap * (n - 1) + one.pcm.size)
        for (k in 0 until n) for (i in one.pcm.indices) mix[k * gap + i] += one.pcm[i].toFloat()
        val top = mix.maxOf { kotlin.math.abs(it) }.coerceAtLeast(Short.MAX_VALUE.toFloat())
        return Clip(one.rate, ShortArray(mix.size) { (mix[it] / top * Short.MAX_VALUE).toInt().toShort() })
    }

    /** Plays [clip] from the start on [toy]'s own player. */
    private fun sound(toy: Toy, clip: Clip) {
        runCatching { Clips.play(toy.name, clip.rate, clip.pcm, volume * volume) }.onFailure { Log.w(TAG, "play $toy", it) }
    }

    /** A buzz pattern as steps of [STEP] ms: soft pulses as strong as [clip] is loud at that moment, up to [most]. */
    private fun follow(clip: Clip, most: Int = 100): IntArray {
        val win = (clip.rate * STEP / 1000).toInt()
        val loud = DoubleArray(clip.pcm.size / win) { w ->
            var sum = 0.0
            for (i in w * win until (w + 1) * win) sum += clip.pcm[i].toDouble() * clip.pcm[i]
            sqrt(sum / win)
        }
        val top = loud.maxOrNull()?.takeIf { it > 0 } ?: 1.0
        return IntArray(loud.size) { (loud[it] / top * most).toInt() }
    }

    /** One firm knock in the hand for each of the clock's [n] strikes. */
    private fun knocks(n: Int): IntArray {
        val gap = (STRIKE_GAP * 1000 / STEP).toInt()
        return IntArray(gap * n) { if (it % gap == 0) 160 else 0 }
    }

    /** Plays [levels] (one per [STEP] ms, 0..255) as short soft pulses, so it's felt as a texture, not a drone. */
    @Suppress("UNUSED_PARAMETER")
    private fun buzz(p: Platform, levels: IntArray) {
        if (levels.isEmpty()) return
        runCatching { Haptics.buzz(levels, STEP) }.onFailure { Log.w(TAG, "buzz", it) }
    }

    private const val STEP = 40L
}
