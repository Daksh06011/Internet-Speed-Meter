package com.netspeedtest.speedtest

/**
 * Plain-language interpretation of a result. Every threshold lives here, in one place,
 * with the reasoning behind it, so labels never overstate what a connection can do.
 *
 * Sources for the figures (rounded up to stay conservative):
 *  - Streaming: major services recommend ~3 Mbps for SD, 5 Mbps for HD and 15–25 Mbps
 *    for 4K. We require 25 Mbps for "4K ready".
 *  - Video calls: popular apps recommend ~3.8 Mbps down / 3 Mbps up for HD group calls
 *    and ~1.5 Mbps each way for 720p one-to-one calls, with latency below 150 ms.
 *  - Gaming: competitive online play is comfortable below 30 ms with little jitter;
 *    above 100 ms most real-time games feel delayed.
 *  - Browsing: modern pages load well from 10 Mbps; latency dominates perceived speed.
 */
object QualityThresholds {
    const val BROWSE_EXCELLENT_MBPS = 25.0
    const val BROWSE_EXCELLENT_PING = 60.0
    const val BROWSE_GOOD_MBPS = 10.0
    const val BROWSE_GOOD_PING = 120.0
    const val BROWSE_FAIR_MBPS = 2.0

    const val STREAM_4K_MBPS = 25.0
    const val STREAM_HD_MBPS = 5.0
    const val STREAM_SD_MBPS = 3.0

    const val GAME_EXCELLENT_PING = 30.0
    const val GAME_EXCELLENT_JITTER = 8.0
    const val GAME_GOOD_PING = 60.0
    const val GAME_GOOD_JITTER = 20.0
    const val GAME_PLAYABLE_PING = 100.0

    const val CALL_HD_DOWN = 4.0
    const val CALL_HD_UP = 3.0
    const val CALL_HD_PING = 100.0
    const val CALL_HD_JITTER = 30.0
    const val CALL_GOOD_MBPS = 1.5
    const val CALL_GOOD_PING = 150.0
    const val CALL_FAIR_MBPS = 0.6
}

enum class Grade { Excellent, Good, Fair, Poor }

data class Verdict(val activity: String, val grade: Grade, val label: String)

object QualityRating {
    fun evaluate(downMbps: Double, upMbps: Double, pingMs: Double, jitterMs: Double): List<Verdict> = with(QualityThresholds) {
        listOf(
            when {
                downMbps >= BROWSE_EXCELLENT_MBPS && pingMs <= BROWSE_EXCELLENT_PING -> Verdict("Browsing", Grade.Excellent, "Excellent")
                downMbps >= BROWSE_GOOD_MBPS && pingMs <= BROWSE_GOOD_PING -> Verdict("Browsing", Grade.Good, "Good")
                downMbps >= BROWSE_FAIR_MBPS -> Verdict("Browsing", Grade.Fair, "Usable")
                else -> Verdict("Browsing", Grade.Poor, "Slow")
            },
            when {
                downMbps >= STREAM_4K_MBPS -> Verdict("Streaming", Grade.Excellent, "4K ready")
                downMbps >= STREAM_HD_MBPS -> Verdict("Streaming", Grade.Good, "HD ready")
                downMbps >= STREAM_SD_MBPS -> Verdict("Streaming", Grade.Fair, "SD only")
                else -> Verdict("Streaming", Grade.Poor, "May buffer")
            },
            when {
                pingMs <= GAME_EXCELLENT_PING && jitterMs <= GAME_EXCELLENT_JITTER -> Verdict("Gaming", Grade.Excellent, "Low latency")
                pingMs <= GAME_GOOD_PING && jitterMs <= GAME_GOOD_JITTER -> Verdict("Gaming", Grade.Good, "Good")
                pingMs <= GAME_PLAYABLE_PING -> Verdict("Gaming", Grade.Fair, "Playable")
                else -> Verdict("Gaming", Grade.Poor, "High latency")
            },
            when {
                downMbps >= CALL_HD_DOWN && upMbps >= CALL_HD_UP && pingMs <= CALL_HD_PING && jitterMs <= CALL_HD_JITTER ->
                    Verdict("Video calls", Grade.Excellent, "HD group calls")
                downMbps >= CALL_GOOD_MBPS && upMbps >= CALL_GOOD_MBPS && pingMs <= CALL_GOOD_PING ->
                    Verdict("Video calls", Grade.Good, "HD 1:1 calls")
                downMbps >= CALL_FAIR_MBPS && upMbps >= CALL_FAIR_MBPS -> Verdict("Video calls", Grade.Fair, "Audio & SD video")
                else -> Verdict("Video calls", Grade.Poor, "Unreliable")
            },
        )
    }
}
