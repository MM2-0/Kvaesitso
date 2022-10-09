# Websearch shortcuts

Websearch shortcuts allow to quickly search for the entered term with a web search engine. Websearch shortcuts appear in the search bar when you type anything.

## Customize websearches

Websearches can be customized at Settings > Search > Websearches. To create a new shortcut, click the plus button in the bottom right corner.

There are two ways of creating a websearch shortcut:

### Automatically

Some websearches can be imported automatically (if they support the [OpenSearch description format](https://developer.mozilla.org/en-US/docs/Web/OpenSearch)

- In the create websearch dialog, click the download icon in the top left-corner
- Enter the base URL of the website you are trying to import (for example: `github.com`)
- Click the arrow next to the text field
- If the website is supported, all the required fields will be filled automatically.
- If the website is not supported, you will need to fill in the required data [manually](#manually).

### Manually

- **Color** the color that is used for the icon. The leftmost option adapts the color to the launcher's color scheme. You can also select a custom icon.
- **Name** is the name that is shown in the search bar interface, you can use anything you like.
- **URL** is a URL with a placeholder. The placeholder is later replaced with the actual search term. The placeholder is `${1}` and must be present somewhere in the URL. To find out what the correct URL is, proceed as follows:

  - Open the website you wish to add in a browser
  - Use the website's search field to search for anything
  - Look at the browser's URL bar. Find the search term you just searched for in the URL
    :::info
    Spaces and special characters might be encoded. Don't worry about it, the launcher will handle the encoding for you.

    If you can't the search term anywhere, try to disable Javascript and try again. If that still doesn't work, then that website cannot be used.
    :::

  - Replace the search term in the URL with `${1}`. Copy the URL and paste it into the URL field.

- **Advanced > Query encoding**: You probably don't need to change this. If you later discover that search queries are encoded incorrectly, you can try to change this setting.
  - **[Percent encoding](https://developer.mozilla.org/en-US/docs/Glossary/percent-encoding)**: encode according to the standard for URL encoding (RFC 3986). Most notably, this encodes spaces as `%20`.
  - **application/x-www-form-urlencode**: encode according to the [application/x-www-form-urlencode](https://url.spec.whatwg.org/#application/x-www-form-urlencoded) spec. Most notably, this encodes spaces as `+`. There is no real reason to use this encoding since percent encoding should cover all cases where this encoding was appropriate.
  - **None**: do not encode the query at all
