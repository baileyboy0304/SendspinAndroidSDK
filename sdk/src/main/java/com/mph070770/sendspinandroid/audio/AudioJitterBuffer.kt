package com.mph070770.sendspinandroid.audio

import java.util.PriorityQueue
import java.util.concurrent.atomic.AtomicLong

internal class AudioJitterBuffer {

    data class Snapshot(
        val queuedChunks: Int,
        val bufferAheadMs: Long,
        val lateDrops: Long,
        val headServerUs: Long?
    )

    data class Chunk(
        val serverTimestampUs: Long,
        val pcmData: ByteArray
    )

    private val q = PriorityQueue<Chunk>(compareBy { it.serverTimestampUs })
    private val lateDropsCounter = AtomicLong(0L)

    fun clear() {
        synchronized(q) { q.clear() }
    }

    fun isEmpty(): Boolean = synchronized(q) { q.isEmpty() }

    fun offer(serverTsUs: Long, pcm: ByteArray, offsetUs: Long, nowLocalUs: Long) {
        synchronized(q) {
            q.add(Chunk(serverTsUs, pcm))
        }
    }

    fun snapshot(offsetUs: Long): Snapshot {
        val nowLocalUs = System.nanoTime() / 1000L
        val nowServerUs = nowLocalUs + offsetUs

        return synchronized(q) {
            val head = q.peek()?.serverTimestampUs
            val aheadMs = if (head != null) ((head - nowServerUs) / 1000L) else 0L
            Snapshot(
                queuedChunks = q.size,
                bufferAheadMs = aheadMs,
                lateDrops = lateDropsCounter.get(),
                headServerUs = head
            )
        }
    }

    /**
     * Drop items that are very late compared to nowServerUs, leaving the queue head within keepWithinUs (lateness).
     * Returns number of chunks dropped.
     */
    fun dropWhileLate(nowLocalUs: Long, offsetUs: Long, keepWithinUs: Long): Int {
        val nowServerUs = nowLocalUs + offsetUs
        var dropped = 0
        synchronized(q) {
            while (true) {
                val head = q.peek() ?: break
                val latenessUs = nowServerUs - head.serverTimestampUs
                if (latenessUs > keepWithinUs) {
                    q.poll()
                    lateDropsCounter.incrementAndGet()
                    dropped++
                    continue
                }
                break
            }
        }
        return dropped
    }

    /**
     * Returns the next playable chunk (based on local time mapping),
     * dropping anything that is too late.
     */
    fun pollPlayable(nowLocalUs: Long, offsetUs: Long, lateDropUs: Long): Chunk? {
        val nowServerUs = nowLocalUs + offsetUs

        synchronized(q) {
            while (true) {
                val head = q.peek() ?: return null

                val latenessUs = nowServerUs - head.serverTimestampUs
                if (latenessUs > lateDropUs) {
                    q.poll()
                    lateDropsCounter.incrementAndGet()
                    continue
                }

                // Not "too late" - caller can decide to wait or play.
                return q.poll()
            }
        }
    }
}
