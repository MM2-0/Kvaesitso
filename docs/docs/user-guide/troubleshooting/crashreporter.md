# Crash Reporter

When the launcher crashes, a notification is posted. When you tap on that notification, the crash reporter screen opens. You can also navigate to that screen like this: Settings > Debug > Crash reporter.

The crash reporter lists crashes and exceptions.

## Crashes

Crashes are marked with the <span class="material-symbols-rounded">error</span> icon. Crashes are unexpected errors that were not handled by launcher. They are often a consequence of bugs and should therefore be reported. You can click the <span class="material-symbols-rounded">bug_report</span> icon in the top right corner to create a new issue on GitHub. Make sure to fill in additional information like steps to reproduce (if possible) or what you were trying to do that lead to the crash.

[Read more about reporting bugs](/docs/contributor-guide/report-bugs).

## Exceptions

Exceptions are marked with the <span class="material-symbols-rounded">warning</span> icon. Exceptions are errors that were handled by the launcher. They can sometimes be helpful to locate bugs and other sources of errors, but as long as you don't notice anything strange, you can safely ignore them and do not need to report them. It is expected that some exceptions will occur while the launcher is running. For example, the most common source of exceptions is network timeouts due to the device being offline.
