# Permission Requests for Already Granted Permissions

On some devices, the launcher keeps asking for the notification permission even after you have
already granted it. This usually occurs a few minutes to hours after you have granted the
permission.

The problem is caused because some OEMs have too aggressive battery "optimizations" in place which
terminate
the launcher's notification listener service prematurely. The launcher then notices that it can no
longer receive notifications and asks for the permission again.

The steps to fix that are different for each OEM but it usually involves disabling battery
optimization
for Kvaesitso. This website has a list of guides for different OEMs:
https://dontkillmyapp.com/