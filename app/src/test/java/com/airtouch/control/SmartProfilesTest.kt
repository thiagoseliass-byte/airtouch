package com.airtouch.control

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SmartProfilesTest {
    @Test fun packages() {
        assertEquals("Apresentação", SmartProfiles.forPackage("com.microsoft.office.powerpoint"))
        assertEquals("Mídia", SmartProfiles.forPackage("com.google.android.youtube"))
        assertEquals("Leitura", SmartProfiles.forPackage("com.adobe.reader"))
        assertEquals("Navegação", SmartProfiles.forPackage("com.sec.android.app.launcher"))
        assertEquals("Jogos", SmartProfiles.forPackage("com.roblox.client"))
        assertNull(SmartProfiles.forPackage("com.exemplo.qualquer"))
        assertNull(SmartProfiles.forPackage(null))
    }

    @Test fun tabs() {
        assertEquals("Apresentação", SmartProfiles.forTab("PRESENT"))
        assertEquals("Leitura", SmartProfiles.forTab("DOCS"))
        assertEquals("Jogos", SmartProfiles.forTab("GAMES"))
        assertNull(SmartProfiles.forTab("PANEL"))
    }

    @Test fun allSuggestedProfilesExist() {
        val names = Profile.defaults().map { it.name }
        listOf("PRESENT", "DOCS", "GAMES").forEach { assert(SmartProfiles.forTab(it) in names) }
        assert(SmartProfiles.MEDIA in names && SmartProfiles.NAVIGATION in names)
    }
}
