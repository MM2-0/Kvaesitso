# Media Control

This integration handles media sessions and exposes them to other launcher components. It is used by
the [music widget](/docs/user-guide/widgets/music-widget) and by
the [media control part](/docs/user-guide/widgets/clock#dynamic-components) of the clock widget.

## My media playback doesn't show up!

Per default, only media sessions from "music apps" are recognized by the media control integration.
You can select which apps should be included in Settings > Integrations > Media control.

## I can't grant notification access permission!

Please refer
to [notification access on Android 13+](/docs/user-guide/troubleshooting/restricted-settings#notification-access).

## Why do I need to grant access to my notifications to control media?

That's a very good question. I don't know. Ask Google, why they make
so [nonsensical decisions](https://www.reddit.com/r/mAndroidDev/comments/gn6ckb/man_im_so_happy_no_malicious_app_can_get_access/).
The only way for third party apps to control media sessions is to read notifications and extract the
media sessions from there. Sadly, the more privacy-friendly alternative is restricted to system apps
only, ["due to privacy of media consumption"](https://developer.android.com/reference/android/Manifest.permission#MEDIA_CONTENT_CONTROL).
Yep, that makes sense. Thank you, Google.

