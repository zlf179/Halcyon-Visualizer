package com.mocharealm.accompanist.lyrics.core.utils

import org.junit.Assert.assertEquals
import org.junit.Test

class SimpleXmlParserTest {
    @Test
    fun inlineLeadingSpacesInsideTextNodesArePreserved() {
        val root = SimpleXmlParser().parse(
            """
            <tt>
              <body>
                <p><span>Runnin'</span><span> through</span><span> this</span></p>
              </body>
            </tt>
            """.trimIndent()
        )

        val spans = root.children
            .first { it.name == "body" }
            .children
            .first { it.name == "p" }
            .children
            .filter { it.name == "span" }

        assertEquals(listOf("Runnin'", " through", " this"), spans.map { it.text })
    }
}
