package com.nodaysidle.voiceanywhere.history

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TranscriptHistoryStoreTest {
    @Test
    fun roundTripsHistoryItems() {
        val items = listOf(
            TranscriptHistoryItem(
                id = "one",
                createdAtMillis = 1000L,
                text = "First transcript",
                targetPackage = "com.example.one",
                resultLabel = "✓ SET"
            ),
            TranscriptHistoryItem(
                id = "two",
                createdAtMillis = 2000L,
                text = "Second transcript",
                targetPackage = "com.example.two",
                resultLabel = "↗ CPY"
            )
        )

        val decoded = TranscriptHistoryStore.decode(TranscriptHistoryStore.encode(items))

        assertEquals(items, decoded)
    }

    @Test
    fun encodeCapsHistoryAtFiftyItems() {
        val items = (0 until 60).map { index ->
            TranscriptHistoryItem(
                id = "item-$index",
                createdAtMillis = index.toLong(),
                text = "Transcript $index",
                targetPackage = "com.example",
                resultLabel = "✓ SET"
            )
        }

        val decoded = TranscriptHistoryStore.decode(TranscriptHistoryStore.encode(items))

        assertEquals(50, decoded.size)
        assertEquals("item-0", decoded.first().id)
        assertEquals("item-49", decoded.last().id)
    }

    @Test
    fun decodeReturnsEmptyListForInvalidJson() {
        assertTrue(TranscriptHistoryStore.decode("not-json").isEmpty())
    }
}
