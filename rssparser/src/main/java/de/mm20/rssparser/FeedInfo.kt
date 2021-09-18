package de.mm20.rssparser

data class FeedInfo(
        /**
         * The name of the channel. It's how people refer to your service. If you have an HTML
         * website that contains the same information as your RSS file, the title of your channel
         * should be the same as the title of your website.
         */
        val title: String,
        /**
         * The URL to the HTML website corresponding to the channel.
         */
        val link: String,
        /**
         * Phrase or sentence describing the channel.
         */
        val description: String,
        /**
         * The language the channel is written in. This allows aggregators to group all Italian
         * language sites, for example, on a single page. A list of allowable values for this
         * element, as provided by Netscape, is
         * [here](http://backend.userland.com/stories/storyReader$16). You may also use
         * [values defined](http://www.w3.org/TR/REC-html40/struct/dirlang.html#langcodes) by the
         * W3C.
         */
        val language: String?,
        /**
         * Copyright notice for content in the channel.
         */
        val copyright: String?,
        /**
         * Email address for person responsible for editorial content.
         */
        val managingEditor: String?,
        /**
         * Email address for person responsible for technical issues relating to channel.
         */
        val webMaster: String?,
        /**
         * The publication date for the content in the channel. For example, the New York Times
         * publishes on a daily basis, the publication date flips once every 24 hours. That's when
         * the pubDate of the channel changes.
         */
        val pubDate: Long?,
        /**
         * The last time the content of the channel changed.
         */
        val lastBuildDate: Long?,
        /**
         * Specify one or more categories that the channel belongs to.
         */
        val category: List<String>,
        /**
         * A string indicating the program used to generate the channel.
         */
        val generator: String?,
        /**
         * A URL that points to the documentation for the format used in the RSS file. It's probably
         * a pointer to [this page](https://validator.w3.org/feed/docs/rss2.html). It's for people
         * who might stumble across an RSS file on a Web server 25 years from now and wonder what it
         * is.
         */
        val docs: String?,
        /**
         * ttl stands for time to live. It's a number of minutes that indicates how long a channel
         * can be cached before refreshing from the source. More info
         * [here](https://validator.w3.org/feed/docs/rss2.html#ltttlgtSubelementOfLtchannelgt).
         */
        val ttl: Int?,
        /**
         * Specifies a GIF, JPEG or PNG image that can be displayed with the channel.
         */
        val image: String?,
        /**
         * A hint for aggregators telling them which hours they can skip. More info
         * [here](http://backend.userland.com/skipHoursDays#skiphours).
         */
        val skipHours: List<Int>,
        /**
         * A hint for aggregators telling them which days they can skip. More info
         * [here](http://backend.userland.com/skipHoursDays#skipdays).
         */
        val skipDays: List<DayOfWeek>
)