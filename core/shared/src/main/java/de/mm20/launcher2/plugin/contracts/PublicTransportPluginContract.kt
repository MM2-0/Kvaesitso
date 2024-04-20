package de.mm20.launcher2.plugin.contracts

abstract class PublicTransportPluginContract {
    object PublicTransportColumns {
        /**
         * ID of the station, unique within a provider.
         * Type: String
         */
        const val StationId = "station_id"

        /**
         * Display name of the transport stop.
         * Type: String?
         */
        const val StationName = "station_name"

        /**
         * Name of the public transport network serving this stop.
         * Type: String
         */
        const val Provider = "provider"

        /**
         * Latitude of stop.
         * Type: Double?
         */
        const val Latitude = "latitude"

        /**
         * Longitude of stop.
         * Type: Double?
         */
        const val Longitude = "longitude"

        /**
         * Display name of the line of this departure.
         * Type: String
         */
        const val Line = "line"

        /**
         * Type of the line serving this stop. E.g. "Bus", "Subway", ...
         * Type: String?
         */
        const val LineType = "line_type"

        /**
         * Display name of the last stop of the line of this departure.
         * Type: String?
         */
        const val LastStop = "last_stop"

        /**
         * Local time of the departure, in HH:mm format.
         * Type: String
         */
        const val LocalTime = "local_time"
    }
}
