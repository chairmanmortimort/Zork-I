package com.thelightphone.zork

import com.thelightphone.zork.engine.ZMachine3
import com.thelightphone.zork.engine.ZScreen
import com.thelightphone.zork.engine.ZState
import com.thelightphone.zork.engine.ZStatus
import java.io.File

/**
 * Thin Kotlin wrapper around the Z-machine interpreter in
 * com.thelightphone.zork.engine (a Kotlin port of the Zplet engine).
 * Drives the story and exposes plain String I/O so the Light UI never touches
 * engine internals.
 *
 * Saves are explicit, player-driven and stored in three numbered slots in
 * filesDir. Each slot also keeps a .txt transcript because the Z-machine save
 * format does not persist screen text.
 */
class ZorkEngine(story: ByteArray, private val saveDir: File) {
    private val storyBytes = story
    private val screen = ZScreen()
    private val status = ZStatus()
    private var machine = ZMachine3(screen, status, story)
    private var lastCursor = 0

    init { start() }

    private fun start() {
        machine.restart()
        machine.run()
    }

    /** Returns the text printed since the last call. */
    fun drainOutput(): String {
        val w = machine.window[0]
        val sb = StringBuilder()
        val n = minOf(w.cursor, w.frameBuffer.size)
        for (i in lastCursor until n) {
            val c = w.frameBuffer[i]
            if (c != '\u0000') sb.append(c)
        }
        lastCursor = n
        return sb.toString()
    }

    fun statusLine(): String {
        val s = machine.status_line
        val where = s.location ?: ""
        return "$where   Score ${s.score}   Turns ${s.turns}"
    }

    /** Refresh the status line (room name / score / turns) from the engine. */
    fun updateStatusLine() {
        machine.update_status_line()
    }

    /** Feed one command line; the engine runs until it next waits for input. */
    fun submit(line: String) {
        machine.fillInputBuffer((line + "\n").toCharArray())
        machine.run()
    }

    fun restart() {
        lastCursor = 0
        machine.restart()
        machine.run()
    }

    companion object {
        const val NUM_SLOTS = 3
        private fun savFile(dir: File, slot: Int) = dir.resolve("zork$slot.sav")
        private fun txtFile(dir: File, slot: Int) = dir.resolve("zork$slot.txt")
    }

    fun saveSlot(slot: Int, transcript: String): Boolean {
        val f = savFile(saveDir, slot)
        val ok = ZState(machine).disk_save(f.absolutePath, machine.pc)
        if (ok) txtFile(saveDir, slot).writeText(transcript)
        return ok
    }

    fun saveNextFree(transcript: String): Int {
        val slot = (1..NUM_SLOTS).firstOrNull { !savFile(saveDir, it).exists() } ?: 1
        saveSlot(slot, transcript)
        return slot
    }

    fun loadSlot(slot: Int): String? {
        val f = savFile(saveDir, slot)
        if (!f.exists()) return null
        val zs = ZState(machine)
        if (!zs.restore_from_disk(f.absolutePath)) return null
        machine.restore(zs)
        lastCursor = 0
        drainOutput()
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
