# Privacy

Pane has no accounts, no ads and no analytics. It does not collect, store on a server, or sell anything about you. There is no Pane server.

## What leaves your TV

Only the weather widgets talk to the internet, and only to two services:

1. **ip-api.com** guesses your approximate location (roughly your city) from your TV's public IP address. This request is plain HTTP, because ip-api.com's free tier has no HTTPS, so anyone on the network path can see it. It sends nothing but the request itself (so ip-api.com sees your public IP address) and returns your city and approximate coordinates.
2. **Open-Meteo** (open-meteo.com) returns weather and air quality for that location. It receives the approximate coordinates.

Nothing else is sent anywhere. These services have their own privacy policies.

## What stays on your TV

Your settings, chosen background, and app layout are stored on the TV only. Removing Pane deletes them.

## Permissions

- **Internet**: for the two weather requests above.
- **Start at boot**: to start the Home-button watcher after a restart.
- **Wi-Fi state**: reads the TV's local IP address only to show it in Settings > This TV. It is never sent anywhere.
- **Installed apps list**: to show your apps. It is never uploaded.
- **Fire TV only, granted once via adb:**
  - `READ_LOGS` lets a background watcher notice when the Home button is pressed so Pane can open. It reads the TV's system log on the device and does not send it anywhere.
  - `SYSTEM_ALERT_WINDOW` lets Pane draw over other apps to take over the Home button.
  - The installer also adds Pane to the battery-saver allowlist (`dumpsys deviceidle whitelist`) so Fire OS does not kill the watcher.

## Questions

Pane is open source, so you can check all of this in the code. Open an issue on GitHub if something here is unclear or wrong.
