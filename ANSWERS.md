# Answers

Every number below comes from the app's own code. `AnswersTest` loads the Appendix B seed into Room, runs the day summary, prices O3 and the Q6 order, and charges 1,077 on the real `LegacyCardTerminal` (with a virtual clock). The blocks in grey are exactly what it prints, and the test fails if this file doesn't match:

```
./gradlew :app:testDebugUnitTest --tests "*AnswersTest" -Panswers --rerun
```

All amounts are BDT, all times are server time in Asia/Dhaka, and the catalog is Appendix A (v1).

## Q1, Q2, Q3. Appendix B, 20 November 2026

```
Q1
O1  347.00
O2  876.00
O3  988.00
O4  1,213.00
O5  520.00
O6  970.00
Day total  4,914.00

Q2
Cash  2,325.00
Card  2,589.00

Q3
Croissant: 9 units
```

- Cash is O1 + O5 + O6 + the 488 cash part of O3. Card is O2 + O4 + the 500 card part of O3. Split payments count by their parts, and 2,325 + 2,589 = 4,914.
- Croissant: O1 has 1, O4 has 2, O6 has 6. The free ones (P1) still count as sold. Next are Americano and Brownie with 4 each.
- The same numbers are on the Sales summary screen after "Load Appendix B seed" (TC-18, `DaySummaryTest`).

## Q4. O3 step by step

```
Q4 (O3, 16:59, DEVS10)
3 x Iced Tea Large @ 150.00 = 450.00
2 x Chicken Sandwich @ 320.00 = 640.00
Promotions: P2 -45.00
Subtotal  1,045.00
Without coupon: T1 -80.00
With coupon:    C1 -104.50
After discount  940.50
VAT exact  4702.5 poisha -> half-up 47.03
Total  987.53
Payable (half-up to whole BDT)  988.00
Payments: CARD 500.00, CASH 488.00
```

1. Iced Tea Large is 120 + 30 = 150.00, times 3 = 450.00. Chicken Sandwich 320.00 times 2 = 640.00. Lines total 1,090.00.
2. P1 (every 3 bakery, cheapest free): no bakery, nothing.
3. P2 happy hour: 16:59 is inside 15:00 to 17:00 (17:00 itself is not), so 10% off each beverage line. 10% of 450.00 is exactly 45.00, nothing to round. Subtotal 1,090.00 − 45.00 = 1,045.00.
4. Only one order discount applies, the bigger one. T1: 1,045.00 is at least 1,000, so 80.00 off. C1 (DEVS10, valid until 1 Dec): 10% of 1,045.00 = 104.50, exact, and under the 120.00 cap. C1 wins: 1,045.00 − 104.50 = 940.50.
5. VAT 5% of 940.50 = 47.025. In poisha that's 4,702.5, exactly half, and half-up makes it 4,703 = 47.03. In code: (94,050 × 500 + 5,000) / 10,000 = 4,703 with Long maths (C1 in DECISIONS.md explains why not Double).
6. Total 940.50 + 47.03 = 987.53.
7. Payable is rounded half-up to whole BDT: 987.53 → 988.00. In poisha: (98,753 + 50) / 100 × 100 = 98,800.
8. Paid card 500.00 and cash 488.00.

Every rounding is half-up and done once, at the step where the fraction appears: per line for happy hour, then the coupon, then VAT, then the payable.

## Q5. A card payment of 1,077 BDT

```
Q5 (card 1,077.00)
No callback. Asked the terminal at 15 s
queryStatus: APPROVED
Charges at the terminal for this ref: 1
```

Second by second:

- **0 s.** The cashier taps Charge. One Room transaction saves the order (PAYING, client id, receipt number), its lines and a card payment row with a new ref, status PENDING (D3). Only after that commit does the app call `charge(107700, ref)`, once. The SDK counts the attempt, marks the ref APPROVED (the money is taken here) and, because the amount ends in 77, never calls back. The screen shows "Waiting for the card terminal…".
- **0 to 15 s.** The wrapper is suspended in `withTimeoutOrNull(15 s)`. Nothing else happens: no retry, no second `charge()`. The wait runs in an app-wide scope, so rotating the phone or leaving the screen changes nothing.
- **15 s.** The timeout fires. The first thing the wrapper does is `queryStatus(ref)`, which answers APPROVED. The result is `Approved` (no auth code, because that only comes in the callback).
- **Straight after.** One transaction marks the payment APPROVED. If 1,077 covers the payable, the order becomes PENDING (paid) in the same transaction, the cashier sees "Done", and a sync is queued. In a split, the screen shows the rest due in cash.
- **If the app dies in those 15 s.** The PENDING row with its ref is still in Room. On the next start `recover()` calls `queryStatus(ref)` for it, never `charge()` (TC-17).

Afterwards there is exactly **one** charge at the terminal for that ref (`attempts(ref) == 1`, TC-12).

## Q6. TC-06 plus one Brownie

```
Q6 (TC-06 plus one Brownie)
Bakery units 4 -> 5
P1  120.00 -> 120.00
Subtotal  1,278.00
Discount: C1 -120.00
VAT  57.90
Total  1,215.90
Payable  1,094.00 -> 1,216.00
```

The payable is **1,216**.

P1 gives one free unit for every 3 full bakery units: floor(units / 3). TC-06 has 2 Croissants + 2 Brownies = 4 units, so 1 free. One more Brownie makes 5, and floor(5 / 3) is still 1; a second free unit needs 6. The free one is the cheapest unit, a Brownie at 120.00, in both orders. So P1 stays 120.00.

What does change: lines are 820 + 300 + 360 = 1,480.00, minus P1 120.00 and P2 82.00, gives 1,278.00. C1 would be 127.80, but it is capped at 120.00, which still beats T1's 80.00. 1,278.00 − 120.00 = 1,158.00, VAT 57.90, total 1,215.90, payable 1,216.
