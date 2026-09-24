package chat.stoat.composables.chat

import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.pressKey
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalTestApi::class)
class EmoticonInputTransformationTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val state = TextFieldState()
    private val transformation = EmoticonInputTransformation()

    private fun setUp() {
        composeRule.setContent {
            BasicTextField(
                state = state,
                inputTransformation = transformation,
                modifier = Modifier.testTag("field")
            )
        }
    }

    private fun type(text: String) {
        composeRule.onNodeWithTag("field").performTextInput(text)
        composeRule.waitForIdle()
    }

    @Test
    fun convertsWhenSpaceIsTyped() {
        setUp()
        type("love you <3")
        assertEquals("love you <3", state.text.toString())
        type(" ")
        assertEquals("love you ❤️ ", state.text.toString())
        type("x")
        assertEquals("love you ❤️ x", state.text.toString())
    }

    @Test
    fun backspaceRestoresEmoticonAndKeepsIt() {
        setUp()
        type("hi :)")
        type(" ")
        assertEquals("hi 🙂 ", state.text.toString())

        composeRule.onNodeWithTag("field").performKeyInput { pressKey(Key.Backspace) }
        composeRule.waitForIdle()
        assertEquals("hi :)", state.text.toString())

        type(" ")
        assertEquals("hi :) ", state.text.toString())
    }

    @Test
    fun leavesCodeAlone() {
        setUp()
        type("`<3")
        type(" ")
        assertEquals("`<3 ", state.text.toString())
    }

    @Test
    fun convertsTrailingEmoticonBeforeSending() {
        setUp()
        type("bye <3")
        composeRule.runOnIdle {
            assertTrue(transformation.convertTrailing(state))
        }
        assertEquals("bye ❤️", state.text.toString())
    }

    @Test
    fun doesNotConvertTrailingEmoticonThatWasUndone() {
        setUp()
        type("bye <3")
        type(" ")
        composeRule.onNodeWithTag("field").performKeyInput { pressKey(Key.Backspace) }
        composeRule.waitForIdle()
        assertEquals("bye <3", state.text.toString())
        composeRule.runOnIdle {
            assertFalse(transformation.convertTrailing(state))
        }
    }
}
