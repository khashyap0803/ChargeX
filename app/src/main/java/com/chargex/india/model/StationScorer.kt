package com.chargex.india.model

import com.chargex.india.utils.distanceBetween
import kotlin.math.abs

/**
 * Multi-Objective Station Scoring and Ranking Engine.
 *
 * ## Mathematical Foundation:
 *
 * ### Weighted Cost Function:
 *     Score(s) = w₁·D̃(s) + w₂·W̃(s) + w₃·(1 - P̃(s)) + w₄·(1 - R(s))
 *
 * Where:
 * - D̃(s) = Normalized distance (0-1), lower = closer = better
 * - W̃(s) = Normalized wait time (0-1), lower = shorter wait = better
 * - P̃(s) = Normalized charging power (0-1), higher = faster = better
 * - R(s) = Reliability score (0-1), higher = more reliable = better
 * - w₁ + w₂ + w₃ + w₄ = 1.0 (user-adjustable weights)
 *
 * ### Pareto Dominance:
 * Station A **dominates** Station B if A is better in ALL objectives.
 * Pareto-optimal stations form the "efficient frontier" — no station
 * in this set can be improved in one objective without worsening another.
 *
 * ### Normalization (Min-Max):
 *     x̃ = (x - x_min) / (x_max - x_min)
 *
 * ### References:
 * - Deb, K. (2001) "Multi-Objective Optimization using Evolutionary Algorithms"
 * - IEEE: "Multi-Criteria Decision Making for EV Charging Station Selection" (2022)
 */
object StationScorer {

    /**
     * User-adjustable weights for the multi-objective scoring function.
     *
     * Default weights are calibrated for Indian driving conditions:
     * - Distance is highest priority (range anxiety)
     * - Wait time is second (time is valuable)
     * - Charging speed is third (faster = less time spent)
     * - Reliability is a safety net (avoid broken stations)
     */
    data class ScoringWeights(
        val distanceWeight: Double = 0.35,   // w₁: How important is proximity?
        val waitTimeWeight: Double = 0.30,   // w₂: How important is short wait?
        val powerWeight: Double = 0.25,      // w₃: How important is charging speed?
        val reliabilityWeight: Double = 0.10 // w₄: How important is station reliability?
    ) {
        init {
            val sum = distanceWeight + waitTimeWeight + powerWeight + reliabilityWeight
            require(abs(sum - 1.0) < 0.01) {
                "Weights must sum to 1.0, got $sum"
            }
        }
    }

    /**
     * Scores a single station on the multi-objective cost function.
     *
     * @param distanceKm Distance from user to station in km
     * @param waitTimeMinutes Predicted wait time from WaitTimeEngine
     * @param maxPowerKw Maximum charging power at station
     * @param hasFaultReport Whether the station has reported faults
     * @param maxDistanceKm Maximum distance in the dataset (for normalization)
     * @param maxWaitTime Maximum wait time in the dataset (for normalization)
     * @param maxPowerInDataset Maximum power in the dataset (for normalization)
     * @param weights User's objective weights
     * @return Score from 0.0 (best) to 1.0 (worst)
     */
    fun scoreStation(
        distanceKm: Double,
        waitTimeMinutes: Int,
        maxPowerKw: Double,
        hasFaultReport: Boolean,
        maxDistanceKm: Double = 200.0,
        maxWaitTime: Int = 120,
        maxPowerInDataset: Double = 150.0,
        weights: ScoringWeights = ScoringWeights()
    ): StationScore {
        // Min-Max Normalization (0 to 1)
        val normalizedDistance = (distanceKm / maxDistanceKm).coerceIn(0.0, 1.0)
        val normalizedWait = (waitTimeMinutes.toDouble() / maxWaitTime).coerceIn(0.0, 1.0)
        val normalizedPower = (maxPowerKw / maxPowerInDataset).coerceIn(0.0, 1.0)
        val reliability = if (hasFaultReport) 0.0 else 1.0

        // Weighted Cost Function
        // Lower score = better station
        val score = weights.distanceWeight * normalizedDistance +
                    weights.waitTimeWeight * normalizedWait +
                    weights.powerWeight * (1.0 - normalizedPower) + // Invert: higher power = lower cost
                    weights.reliabilityWeight * (1.0 - reliability)   // Invert: higher reliability = lower cost

        return StationScore(
            totalScore = score,
            distanceScore = normalizedDistance,
            waitTimeScore = normalizedWait,
            powerScore = normalizedPower,
            reliabilityScore = reliability,
            weights = weights
        )
    }

