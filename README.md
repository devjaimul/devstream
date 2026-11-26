# Café POS, offline first

[![tests](https://github.com/devjaimul/devstream/actions/workflows/test.yml/badge.svg)](https://github.com/devjaimul/devstream/actions/workflows/test.yml)

DevsStream Android assessment. A café till that validates items, prices orders to the poisha, takes cash, card and split payments without ever charging a card twice, and keeps every sale in Room until the (fake) server has it, with no network, a wrong phone clock or a killed process.

Kotlin, Jetpack Compose, MVVM with StateFlow, Coroutines + Flow, Room, Hilt, WorkManager, JUnit + Robolectric. minSdk 24.

- Decisions D1 to D6 and checkpoints C1 to C4: [DECISIONS.md](DECISIONS.md)
- Answers Q1 to Q6: [ANSWERS.md](ANSWERS.md)

## Run it

You need JDK 17 and the Android SDK (platform 36). Android Studio has both.

```
./gradlew test             # all unit tests, :core and :app
./gradlew assembleDebug    # app/build/outputs/apk/debug/app-debug.apk
./gradlew :app:testDebugUnitTest --tests "*AnswersTest" -Panswers --rerun   # prints ANSWERS.md from the code
```

There are 126 tests: 36 in `:core`, 90 in `:app`. Gradle runs the `:app` tests for both debug and release, so the console shows them twice. The first run downloads Robolectric's Android jar, which takes a minute. CI runs the same tests and builds the APK on every push ([.github/workflows/test.yml](.github/workflows/test.yml)). The debug APK is also attached to the GitHub release.

## Trying it on a phone

The **Server** tab has the Appendix C test hooks:
- server time, with buttons for 20 Nov 15:30, 20 Nov 17:00 and 1 Dec 15:30;
- failure modes: RANDOM (Appendix C), OFF, NETWORK_DOWN, ERROR_AFTER_SAVE;
- catalog bump to v2;
- sync now.

| To see | Do |
|---|---|
| TC-01 to TC-04 | Open Latte. The rule messages show live and Add stays disabled. |
| TC-06 | Server time 20 Nov 15:30. Latte Large + Oat + Extra shot + Vanilla ×2, Croissant ×2, Brownie ×2, coupon `DEVS10`: payable 1,094. |
| TC-07, TC-08 | Same cart after setting the server time to 17:00 or 1 Dec. |
| TC-09 | Change the phone's date. Prices and the business day don't move. |
| TC-10 to TC-13 | Pay by Split. A card part ending in 13 is declined; ending in 77 never calls back (watch the 15 s); ending in 55 calls back twice. |
| TC-14 | Airplane mode, make 3 sales, turn it off. The Orders tab shows them go out in order. |
| TC-15 | Failures ERROR_AFTER_SAVE, make a sale: the server keeps it but answers 500. Switch back to OFF: the retry comes back as a duplicate, and the server still has one copy. |
| TC-16 | Failures NETWORK_DOWN, make a sale: after 5 tries it's FAILED and stays in Orders. Switch to OFF and tap Retry. |
| TC-17 | Start a card ending in 77, press Home, run `adb shell am kill com.devsstream.cafepos`, open the app again. |
| TC-18 | Summary tab, then **Load Appendix B seed**. |

## Architecture

Two Gradle modules:

- **`:core`** is pure Kotlin with no Android dependency (the Part F bonus): catalog model, `validate()`, `priceOrder()`, money in `Long` poisha with half-up rounding, receipt number format, business day. It has the Part A and B tests.
- **`:app`** is the Android app. Its test fixtures (Appendix A, the Section 8 orders) come from `:core`'s `testFixtures`.

Packages in `:app`, top to bottom:

```
ui/           Compose screens and their ViewModels. Screens only read StateFlows,
              which come from Room or from the catalog.
checkout/     Checkout: prices again at the tap, saves the order with its first payment (D3, D4)
payment/      CardTerminal: suspend wrapper around LegacyCardTerminal (C2)
              CardPayments: runs charges in an app-wide scope, recovers open cards on start
summary/      DaySummary (one pure function), SeedLoader (Appendix B from a JSON file)
data/         OrderRepository: Room transactions, order states (D2), receipt numbers
              CatalogRepository: last catalog cached on disk for offline
data/local/   Room entities, DAO, database. Exported schema: app/schemas/
data/remote/  PosApi and DTOs, ClockSyncingApi, OfflineGateApi, fake/FakePosServer (Appendix C)
data/sync/    OrderSync (the queue), SyncWorker (WorkManager), SyncScheduler
time/         ServerClock (D1)
di/           Hilt module and the AppScope qualifier
```

**DI (Hilt).** `AppModule` provides the singletons:
- `ServerClock`, `FakePosServer` and `PosApi` (= `ClockSyncingApi(OfflineGateApi(FakePosServer))`);
- `PosDatabase`, the catalog cache and `SyncScheduler`;
- `CardTerminal` and an `@AppScope` `CoroutineScope`.

Everything else uses `@Inject` constructors. The worker is a `@HiltWorker`, and `PosApp` gives WorkManager Hilt's worker factory.

**A card sale, end to end.**
1. The tap prices the cart again at server time; if the price changed, nothing is saved.
2. One transaction saves the order, its lines, its receipt number and a PENDING card row with a new ref.
3. `charge()` is called once, in the app scope.
4. The result is written to Room. If the payments cover the payable, the order becomes PENDING (paid) in the same transaction and a sync is queued.
5. WorkManager posts orders oldest first, with the same `clientOrderId` on every try, so a retry after a lost response comes back `duplicate = true`.

## Requirements and where they are tested

| Brief | Code | Tests |
|---|---|---|
| Part A validation | `core/.../validation/Validate.kt` | `ValidateTest` |
| Part B pricing | `core/.../pricing/PriceOrder.kt`, `core/.../money/Money.kt` | `PriceOrderTest`, `MoneyTest` |
| Part C payments | `payment/CardTerminal.kt`, `payment/CardPayments.kt`, `checkout/Checkout.kt` | `CardTerminalTest`, `CheckoutTest` |
| Part D offline + sync | `data/OrderRepository.kt`, `data/sync/`, `time/ServerClock.kt`, `core/.../order/ReceiptNumber.kt` | `OrderRepositoryTest`, `OrderSyncTest`, `SyncWorkerTest`, `ServerClockTest`, `ReceiptNumberTest` |
| Part E summary | `summary/DaySummary.kt`, `summary/SeedLoader.kt`, `ui/summary/` | `DaySummaryTest` |
| Fake API (App. C) | `data/remote/fake/` | `FakePosServerTest` |
| Data-driven catalog | `data/remote/CatalogDto.kt`, `data/CatalogRepository.kt` | `CatalogDtoTest`, `CatalogRepositoryTest` |
| ANSWERS.md | `AnswersTest` checks the file against the code | `AnswersTest` |

| TC | Test |
|---|---|
| TC-01 to TC-04 | `ValidateTest`: `TC-01 latte with no size is blocked`, … `TC-04 lists both syrup maxDistinct and R2` |
| TC-05 to TC-08 | `PriceOrderTest`: `TC-05 unit prices of X Y Z`, … `TC-08 on 1 Dec DEVS10 is expired and T1 applies` |
| TC-09 | `Tc09ServerTimeTest` |
| TC-10 | `CheckoutTest`: `TC-10 payable 1094, card 600 approved, cash tendered 500 gives change 6` |
| TC-11 to TC-13 | `CardTerminalTest` (the wrapper) and `CheckoutTest` (the whole flow with Room) |
| TC-14 | `OrderSyncTest` (order and count) and `SyncWorkerTest` (waits for the network constraint) |
| TC-15, TC-16 | `OrderSyncTest` |
| TC-17 | `CheckoutTest`: screen destroyed while waiting, and process killed then recovered on start |
| TC-18 | `DaySummaryTest`: `TC-18 summary for 20 Nov with Appendix B loaded` |

## Data-driven catalog

The menu, sizes, modifier groups, limits, rules, promotions and coupons all come from the fake server's catalog JSON. The last copy is cached in `catalog.json`, so the till sells offline.
- The item picker builds itself from the groups and their limits.
- Promotion names come from their type.
- Category colours come from their position in the catalog.

A search of the main sources finds no product, option or rule ids and no prices. The only "T1" is the till prefix in receipt numbers. **Bump catalog** on the Server tab makes Latte 240 with no code change. The Appendix B seed is a data file (`app/src/main/resources/seed/appendix-b.json`) that our engine prices.

## Trade-offs

- **Money** is `Long` poisha everywhere. Percentages are basis points, and each step is rounded half-up once (C1).
- **One order status** instead of a payment status plus a sync status, so impossible combinations can't exist (D2).
- **Sync** sends one order at a time, oldest first. A failing order holds back the ones behind it until it becomes FAILED (5 tries, backoff 10, 20, 40, 80 s). Creation order is kept, at the cost of up to about 2.5 minutes' delay behind a bad order.
- **Unique work with APPEND_OR_REPLACE** can cause a few empty sync runs, but a sale saved during a run is never missed.
- **Price drift:** prices are checked again at the tap, and a changed price is shown rather than charged.
- **The day summary** is worked out in Kotlin from one day's Room Flow, not SQL sums. A day is small, and one pure function is easy to test.
- **The fake server runs inside the app.** `OfflineGateApi` makes airplane mode count as "no network" for it too, and its state lives in its own file so a killed app doesn't wipe the "server".
- **Robolectric tests run on SDK 34**, because SDK 35+ needs JDK 21 and the build uses 17.

## Limitations and what's unfinished

- **Part F live order board** (fake WebSocket) is not done. The other two bonus items, CI and the pure-Kotlin pricing module, are.
- **The card terminal is simulated inside the app process.** After a real process kill it forgets every ref, so a card that was waiting comes back as "Card result unknown", and the cashier checks the terminal's slip. The app never charges that ref again. A real terminal is a separate device and would still know.
- **After a reboot with no network**, server time is a best guess. It never goes back before the last server time, and those orders are marked "time not confirmed". Moving the phone clock *forward* in that state can't be detected until the next server reply. The fake server's own clock also starts again from the phone clock after a reboot.
- **A cart that isn't paid yet is kept in memory**, so it's lost if Android kills the app before Pay. Orders and payments are always in Room.
- **The receipt sequence lives on the device.** Clearing app data starts it again; a real server would need to own it.
- **The summary counts every paid sale of the day.** The Appendix B total (4,914) assumes the seed is the only thing on 20 Nov.
- **No Compose UI tests.** The logic under the screens is unit tested, and the flows were checked by hand on an emulator, including killing the process during a card payment and rotating.
- **UI strings are inline English, not `strings.xml`.** The release build isn't minified or signed.

## Time spent

About 12 hours.

## How I used AI

I'm a Flutter developer, and this was my first full Kotlin/Android project. I used an AI assistant (Claude, through Claude Code) heavily: it wrote most of the code, tests and first drafts of the documents, in small steps. For each decision point I was given options with the trade-offs and picked one, and DECISIONS.md records those choices. I went through each step with explanations mapped to things I know from Flutter (a Completer and `suspendCancellableCoroutine`, drift and Room, `fake_async` and `runTest`), ran the tests, and committed it myself. That's why the history is made of many small commits. The Section 8 and Appendix B numbers were worked out by hand first and then compared with the engine, and the app was run on an emulator, not only tested.
