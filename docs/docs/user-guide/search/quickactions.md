# Quick Actions

Quick actions are shown below the search terms. They are shortcuts that pass your search term to
another app to do _something_ with it.
There are four kinds of actions: built-in actions, web search actions, app search actions and custom
intents.

Quick actions can be customized at settings > search > quick actions. Click
the <span class="material-symbols-rounded">add</span> icon to create a new action. Long-press and
drag actions to reorder them.

## Built-in

Built-in actions can be enabled or disabled using the switches. There are currently eight built-in
actions.

- **Call**: Dial the number that has been typed into the search bar, only shown if the search term
  is a phone number
- **Message**: Message the number that has been typed into the search bar, only shown if the search
  term is a phone number
- **Email**: Email the address that has been typed into the search bar, only shown if the search
  term is an email address
- **Add to contacts**: Create a new contact, only shown if the search term is an email address or a
  phone number
- **Set alarm**: Set an alarm, only shown if the search term is a time (e.g. `16:39` or `2:20 am`)
- **Start timer**: Start a timer, only shown if the search term is a timespan (e.g. `11 min` or
  `30 s`).
- **Schedule event**: Create a new event, only shown if the search term is a date or a date with a
  time. The date format depends on the system's locale.
- **Open website**: Open a website, only shown if the search term is a URL.

## Web search

Web search actions are shortcuts to perform a search on a website, e.g. to search on Google.

To create a new web search action, click the <span class="material-symbols-rounded">add</span>
button in the bottom right corner and select _Search on a website_.

In the next step, enter the URL of the website, e.g. "google.com" and press continue. The launcher
will try to fetch the search specification from the website and proceed to the last step.

:::info
In some cases, the following error is shown:

> The given website cannot automatically be imported as a web search. You can try a different
> website or enter the required data manually in the next step.

If you are sure that the address you entered is correct, press _Skip_. You can still add the website
as a web search action but you'll need to enter the required data manually.
:::

In the last step, you can customize the web search action. Depending on whether the previous step
was successful, these fields are either prefilled or empty.

- You can tap on the **icon** to customize it
- **Name** is the name for the action that is shown in the launcher interface
- The **URL template** is a URL with a placeholder. The placeholder is later replaced with the
  actual search term. The placeholder is `${1}` and must be present somewhere in the URL. To find
  out what the correct URL is, proceed as follows:

    - Open the website you wish to add in a browser
    - Use the website's search field to search for anything
    - Look at the browser's URL bar. Find the search term you just searched for in the URL

      > [!INFO]
      > Spaces and special characters might be encoded. Don't worry about it, the launcher will
      handle the encoding for you.
      >
      > If you can't find the search term anywhere, try to disable Javascript and try again. If that
      still doesn't work, then that website cannot be used.

    - Replace the search term in the URL with `${1}`. Copy the URL and paste it into the URL
      template field.

### Advanced settings

**Query encoding**: You probably don't need to change this. If you later discover that search
queries are encoded incorrectly, you can try to change this setting.

- **[Percent encoding](https://developer.mozilla.org/en-US/docs/Glossary/percent-encoding)**: encode
  according to the standard for URL encoding (RFC 3986). Most notably, this encodes spaces as `%20`.
- **application/x-www-form-urlencode**: encode according to
  the [application/x-www-form-urlencode](https://url.spec.whatwg.org/#application/x-www-form-urlencoded)
  spec. Most notably, this encodes spaces as `+`. There is no real reason to use this encoding since
  percent encoding should cover all cases where this encoding was appropriate.
- **None**: do not encode the query at all

## App search

> [!INFO]
> App search actions are an experimental feature. Some actions might not work as expected.

App search actions allow you to directly launch an apps search screen – for apps that support it. To
create a new web search action, click the <span class="material-symbols-rounded">add</span> button
in the bottom right corner and select _Search in an app_. A list of apps will appear. Pick an app to
search.

### Advanced settings

You probably won't need this but if you know what you're doing, you can use this to pass extra data
to the search intent. First, select a data type and a key and click
the <span class="material-symbols-rounded">add</span> button to add a new extra. A new text field (
or switch in case of a boolean extra) will appear where you can change the extra's value.

## Custom intent

In Android, an [Intent](https://developer.android.com/training/basics/intents/sending) is a
messaging object that is used to request an action from another app. With custom intents, you can
create your own quick actions that launch any app that can handle the given intent.

> [!NOTE]
> Custom intents are an advanced feature. They can be a powerful tool, but you need to know what
> you're doing. Custom intents aren't supposed to be easy to use, and they might require some
> digging through documentation or even source code of the target app to find out how to use them
> correctly. The following instructions assume that you have at least a basic understanding of how
> intents work in Android.

### Configuration

#### Query

There are two ways the search term can be passed to the intent. You can either pass it
as data (URI) or as an extra:

- **As data**: The search term is passed as an URI in the data field of the intent. You need to
  provide a URI template that contains the placeholder `${1}`. The placeholder is replaced with the
  actual search term when the intent is sent. For example, you could use the URI template
  `geo:0,0?q=${1}` to create an intent that searches for a location in a maps app.
- **As string extra**: The search term is passed as a string extra. You need to provide a key
  that
  is used
  to store the search term in the intent's extras. For example, you could use the key
  `android.intent.extra.TEXT` to create an intent that shares the search term as text. You can
  also
  provide a template to format the extra value. Again, use the placeholder `${1}` to include the
  search term in the template.

#### Base intent

The base intent defines the action.

- **Action**
  The [action](https://developer.android.com/reference/android/content/Intent#setAction(java.lang.String))
  of the intent
- **Category**:
  The [category](https://developer.android.com/reference/android/content/Intent#addCategory(java.lang.String))
  of the intent
- **Type**:
  The [MIME type](https://developer.android.com/reference/android/content/Intent#setType(java.lang.String))
  of the intent
- **Data**:
  The [data URI](https://developer.android.com/reference/android/content/Intent#setData(android.net.Uri))
  of the intent. Only used if the search term is not passed as data.

#### Advanced settings

**Extras**: here you can add custom extras that are passed along with the intent. First, select a
data type and a key and click the <span class="material-symbols-rounded">add</span> button to add a
new extra. A new text field (or switch in case of a boolean extra) will appear where you can change
the extra's value.
