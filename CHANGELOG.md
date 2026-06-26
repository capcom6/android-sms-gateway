# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

### Added
- SMS Receiver settings section with Content Provider Monitoring toggle to disable fallback content observer

### Fixed
- Duplicate SMS detection when a message arrives via both broadcast receiver and content observer — use `DATE_SENT` instead of `DATE` in both paths so timestamps match consistently
- Export (`POST /messages/inbox/export`) no longer produces duplicates of messages already received via broadcast

## [v1.66.1] - 2026-06-24

### Changed
- Add SMS content observer fallback [a4354d3]

## [v1.66.0] - 2026-06-22

### Added
- `app:started` event support for webhooks [361de56]

## [v1.65.3] - 2026-06-16

### Changed
- Start working thread on create [aacce02]


## [v1.65.2] - 2026-06-04

### Changed
- Always use application context for broadcast receiver [458768f]


## [v1.65.1] - 2026-06-02

### Changed
- Clear backstack on messages tab change [f7a1df5]


## [v1.65.0] - 2026-05-29

### Changed
- Sim cards list [d485d59]


## [v1.64.0] - 2026-05-26

### Added
- JWT refresh tokens support [4ac0303]


## [v1.63.0] - 2026-05-22

### Changed
- Export MMS support [10f287a]


## [v1.62.0] - 2026-05-09

### Changed
- Hide cloud password [c1f0cba]


## [v1.61.1] - 2026-05-05

### Changed
- Fix queue stuck on scheduled messages [3722a64]


## [v1.61.0] - 2026-04-27

### Changed
- Add scheduling [3f82b13]


## [v1.60.0] - 2026-04-23

### Changed
- Refactor LocaleHelper to use SettingsHelper [caf8fd4]
- Initial Arabic localization support [08adfd5]


## [v1.59.0] - 2026-04-20

### Added
- Introduce inbox API [ef51784]
- Introduce get queries [49c42d8]

### Changed
- Conditionally trigger webhooks on messages export [43b77ce]


## [v1.58.0] - 2026-04-16

### Added
- Implement message content access [438d06d]

### Changed
- Improve duplicate ID handling [25b58b3]

### Maintenance
- Close stale PRs and remove labels on push [af34ccb]


## [v1.57.1] - 2026-04-14

### Added
- File-backed payload storage [a9c13f9]


## [v1.57.0] - 2026-04-01

### Added
- Add module for tracking incoming messages [4d6169b]


## [v1.56.0] - 2026-03-19

### Added
- Initial `mms:downloaded` event support [adfb3ae]

### Changed
- Refactor MMS content processing [5253364]

### Documentation
- Update README [c1682a7]


## [v1.55.1] - 2026-03-13

### Changed
- Allow `*` and `#` in phone number [1251ae5]


## [v1.55.0] - 2026-03-03

### Added
- Add JWT auth support [e549ecd]


## [v1.54.0] - 2026-02-28

### Added
- Add SIM card phone number to payload [a90d611]


## [v1.53.1] - 2026-02-20

### Added
- Add logging of messages pulling [754892a]
- Force pull messages on connect [1fd930c]


## [v1.53.0] - 2026-02-17

### Changed
- Improve start errors handling [f5a24db]
- Add zh translations [0d66735]
- Select notification channel [b82ba5e]


## [v1.52.5] - 2026-01-29

### Changed
- Intercept clicks in empty webhooks list [1245c7b]


## [v1.52.4] - 2026-01-27

### Maintenance
- Upgrade libphonenumber to 9.0.22 [9cc606d]


## [v1.52.3] - 2026-01-23

### Changed
- Unregister before registration to avoid double events [5521bc7]


## [v1.52.2] - 2026-01-14

### Changed
- Stringify enums before saving [176bd0a]


## [v1.52.1] - 2025-12-07

### Added
- Add placeholders for JWT routes [5a73fff]

### Documentation
- Update API docs [d7ccf26]


## [v1.52.0] - 2025-12-06

### Added
- Webhooks queue [8d81c5b]


## [v1.51.3] - 2025-11-15

### Changed
- Fix double registration of broadcast receivers [c7b3cfe]


## [v1.51.2] - 2025-11-11

### Changed
- Notify user on real changes only [3349896]


## [v1.51.1] - 2025-10-19

### Changed
- Add dynamic registration of BroadcastReceiver [4aa519d]


## [v1.51.0] - 2025-10-04

### Changed
- Fix possible null exception [6106864]
- Dynamic messages list loading [aaff6be]


## [v1.50.0] - 2025-10-03

### Changed
- Rate limit high-priority messages in some cases [bd1a1c9]


## [v1.49.0] - 2025-10-01

### Changed
- Update translations [fb09f13]
- Add queue status info [d516ed7]


## [v1.48.0] - 2025-09-29

### Added
- Add parts count to the `sms:sent` webhook payload [e38c656]

