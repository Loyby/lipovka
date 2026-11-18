package app.lipovka.ui

import app.lipovka.Platform

/*
 * The sound and the buzz each phone makes its own way: Android with AudioTrack and the vibrator,
 * the iPhone with AVAudioEngine and the Taptic Engine. The mixing and the sounds themselves are shared.
 */

/** A mono 16-bit stream to the speaker; [write] blocks while the device's buffer is full. */
expect class PcmStream(rate: Int, block: Int) {
    /** Opens the stream; false if the phone won't play now. */
    fun start(): Boolean

    /** Queues [n] samples of [data]; false once the stream has broken. */
    fun write(data: ShortArray, n: Int): Boolean

    /** How far the speaker has played, in ms of the stream's own time; null if it can't tell. */
    fun playedMs(): Long?

    fun close()
}

/** Short sounds, one player per [key]: a new play starts the sound over. */
expect object Clips {
    fun play(key: String, rate: Int, pcm: ShortArray, volume: Float)

    /** Drops [key]'s player (its sound is about to change). */
    fun forget(key: String)
}

/** The buzz in the hand: [levels] 0..255, one per [stepMs]. */
expect object Haptics {
    fun buzz(levels: IntArray, stepMs: Long)
}

/** A recording from assets/ambience by its name ("rain"), as 16-bit mono PCM at [rate]; null if it can't be read. */
expect fun decodeRecording(p: Platform, name: String, rate: Int): ShortArray?
