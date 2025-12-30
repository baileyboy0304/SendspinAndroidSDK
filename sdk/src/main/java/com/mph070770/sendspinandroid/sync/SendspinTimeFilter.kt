package com.mph070770.sendspinandroid.sync

import kotlin.math.sqrt

/**
 * Two-dimensional Kalman filter for NTP-style time synchronization.
 *
 * Direct port of the TypeScript/Python implementation from aiosendspin.
 *
 * This class implements a time synchronization filter that tracks both the timestamp
 * offset and clock drift rate between a client and server. It processes measurements
 * obtained with NTP-style time messages that contain round-trip timing information to
 * optimally estimate the time relationship while accounting for network latency
 * uncertainty.
 *
 * The filter maintains a 2D state vector [offset, drift] with associated covariance
 * matrix to track estimation uncertainty. An adaptive forgetting factor helps the
 * filter recover quickly from network disruptions or server clock adjustments.
 *
 * ## Algorithm
 * Uses NTP-style measurements to estimate clock offset:
 * ```
 * offset = ((T2 - T1) + (T3 - T4)) / 2
 * ```
 * where:
 * - T1 = client_transmitted
 * - T2 = server_received
 * - T3 = server_transmitted
 * - T4 = client_received
 *
 * The Kalman filter smooths these measurements and tracks drift over time.
 *
 * ## Time Conversion
 * - Server to Client: T_client = (T_server - offset + drift * T_last) / (1 + drift)
 * - Client to Server: T_server = T_client + offset + drift * (T_client - T_last)
 */