### Changed
- Improve message sending for multiple recipients [e21fbb2]


## [v1.47.2] - 2025-09-20

### Changed
- Add device ID to Home tab [b7c8076]
- Refresh Home tab layout [4b85998]


## [v1.47.1] - 2025-09-04

### Changed
- Add `ru` locale [94a9f19]


## [v1.47.0] - 2025-09-03

### Changed
- Add basic MMS support [20a04be]

### Documentation
- Include MMS support in README [03781d0]


## [v1.46.1] - 2025-08-29

### Changed
- Add some logging to the worker [1a72ba2]


## [v1.46.0] - 2025-08-17

### Changed
- Add order param for messages request [cfea2d7]
- Add processing order option [f9bfc0f]


## [v1.45.1] - 2025-08-13

### Changed
- Improve logs and error handling [1ebe232]


## [v1.45.0] - 2025-08-11

### Added
- Allow http adresses in insecure build [0222f30]

### Documentation
- Update README according insecure build [4ae8b02]

### Maintenance
- Add insecure build [aa374b9]


## [v1.44.0] - 2025-08-08

### Added
- Get messages history [9a8d3de]


## [v1.43.0] - 2025-08-06

### Changed
- Conditional SSE connection [09f9eb9]
- Improve logging [02823bf]
- Add realtime events notification [10f28e9]
- Unify events processing [7a31336]
- Draft SSE implementation [6cfa071]


## [v1.42.0] - 2025-08-03

### Changed
- Add Chinese strings [09e7f5a]


## [v1.41.0] - 2025-08-02

### Added
- Add swagger docs endpoint [b0e0933]


## [v1.40.1] - 2025-07-09

### Changed
- Update drawables colors [404dd29]
- Display device id [96b54b8]


## [v1.40.0] - 2025-07-09

### Added
- Support for `deviceId` field in messages [33d4384]


## [v1.39.1] - 2025-07-07

### Changed
- Add support for data messages [6692dc1]

### Documentation
- Update README [327b9e7]


## [v1.39.0] - 2025-06-28

### Added
- Add support for data messages [a484b6c]

### Changed
- Prepare for receiving data messages [18acb8b]

### Documentation
- Add data message feature to README [d19025f]
- Update API docs [8aae8d8]
- Update API docs [53af4a1]

### Testing
- Update tests [0b1405f]


## [v1.38.1] - 2025-06-27

### Changed
- Don't show notification for empty update [a36d4d2]


## [v1.38.0] - 2025-05-29

### Added
- Add periodic reminders regarding `sms:received` webhooks [d8e4253]
- Add `sms:received` webhook registration notification [719d7e7]


## [v1.37.0] - 2025-05-28

### Added
- Add settings import and export [5fc0a40]

### Changed
- Add settings sync with the server [0712df0]
- Add validation for import [7e2be34]


## [v1.36.1] - 2025-05-17

### Added
- Add `deviceId` field to the API for compatibility with Cloud server [45edf1b]


## [v1.36.0] - 2025-05-15

### Added
- Add webhooks list UI [9d5cc83]
- Add `source` field to the DTO [070aa88]


## [v1.35.1] - 2025-05-12

### Added
- Improve date parsing compatibility [a6ec4ae]

### Changed
- Add logging of unhandled crashes [0af2a79]


## [v1.35.0] - 2025-04-03

### Added
- Improve errors handling [ea0f21d]

### Changed
- Support for `priority` field [ae51153]


## [v1.34.0] - 2025-03-28

### Changed
- Add registration by code [7fb8705]

### Maintenance
- Skip APK build for draft PRs [933a7b3]


## [v1.33.4] - 2025-03-15

### Added
- Pass `id` instead of whole payload [3974e38]


## [v1.33.3] - 2025-03-12

### Changed
- Remove invalid "restart required" notification [ef23b37]


## [v1.33.2] - 2025-03-12

### Added
- Allow loopback URLs without encryption [b9ccf9b]


## [v1.33.1] - 2025-03-10

### Changed
- Fix duplicate connection status log entries [26b14b9]


## [v1.33.0] - 2025-03-10

### Added
- Add payload signing [e2a9d18]

### Maintenance
- Upload pr artifacts to S3 [87328c0]


## [v1.32.0] - 2025-02-16

### Changed
- Improve ui for multi-device mode [c0f3f0a]
- Improve device update and registration flows [c2d4b5d]
- Make password response optional for an existing account [877da7d]
- Prepare sign in flow [e873d66]
- Refactor autostart code [ff51b38]
- Prepare first start dialog [34d322a]
- Improve device registration errors handling [ad52a3f]
- Cleanup server URL setting code [6b53c27]

### Documentation
- Update README [1f262f9]


## [v1.31.2] - 2025-02-14

### Changed
- Make `Failed` state final [3efeff6]
- Add logging and PDU parsing [3ac6beb]


