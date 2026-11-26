# Decisions

Money is always `Long` poisha (1 BDT = 100 poisha). Times are server time in Asia/Dhaka unless said otherwise.

## D1. Server time, offline and after a restart

Every server reply carries `serverTime`. I save it with `elapsedRealtime()` from the middle of the request and the boot count, and "now" is that server time plus the elapsed time since. `elapsedRealtime` ignores the phone clock and keeps counting across app restarts, so the time stays right offline and after a restart. After a reboot I use the phone clock plus the last known offset, never earlier than the last server time, and mark those sales "time not confirmed".

## D2. Order states

One status per order: `PAYING → PENDING → SYNCED`, plus `FAILED` and `CANCELLED`, so "unpaid but synced" can't happen. PAYING shows the payment screen (waiting for card, declined, or card result unknown); PENDING means paid, the cashier saw "Done", and it isn't on the server yet. SYNCED shows the server's order number. The 5th failed upload makes it FAILED with the error and a Retry back to PENDING; CANCELLED is only for an unpaid order with nothing taken, and it stays visible.

## D3. When the client id and the card ref are created

Both are created and saved before the terminal is touched. On Pay the order gets a UUID client id and the card attempt a new UUID ref; the order, lines and the PENDING card row are committed in one transaction, and only then is `charge(ref)` called, once. If the app dies after that, the ref is on disk and the next start asks `queryStatus(ref)` instead of charging again. Every new attempt gets a new ref.

## D4. Split payment: which tender first

Card first, then cash, because only the card can be declined or come back unknown. Cash is taken once the card part is approved, as the exact amount left, with the change shown (TC-10: card 600, cash 494, tendered 500, change 6). If the card is declined, no cash is in the order yet: the cashier tries another card, takes it all in cash, or hands the notes back and cancels. While a card result is open, the order takes no cash and no second card.

## D5. Catalog changes while orders are unsynced

Each order keeps the prices it was sold at. The catalog version and the whole breakdown are saved with the sale, and sync and the receipt use exactly those numbers, so 3 unsynced orders still say Latte 220 after it goes to 240. The new catalog only applies from the next cart.

## D6. Room schema and JSON shapes

Room has three tables, `orders`, `order_lines` and `payments`, with money as `Long` poisha; options and promotions are small JSON maps. The receipt number is `MAX(seq) + 1` for the day, read inside the insert transaction, with a unique index as a backstop. JSON money is integer poisha (`unitPricePoisha`), times are UTC, and `postOrder` sends the whole order with the payments that paid it.

Reference (not part of the answer):

```
orders       id PK autoincrement, clientOrderId UNIQUE, receiptNo, businessDay, receiptSeq,
             UNIQUE(businessDay, receiptSeq), createdAt, timeConfirmed, catalogVersion,
             coupon, couponRejection, subtotal, promotions {"P1":12000,"P2":8200},
             discountId, discountAmount, vat, total, payable,
             status, syncAttempts, lastError, serverOrderNo
order_lines  id PK, orderId FK, productId, sizeId, options {"oat":1,"extra_shot":1},
             label, category, quantity, unitPrice, total
payments     id PK, orderId FK, type CASH|CARD, status PENDING|APPROVED|DECLINED|UNKNOWN|VOIDED,
             amount, tendered, change, cardRef UNIQUE, authCode, declineReason
```

Cash rows are written APPROVED straight away. The generated schema is in `app/schemas/`.

```json
POST order
{
  "clientOrderId": "3f1c9e2a-...", "receiptNo": "T1-20261120-0001",
  "createdAt": "2026-11-20T09:30:00Z", "businessDay": "2026-11-20", "timeConfirmed": true,
  "catalogVersion": 1, "coupon": "DEVS10",
  "lines": [{ "productId": "latte", "sizeId": "large", "options": [{ "id": "oat", "quantity": 1 }],
              "label": "Latte Large, Oat", "category": "BEVERAGE",
              "quantity": 2, "unitPricePoisha": 33000, "totalPoisha": 66000 }],
  "promotions": [{ "id": "P2", "amountPoisha": 6600 }],
  "subtotalPoisha": 59400, "discount": { "id": "C1", "amountPoisha": 5940 },
  "vatPoisha": 2673, "totalPoisha": 56133, "payablePoisha": 56100,
  "payments": [{ "type": "CARD", "amountPoisha": 50000, "cardRef": "8d0b...", "authCode": "AUTH-123" },
               { "type": "CASH", "amountPoisha": 6100, "tenderedPoisha": 10000, "changePoisha": 3900 }]
}
response: { "serverOrderNo": 7, "serverTime": "2026-11-20T09:32:31Z", "duplicate": false }

GET catalog -> { "serverTime": "...", "catalog": { "version": 1, "timeZone": "Asia/Dhaka", "vatBps": 500,
  "products": [...], "groups": [...],
  "rules": [{ "id": "R1", "type": "requires", "optionId": "hazelnut", "anyOf": ["oat", "almond"] }],
  "promotions": [{ "id": "P1", "type": "cheapest_free", "category": "BAKERY", "every": 3 }],
  "orderDiscounts": [{ "id": "T1", "type": "threshold", "minSubtotalPoisha": 100000, "amountOffPoisha": 8000 }] } }
```

