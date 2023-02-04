# Music Widget

The music widget is one of the launcher's built-in widgets. It can be used to control media sessions on the device. The general usage is pretty self-explanatory: control the playback with the skip previous, skip next and toggle pause buttons. Tap on the album cover to open the current media player app. Long-press the album cover to open a player chooser.

However there are a few things you might want to know:

## My media playback doesn't show up!

Per default, only media sessions from "music apps" are shown in the music widget. An app qualifies as "music app" if it has an activity that has either the `android.intent.action.MUSIC_PLAYER` action or the `android.intent.action.MAIN` action and the `android.intent.category.APP_MUSIC` category. Some media apps don't declare these in their manifests so the launcher won't recognize them as music apps. You can disable this filter in Settings > Widgets > Music > Restrict to music apps. With this setting disabled, the music widget will show media sessions from all apps, including non-music apps like browsers, video player apps and video streaming apps.

## I can't grant notification access permission!

Please refer to [notification access on Android 13+](/docs/user-guide/troubleshooting/restricted-settings#notification-access).

## Why do I need to grant access to my notifications just to control some stupid media session?

That's a very good question. I don't know. Ask Google, why they make so [nonsensical decisions](https://www.reddit.com/r/mAndroidDev/comments/gn6ckb/man_im_so_happy_no_malicious_app_can_get_access/). The only way for third party apps to control media sessions is to read notifications and extract the media sessions from there. Sadly, the more privacy-friendly alternative is restricted to system apps only, ["due to privacy of media consumption"](https://developer.android.com/reference/android/Manifest.permission#MEDIA_CONTENT_CONTROL). Yep, that makes sense. Thank you, Google.