## [v1.31.1] - 2025-02-13

### Changed
- Log exception stacktrace in `sendSMS` [3dabaac]

### Documentation
- Add Android 15 note [6ec03ed]


## [v1.31.0] - 2025-02-03

### Changed
- Add root CA certificate [4daab6f]


## [v1.30.1] - 2025-01-29

### Changed
- Add error descriptions [e0012cc]


## [v1.30.0] - 2025-01-22

### Changed
- Expose connection, battery and charging statuses [e582aaf]


## [v1.29.2] - 2025-01-20

### Changed
- Skip monitoring for API less than N [665ad24]


## [v1.29.1] - 2025-01-18

### Changed
- Catch `ForegroundServiceStartNotAllowedException` while starting from background [e900c67]
- Update some strings [b4c2ba5]

### Maintenance
- Use my own Discord action to fix flags bug [e2715d4]


## [v1.29.0] - 2025-01-12

### Changed
- Add network status monitoring [c15eacf]
- Allow empty token [755f353]
- Add registration process logs [fc991b2]

### Documentation
- Add `READ_SMS` permission description [66db6fe]


## [v1.28.0] - 2025-01-02

### Added
- Implement `/messages/inbox/export` endpoint [5b06bab]
- Move messages routes to sub-router [5ae023e]

### Changed
- Add support for `MessagesExportRequested` event [141c23e]
- Provide message hash as id for received messages [33ea82b]
- Add logging [bb0fc53]
- Introduce ReceiverService [875d228]

### Documentation
- Add export endpoint to swagger [eed46c0]
- Add `simNumber` field for webhooks payload in swagger [6ecaafe]
- Update screenshots [a867675]

### Maintenance
- Fix repository URL in Discord notification [e0fa7cb]


## [v1.27.2] - 2024-12-24

### Added
- Add `simNumber` field to payload [02452a3]

### Changed
- Fix Discord message [587c972]


## [v1.27.1] - 2024-12-13

### Changed
- Fix sim rotation preference name [00aa61a]

### Documentation
- Update links [1e8d06c]
- Add SIM rotation feature [1d2f95f]


## [v1.26.0] - 2024-12-04

### Changed
- Allow to change cloud password [a4d78e1]


## [v1.25.0] - 2024-11-30

### Added
- Add ability to change port and credentials, see #138 [6b44548]


## [v1.24.0] - 2024-11-28

### Added
- Allow to ignore battery optimizations [9572a2d]


## [v1.23.1] - 2024-11-27

### Changed
- Include `Integration` question [1544ad1]

### Documentation
- Add CLI tool info [3fb1498]

### Maintenance
- Update libphonenumber to 8.13.50 [c803b0b]


## [v1.23.0] - 2024-10-22

### Changed
- Try to determine SIM slot number [12758d9]

### Maintenance
- Add action for closing issues [505708a]


## [v1.22.0] - 2024-10-16

### Maintenance
- Replace ipify with cloud server endpoint [8a6fc4c]


## [v1.21.1] - 2024-10-16

### Changed
- Add app restart notice on server mode changes [006f148]
- Improve credentials display format [157b9d2]


## [v1.21.0] - 2024-10-15

### Added
- Add webhooks retry count option, see #125 [910e4da]


## [v1.20.0] - 2024-10-09

### Added
- Require internet access option [3a8d901]

### Changed
- Move messages settings to another fragment [bd7e5b5]


## [v1.19.2] - 2024-10-03

### Documentation
- Update roadmap [ef18790]

### Maintenance
- Update libphonenumber to 8.13.46 [d5707f3]


## [v1.19.1] - 2024-09-20

### Changed
- Use new domain name [6294434]


## [v1.19.0] - 2024-09-17

### Added
- Add `sms:delivered` and `sms:failed` events [bbfa4c7]

### Changed
- Send new first and limit batch to 100 messages [49d3bfc]
- Add support for user certificates [89716ac]
- Bump actions/download-artifact from 3 to 4.1.7 in /.github/workflows [2e7941b]

### Maintenance
- Upgrade upload/download artifacts actions [fcadc8d]


## [v1.18.0] - 2024-08-12

### Added
- Add `sms:sent` event [f41d261]

### Changed
- Refactor `MessageState` to `ProcessingState` [987b9b5]

### Documentation
- Update webhooks events type enum [8455438]

### Testing
- Update tests after refactoring [5f93994]


## [v1.17.0] - 2024-08-08

### Added
- Improve routing [0489023]
- Add `/logs` endpoint [ae1001d]
- Add logging to worker [b1fd4c2]

### Changed
- Add default log lifetime [34ce7bf]
- Remove autogenerated placeholders [3e744a4]
- Add simple log entries fragment [e2279f0]
- Log registration errors [d8788b6]
- Add logs module [9f7907b]

