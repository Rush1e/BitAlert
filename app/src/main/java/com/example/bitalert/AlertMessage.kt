package com.example.bitalert

import java.util.UUID

/**
 * Represents a single alert message to be broadcast over the mesh network.
 *
 * @property id A unique identifier for this specific alert to prevent duplicate processing.
 * @property message The actual content of the alert (e.g., "Road blocked at Main St.").
 * @property timestamp The time the alert was created, in milliseconds since the epoch.
 * @property latitude The geographical latitude where the alert was generated.
 * @property longitude The geographical longitude where the alert was generated.
 */
data class AlertMessage(
    val id: String = UUID.randomUUID().toString(),
    val message: String,
    val timestamp: Long = System.currentTimeMillis(),
    val latitude: Double,
    val longitude: Double
) {
    /**
     * Serializes the AlertMessage into a byte array for BLE transmission.
     * A simple format like CSV is used for simplicity and low bandwidth.
     * Format: "id,timestamp,latitude,longitude,message"
     */
    fun toByteArray(): ByteArray {
        val dataString = "$id,$timestamp,$latitude,$longitude,$message"
        return dataString.toByteArray(Charsets.UTF_8)
    }

    companion object {
        /**
         * Deserializes a byte array back into an AlertMessage object.
         * Returns null if the format is incorrect.
         */
        fun fromByteArray(byteArray: ByteArray): AlertMessage? {
            return try {
                val dataString = byteArray.toString(Charsets.UTF_8)
                val parts = dataString.split(",", limit = 5)
                if (parts.size == 5) {
                    AlertMessage(
                        id = parts[0],
                        timestamp = parts[1].toLong(),
                        latitude = parts[2].toDouble(),
                        longitude = parts[3].toDouble(),
                        message = parts[4]
                    )
                } else {
                    null
                }
            } catch (e: Exception) {
                // Handle potential parsing errors (e.g., NumberFormatException)
                null
            }
        }
    }
}
