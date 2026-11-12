package dev.hram.androsaur

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class StandStoreTest {
    @Test fun survivesRecreationWithoutStoringPlaintextSecrets() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val prefs = context.getSharedPreferences("stand", 0)
        val previous = prefs.getString("binding", null)
        try {
            val secret = StandStore.randomKey()
            val store = StandStore(context)
            store.write(JSONObject().put("id", "stand-112233445566").put("fingerprint", "b".repeat(64)).put("key", secret))
            assertFalse(prefs.getString("binding", "")!!.contains(secret))
            assertEquals(secret, StandStore(context).read()!!.getString("key"))
            store.forget()
            assertNull(StandStore(context).read())
        } finally {
            if (previous == null) prefs.edit().remove("binding").commit() else prefs.edit().putString("binding", previous).commit()
        }
    }
}