### Documentation
- Add response to `POST /webhooks` endpoint [2e7ab40]
- Add line wrap to shell examples [0091e04]
- Update Discord link [41ca8e1]
- Add additional contacts [aaf94e1]


## [v1.16.2] - 2024-07-10

### Changed
- Add distinct icons for operations [d49df3a]


## [v1.16.1] - 2024-07-09

### Added
- Change foreground service type to `connectedDevice` [dd04c56]

### Changed
- Acquire WiFi lock [304ce49]
- Add sim cards count question [bd40d08]
- Add feature request template [7587c37]
- Add issue templates [7ebfa97]


## [v1.16.0] - 2024-07-03

### Added
- Add ping support [afb7740]

### Changed
- Update README `Features` section [0507cbd]
- Fix stop hanging [bfa1aa0]
- Fix autostart issue [ba79f9f]
- Use of event bus [a41dfe4]
- Try to use events instead of direct calls [0bfcb00]
- Fix settings reference [7bd7a3a]
- Introduce singleton event bus [799d167]
- Introduce orchestrator service [bce1264]
- Introduce Ping module [4c3ebbe]

### Documentation
- Update use cases [9397afc]


## [v1.15.3] - 2024-06-26

### Changed
- Change device id access API [d55e038]


## [v1.15.2] - 2024-06-24

### Added
- Add http timeouts [83926f0]
- Refactor retry condition [453772c]
- Make webhooks worker expedited [33c0319]
- Improve webhooks retry policy [d9bc9f5]
- Process webhook receiver response code and add some fields to event [a51be7b]

### Changed
- Update by default every 24 hours [28625bd]
- Fix API version interpolation in swagger.json [7bde317]
- Improve message details layout for long messages [1003abd]


## [v1.15.1] - 2024-06-20

### Changed
- Add `MANAGE_SUBSCRIPTION_USER_ASSOCIATION` permission [7e852a1]


## [v1.15.0] - 2024-06-19

### Added
- Add device id to webhook event [41ec6c6]
- Add webhooks sync with cloud [1c3fa99]
- Add webhooks docs [de1fc9f]
- Add webhooks sending with retries [a6410c8]
- Add webhooks entity for received SMS [21cba87]

### Changed
- Move webhooks update worker to `gateway` module [6937834]
- Prepare API routes [8d4ae68]

### Documentation
- Add webhooks in cloud mode note [7f6a69a]

### Testing
- Fix tests after classes renaming [5f98cf7]


## [v1.14.0] - 2024-05-27

### Added
- Add `Date` response header [66c41fe]

### Changed
- Fix failed messages counting [47ecb5e]
- Add health module with single check [520bcff]

### Documentation
- Add `GET /health` to OpenAPI docs [243cbc7]


## [v1.13.0] - 2024-05-17

### Changed
- Use dates instead of timestamp for states log [a575f40]
- Send message state by `Worker` [5347016]
- Add message states history to response [65e4a4e]
- Add states log entity and operations [d700476]

### Documentation
- Update API docs [533352c]


## [v1.12.4] - 2024-05-14

### Changed
- Add app version text [c0c20e3]


## [v1.12.3] - 2024-05-08

### Changed
- Fix timezone formatting for Android 6 and below [f268044]


## [v1.12.2] - 2024-04-17

### Added
- Try to get local IP from iface in tethering mode [68f26c0]


## [v1.12.1] - 2024-04-16

### Changed
- Introduce delay interval settings [95893cb]
- Fix truncation period icon color [60b45c5]


## [v1.12.0] - 2024-04-09

### Changed
- Add log truncation worker [1e0673e]


## [v1.11.0] - 2024-04-05

### Changed
- Improve UX for settings fields [06ca8cb]
- Fix message state update, or not? [a325fd8]
- Add per period limits [f15d9c8]


## [v1.10.0] - 2024-03-20

### Changed
- Fix device registration issues [a67b794]
- Set default API URL [f2d8441]
- Try to register as new device on 401 code [1fa7b5e]
- Add some settings [5fa6b13]

### Documentation
- Fix API docs tags and version [55ae503]


## [v1.9.0] - 2024-03-05

### Changed
- Add `GET /device` endpoint with device info [21b4059]
- Expire old messages in pending state [76a21f3]
- More straightforward starting/stopping of `Send` worker [6e5c0b4]
- Send as foreground service [5319679]
- Introduce notifications module [0fb453c]
- Pending messages queue [201d5dd]

### Testing
- [messages] add `skipPhoneValidation` to constructor [0a0dd72]


## [v1.8.0] - 2024-02-28

### Changed
- Expire old messages in pending state [2872aca]
- More straightforward starting/stopping of `Send` worker [a86385d]
- Send as foreground service [e644d60]
- Introduce notifications module [131c26e]
- Pending messages queue [e2345dd]

