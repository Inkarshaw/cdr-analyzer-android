package ink.clearexams.cdranalyzer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ContactIntelligenceTest {
    private fun record(number: String, date: String, type: String = "Outgoing", subject: String = "9000000000") =
        CdrRecord(number = subject, otherParty = number, dateTime = date, direction = type, duration = "30")

    @Test fun historicalFirstCallPrecedesActiveDateFilter() {
        val old = record("9111111111", "01-01-2026 10:00:00")
        val recent = record("9111111111", "03-02-2026 11:00:00")
        val contact = contactInsights(listOf(recent), listOf(old, recent)).single()
        assertEquals(old.dateTime, contact.firstCall?.dateTime)
        assertEquals(recent.dateTime, contact.lastRecord?.dateTime)
        assertEquals(1, contact.calls)
    }

    @Test fun smsIsNotMistakenForFirstVoiceCall() {
        val sms = record("9111111111", "01-01-2026 10:00:00", "SMS")
        val call = record("9111111111", "01-02-2026 10:00:00", "MOC")
        val contact = contactInsights(listOf(sms, call), listOf(sms, call)).single()
        assertEquals(call.dateTime, contact.firstCall?.dateTime)
        assertEquals(1, contact.sms)
        assertEquals(1, contact.calls)
    }

    @Test fun unknownDatesAreSortedLast() {
        val dated = record("9111111111", "01-01-2026 10:00:00")
        val unknown = record("9222222222", "")
        val contacts = contactInsights(listOf(unknown, dated), listOf(unknown, dated))
        assertEquals("9111111111", orderedContacts(contacts, "First ↑").first().number)
        assertEquals("9222222222", orderedContacts(contacts, "First ↓").last().number)
    }

    @Test fun commonContactsAreVisibleAcrossSubjectCdrs() {
        val a = record("9111111111", "01-01-2026 10:00:00", subject = "9000000001")
        val b = record("9111111111", "02-01-2026 10:00:00", subject = "9000000002")
        val contact = contactInsights(listOf(a, b), listOf(a, b)).single()
        assertEquals(setOf("9000000001", "9000000002"), contact.subjects)
    }

    @Test fun csvQuotesAndNeutralizesSpreadsheetFormulae() {
        val r = record("9111111111", "01-01-2026 10:00:00")
        val c = contactInsights(listOf(r), listOf(r))
        val export = exportContactsCsv(c, mapOf("9111111111" to ContactTag(name = "=HYPERLINK(\"x\")", relation = "Witness")))
        assertTrue(export.contains("'=HYPERLINK"))
        assertTrue(export.contains("\"\"x\"\""))
        assertFalse(export.contains(",=HYPERLINK"))
    }
}
