package com.devsstream.cafepos.core.time

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

// The café's day is the calendar date in the catalog's time zone, at server time.
fun Instant.businessDay(zone: ZoneId): LocalDate = atZone(zone).toLocalDate()