### Testing
- [messages] add `skipPhoneValidation` to constructor [c7c8340]


## [v1.7.2] - 2024-02-25

### Changed
- Fix message state with multiple recipients [e552607]


## [v1.7.1] - 2024-02-19

### Changed
- [ttl] expire after inserting to DB [c314a6c]
- Add TTL and ValidUntil support [453c67a]

### Maintenance
- Build APK on PR [61a6389]


## [v1.6.1] - 2024-02-13

### Added
- Improve errors handling [12903df]

### Changed
- Always normalize phone number [529d8e0]
- Allow to skip phone number validation [7908e9c]
- Screenshot [947e2dc]

### Documentation
- Add link to API docs [1355860]


## [v1.6.0] - 2024-02-06

### Added
- Random delays between messages [dd1b373]

### Changed
- Post message response code [c088c20]
- Refactored: settings [db9f53d]

### Fixed
- Code cleanup [e8b439f]


## [v1.5.0] - 2024-01-26

### Added
- End-to-end encryption [23b8060]

### Changed
- API docs for `isHashed` field [a25baaf]


## [v1.4.0] - 2023-12-20

### Added
- Message details info [5609f5c]

### Changed
- Improved: use of IO context for DB operations from UI [ca0cc75]

### Fixed
- Use of `LiveData` instead of `Flow` [c55525b]
- Back navigation and some styles [4716f8b]


## [v1.3.1] - 2023-12-18

### Added
- Max message length constraint [eda24c1]

### Changed
- Upgraded: libphonenumber to v8.13.26 [0bea6d3]


## [v1.3.0] - 2023-12-07

### Added
- `withDeliveryReport` option support [12729f1]


## [v1.2.3] - 2023-12-05

### Changed
- Moved: some strings to resources [6cfd1d7]

### Fixed
- UI messages for not available address [b3d0d69]
- Local server address without WiFi [9b00fb1]


## [v1.2.2] - 2023-11-29

### Added
- Log errors [5f2b40b]


## [v1.2.1] - 2023-11-21

### Added
- Version code update on build [2b4b10f]
- Support for multipart messages [3915688]

### Changed
- Docs [d82c995]


## [v1.2.0] - 2023-11-17

### Added
- User-agent [8166f46]
- Support for multiple SIMs [dbaa254]
- Read phone state permission request for SIM access [9779c2e]
- Links to addresses and copy credentials [99c35d1]
- Services state watching [23957e5]
- Messages list basic components [6a1e466]

### Changed
- Docs with TTL [760e790]
- Improved: strings to resources and hide action bar [7667adb]
- Improved: some refactoring and messages list scroll to top [5d7d22a]
- Moved: settings to fragment [71a2fd5]
- Target SDK [c75a94e]

### Fixed
- Select SIM card by Slot Index [2920004]
- Messages list items divider [219b624]
- Don't send local messages status to cloud [a91cef5]
- App manual stopping [58ee68c]
- Show only 50 last messages [960b37f]


## [v1.1.2] - 2023-10-17

### Added
- Processed state support [748ae54]

### Changed
- GitHub actions [53f4f59]

### Fixed
- Keystore location [fdbd81f]
- Updating state on cloud server [b864a8d]


## [v1.1.1] - 2023-10-11

### Added
- Support for non-Russian numbers [ab4eecc]
- Screenshot [18346cf]
- Google-services-json for build APK [bd8c0cf]

### Changed
- GitHub actions [711d238]
- Renamed: remote server to cloud server [f963a43]
- Icon [f46e02f]
- README [dd08b01]
- API docs [60707ea]
- Readme file [46c2062]
- API docs with sms.capcom.me server [3b56c4c]

### Fixed
- Status code 200->201 [64aaab0]


## [v1.1.0] - 2023-08-19

### Added
- Local server activation/deactivation [f46dd87]
- Remote server activation/deactivation [debe760]
- Refresh message state on server [6b0e5d3]
- Get messages from server and send message status [49e263e]
- Server registration and token update [6ce6c09]
- EventBus, Settings and Gateway modules [dbbfbe0]

### Changed
- README [1e6da1d]

### Fixed
- Messages state processing [59d83cd]
- Gateway module status issues [7a2af52]
- Base url in api docs, see #1 [63693c7]


## [v1.0.0] - 2022-11-08

### Added
- Classes for pulling messages from server [3c5274b]
- Recipients entity [bdd6bd3]
- Messages db and state updating [4e4d25c]
- Events receiver registration for sent and delivered state [842694f]
- Permissions request on start [880284b]
- Events receiver [f870207]
- Authorization [0279979]
- LICENSE and README [c50081a]
- Start on boot, wakelocks and token generation [4b3d6d7]
- Information about local and public IP [502baaf]
- Sms sending [7b24cf7]
- Initial commit [0eb1adb]

