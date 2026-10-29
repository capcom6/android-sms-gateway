package me.capcom.smsgateway.modules.gateway

import com.google.gson.GsonBuilder
import com.google.gson.JsonParseException
import me.capcom.smsgateway.extensions.configure
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.TimeZone

/**
 * T7 per-element pull hardening - batch deserialization policy.
 *
 * Proves the AC "poison-element: one bad element skips per policy while the
 * rest of the batch processes":
 * - an element that fails deserialization no longer aborts the whole batch
 *   at the GatewayApi throw site (historically `.body()` over the full list);
 * - every skipped element is reported (index + best-effort id + error):
 *   never silent (isolate-and-skip-with-logging, the conservative default
 *   per permanent gap OQ-B);
 * - a body-level fault (whole payload unparseable / not an array) still
 *   throws, keeping the old retry semantics for transport/shape errors.
 *
 * Poison kinds are empirically verified against the resolved runtime
 * (gson 2.10, both extensions/GsonBuilder.configure() date patterns): empty
 * date string, non-RFC3339 date string, and wrong-type field each throw a
 * per-element JsonSyntaxException. NOTE: the plan's fraction-less-timestamp
 * hypothesis ("2026-10-17T12:00:00+03:00" vs mandatory .SSS) does NOT throw
 * on JVM SimpleDateFormat (parse tolerates trailing unparsed text) - pinned
 * below so the fixture choice is explicit.
 *
 * Timezone note: local unit tests run with Build.VERSION.SDK_INT == 0
 * (mockable android.jar), so GsonBuilder.configure() takes the pre-N branch
 * and embeds the device-local offset literally. The default TZ is pinned to
 * GMT+03:00 (+03:00, the production shape per plan D-OQ-D) so fixtures are
 * deterministic regardless of the host TZ.
 */
class GatewayMessageBatchParseTest {

    private val gson = GsonBuilder().configure().create()

    private lateinit var previousTimeZone: TimeZone

    @Before
    fun pinTimeZoneToProductionShape() {
        previousTimeZone = TimeZone.getDefault()
        TimeZone.setDefault(TimeZone.getTimeZone("GMT+03:00"))
    }

    @After
    fun restoreTimeZone() {
        TimeZone.setDefault(previousTimeZone)
    }

    //region Fixtures
    private fun validMessageJson(id: String): String = """
        {"id":"$id","textMessage":{"text":"hello $id"},
         "phoneNumbers":["+1000"],"state":"Pending",
         "createdAt":"2026-10-17T12:00:00.123+03:00"}
    """.trimIndent().replace("\n", "")

    /** Poison kind 1 (timestamp family): date value is the empty string. */
    private fun poisonEmptyDateJson(id: String): String = """
        {"id":"$id","textMessage":{"text":"poison $id"},
         "phoneNumbers":["+1000"],"state":"Pending",
         "createdAt":""}
    """.trimIndent().replace("\n", "")

    /** Poison kind 2 (timestamp family): value not RFC3339 at all. */
    private fun poisonGarbageDateJson(id: String): String = """
        {"id":"$id","textMessage":{"text":"poison $id"},
         "phoneNumbers":["+1000"],"state":"Pending",
         "createdAt":"not-a-date"}
    """.trimIndent().replace("\n", "")

    /** Poison kind 3 (shape fault): scalar where the wire type is an array. */
    private fun poisonTypeFaultJson(id: String): String = """
        {"id":"$id","textMessage":{"text":"poison $id"},
         "phoneNumbers":"not-an-array","state":"Pending",
         "createdAt":"2026-10-17T12:00:00.123+03:00"}
    """.trimIndent().replace("\n", "")

    /** Refuted premise fixture: fraction-less timestamp parses on JVM. */
    private fun fractionLessTimestampJson(id: String): String = """
        {"id":"$id","textMessage":{"text":"prefix $id"},
         "phoneNumbers":["+1000"],"state":"Pending",
         "createdAt":"2026-10-17T12:00:00+03:00"}
    """.trimIndent().replace("\n", "")

    private fun array(vararg elements: String): String =
        elements.joinToString(separator = ",", prefix = "[", postfix = "]")
    //endregion

    //region happy-path
    @Test
    fun parsesEveryElementOfHealthyBatch() {
        val batch = parseMessageBatch(
            array(validMessageJson("m1"), validMessageJson("m2"), validMessageJson("m3")),
            gson
        )

        assertEquals(listOf("m1", "m2", "m3"), batch.messages.map { it.id })
        assertEquals("healthy batch must skip nothing", 0, batch.skipped.size)
        assertNotNull("createdAt with 3-digit fraction must parse", batch.messages[0].createdAt)
        val text = batch.messages[1].content as GatewayApi.MessageContent.Text
        assertEquals("hello m2", text.text)
    }
    //endregion