    /**
     * Ranks a list of stations from best to worst using multi-objective scoring.
     *
     * Algorithm:
     * 1. Calculate min/max values for normalization
     * 2. Score each station using the weighted cost function
     * 3. Sort by total score (ascending — lower is better)
     *
     * @param stations List of (ChargeLocation, distanceKm, waitTimeMinutes) tuples
     * @param weights Scoring weights
     * @return Sorted list of RankedStation objects
     */
    fun rankStations(
        stations: List<StationData>,
        weights: ScoringWeights = ScoringWeights()
    ): List<RankedStation> {
        if (stations.isEmpty()) return emptyList()

        // Find normalization bounds
        val maxDistance = stations.maxOf { it.distanceKm }.coerceAtLeast(1.0)
        val maxWait = stations.maxOf { it.waitTimeMinutes }.coerceAtLeast(1)
        val maxPower = stations.maxOf { it.maxPowerKw }.coerceAtLeast(1.0)

        // Score and rank
        return stations.map { data ->
            val score = scoreStation(
                distanceKm = data.distanceKm,
                waitTimeMinutes = data.waitTimeMinutes,
                maxPowerKw = data.maxPowerKw,
                hasFaultReport = data.hasFaultReport,
                maxDistanceKm = maxDistance,
                maxWaitTime = maxWait,
                maxPowerInDataset = maxPower,
                weights = weights
            )
            RankedStation(data, score)
        }.sortedBy { it.score.totalScore }
    }

    /**
     * Identifies Pareto-optimal stations from a list.
     *
     * A station is Pareto-optimal if no other station is better
     * in ALL objectives simultaneously.
     *
     * @param stations List of station data
     * @return List of Pareto-optimal stations (the "efficient frontier")
     */
    fun findParetoOptimal(stations: List<StationData>): List<StationData> {
        return stations.filter { candidate ->
            stations.none { other ->
                other !== candidate &&
                other.distanceKm <= candidate.distanceKm &&
                other.waitTimeMinutes <= candidate.waitTimeMinutes &&
                other.maxPowerKw >= candidate.maxPowerKw &&
                !other.hasFaultReport &&
                (other.distanceKm < candidate.distanceKm ||
                 other.waitTimeMinutes < candidate.waitTimeMinutes ||
                 other.maxPowerKw > candidate.maxPowerKw)
            }
        }
    }
}

/**
 * Input data for station scoring.
 */
data class StationData(
    val stationId: Long,
    val stationName: String,
    val distanceKm: Double,
    val waitTimeMinutes: Int,
    val maxPowerKw: Double,
    val hasFaultReport: Boolean
)

/**
 * Detailed score breakdown for a single station.
 */
data class StationScore(
    val totalScore: Double,        // 0.0 (best) to 1.0 (worst)
    val distanceScore: Double,     // Normalized distance component
    val waitTimeScore: Double,     // Normalized wait time component
    val powerScore: Double,        // Normalized power component
    val reliabilityScore: Double,  // 0.0 (faulted) or 1.0 (operational)
    val weights: StationScorer.ScoringWeights
) {
    /** Display-friendly overall rating out of 5 stars */
    val starRating: Double get() = ((1.0 - totalScore) * 5.0).coerceIn(0.0, 5.0)

    /** Display-friendly percentage score */
    val percentScore: Int get() = ((1.0 - totalScore) * 100).toInt().coerceIn(0, 100)
}

/**
 * A station paired with its multi-objective score and rank.
 */
data class RankedStation(
    val data: StationData,
    val score: StationScore
)
