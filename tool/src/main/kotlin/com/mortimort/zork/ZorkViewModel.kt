package com.mortimort.zork

import androidx.compose.runtime.*
import com.thelightphone.sdk.LightViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.io.File

/**
 * Owns the Z-machine engine and the visible transcript. One [LightViewModel]
 * drives the [ZorkScreen].
 *
 * Saves are explicit: typing "save" writes the current game to the next free
 * of three numbered slots. Slots are loaded/deleted from the top-left Saves
 * menu. The transcript is stored newest-first.
 */
class ZorkViewModel(private val saveDir: File) : LightViewModel<Unit>() {

    private val engine = ZorkEngine(StoryData.bytes, saveDir)

    val titleCard: String

    private val roomDescription: String

    init {
        val intro = engine.drainOutput()
        val split = intro.indexOf("\n\n")
        if (split >= 0) {
            titleCard = intro.substring(0, split)
            roomDescription = intro.substring(split + 2)
        } else {
            titleCard = intro
            roomDescription = ""
        }
    }

    private val _state = MutableStateFlow(
        ZorkState(
            transcript = roomDescription,
            statusLine = engine.statusLine(),
            input = "",
            lastSavedSlot = 0,
        ),
    )
    val state: StateFlow<ZorkState> = _state

    /**
     * Submit a command. If the command is "save", write to the next free slot
     * and report which slot was used. Otherwise run it through the engine.
     */
    fun submit(line: String) {
        val cmd = line.trim()
        if (cmd.isEmpty()) return

        if (cmd.equals("save", ignoreCase = true)) {
            val slot = engine.saveNextFree(_state.value.transcript)
            _state.value = _state.value.copy(
                transcript = "Game saved to slot $slot.\n\n${_state.value.transcript}",
                lastSavedSlot = slot,
            )
            return
        }

        engine.submit(cmd)
        val response = engine.drainOutput()
        val next = "> $cmd\n$response\n\n${_state.value.transcript}"
        _state.value = _state.value.copy(
            transcript = next,
            statusLine = engine.statusLine(),
            input = "",
        )
    }

    /** Load a numbered slot; returns true if it was restored. */
    fun loadSlot(slot: Int): Boolean {
        val transcript = engine.loadSlot(slot) ?: return false
        _state.value = _state.value.copy(
            transcript = transcript,
            statusLine = engine.statusLine(),
            input = "",
        )
        return true
    }

    fun deleteSlot(slot: Int) {
        engine.deleteSlot(slot)
    }

    fun listSlots(): List<ZorkEngine.SlotInfo> = engine.listSlots()

    fun setInput(s: String) {
        _state.value = _state.value.copy(input = s)
    }

    /** Reset the engine to the initial room, clear the transcript and all slots. */
    fun restart() {
        engine.restart()
        engine.deleteAll()
        val intro = engine.drainOutput()
        val split = intro.indexOf("\n\n")
        val room = if (split >= 0) intro.substring(split + 2) else ""
        _state.value = ZorkState(
            transcript = room,
            statusLine = engine.statusLine(),
            input = "",
            lastSavedSlot = 0,
        )
    }
}

data class ZorkState(
    val transcript: String,
    val statusLine: String,
    val input: String,
    val lastSavedSlot: Int,
)
