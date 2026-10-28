package dev.neovidecaret

/** Absolute deadlines avoid accumulating the callback duration into every frame. */
internal class FrameClock {
    private var periodNs = 1_000_000_000L / 60
    private var deadline = 0L

    fun start(now: Long, fps: Int) {
        periodNs = 1_000_000_000L / fps.coerceIn(60, 120)
        deadline = now + periodNs
    }

    fun advance(now: Long) {
        deadline += periodNs
        if (now >= deadline) deadline += ((now - deadline) / periodNs + 1) * periodNs
    }

    fun delayMs(now: Long): Int = ((deadline - now) / 1_000_000L).coerceIn(1L, 1000L).toInt()
}
