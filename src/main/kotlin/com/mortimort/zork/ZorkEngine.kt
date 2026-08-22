package com.mortimort.zork

import de.onyxbits.textfiction.zengine.GrueException
import de.onyxbits.textfiction.zengine.ZMachine3
import de.onyxbits.textfiction.zengine.ZScreen
import de.onyxbits.textfiction.zengine.ZState
import de.onyxbits.textfiction.zengine.ZStatus
import java.io.File

/**
 * Thin Kotlin wrapper around the vendored pure-Java Z-machine interpreter
 * (de.onyxbits.textfiction.zengine, Apache-2.0). Drives the story and exposes
 * plain String I/O so the Light UI never touches the engine internals.
 *
 * Saves are explicit, player-driven ("save" command) and stored in three
 * numbered slots (zork1..zork3) in filesDir. Each slot also keeps a .txt
 * transcript because the Z-machine save format does not persist screen text.
 */
class ZorkEngine(story: ByteArray, private val saveDir: File) {
    private val screen = ZScreen()
    private val status = ZStatus()
    private val machine = ZMachine3(screen, status, story)

    init {
        machine.restart()
        machine.run()
    }

    /** Returns the text printed since the last call, then clears the buffer. */
    fun drainOutput(): String {
        val w = machine.window[0]
        val len = w.cursor
        val text = String(w.frameBuffer, 0, len)
        w.cursor = 0
        return text
    }

    fun statusLine(): String =
        "${status.location ?: ""}   Score ${status.score}   Turns ${status.turns}"

    /** Feed one command line; the engine runs until it next waits for input. */
    fun submit(line: String) {
        try {
            machine.fillInputBuffer((line + "\n").toCharArray())
            machine.run()
        } catch (e: GrueException) {
            // Engine fatal error (stack overflow, etc.) — swallow so the UI
            // stays alive; the transcript simply doesn't grow for this command.
        }
    }

    fun restart() {
        machine.restart()
        machine.run()
    }

    companion object {
        const val NUM_SLOTS = 3
        private fun savFile(dir: File, slot: Int) = dir.resolve("zork$slot.sav")
        private fun txtFile(dir: File, slot: Int) = dir.resolve("zork$slot.txt")
    }

    /** Snapshot the live machine state into a numbered slot. Returns the slot used. */
    fun saveSlot(slot: Int, transcript: String) {
        val snapshot = ZState(machine)
        snapshot.save_current()
        snapshot.disk_save(savFile(saveDir, slot).absolutePath, machine.pc)
        txtFile(saveDir, slot).writeText(transcript)
    }

    /** Write to the next free slot, or slot 1 if all are occupied. */
    fun saveNextFree(transcript: String): Int {
        val slot = (1..NUM_SLOTS).firstOrNull { !savFile(saveDir, it).exists() } ?: 1
        saveSlot(slot, transcript)
        return slot
    }

    /** Restore a numbered slot into the live machine. Returns the transcript, or null. */
    fun loadSlot(slot: Int): String? {
        val f = savFile(saveDir, slot)
        if (!f.exists()) return null
        val snapshot = ZState(machine)
        val ok = snapshot.restore_from_disk(f.absolutePath)
        if (!ok) return null
        snapshot.restore_saved()
        val txt = txtFile(saveDir, slot)
        return if (txt.exists()) txt.readText() else null
    }

    /** Delete a numbered slot (both files). */
    fun deleteSlot(slot: Int) {
        savFile(saveDir, slot).delete()
        txtFile(saveDir, slot).delete()
    }

    /** Metadata for every slot, for the saves screen. */
    fun listSlots(): List<SlotInfo> =
        (1..NUM_SLOTS).map { slot ->
            val f = savFile(saveDir, slot)
            SlotInfo(
                slot = slot,
                exists = f.exists(),
                lastModified = if (f.exists()) f.lastModified() else 0L,
            )
        }

    /** Clear all three slots (used by restart). */
    fun deleteAll() {
        for (slot in 1..NUM_SLOTS) deleteSlot(slot)
    }

    data class SlotInfo(
        val slot: Int,
        val exists: Boolean,
        val lastModified: Long,
    )
}