### Changed
- Build 1 [0e0e811]
- Docs [dd5cceb]
- Migrate to ktor client instead of retrofit [d0d6c77]

### Fixed
- Process send error [e815bf9]
- SDK version check for SmsManager compatibility [4b7e593]


[Unreleased]: https://github.com/capcom6/android-sms-gateway/compare/v1.66.1...HEAD
[v1.0.0]: https://github.com/capcom6/android-sms-gateway/releases/tag/v1.0.0
[v1.1.0]: https://github.com/capcom6/android-sms-gateway/compare/v1.0.0...v1.1.0
[v1.1.1]: https://github.com/capcom6/android-sms-gateway/compare/v1.1.0...v1.1.1
[v1.1.2]: https://github.com/capcom6/android-sms-gateway/compare/v1.1.1...v1.1.2
[v1.2.0]: https://github.com/capcom6/android-sms-gateway/compare/v1.1.2...v1.2.0
[v1.2.1]: https://github.com/capcom6/android-sms-gateway/compare/v1.2.0...v1.2.1
[v1.2.2]: https://github.com/capcom6/android-sms-gateway/compare/v1.2.1...v1.2.2
[v1.2.3]: https://github.com/capcom6/android-sms-gateway/compare/v1.2.2...v1.2.3
[v1.3.0]: https://github.com/capcom6/android-sms-gateway/compare/v1.2.3...v1.3.0
[v1.3.1]: https://github.com/capcom6/android-sms-gateway/compare/v1.3.0...v1.3.1
[v1.4.0]: https://github.com/capcom6/android-sms-gateway/compare/v1.3.1...v1.4.0
[v1.5.0]: https://github.com/capcom6/android-sms-gateway/compare/v1.4.0...v1.5.0
[v1.6.0]: https://github.com/capcom6/android-sms-gateway/compare/v1.5.0...v1.6.0
[v1.6.1]: https://github.com/capcom6/android-sms-gateway/compare/v1.6.0...v1.6.1
[v1.7.1]: https://github.com/capcom6/android-sms-gateway/compare/v1.6.1...v1.7.1
[v1.7.2]: https://github.com/capcom6/android-sms-gateway/compare/v1.7.1...v1.7.2
[v1.8.0]: https://github.com/capcom6/android-sms-gateway/compare/v1.7.2...v1.8.0
[v1.9.0]: https://github.com/capcom6/android-sms-gateway/compare/v1.8.0...v1.9.0
[v1.10.0]: https://github.com/capcom6/android-sms-gateway/compare/v1.9.0...v1.10.0
[v1.11.0]: https://github.com/capcom6/android-sms-gateway/compare/v1.10.0...v1.11.0
[v1.12.0]: https://github.com/capcom6/android-sms-gateway/compare/v1.11.0...v1.12.0
[v1.12.1]: https://github.com/capcom6/android-sms-gateway/compare/v1.12.0...v1.12.1
[v1.12.2]: https://github.com/capcom6/android-sms-gateway/compare/v1.12.1...v1.12.2
[v1.12.3]: https://github.com/capcom6/android-sms-gateway/compare/v1.12.2...v1.12.3
[v1.12.4]: https://github.com/capcom6/android-sms-gateway/compare/v1.12.3...v1.12.4
[v1.13.0]: https://github.com/capcom6/android-sms-gateway/compare/v1.12.4...v1.13.0
[v1.14.0]: https://github.com/capcom6/android-sms-gateway/compare/v1.13.0...v1.14.0
[v1.15.0]: https://github.com/capcom6/android-sms-gateway/compare/v1.14.0...v1.15.0
[v1.15.1]: https://github.com/capcom6/android-sms-gateway/compare/v1.15.0...v1.15.1
[v1.15.2]: https://github.com/capcom6/android-sms-gateway/compare/v1.15.1...v1.15.2
[v1.15.3]: https://github.com/capcom6/android-sms-gateway/compare/v1.15.2...v1.15.3
[v1.16.0]: https://github.com/capcom6/android-sms-gateway/compare/v1.15.3...v1.16.0
[v1.16.1]: https://github.com/capcom6/android-sms-gateway/compare/v1.16.0...v1.16.1
[v1.16.2]: https://github.com/capcom6/android-sms-gateway/compare/v1.16.1...v1.16.2
[v1.17.0]: https://github.com/capcom6/android-sms-gateway/compare/v1.16.2...v1.17.0
[v1.18.0]: https://github.com/capcom6/android-sms-gateway/compare/v1.17.0...v1.18.0
[v1.19.0]: https://github.com/capcom6/android-sms-gateway/compare/v1.18.0...v1.19.0
[v1.19.1]: https://github.com/capcom6/android-sms-gateway/compare/v1.19.0...v1.19.1
[v1.19.2]: https://github.com/capcom6/android-sms-gateway/compare/v1.19.1...v1.19.2
[v1.20.0]: https://github.com/capcom6/android-sms-gateway/compare/v1.19.2...v1.20.0
[v1.21.0]: https://github.com/capcom6/android-sms-gateway/compare/v1.20.0...v1.21.0
[v1.21.1]: https://github.com/capcom6/android-sms-gateway/compare/v1.21.0...v1.21.1
[v1.22.0]: https://github.com/capcom6/android-sms-gateway/compare/v1.21.1...v1.22.0
[v1.23.0]: https://github.com/capcom6/android-sms-gateway/compare/v1.22.0...v1.23.0
[v1.23.1]: https://github.com/capcom6/android-sms-gateway/compare/v1.23.0...v1.23.1
[v1.24.0]: https://github.com/capcom6/android-sms-gateway/compare/v1.23.1...v1.24.0
[v1.25.0]: https://github.com/capcom6/android-sms-gateway/compare/v1.24.0...v1.25.0
[v1.26.0]: https://github.com/capcom6/android-sms-gateway/compare/v1.25.0...v1.26.0
[v1.27.1]: https://github.com/capcom6/android-sms-gateway/compare/v1.26.0...v1.27.1
[v1.27.2]: https://github.com/capcom6/android-sms-gateway/compare/v1.27.1...v1.27.2
[v1.28.0]: https://github.com/capcom6/android-sms-gateway/compare/v1.27.2...v1.28.0
[v1.29.0]: https://github.com/capcom6/android-sms-gateway/compare/v1.28.0...v1.29.0
[v1.29.1]: https://github.com/capcom6/android-sms-gateway/compare/v1.29.0...v1.29.1
[v1.29.2]: https://github.com/capcom6/android-sms-gateway/compare/v1.29.1...v1.29.2
[v1.30.0]: https://github.com/capcom6/android-sms-gateway/compare/v1.29.2...v1.30.0
[v1.30.1]: https://github.com/capcom6/android-sms-gateway/compare/v1.30.0...v1.30.1
[v1.31.0]: https://github.com/capcom6/android-sms-gateway/compare/v1.30.1...v1.31.0
[v1.31.1]: https://github.com/capcom6/android-sms-gateway/compare/v1.31.0...v1.31.1
[v1.31.2]: https://github.com/capcom6/android-sms-gateway/compare/v1.31.1...v1.31.2
[v1.32.0]: https://github.com/capcom6/android-sms-gateway/compare/v1.31.2...v1.32.0
[v1.33.0]: https://github.com/capcom6/android-sms-gateway/compare/v1.32.0...v1.33.0
[v1.33.1]: https://github.com/capcom6/android-sms-gateway/compare/v1.33.0...v1.33.1
[v1.33.2]: https://github.com/capcom6/android-sms-gateway/compare/v1.33.1...v1.33.2
[v1.33.3]: https://github.com/capcom6/android-sms-gateway/compare/v1.33.2...v1.33.3
[v1.33.4]: https://github.com/capcom6/android-sms-gateway/compare/v1.33.3...v1.33.4
[v1.34.0]: https://github.com/capcom6/android-sms-gateway/compare/v1.33.4...v1.34.0
[v1.35.0]: https://github.com/capcom6/android-sms-gateway/compare/v1.34.0...v1.35.0
[v1.35.1]: https://github.com/capcom6/android-sms-gateway/compare/v1.35.0...v1.35.1
[v1.36.0]: https://github.com/capcom6/android-sms-gateway/compare/v1.35.1...v1.36.0
[v1.36.1]: https://github.com/capcom6/android-sms-gateway/compare/v1.36.0...v1.36.1
[v1.37.0]: https://github.com/capcom6/android-sms-gateway/compare/v1.36.1...v1.37.0
[v1.38.0]: https://github.com/capcom6/android-sms-gateway/compare/v1.37.0...v1.38.0
[v1.38.1]: https://github.com/capcom6/android-sms-gateway/compare/v1.38.0...v1.38.1
[v1.39.0]: https://github.com/capcom6/android-sms-gateway/compare/v1.38.1...v1.39.0
[v1.39.1]: https://github.com/capcom6/android-sms-gateway/compare/v1.39.0...v1.39.1
[v1.40.0]: https://github.com/capcom6/android-sms-gateway/compare/v1.39.1...v1.40.0
[v1.40.1]: https://github.com/capcom6/android-sms-gateway/compare/v1.40.0...v1.40.1
[v1.41.0]: https://github.com/capcom6/android-sms-gateway/compare/v1.40.1...v1.41.0
[v1.42.0]: https://github.com/capcom6/android-sms-gateway/compare/v1.41.0...v1.42.0
[v1.43.0]: https://github.com/capcom6/android-sms-gateway/compare/v1.42.0...v1.43.0
[v1.44.0]: https://github.com/capcom6/android-sms-gateway/compare/v1.43.0...v1.44.0
[v1.45.0]: https://github.com/capcom6/android-sms-gateway/compare/v1.44.0...v1.45.0
[v1.45.1]: https://github.com/capcom6/android-sms-gateway/compare/v1.45.0...v1.45.1
[v1.46.0]: https://github.com/capcom6/android-sms-gateway/compare/v1.45.1...v1.46.0
[v1.46.1]: https://github.com/capcom6/android-sms-gateway/compare/v1.46.0...v1.46.1
[v1.47.0]: https://github.com/capcom6/android-sms-gateway/compare/v1.46.1...v1.47.0
[v1.47.1]: https://github.com/capcom6/android-sms-gateway/compare/v1.47.0...v1.47.1
[v1.47.2]: https://github.com/capcom6/android-sms-gateway/compare/v1.47.1...v1.47.2
[v1.48.0]: https://github.com/capcom6/android-sms-gateway/compare/v1.47.2...v1.48.0
[v1.49.0]: https://github.com/capcom6/android-sms-gateway/compare/v1.48.0...v1.49.0
[v1.50.0]: https://github.com/capcom6/android-sms-gateway/compare/v1.49.0...v1.50.0
[v1.51.0]: https://github.com/capcom6/android-sms-gateway/compare/v1.50.0...v1.51.0
[v1.51.1]: https://github.com/capcom6/android-sms-gateway/compare/v1.51.0...v1.51.1
[v1.51.2]: https://github.com/capcom6/android-sms-gateway/compare/v1.51.1...v1.51.2
[v1.51.3]: https://github.com/capcom6/android-sms-gateway/compare/v1.51.2...v1.51.3
[v1.52.0]: https://github.com/capcom6/android-sms-gateway/compare/v1.51.3...v1.52.0
[v1.52.1]: https://github.com/capcom6/android-sms-gateway/compare/v1.52.0...v1.52.1
[v1.52.2]: https://github.com/capcom6/android-sms-gateway/compare/v1.52.1...v1.52.2
[v1.52.3]: https://github.com/capcom6/android-sms-gateway/compare/v1.52.2...v1.52.3
[v1.52.4]: https://github.com/capcom6/android-sms-gateway/compare/v1.52.3...v1.52.4
[v1.52.5]: https://github.com/capcom6/android-sms-gateway/compare/v1.52.4...v1.52.5
[v1.53.0]: https://github.com/capcom6/android-sms-gateway/compare/v1.52.5...v1.53.0
[v1.53.1]: https://github.com/capcom6/android-sms-gateway/compare/v1.53.0...v1.53.1
[v1.54.0]: https://github.com/capcom6/android-sms-gateway/compare/v1.53.1...v1.54.0
[v1.55.0]: https://github.com/capcom6/android-sms-gateway/compare/v1.54.0...v1.55.0
[v1.55.1]: https://github.com/capcom6/android-sms-gateway/compare/v1.55.0...v1.55.1
[v1.56.0]: https://github.com/capcom6/android-sms-gateway/compare/v1.55.1...v1.56.0
[v1.57.0]: https://github.com/capcom6/android-sms-gateway/compare/v1.56.0...v1.57.0
[v1.57.1]: https://github.com/capcom6/android-sms-gateway/compare/v1.57.0...v1.57.1
[v1.58.0]: https://github.com/capcom6/android-sms-gateway/compare/v1.57.1...v1.58.0
[v1.59.0]: https://github.com/capcom6/android-sms-gateway/compare/v1.58.0...v1.59.0
[v1.60.0]: https://github.com/capcom6/android-sms-gateway/compare/v1.59.0...v1.60.0
[v1.61.0]: https://github.com/capcom6/android-sms-gateway/compare/v1.60.0...v1.61.0
[v1.61.1]: https://github.com/capcom6/android-sms-gateway/compare/v1.61.0...v1.61.1
[v1.62.0]: https://github.com/capcom6/android-sms-gateway/compare/v1.61.1...v1.62.0
[v1.63.0]: https://github.com/capcom6/android-sms-gateway/compare/v1.62.0...v1.63.0
[v1.64.0]: https://github.com/capcom6/android-sms-gateway/compare/v1.63.0...v1.64.0
[v1.65.0]: https://github.com/capcom6/android-sms-gateway/compare/v1.64.0...v1.65.0
[v1.65.1]: https://github.com/capcom6/android-sms-gateway/compare/v1.65.0...v1.65.1
[v1.65.2]: https://github.com/capcom6/android-sms-gateway/compare/v1.65.1...v1.65.2
[v1.65.3]: https://github.com/capcom6/android-sms-gateway/compare/v1.65.2...v1.65.3
[v1.66.0]: https://github.com/capcom6/android-sms-gateway/compare/v1.65.3...v1.66.0
[v1.66.1]: https://github.com/capcom6/android-sms-gateway/compare/v1.66.0...v1.66.1