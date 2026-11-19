package pe.edu.upn.llmia.command

import java.util.concurrent.atomic.AtomicLong

/** Revoca órdenes ya en cola. La red debe serializar MOVE y STOP por separado. */
class MotionPermit {
    private val epoch = AtomicLong(0)
    private val active = AtomicLong(0)
    @Synchronized fun begin(): Long = epoch.incrementAndGet().also { active.set(it) }
    @Synchronized fun revoke() { active.set(0) }
    fun allows(lease: Long): Boolean = lease != 0L && active.get() == lease
    fun isMoving(): Boolean = active.get() != 0L
}