    //region invariant: poison does not discard siblings (headline AC)
    @Test
    fun poisonElementDoesNotDiscardSiblings() {
        val batch = parseMessageBatch(
            array(
                validMessageJson("m1"),
                poisonEmptyDateJson("m2"),
                validMessageJson("m3")
            ),
            gson
        )

        assertEquals(
            "the two healthy siblings must survive one poison element",
            listOf("m1", "m3"),
            batch.messages.map { it.id }
        )
        assertEquals("the poison must be reported exactly once", 1, batch.skipped.size)
        val skipped = batch.skipped.single()
        assertEquals(1, skipped.index)
        assertEquals("skip report must carry the server row id", "m2", skipped.id)
        assertNotNull("skip report must carry the cause", skipped.error)
    }

    @Test
    fun mixedPoisonKindsAreEachIsolated() {
        val batch = parseMessageBatch(
            array(
                poisonEmptyDateJson("m1"),
                poisonGarbageDateJson("m2"),
                poisonTypeFaultJson("m3"),
                validMessageJson("m4")
            ),
            gson
        )

        assertEquals(listOf("m4"), batch.messages.map { it.id })
        assertEquals(listOf(0, 1, 2), batch.skipped.map { it.index })
        assertEquals(listOf("m1", "m2", "m3"), batch.skipped.map { it.id })
        assertTrue(batch.skipped.all { it.error != null })
    }

    @Test
    fun poisonAtBothBatchEdgesIsIsolated() {
        val batch = parseMessageBatch(
            array(
                poisonGarbageDateJson("m0"),
                validMessageJson("m1"),
                validMessageJson("m2"),
                validMessageJson("m3"),
                poisonGarbageDateJson("m4")
            ),
            gson
        )

        assertEquals(listOf("m1", "m2", "m3"), batch.messages.map { it.id })
        assertEquals(listOf(0, 4), batch.skipped.map { it.index })
        assertEquals(listOf("m0", "m4"), batch.skipped.map { it.id })
    }

    @Test
    fun allPoisonBatchYieldsEmptyMessagesAndFullSkipReport() {
        val batch = parseMessageBatch(
            array(
                poisonEmptyDateJson("m1"),
                poisonGarbageDateJson("m2"),
                poisonTypeFaultJson("m3")
            ),
            gson
        )

        assertEquals(0, batch.messages.size)
        assertEquals(
            "never silent: every poison element must appear in the report",
            3,
            batch.skipped.size
        )
        assertEquals(listOf("m1", "m2", "m3"), batch.skipped.map { it.id })
    }
    //endregion

    //region boundary
    @Test
    fun emptyBatchParsesToEmptyListWithoutSkips() {
        val batch = parseMessageBatch("[]", gson)

        assertEquals(0, batch.messages.size)
        assertEquals(0, batch.skipped.size)
    }

    @Test
    fun nullElementIsSkippedAndReportedWithNullId() {
        val batch = parseMessageBatch(
            array(validMessageJson("m1"), "null", validMessageJson("m3")),
            gson
        )

        assertEquals(listOf("m1", "m3"), batch.messages.map { it.id })
        val skipped = batch.skipped.single()
        assertEquals(1, skipped.index)
        assertEquals(null, skipped.id)
        assertNotNull(skipped.error)
    }

    @Test
    fun fractionLessTimestampIsParsedNotSkippedOnJvm() {
        // Documents the refuted premise: SimpleDateFormat (JVM and Android
        // java.text) requires only the BEGINNING of the date to parse, so the
        // plan's fraction-less "+03:00" input does NOT throw against the .SSS
        // pattern here. T7's isolation is mechanism-agnostic: it catches
        // whatever element-level fault actually occurs (see the three poison
        // kinds above), so this input must stay in the batch, unskipped.
        val batch = parseMessageBatch(
            array(validMessageJson("m1"), fractionLessTimestampJson("m2")),
            gson
        )

        assertEquals(listOf("m1", "m2"), batch.messages.map { it.id })
        assertEquals(0, batch.skipped.size)
        assertNotNull(batch.messages[1].createdAt)
    }
    //endregion

    //region error-path: body-level faults keep old failure semantics
    @Test
    fun wholeBodyGarbageStillThrows() {
        try {
            parseMessageBatch("<html>502 Bad Gateway</html>", gson)
            throw AssertionError("body-level garbage must throw, not return a partial batch")
        } catch (expected: JsonParseException) {
            assertTrue(expected.message != null)
        }
    }

    @Test
    fun nonArrayBodyStillThrows() {
        try {
            parseMessageBatch("""{"id":"not-a-batch"}""", gson)
            throw AssertionError("non-array body must throw, not return a partial batch")
        } catch (expected: JsonParseException) {
            assertTrue(expected.message != null)
        }
    }
    //endregion
}
