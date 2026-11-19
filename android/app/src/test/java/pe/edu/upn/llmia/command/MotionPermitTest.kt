package pe.edu.upn.llmia.command

import org.junit.Assert.*
import org.junit.Test

class MotionPermitTest {
    @Test fun idleRejectsEveryLease() {
        val p = MotionPermit()
        assertFalse(p.isMoving())
        assertFalse(p.allows(0))
        assertFalse(p.allows(1))
    }
    @Test fun stopRevokesQueuedMoves() {
        val p = MotionPermit()
        val queued = p.begin()
        assertTrue(p.allows(queued))
        p.revoke()
        assertFalse(p.allows(queued))
        assertFalse(p.isMoving())
    }
    @Test fun newMotionCannotReuseCancelledLease() {
        val p = MotionPermit()
        val old = p.begin()
        p.revoke()
        val current = p.begin()
        assertFalse(p.allows(old))
        assertTrue(p.allows(current))
        assertNotEquals(old, current)
    }
    @Test fun replacementRevokesPreviousLease() {
        val p = MotionPermit()
        val old = p.begin()
        val current = p.begin()
        assertFalse(p.allows(old))
        assertTrue(p.allows(current))
    }
}
