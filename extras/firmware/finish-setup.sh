#!/system/bin/sh
# For dedicated TV firmware that replaces its vendor setup/launcher with Aerial.
# Run once in the background from the root vendor init script, never from the APK.
[ -f /system/app/AerialLegacy.apk ] || exit 0
[ "$(getprop persist.sys.burningmode)" != 1 ] || exit 0
aerial_attempt=0
while [ "$aerial_attempt" -lt 60 ]; do
    # Wait until SettingsProvider is ready. Do not mistake a command error for a value.
    aerial_provisioned=$(settings get global device_provisioned 2>/dev/null)
    case "$aerial_provisioned" in
        0|null|1)
            settings put global device_provisioned 1
            settings put secure user_setup_complete 1
            if [ "$(settings get global device_provisioned)" = 1 ] &&
               [ "$(settings get secure user_setup_complete)" = 1 ]; then
                log -p i -t AerialSetup "dedicated TV setup complete; HOME enabled"
                exit 0
            fi
            ;;
    esac
    aerial_attempt=$((aerial_attempt + 1))
    sleep 2
done
log -p w -t AerialSetup "SettingsProvider unavailable; setup completion deferred to next boot"
