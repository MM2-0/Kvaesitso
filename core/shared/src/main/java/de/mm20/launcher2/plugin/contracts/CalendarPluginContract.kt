package de.mm20.launcher2.plugin.contracts

abstract class CalendarPluginContract {
    object EventColumns: Columns() {
        /**
         * The unique ID of the event.
         */
        val Id = column<String>("id")

        /**
         * The title of the event.
         */
        val Title = column<String>("title")

        /**
         * The description of the event.
         */
        val Description = column<String>("description")

        /**
         * The location of the event.
         */
        val Location = column<String>("location")

        /**
         * The start time of the event.
         */
        val StartTime = column<Long>("start_time")

        /**
         * The end time of the event.
         */
        val EndTime = column<Long>("end_time")

        /**
         * Whether the event is an all-day event.
         */
        val AllDay = column<Boolean>("all_day")

        /**
         * The color of the event.
         */
        val Color = column<Int>("color")

        /**
         * The attendees of the event.
         */
        val Attendees = column<List<String>>("attendees")
    }
}