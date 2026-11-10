package com.thelightphone.zork.engine

/** Status-line model (ported from Zplet's ZStatus). */
class ZStatus {
    var timegame: Boolean = false
    var location: String? = null
    var score: Int = 0
    var turns: Int = 0
    var hours: Int = 0
    var minutes: Int = 0

    fun update_score_line(location: String, score: Int, turns: Int) {
        this.timegame = false
        this.location = location
        this.score = score
        this.turns = turns
    }

    fun update_time_line(location: String, hours: Int, minutes: Int) {
        this.timegame = true
        this.location = location
        this.hours = hours
        this.minutes = minutes
    }

    override fun toString(): String {
        val sb = StringBuilder()
        sb.append(location)
        sb.append("                ")
        if (timegame) {
            if (hours < 10) sb.append("0")
            sb.append(hours)
            sb.append(":")
            sb.append(minutes)
        } else {
            sb.append("Score: ")
            sb.append(score)
            sb.append(" Turn: ")
            sb.append(turns)
        }
        return sb.toString()
    }
}