The full catalog file is `app/src/main/resources/fake-server/catalog-v1.json`.

## C1. Money in a Double, and rounding 47.025

**Why not Double.** A Double stores numbers in binary, and most decimal amounts have no exact binary form. 1,094.31 is really stored as 1094.30999999999994543... It prints fine, but every add and multiply carries a tiny error, and when a value lands exactly on a half poisha, that error decides which way it rounds. I checked on the JVM: 20.70 BDT x 0.05 prints as 1.035, but the stored value is just under it, so rounding gives 1.03 instead of 1.04. One poisha looks small, but then the receipt, the day summary and the server won't agree, and a POS has to be exact. So all money is a Long in poisha (1,094.31 BDT = 109431), and the only place a fraction can appear is a percentage step, where I round on purpose.

**Rounding 47.025 BDT by hand.** This is order O3. Discounted subtotal is 940.50 BDT = 94,050 poisha.

- VAT 5% = 94,050 x 5 / 100 = 4,702.5 poisha
- Half-up to the nearest poisha: exactly .5 goes up, so 4,703 poisha = **47.03 BDT**
- In code it's Long math with the rate in basis points (5% = 500): (94,050 x 500 + 5,000) / 10,000 = 47,030,000 / 10,000 = 4,703. Adding half the divisor (5,000) before the integer division turns "cut off" into "round half up". That's only correct for non-negative values, which money here always is.
- Payable = 94,050 + 4,703 = 98,753 poisha = 987.53 BDT. Rounded half-up to whole BDT: (98,753 + 50) / 100 = **988 BDT**.

## C2. Card wrapper: callback once, twice, never, after cancel

`CardTerminal.charge(amount, ref)` is a suspend function. It calls the SDK's `charge()` once and waits for the callback with a 15 s timeout. An `AtomicBoolean` makes sure only the first callback can resume the coroutine.

- **(a) Once.** The callback resumes the coroutine with `Approved(authCode)` or `Declined(reason)` and the caller updates the PENDING payment row.
- **(b) Twice.** The first callback resumes; the second sees the flag already set and is only logged. Resuming twice would crash, and saving twice would double the payment, so it never gets that far (TC-13).
- **(c) Never.** After 15 s the timeout fires and the first thing I do is `queryStatus(ref)`: APPROVED gives `Approved`, DECLINED gives `Declined`, NOT_FOUND gives `Unknown`. `charge()` is never called again for that ref, so TC-12 (1,077) ends Approved with `attempts(ref) == 1`. On `Unknown` the cashier sees "Card result unknown" with "Check again" (queries again) and "Not charged, try again" (voids that ref, new attempt with a new ref or cash). The app never re-charges by itself.
- **(d) After the coroutine was cancelled.** The callback sees nobody is waiting and is only logged. In practice that's the 15 s timeout from (c): the wait was cancelled, `queryStatus` already gave the answer, and a late callback can't change a payment row that is already settled. Leaving or rotating the screen doesn't cancel anything, because the charge runs in an app-wide scope. If the process dies there is no callback at all: the row is still PENDING in Room with its ref, and on the next start the app calls `queryStatus(ref)` for every open card row. Room is the source of truth; the callback is just the fast way to hear about it.

## C3. The POST reached the server, then the app was killed

1. Before the kill: the server saved the order (say #7), but the app never read the answer. In Room the order is still PENDING with no server number, and no failure was counted either, because the code after the POST never ran.
2. Next launch: `PosApp.onCreate` asks for a sync. WorkManager would also run it again on its own, since it keeps its queue in its own database.
3. Once there is a network, the worker takes the oldest PENDING order, which is this one, and POSTs it again with the same `clientOrderId` and the same body. Nothing is rebuilt or re-priced.
4. The server already has that `clientOrderId`, so it answers `serverOrderNo 7, duplicate = true` and creates nothing.
5. The app treats duplicate as success: the order becomes SYNCED with #7 and the worker moves on to the next one. The server still has exactly one copy (TC-15). A 500 after the save works the same way, it just counts as one failed attempt first.

## C4. Phone says 19 November and the app is offline

The sale belongs to 20 November. The app never asks the phone what day it is: the business day is `ServerClock.now()` in Asia/Dhaka, and that is the last server time plus how long has passed on the monotonic clock. Changing the phone's date changes `System.currentTimeMillis()`, which this doesn't read. Even if the phone was also rebooted offline, the fallback can't go back before the last server time it saw (20 Nov), so the sale still lands on 20 Nov, just marked "time not confirmed" until the next sync.
