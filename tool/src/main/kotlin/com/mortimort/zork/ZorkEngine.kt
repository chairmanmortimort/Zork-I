package com.thelightphone.zork

import com.thelightphone.zork.engine.ZMachine
import com.thelightphone.zork.engine.ZStoryLoader
import com.thelightphone.zork.engine.ZOutputBuffer
import java.io.File

/**
 * Thin Kotlin wrapper around the pure-Kotlin Z-machine interpreter
 * (com.thelightphone.zork.engine). Drives the story and exposes plain String I/O so the Light UI
 * never touches the engine internals.
 *
 * Saves are explicit, player-driven and stored in three numbered slots in
 * filesDir. Each slot also keeps a .txt transcript because the Z-machine save
 * format does not persist screen text.
 */
class ZorkEngine(story: ByteArray, private val saveDir: File) {
    private val storyBytes = story
    private val output = ZOutputBuffer()
    private var machine = ZMachine(ZStoryLoader.fromBytes(story), output)

    init { machine.start() }

    /** Returns the text printed since the last call, then clears the buffer. */
    fun drainOutput(): String = output.take()

    fun statusLine(): String {
        val (loc, score, turns) = machine.status()
        val where = machine.objectName(loc)
        return "$where   Score $score   Turns $turns"
    }

    /** Feed one command line; the engine runs until it next waits for input. */
    fun submit(line: String) { machine.submitInput(line) }

    fun restart() {
        output.take()
        machine = ZMachine(ZStoryLoader.fromBytes(storyBytes), output)
        machine.start()
    }

    companion object {
        const val NUM_SLOTS = 3
        private fun savFile(dir: File, slot: Int) = dir.resolve("zork$slot.sav")
        private fun txtFile(dir: File, slot: Int) = dir.resolve("zork$slot.txt")
    }

    fun saveSlot(slot: Int, transcript: String) {
        savFile(saveDir, slot).writeBytes(machine.saveState())
        txtFile(saveDir, slot).writeText(transcript)
    }

    fun saveNextFree(transcript: String): Int {
        val slot = (1..NUM_SLOTS).firstOrNull { !savFile(saveDir, it).exists() } ?: 1
        saveSlot(slot, transcript)
        return slot
    }

    fun loadSlot(slot: Int): String? {
        val f = savFile(saveDir, slot)
        if (!f.exists()) return null
        if (!machine.restoreState(f.readBytes())) return null
        val txt = txtFile(saveDir, slot)
        return if (txt.exists()) txt.readText() else null
    }

    fun deleteSlot(slot: Int) {
        savFile(saveDir, slot).delete()
        txtFile(saveDir, slot).delete()
    }

    fun listSlots(): List<SlotInfo> = (1..NUM_SLOTS).map { slot ->
        val f = savFile(saveDir, slot)
        SlotInfo(slot, f.exists(), if (f.exists()) f.lastModified() else 0L)
    }

    fun deleteAll() { for (slot in 1..NUM_SLOTS) deleteSlot(slot) }

    data class SlotInfo(val slot: Int, val exists: Boolean, val lastModified: Long)
}
