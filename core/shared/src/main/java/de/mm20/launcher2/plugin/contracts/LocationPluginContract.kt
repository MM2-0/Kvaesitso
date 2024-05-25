package de.mm20.launcher2.plugin.contracts

abstract class LocationPluginContract {
    object Paths {
        const val Search = "search"
        const val Get = "get"
    }
    object SearchParams {
        /**
         * Search query.
         * Type: String
         */
        const val Query = "query"

        /**
         * Latitude of user's current location in degrees.
         * Type: Double?
         */
        const val UserLatitude = "user_latitude"

        /**
         * Longitude of user's current location in degrees.
         * Type: Double?
         */
        const val UserLongitude = "user_longitude"

        /**
         * Search radius in meters.
         * Type: Long
         */
        const val SearchRadius = "search_radius"

        /**
         * Whether to allow network requests.
         * Type: Boolean
         */
        const val AllowNetwork = "network"
    }
    object GetParams {
        /**
         * Unique identifier of location to look up.
         * Type: String
         */
        const val Id = "id"
    }
    object LocationColumns {
        /**
         * Unique identifier of location.
         * Type: String
         */
        const val Id = "id"

        /**
         * Display name of location.
         * Type: String
         */
        const val Label = "label"

        /**
         * Latitude of location in degrees.
         * Type: Double
         */
        const val Latitude = "latitude"

        /**
         * Longitude of location in degrees.
         * Type: Double
         */
        const val Longitude = "longitude"

        /**
         * Url for users to report / fix incorrect data.
         * Type: String?
         */
        const val FixMeUrl = "fix_me_url"

        /**
         * Location category.
         * Type: String? (LocationCategory enum value)
         */
        const val Category = "category"

        /**
         * Street name of location.
         * Type: String? (JSON)
         */
        const val Address = "street"

        /**
         * Opening schedule of location, encoded as JSON.
         * Type: String? (JSON)
         */
        const val OpeningSchedule = "opening_schedule"

        /**
         * Website URL of location.
         * Type: String?
         */
        const val WebsiteUrl = "website_url"

        /**
         * Phone number of location.
         * Type: String?
         */
        const val PhoneNumber = "phone_number"

        /**
         * User rating of location, from 0.0 (worst) to 1.0 (best)
         * Type: Float?
         */
        const val UserRating = "user_rating"

        /**
         * Public transport departures originating from this location, encoded as JSON.
         * Type: String? (JSON)
         */
        const val Departures = "departures"
    }
}