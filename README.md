# Kavach Guard (Android prototype) + Women's Safety

Personal scam guard for Indian phones. After a one-time setup it reads incoming notifications
(SMS, WhatsApp, Telegram, UPI apps, calls), checks them on the phone, and when a scam is likely it:

1. Opens a full-screen red warning in the user's language (13 Indian languages: English, Hindi, Bengali, Marathi, Telugu, Tamil, Gujarati, Urdu, Kannada, Malayalam, Odia, Punjabi, Assamese)
2. Speaks the warning aloud with the phone's Text-to-Speech
3. Alerts family instantly through Family Link (their own Kavach app), with WhatsApp and SMS as backups
4. Offers one tap to call 1930 or report on Sanchar Saathi (Chakshu)

Nothing is uploaded. Classification runs fully on-device.

## Install on a test phone

1. Copy `kavach-guard-debug.apk` to the phone and open it (allow "Install unknown apps").
2. Open Kavach Guard and tap **Allow** on each setup row.
3. **Android 13+ sideload note:** "Read notifications" will be greyed out at first. Go to
   Settings > Apps > Kavach Guard > three-dot menu (top right) > **Allow restricted settings**,
   then grant notification access again. Play Store installs don't need this step.
4. Pick the language, fill Family Guard, tap **Create Family Link code** and share it.
5. On the family member's phone: install the same APK, tap **I'm family**, enter the code, allow notifications and set battery to Unrestricted.
6. Back on the first phone tap **Send a test alert to family**. The family phone shows a red alert within seconds.

## Demo script for judges (about 60 seconds)

1. Tap **Digital arrest (message, then video call)**. The red screen appears and speaks in Odia/Hindi.
   Six seconds later a fake "incoming video call" arrives and is escalated to a full scam alert,
   because it follows an arrest-style message. The linked family phone gets its own red alert.
2. Tap **UPI 'sent by mistake'**: "You never need your UPI PIN to receive money."
3. Tap **Real bank OTP (should be safe)**: no warning. Shows it does not cry wolf.
4. Real-world proof: send the phone an actual SMS with a fake KYC link from another phone.

## How it works

| File | Job |
|---|---|
| `GuardListenerService.kt` | NotificationListenerService. Pulls title, text, big text and the last messages of chat notifications |
| `ScamClassifier.kt` | Weighted rules in English, Hinglish, Hindi and Odia. Categories: digital arrest, UPI fraud, OTP theft, KYC/account/electricity block, lottery, job/task, loan, phishing link. Combination bonuses and a dampener for genuine bank OTP SMS |
| `Guard.kt` | Pipeline: de-duplicate, classify, video-call escalation, UPI-app request rule, log, warn, speak, ping |
| `AlertActivity.kt` | Full-screen warning with 1930 and Chakshu buttons |
| `VoiceAlert.kt` | TTS with per-language fallback (e.g. Assamese > Bengali > Hindi > English), speaks text matching the voice it found |
| `FamilyPing.kt` | Family Link push, optional SMS and webhook, max one alert per category per 10 minutes |
| `Relay.kt` | Family Link codes and push publishing |
| `FamilyListenerService.kt` | Family phone: background listener that raises alerts |
| `FamilyAlertActivity.kt` | Family phone: full-screen alert with Call button |
| `Messages.kt`, `RegionalKeywords.kt` | Warning text and scam keywords for 13 languages |
| `CallRules.kt`, `CallGuard.kt` | Call risk score, screening service, call banner, call state |
| `PostCallActivity.kt` | After-call check and next steps |
| `ListenActivity.kt` | Live transcript and scam meter, scripted demo calls |
| `MainActivity.kt` | Setup, language, family, demo buttons, "check any message" box, alert log |

Tests: `app/src/test/.../ScamClassifierTest.kt` (41 cases: messages in 11 languages, call rules, call transcripts; all passing).

## Phone calls

- **Screening:** Kavach is the phone's "caller ID & spam app" (Android CallScreeningService). Before the phone
  rings it scores the number: international, hidden, network-unverified (spoofed), repeat caller, or a call
  within an hour of a caught scam SMS. Reported numbers are blocked; "Auto-block high-risk calls" is optional.
