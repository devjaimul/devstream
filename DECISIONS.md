# Decisions

Money is always `Long` poisha (1 BDT = 100 poisha). Times are server time in Asia/Dhaka unless said otherwise.

## C1. Money in a Double, and rounding 47.025

**Why not Double.** A Double stores numbers in binary, and most decimal amounts have no exact binary form. 1,094.31 is really stored as 1094.30999999999994543... It prints fine, but every add and multiply carries a tiny error, and when a value lands exactly on a half poisha, that error decides which way it rounds. I checked on the JVM: 20.70 BDT x 0.05 prints as 1.035, but the stored value is just under it, so rounding gives 1.03 instead of 1.04. One poisha looks small, but then the receipt, the day summary and the server won't agree, and a POS has to be exact. So all money is a Long in poisha (1,094.31 BDT = 109431), and the only place a fraction can appear is a percentage step, where I round on purpose.

**Rounding 47.025 BDT by hand.** This is order O3. Discounted subtotal is 940.50 BDT = 94,050 poisha.

- VAT 5% = 94,050 x 5 / 100 = 4,702.5 poisha
- Half-up to the nearest poisha: exactly .5 goes up, so 4,703 poisha = **47.03 BDT**
- In code it's Long math with the rate in basis points (5% = 500): (94,050 x 500 + 5,000) / 10,000 = 47,030,000 / 10,000 = 4,703. Adding half the divisor (5,000) before the integer division turns "cut off" into "round half up". That's only correct for non-negative values, which money here always is.
- Payable = 94,050 + 4,703 = 98,753 poisha = 987.53 BDT. Rounded half-up to whole BDT: (98,753 + 50) / 100 = **988 BDT**.
