# Decisions

Money is always `Long` poisha (1 BDT = 100 poisha). Times are server time in Asia/Dhaka unless said otherwise.

## C1. Money in a Double, and rounding 47.025

**Why not Double.** A Double stores numbers in binary, and most decimal amounts have no exact binary form. 1,094.31 is really stored as 1094.30999999999994543... It prints fine, but every add and multiply carries a tiny error, and when a value lands exactly on a half poisha, that error decides which way it rounds. I checked on the JVM: 20.70 BDT x 0.05 prints as 1.035, but the stored value is just under it, so rounding gives 1.03 instead of 1.04. One poisha looks small, but then the receipt, the day summary and the server won't agree, and a POS has to be exact. So all money is a Long in poisha (1,094.31 BDT = 109431), and the only place a fraction can appear is a percentage step, where I round on purpose.

**Rounding 47.025 BDT by hand.** This is order O3. Discounted subtotal is 940.50 BDT = 94,050 poisha.

- VAT 5% = 94,050 x 5 / 100 = 4,702.5 poisha
- Half-up to the nearest poisha: exactly .5 goes up, so 4,703 poisha = **47.03 BDT**
- In code it's Long math with the rate in basis points (5% = 500): (94,050 x 500 + 5,000) / 10,000 = 47,030,000 / 10,000 = 4,703. Adding half the divisor (5,000) before the integer division turns "cut off" into "round half up". That's only correct for non-negative values, which money here always is.
- Payable = 94,050 + 4,703 = 98,753 poisha = 987.53 BDT. Rounded half-up to whole BDT: (98,753 + 50) / 100 = **988 BDT**.

## D3. When the client id and the card ref are created

Both are created and saved before the terminal is touched. When the cashier taps Pay, the order gets its clientOrderId (UUID, once per order) and each card attempt gets a new card ref (UUID). Order, lines and the card payment row (ref, PENDING) go into Room in one transaction, and only after that commit do I call `charge(ref)`. So if the app dies any time after `charge()`, the ref is already on disk and the next launch asks `queryStatus(ref)` instead of charging again. A declined or voided attempt keeps its row; a retry gets a new ref.

## C2. Card wrapper: callback once, twice, never, after cancel

`CardTerminal.charge(amount, ref)` is a suspend function. It calls the SDK's `charge()` once and waits for the callback with a 15 s timeout. An `AtomicBoolean` makes sure only the first callback can resume the coroutine.

- **(a) Once.** The callback resumes the coroutine with `Approved(authCode)` or `Declined(reason)` and the caller updates the PENDING payment row.
- **(b) Twice.** The first callback resumes; the second sees the flag already set and is only logged. Resuming twice would crash, and saving twice would double the payment, so it never gets that far (TC-13).
- **(c) Never.** After 15 s the timeout fires and the first thing I do is `queryStatus(ref)`: APPROVED gives `Approved`, DECLINED gives `Declined`, NOT_FOUND gives `Unknown`. `charge()` is never called again for that ref, so TC-12 (1,077) ends Approved with `attempts(ref) == 1`. On `Unknown` the cashier sees "Card result unknown" with "Check again" (queries again) and "Not charged, try again" (voids that ref, new attempt with a new ref or cash). The app never re-charges by itself.
- **(d) After the coroutine was cancelled.** The callback sees nobody is waiting and is only logged. Nothing is lost: the payment row is still PENDING in Room with its ref, and the next time the order is opened I call `queryStatus(ref)` and get the real answer. Room is the source of truth; the callback is just the fast way to hear about it.