- **During the call:** a red/amber banner sits at the top of the call screen in the user's language.
- **Long call:** if a risky call is answered and passes 2 minutes, family gets a Family Link alert.
- **After the call:** "Did they ask for OTP, PIN, money or a video call?" Yes blocks the number, alerts family,
  and shows 1930 / Chakshu. No offers "this number is safe".
- **Live call guard:** live transcript with a scam meter using the phone's speech recognizer. Android does not
  let any third-party app hear calls on the same phone, so this runs on a second phone next to a call on
  speaker, or as a scripted demo (Hindi and English).

What it cannot do on stock Android: answer calls with an AI voice, hear call audio, or talk to the scammer.
That needs a cloud telephony route (call forwarding to a voice agent), see the pitch notes.

Demo script for a real two-phone call: from phone B, call phone A and read the Hindi lines in
`ListenActivity.HINDI_SCRIPT` on speaker while a third phone runs Live call guard.

## Family Link (how family gets alerts)

- The protected phone shows a 12-character code (e.g. `K7QM-2XPA-9DTR`). Share it on WhatsApp.
- The family phone enters it in **I'm family** mode and keeps a background connection open
  (a small "Kavach family guard is on" notification shows while it runs).
- When a scam is caught, the protected phone publishes the alert to a secret channel on a push relay
  (`ntfy.sh` in this prototype). The family phone gets a loud full-screen alert with a **Call** button.
- Missed alerts are picked up when the connection comes back (relay keeps them for 12 hours).
- Making a new code unlinks all family phones.

Backups: the red warning screen has a **Tell <name> on WhatsApp** button with the message pre-typed,
and SMS is still available where Android allows it. A webhook option sits under Advanced for n8n users.

Production note: alert text passes through the public ntfy.sh relay. Before launch, switch `Relay.BASE`
to a self-hosted ntfy or Supabase + Firebase Cloud Messaging.

## Build

```
export ANDROID_HOME=/path/to/android-sdk
./gradlew testDebugUnitTest assembleDebug
```

Output: `app/build/outputs/apk/debug/app-debug.apk`. Plain Android APIs only, no third-party libraries.

## Known limits (be upfront with judges)

- Only sees notification text. Muted chats or apps with notifications off are invisible.
- Rule-based classifier. Next step: a small on-device model (TFLite / MediaPipe text classifier)
  trained on real Indian scam SMS, with these rules kept as a safety net.
- Unknown-number video call detection relies on the caller showing as a number, not a saved name.
- SEND_SMS needs a Play Store declaration. For a public release, use the webhook + WhatsApp route instead.
- Not yet included: CallScreeningService for regular phone calls (planned next), scam-number database lookup.


## Women's safety (added)

- **SOS** (`SosActivity`, `SosService`): 5-second cancel countdown, then SMS with a live Google Maps link to up to 3 trusted
  contacts (works without mobile data), refreshed every 3 minutes for up to 60 minutes, plus a Family Link alert and the
  dialer opened on 112. Needs Location and SMS permission (asked on first use). Stop from the screen or the notification.
- **Unwanted calls from many numbers -> one complaint** (`HarassmentActivity`, `CaseStore`): type every number, or pick
  unsaved callers from call history (READ_CALL_LOG, optional). All numbers, call counts, notes and Kavach-flagged
  messages go into ONE PDF + one copy-ready summary (`EvidencePack`).
- **Report screen** (`ReportActivity`): cybercrime.gov.in, 1930, nearest police station (map), share PDF, 112, women helpline 1091.
  The numbers are added to Kavach's block list so future calls are stopped.
- **Voice** (`VoiceActivity`): say "help / bachao / বাঁচাও / बचाओ" to start SOS; "phone / threat" opens the calls case; "report / police" opens reporting.
- **Languages**: Bengali built in. **Kokborok** shows as a language; text and voice use Bengali until a native speaker fills
  `app/src/main/assets/kokborok.properties` (template included, no code change needed). Android has no Kokborok voice or recognizer.
- Evidence stays on the phone (30 days) and is shared only when the user taps share. Kavach prepares the report; the user files it.
