# YouTube account in the browser

Telos YouTube and YouTube Music both offer **Account in browser**. The panel opens the official YouTube websites in the same Custom Tabs browser. Sign in, switch accounts and sign out on those websites. Telos does not read the browser cookies, passwords or account identity and does not display a connected badge after opening the browser.

YouTube Music on the phone can keep **local favorites and playlists**, and start an **artist radio** (related mix) or a **genre radio**. Those lists stay on the device. They are not your Google YouTube Music library.

This is a browser account option, **not native account synchronization**. Native Telos search and background playback remain account-free. Subscriptions, personal playlists and recommendations from your Google account are not imported into Telos. Browser playback has the behavior of the official website and browser; it does not inherit the native Telos player's background-playback service.

If there is no installed browser that supports Custom Tabs, the panel explains that a compatible browser is needed. Links are explicitly sent to that browser instead of being redirected to the installed YouTube app.

## Why native browser sign-in remains pending

Signing into Chrome does not expose its authenticated cookies to Telos. Flow's `InnerTube.cookie` and `account/account_menu` hooks require an already authenticated cookie session; they are not a browser authorization implementation.

A supported native Google account integration needs a project owned by the maintainer in Google Cloud, a configured consent screen, enabled YouTube Data API, OAuth client registration for the release application package and signing certificate, and the required scope verification where applicable. No such app registration or client configuration is provided in this repository. Google disallows native OAuth authorization in embedded WebViews; Telos therefore does not add a credential-capturing WebView or disguise its user agent to bypass a sign-in restriction.

Even a registered YouTube Data API client would authorize account-data features such as playlists; it would not automatically authorize account cookies for NewPipe extraction or provide a YouTube Music playback API. The native account design must specify those supported account-data features before claiming full YouTube Music account support.

References: [Google OAuth policies](https://developers.google.com/identity/protocols/oauth2/policies), [Google native app authorization](https://developers.google.com/identity/protocols/oauth2/native-app), [YouTube Data API authentication](https://developers.google.com/youtube/v3/guides/authentication).

## Ελληνικά

Οι ενότητες YouTube και YouTube Music έχουν κοινό πάνελ **Λογαριασμός στον browser**. Ανοίγουν τις επίσημες ιστοσελίδες στον ίδιο συμβατό browser. Η σύνδεση, η αλλαγή λογαριασμού και η αποσύνδεση γίνονται στην ιστοσελίδα.

Η σύνδεση ισχύει για τον browser. **Δεν συγχρονίζει τον λογαριασμό στον εγγενή player του Telos.** Οι συνδρομές, οι προσωπικές λίστες και οι προτάσεις δεν εισάγονται στο Telos. Η εγγενής αναζήτηση και η αναπαραγωγή στο παρασκήνιο συνεχίζουν χωρίς λογαριασμό. Το Telos δεν διαβάζει κωδικούς ή cookies και δεν εμφανίζει ψευδή ένδειξη σύνδεσης.

Για εγγενείς λειτουργίες λογαριασμού χρειάζεται πρώτα καταχώριση της εφαρμογής στο Google Cloud από τον διαχειριστή του project και σχεδιασμός των συγκεκριμένων λειτουργιών που υποστηρίζει το επίσημο API. Η υπάρχουσα υποστήριξη cookies του Flow δεν αποτελεί έτοιμη σύνδεση μέσω browser.

## Open YouTube links in Telos

Android can offer **Telos YouTube** for `youtube.com`, `www.youtube.com`, `m.youtube.com` and
`youtu.be`, and **Telos YouTube Music** for `music.youtube.com`. Link handlers enter the normal
Telos Media task and app-lock flow; video links open the video space, and music links open
Music. Watch/short links resolve the selected video; search and playlist links open their
corresponding results. Unsupported or unavailable content can still fail at the upstream service.

In the account panel of either space, choose **Open links with Shizuku** after starting and
authorizing Shizuku. Telos enables link handling and selects these five domains for the current
Android user, then reads the result back before showing **Done**. This may replace another app’s
user selection for those domains. It does not change unrelated domains or pretend that YouTube
has verified Telos ownership. If Shizuku is unavailable or Android rejects a command, the UI
reports failure. There is no root fallback for this action. Android’s app **Open by default**
settings remain available for manual selection and undoing the choice.

Recognition results in Άκου also offer the official YouTube Music app when installed. That
explicit destination is independent of the selected web-link default.
