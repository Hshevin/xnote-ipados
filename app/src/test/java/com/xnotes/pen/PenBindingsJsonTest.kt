package com.xnotes.pen

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class PenBindingsJsonTest {
    @Test fun allThreeBindingsRoundTripIndependently() {
        val bindings = mapOf(
            32 to PenBinding("eraser", PenBindingMode.HOLD),
            64 to PenBinding("highlighter", PenBindingMode.SELECT),
            4 to PenBinding("redo", PenBindingMode.SELECT),
        )
        assertEquals(bindings, PenBindingsJson.read(JSONObject(PenBindingsJson.write(bindings).toString())))
    }

    @Test fun oldAndMalformedPreferencesHaveSafeDefaults() {
        assertTrue(PenBindingsJson.read(null).values.all { it.action == "none" })
        val data = JSONObject("""{"32":{"action":"delete_all","mode":"SELECT"},"64":42,"4":{"action":"undo","mode":"HOLD"}}""")
        val loaded = PenBindingsJson.read(data)
        assertEquals("none", loaded[32]?.action)
        assertEquals("none", loaded[64]?.action)
        assertEquals(PenBinding("undo", PenBindingMode.SELECT), loaded[4])
    }
}
