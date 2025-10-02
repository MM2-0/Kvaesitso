# Calendar Widget

Display calendar events and appointments for the next seven days. In the widget settings, you can
choose which calendars to display: Tap 'Edit widgets', then tap the <span class="material-symbols-rounded">tune</span> icon for the
calendar widget. There is also an option to hide all-day events.

## My calendars don't show up!

Calendar apps need to use the Android calendar provider APIs to store their calendar data in order
to make them accessible for other apps (like this widget).

The calendar provider framework consists of two parts:

- calendar providers that serve as a backend to store and sync calendar data
- calendar apps that provide a user interface to view and modify calendar data

Both parts can be provided by the same app, or they can be provided by different apps. Calendar apps
can interact with multiple calendar providers, and calendar providers can be used by multiple calendar
apps. This system makes it possible that you can, for example, use the Google Calendar app to read
and modify your Outlook calendar.

If your calendar doesn't show up, it is
likely that your calendar app doesn't participate in this system. For some apps, there are solutions
or workarounds:

### Outlook

Outlook doesn't sync its calendar data with the Android calendar provider by default. To enable it,
open the Outlook app, and navigate to its settings. Tap on your account, then enable 'Sync calendar'.

### Proton Calendar

Proton Calendar does not support syncing with the Android calendar provider. Unfortunately, they
see this as a privacy feature, so it is unlikely that this will change in the future. For that reason,
this widget cannot display Proton Calendar events. As a workaround, if you are on a paid Proton Calendar
plan, you can [share your calendar via link](https://proton.me/support/share-calendar-via-link) and
then sync that calendar link using [ICSx⁵](https://f-droid.org/en/packages/at.bitfire.icsdroid/). However,
this solution has some drawbacks:

- The synced calendar is read-only, if you need to make changes, you need to do that in the Proton Calendar app
- The calendar widget will not open the event in the Proton Calendar app, but in the default calendar app
- You effectively bypass Proton's privacy features

### Fossify Calendar (Simple Calendar)

Fossify Calendar uses its own internal calendar storage by default. These calendars are not accessible
to other apps. Fossify Calendar can also read and write calendars to the Android calendar provider.
This feature mainly intended to allow Fossify Calendar users to use their (Google, Outlook, etc.)
calendars in Fossify Calendar. Unfortunately, Fossify Calendar cannot act as a calendar provider, so
you need to rely on another calendar provider app.

If you want an offline solution, you
can use [Etar](https://f-droid.org/en/packages/ws.xsoh.etar/) (which is a full-blown calendar app by
its own, but it has the capability to create offline calendars) or
Offline Calendar (`org.sufficientlysecure.localcalendar`) in F-Droid Archive repo (which is
just a calendar provider but no [longer maintained](https://github.com/SufficientlySecure/offline-calendar)).
It is also possible that your device's default calendar app has this feature built-in, so best check that first.

After you have created an offline calendar, you can sync it with Fossify Calendar by opening the
Fossify Calendar settings, and enabling 'CalDAV sync'. There you can select your offline calendar.

To migrate your existing Fossify Calendar calendars to the new calendar provider, select
'Export events to an .ics file', then clear them from Fossify Calendar (settings > Delete all events and tasks)
and reimport them ('Import events from an .ics file'). Make sure to select the right calendar
and that 'Ignore event types in the file, always use the default one' is ticked.