internal class SendspinTimeFilter(
    processStdDev: Double = 0.01,      // Process noise standard deviation (µs)
    forgetFactor: Double = 1.001       // Forgetting factor for adaptive recovery
) {
    companion object {
        // Residual threshold as fraction of max_error for triggering adaptive forgetting
        private const val ADAPTIVE_FORGETTING_CUTOFF = 0.75

        // Minimum measurements before adaptive forgetting is enabled
        private const val ADAPTIVE_FORGETTING_THRESHOLD = 100

        // Minimum measurements for basic readiness
        private const val MIN_MEASUREMENTS_READY = 2

        // Minimum measurements for convergence (used for audio playback)
        private const val MIN_MEASUREMENTS_CONVERGED = 12

        // Maximum acceptable error for considering sync converged (microseconds)
        private const val CONVERGED_ERROR_THRESHOLD = 5000.0 // 5ms
    }

    // Filter state
    private var lastUpdate: Long = 0
    private var count: Int = 0

    private var offset: Double = 0.0
    private var drift: Double = 0.0

    // Covariance matrix elements: P = [[P00, P01], [P10, P11]]
    private var offsetCovariance: Double = Double.POSITIVE_INFINITY  // P[0,0]
    private var offsetDriftCovariance: Double = 0.0                  // P[0,1] = P[1,0]
    private var driftCovariance: Double = 0.0                        // P[1,1]

    // Filter parameters
    private val processVariance: Double = processStdDev * processStdDev
    private val forgetVarianceFactor: Double = forgetFactor * forgetFactor

    /**
     * Process a new time synchronization measurement through the Kalman filter.
     *
     * Updates the filter's offset and drift estimates using a two-stage Kalman filter
     * algorithm: predict based on the drift model then correct using the new
     * measurement. The measurement uncertainty is derived from the network round-trip
     * delay.
     *
     * @param measurement Computed offset from NTP-style exchange: ((T2-T1)+(T3-T4))/2 in microseconds
     * @param maxError Half the round-trip delay: ((T4-T1)-(T3-T2))/2, representing maximum measurement uncertainty in microseconds
     * @param timeAdded Client timestamp when this measurement was taken in microseconds
     */
    fun update(measurement: Double, maxError: Double, timeAdded: Long) {
        if (timeAdded == lastUpdate) {
            // Skip duplicate timestamps to avoid division by zero in drift calculation
            return
        }

        val dt = (timeAdded - lastUpdate).toDouble()
        lastUpdate = timeAdded

        val updateStdDev = maxError
        val measurementVariance = updateStdDev * updateStdDev

        // Filter initialization: First measurement establishes offset baseline
        if (count <= 0) {
            count = 1

            offset = measurement
            offsetCovariance = measurementVariance
            drift = 0.0  // No drift information available yet

            return
        }

        // Second measurement: Initial drift estimation from finite differences
        if (count == 1) {
            count = 2

            drift = (measurement - offset) / dt
            offset = measurement

            // Drift variance estimated from propagation of offset uncertainties
            driftCovariance = (offsetCovariance + measurementVariance) / dt
            offsetCovariance = measurementVariance

            return
        }

        /// Kalman Prediction Step ///
        // State prediction: x_k|k-1 = F * x_k-1|k-1
        val predictedOffset = offset + drift * dt

        // Covariance prediction: P_k|k-1 = F * P_k-1|k-1 * F^T + Q
        // State transition matrix F = [1, dt; 0, 1]
        val dtSquared = dt * dt

        // Process noise only applied to offset (modeling clock jitter/wander)
        // Drift assumed stable (no process noise)
        val driftProcessVariance = 0.0
        var newDriftCovariance = driftCovariance + driftProcessVariance

        val offsetDriftProcessVariance = 0.0
        var newOffsetDriftCovariance =
            offsetDriftCovariance +
                    driftCovariance * dt +
                    offsetDriftProcessVariance

        val offsetProcessVariance = dt * processVariance
        var newOffsetCovariance =
            offsetCovariance +
                    2.0 * offsetDriftCovariance * dt +
                    driftCovariance * dtSquared +
                    offsetProcessVariance

        /// Innovation and Adaptive Forgetting ///
        val residual = measurement - predictedOffset  // Innovation: y_k = z_k - H * x_k|k-1
        val maxResidualCutoff = maxError * ADAPTIVE_FORGETTING_CUTOFF

        if (count < ADAPTIVE_FORGETTING_THRESHOLD) {
            // Build sufficient history before enabling adaptive forgetting
            count += 1
        } else if (residual > maxResidualCutoff) {
            // Large prediction error detected - likely network disruption or clock adjustment
            // Apply forgetting factor to increase Kalman gain and accelerate convergence
            newDriftCovariance *= forgetVarianceFactor
            newOffsetDriftCovariance *= forgetVarianceFactor
            newOffsetCovariance *= forgetVarianceFactor
        }

        /// Kalman Update Step ///
        // Innovation covariance: S = H * P * H^T + R, where H = [1, 0]
        val uncertainty = 1.0 / (newOffsetCovariance + measurementVariance)

        // Kalman gain: K = P * H^T * S^(-1)
        val offsetGain = newOffsetCovariance * uncertainty
        val driftGain = newOffsetDriftCovariance * uncertainty

        // State update: x_k|k = x_k|k-1 + K * y_k
        offset = predictedOffset + offsetGain * residual
        drift += driftGain * residual

        // Covariance update: P_k|k = (I - K*H) * P_k|k-1
        // Using simplified form to ensure numerical stability
        driftCovariance = newDriftCovariance - driftGain * newOffsetDriftCovariance
        offsetDriftCovariance = newOffsetDriftCovariance - driftGain * newOffsetCovariance
        offsetCovariance = newOffsetCovariance - offsetGain * newOffsetCovariance
    }

    /**
     * Convenience function matching the old API signature.
     */
    fun onServerTime(
        clientTransmittedUs: Long,
        clientReceivedUs: Long,
        serverReceivedUs: Long,
        serverTransmittedUs: Long
    ) {
        val t0 = clientTransmittedUs.toDouble()
        val t3 = clientReceivedUs.toDouble()
        val s1 = serverReceivedUs.toDouble()
        val s2 = serverTransmittedUs.toDouble()

        // Calculate RTT and server processing time
        val rtt = (t3 - t0).coerceAtLeast(0.0)
        val serverProc = (s2 - s1).coerceAtLeast(0.0)

        // Estimate one-way delay (assuming symmetric network)
        val oneWay = ((rtt - serverProc) / 2.0).coerceAtLeast(0.0)

        // Calculate offset: server_time - client_time
        // Using midpoint method for better accuracy
        val clientMid = t0 + rtt / 2.0
        val serverMid = s1 + serverProc / 2.0
        val measuredOffset = serverMid - clientMid

        // Maximum error is the one-way delay (uncertainty in timing)
        val maxError = oneWay.coerceAtLeast(100.0) // At least 100µs uncertainty

        // Add measurement to Kalman filter
        update(measuredOffset, maxError, clientReceivedUs)
    }

    /**
     * Convert a client timestamp to the equivalent server timestamp.
     *
     * Applies the current offset and drift compensation to transform from client time
     * domain to server time domain. The transformation accounts for both static offset
     * and dynamic drift accumulated since the last filter update.
     *
     * @param clientTimeUs Client timestamp in microseconds
     * @return Equivalent server timestamp in microseconds
     */
    fun clientToServer(clientTimeUs: Long): Long {
        // Transform: T_server = T_client + offset + drift * (T_client - T_last_update)
        val dt = (clientTimeUs - lastUpdate).toDouble()
        val instantOffset = offset + drift * dt
        return clientTimeUs + instantOffset.toLong()
    }

    /**
     * Convert a server timestamp to the equivalent client timestamp.
     *
     * Inverts the time transformation to convert from server time domain to client
     * time domain. Accounts for both offset and drift effects in the inverse
     * transformation.
     *
     * @param serverTimeUs Server timestamp in microseconds
     * @return Equivalent client timestamp in microseconds
     */
    fun serverToClient(serverTimeUs: Long): Long {
        // Inverse transform solving for T_client:
        // T_server = T_client + offset + drift * (T_client - T_last_update)
        // T_server = (1 + drift) * T_client + offset - drift * T_last_update
        // T_client = (T_server - offset + drift * T_last_update) / (1 + drift)

        val result = (serverTimeUs - offset + drift * lastUpdate) / (1.0 + drift)
        return result.toLong()
    }

    /**
     * Reset the filter state.
     */
    fun reset() {
        count = 0
        offset = 0.0
        drift = 0.0
        offsetCovariance = Double.POSITIVE_INFINITY
        offsetDriftCovariance = 0.0
        driftCovariance = 0.0
        lastUpdate = 0
    }

    /**
     * Get the number of time sync measurements processed.
     */
    fun getMeasurementCount(): Int = count

    /**
     * Check if time synchronization is ready for basic use.
     *
     * Time sync is considered ready when at least 2 measurements have been
     * collected and the offset covariance is finite (not infinite).
     */
    fun isReady(): Boolean {
        return count >= MIN_MEASUREMENTS_READY && offsetCovariance.isFinite()
    }

    /**
     * Check if the filter has fully converged and is suitable for audio playback.
     * Requires more measurements than isReady() and checks error bounds.
     */
    fun hasConverged(): Boolean {
        if (count < MIN_MEASUREMENTS_CONVERGED) return false
        if (!offsetCovariance.isFinite() || offsetCovariance < 0) return false

        // Check if error is within acceptable bounds
        val errorUs = sqrt(offsetCovariance)
        return errorUs < CONVERGED_ERROR_THRESHOLD
    }

    /**
     * Get the standard deviation estimate in microseconds.
     */
    fun estimatedErrorUs(): Long {
        return if (offsetCovariance.isFinite() && offsetCovariance >= 0) {
            sqrt(offsetCovariance).toLong()
        } else {
            Long.MAX_VALUE
        }
    }

    /**
     * Get the covariance (variance) estimate for the offset.
     */
    fun getCovariance(): Long {
        return if (offsetCovariance.isFinite()) {
            offsetCovariance.toLong()
        } else {
            Long.MAX_VALUE
        }
    }

    /**
     * Get the current filtered offset estimate in microseconds.
     */
    fun estimatedOffsetUs(): Long = offset.toLong()

    /**
     * Get the current clock drift rate estimate in PPM (parts per million).
     * Converts drift (µs/µs) to PPM for display.
     */
    fun estimatedDriftPpm(): Double = drift * 1_000_000.0

    /**
     * Get the estimated RTT (for compatibility).
     * Note: This is not stored by the filter, so we return an estimate based on error.
     */
    fun estimatedRttUs(): Long {
        // Rough estimate: error * 2 (since error is one-way delay estimate)
        return estimatedErrorUs() * 2
    }
}