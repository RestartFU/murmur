package com.restartfu.murmur.input

import org.junit.Assert.assertEquals
import org.junit.Test

class TextInserterTest {
    private fun fit(before: String, text: String, after: String = "") =
        TextInserter.fitToContext(before, text, after)

    @Test fun emptyFieldInsertsAsIs() = assertEquals("Hello there.", fit("", " Hello there. "))

    @Test fun addsSpaceAfterWord() = assertEquals(" Hello.", fit("Note:", "Hello."))

    @Test fun noExtraSpaceAfterWhitespace() = assertEquals("Hello.", fit("Note: ", "Hello."))

    @Test fun noSpaceBeforePunctuation() = assertEquals(", and more", fit("this", ", and more"))

    @Test fun addsSpaceBeforeFollowingWord() = assertEquals(" big ", fit("a", "big", "dog"))

    @Test fun noSpaceBeforeFollowingPunctuation() = assertEquals(" dog", fit("a", "dog", "."))

    @Test fun blankTranscriptStaysBlank() = assertEquals("", fit("abc", "   ", "def"))
}
