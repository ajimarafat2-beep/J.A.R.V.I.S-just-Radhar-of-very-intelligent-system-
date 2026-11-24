package com.example.accounts

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ConnectedAccountsParserTest {

    @Test
    fun parseIcalEvents_extractsMultipleAirbnbReservations() {
        val sampleIcs = """
            BEGIN:VCALENDAR
            VERSION:2.0
            PRODID:-//Airbnb Inc//Hosting Calendar 0.8.8//EN
            BEGIN:VEVENT
            DTEND;VALUE=DATE:20261215
            DTSTART;VALUE=DATE:20261210
            UID:abc123@airbnb.com
            SUMMARY:Reserved - Dhaka Penthouse Stay
            END:VEVENT
            BEGIN:VEVENT
            DTEND;VALUE=DATE:20261222
            DTSTART;VALUE=DATE:20261220
            SUMMARY:Airbnb (Not available)
            END:VEVENT
            END:VCALENDAR
        """.trimIndent()

        val events = ConnectedAccountsManager.parseIcalEvents(sampleIcs)
        assertEquals(2, events.size)
        assertEquals("Reserved - Dhaka Penthouse Stay", events[0].summary)
        assertEquals("20261210", events[0].checkIn)
        assertEquals("20261215", events[0].checkOut)
        assertEquals("Airbnb (Not available)", events[1].summary)
        assertEquals("20261220", events[1].checkIn)
        assertEquals("20261222", events[1].checkOut)
    }

    @Test
    fun parseIcalEvents_handlesEmptyCalendarSafely() {
        val events = ConnectedAccountsManager.parseIcalEvents("BEGIN:VCALENDAR\nEND:VCALENDAR")
        assertTrue(events.isEmpty())
    }
}
