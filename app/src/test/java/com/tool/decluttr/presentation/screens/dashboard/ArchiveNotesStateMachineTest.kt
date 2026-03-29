package com.tool.decluttr.presentation.screens.dashboard

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ArchiveNotesStateMachineTest {

    @Test
    fun `starts in edit mode when no existing note`() {
        val machine = ArchiveNotesStateMachine("")

        assertEquals(ArchiveNotesMode.EDIT, machine.state().mode)
    }

    @Test
    fun `save flow updates saved text and returns to view mode`() {
        val machine = ArchiveNotesStateMachine("initial")

        machine.startEdit()
        machine.updateDraft("updated note")
        val saved = machine.save()

        assertEquals("updated note", saved)
        assertEquals(ArchiveNotesMode.VIEW, machine.state().mode)
        assertEquals("updated note", machine.state().savedText)
    }

    @Test
    fun `cancel flow discards draft`() {
        val machine = ArchiveNotesStateMachine("keep me")

        machine.startEdit()
        machine.updateDraft("temporary edit")
        machine.cancel()

        assertEquals("keep me", machine.state().savedText)
        assertEquals(ArchiveNotesMode.VIEW, machine.state().mode)
    }

    @Test
    fun `escape in edit mode cancels edit`() {
        val machine = ArchiveNotesStateMachine("a")
        machine.startEdit()
        machine.updateDraft("b")

        val handled = machine.onEscape()

        assertTrue(handled)
        assertEquals("a", machine.state().savedText)
        assertEquals(ArchiveNotesMode.VIEW, machine.state().mode)
    }

    @Test
    fun `ctrl enter in edit mode saves`() {
        val machine = ArchiveNotesStateMachine("old")
        machine.startEdit()
        machine.updateDraft("new")

        val result = machine.onCtrlEnter()

        assertEquals("new", result)
        assertEquals("new", machine.state().savedText)
        assertEquals(ArchiveNotesMode.VIEW, machine.state().mode)
    }

    @Test
    fun `ctrl enter in view mode is ignored`() {
        val machine = ArchiveNotesStateMachine("old")

        val result = machine.onCtrlEnter()

        assertFalse(result != null)
    }
}
