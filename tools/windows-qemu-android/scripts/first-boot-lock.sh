#!/system/bin/sh
# Enforce Setup Wizard + credential for FBE/CE storage on first boot.
# Pushed by TelosAndroidVm.exe after sys.boot_completed.
# The lockscreen PIN/pattern/password is the credential-encrypted (CE) key.

export PATH=/system/bin:/system/xbin:$PATH

MARKER=/data/local/tmp/telos-lock-enforced
if [ -f "$MARKER" ]; then
  exit 0
fi

# Keep SUW enabled until the user finishes it.
settings put global device_provisioned 0 2>/dev/null
settings put secure user_setup_complete 0 2>/dev/null
settings put global setup_wizard_has_run 0 2>/dev/null
settings put system setup_wizard_has_run 0 2>/dev/null

# Do not allow a lockscreen-less encrypted userdata.
settings put secure lockscreen.disabled 0 2>/dev/null
locksettings set-disabled false 2>/dev/null

# Prefer AOSP / Lineage / Bliss setup wizard packages if present.
for pkg in \
  com.google.android.setupwizard \
  com.android.setupwizard \
  org.lineageos.setupwizard \
  com.bliss.setupwizard
do
  pm enable "$pkg" 2>/dev/null
done

# Surface crypto type for operators (file-based when the fstab overlay applied).
resetprop ro.crypto.state encrypted 2>/dev/null
resetprop persist.sys.telos.crypto fbe 2>/dev/null

# If FBE is active, default key is the lockscreen credential.
# Do not set a PIN from the host; the user must choose it in SUW.

date > "$MARKER"
exit 0
