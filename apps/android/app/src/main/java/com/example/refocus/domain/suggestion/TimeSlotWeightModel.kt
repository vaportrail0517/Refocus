package com.example.refocus.domain.suggestion

import com.example.refocus.core.model.SuggestionTimeSlot
import java.time.Instant
import java.time.ZoneId
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.min

interface TimeSlotWeightModel {
    /**
     * @return 0.0..1.0（高いほど「今の時刻にそのラベルが合う」）
     */
    fun weight(
        slot: SuggestionTimeSlot,
        nowMillis: Long,
        zoneId: ZoneId = ZoneId.systemDefault(),
    ): Double
}

class GaussianCircularTimeSlotWeightModel(
    private val profiles: Map<SuggestionTimeSlot, GaussianProfile> = defaultProfiles(),
) : TimeSlotWeightModel {
    data class GaussianProfile(
        val meanMinutes: Int, // 0..1439
        val sigmaMinutes: Double,
    )

    override fun weight(
        slot: SuggestionTimeSlot,
        nowMillis: Long,
        zoneId: ZoneId,
    ): Double {
        if (slot == SuggestionTimeSlot.Anytime) return 1.0

        val p = profiles[slot] ?: return 0.0
        val minutes =
            Instant
                .ofEpochMilli(nowMillis)
                .atZone(zoneId)
                .toLocalTime()
                .let { it.hour * 60 + it.minute } // 0..1439

        val d = circularDistance(minutes, p.meanMinutes, 24 * 60)
        val sigma = p.sigmaMinutes

        // 正規分布の形（正規化係数は不要。相対比較できればよい）
        return exp(-(d.toDouble() * d.toDouble()) / (2.0 * sigma * sigma))
    }

    private fun circularDistance(
        x: Int,
        mu: Int,
        period: Int,
    ): Int {
        val raw = abs(x - mu)
        return min(raw, period - raw)
    }

    companion object {
        fun defaultProfiles(): Map<SuggestionTimeSlot, GaussianProfile> =
            mapOf(
                SuggestionTimeSlot.Dawn to
                    GaussianProfile(
                        meanMinutes = 5 * 60 + 30,
                        sigmaMinutes = 90.0,
                    ),
                // 05:30 ±1.5h
                SuggestionTimeSlot.Morning to
                    GaussianProfile(
                        meanMinutes = 9 * 60 + 30,
                        sigmaMinutes = 150.0,
                    ),
                // 09:30 ±2.5h
                SuggestionTimeSlot.Noon to
                    GaussianProfile(
                        meanMinutes = 12 * 60 + 30,
                        sigmaMinutes = 90.0,
                    ),
                // 12:30 ±1.5h
                SuggestionTimeSlot.Afternoon to
                    GaussianProfile(
                        meanMinutes = 15 * 60 + 30,
                        sigmaMinutes = 150.0,
                    ),
                // 15:30 ±2.5h
                SuggestionTimeSlot.Evening to
                    GaussianProfile(
                        meanMinutes = 18 * 60 + 0,
                        sigmaMinutes = 120.0,
                    ),
                // 18:00 ±2h
                SuggestionTimeSlot.Night to
                    GaussianProfile(
                        meanMinutes = 21 * 60 + 0,
                        sigmaMinutes = 150.0,
                    ),
                // 21:00 ±2.5h
                SuggestionTimeSlot.LateNight to
                    GaussianProfile(
                        meanMinutes = 0 * 60 + 30,
                        sigmaMinutes = 150.0,
                    ),
                // 00:30 ±2.5h（円環で日跨ぎOK）
            )
    }
}
