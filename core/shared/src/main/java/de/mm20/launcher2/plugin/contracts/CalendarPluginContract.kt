package de.mm20.launcher2.plugin.contracts

import de.mm20.launcher2.search.calendar.CalendarListType

abstract class CalendarPluginContract {
    object EventColumns : Columns() {
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
         * The calendar name of the event.
         */
        val CalendarName = column<String>("calendar_name")

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
         * Whether the times should include times or truncate to dates.
         */
        val IncludeTime = column<Boolean>("include_time")

        /**
         * The color of the event.
         */
        val Color = column<Int>("color")

        /**
         * The attendees of the event.
         */
        val Attendees = column<List<String>>("attendees")

        /**
         * The URI of the event.
         */
        val Uri = column<String>("uri")

        /**
         * Whether the event is a task and if so, whether it is completed.
         */
        val IsCompleted = column<Boolean>("completed")
    }

    object CalendarListColumns : Columns() {
        val Id = column<String>("id")
        val Name = column<String>("name")
        val Color = column<Int>("color")
        val AccountName = column<String>("account_name")
        val ContentTypes = column<List<CalendarListType>>("content_types")
    }


    object Params {
        const val Query = "query"
        const val StartTime = "start"
        const val EndTime = "end"
        const val Exclude = "exclude"
    }

    object Paths {
        const val CalendarLists = "calendar_lists"
    }
}