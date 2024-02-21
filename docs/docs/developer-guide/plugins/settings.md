# Plugin Settings

To add a plugin settings activity, create an activity with the following intent filter:

```xml
<activity
    android:name=".SettingsActivity"
    android:exported="true">

    <intent-filter>
        <action android:name="de.mm20.launcher2.action.PLUGIN_SETTINGS" />
        <category android:name="android.intent.category.DEFAULT" />
    </intent-filter>
</activity>
```

Users can launch this activity by pressing the <span class="material-symbols-rounded">settings</span> on the plugin settings screen.
