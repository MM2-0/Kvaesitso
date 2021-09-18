package de.mm20.rssparser

/**
 * Represents an <item> of the RSS feed.
 * A channel may contain any number of <item>s. An item may represent a "story" -- much like a story
 * in a newspaper or magazine; if so its description is a synopsis of the story, and the link points
 * to the full story. An item may also be complete in itself, if so, the description contains the
 * text (entity-encoded HTML is allowed), and the link and title may be omitted. All elements of an
 * item are optional, however at least one of title or description must be present.
 */
data class Article(
        /**
         * The title of the item.
         */
        val title: String,
        /**
         * The URL of the item.
         */
        val link: String,
        /**
         * The item synopsis.
         */
        val description: String,
        /**
         * Email address of the author of the item.
         */
        val author: String?,
        /**
         * Includes the item in one or more categories.
         */
        val categories: List<String>,
        /**
         * URL of a page for comments relating to the item.
         */
        val comments: String?,
        /**
         * Describes a media object that is attached to the item.
         */
        val enclosure: String?,
        /**
         * A string that uniquely identifies the item.
         */
        val guid: String?,
        /**
         * Indicates when the item was published.
         */
        val pubDate: Long?,
        /**
         * The RSS channel that the item came from.
         */
        val source: String?
)
