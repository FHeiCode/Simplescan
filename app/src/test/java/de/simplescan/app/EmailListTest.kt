package de.simplescan.app

import de.simplescan.app.data.EmailList
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class EmailListTest {
    @Test
    fun splitsAndDeduplicates() {
        assertEquals(listOf("a@b.de", "c@d.com"), EmailList.split(" a@b.de, c@d.com; A@B.de "))
        assertEquals(emptyList<String>(), EmailList.split("   "))
    }

    @Test
    fun validates() {
        assertNull(EmailList.firstInvalid("anna.meier+scan@mail.example.de"))
        assertNull(EmailList.firstInvalid(""))
        assertEquals("anna@", EmailList.firstInvalid("ok@x.de, anna@"))
        assertEquals("keinat.de", EmailList.firstInvalid("keinat.de"))
        assertEquals("a@b", EmailList.firstInvalid("a@b"))
    }

    @Test
    fun normalizeAndMailto() {
        assertEquals("a@b.de, c@d.de", EmailList.normalize("a@b.de;c@d.de"))
        assertEquals("a+x@b.de,c@d.de", EmailList.mailtoRecipients(listOf("a+x@b.de", "c@d.de")).replace("%2B", "+"))
        assertEquals("", EmailList.mailtoRecipients(emptyList()))
    }
}
