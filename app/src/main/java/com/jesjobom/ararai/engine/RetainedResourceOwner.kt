package com.jesjobom.ararai.engine

import java.lang.ref.ReferenceQueue
import java.lang.ref.WeakReference

internal data class RetainedResource<R : Any, S>(
    val resource: R,
    val state: S,
)

internal class RetainedResourceOwner<R : Any, S>(
    private val cancelResource: (R) -> Unit,
    private val closeResource: (R) -> Unit,
) {
    private val lock = Any()
    private val disposed = WeakIdentitySet<R>()
    private var active: R? = null
    private var retained: RetainedResource<R, S>? = null

    fun retained(): RetainedResource<R, S>? = synchronized(lock) { retained }

    fun activate(resource: R): Boolean = synchronized(lock) {
        if (disposed.contains(resource)) return@synchronized false
        active = resource
        true
    }

    fun retain(
        resource: R,
        state: S,
    ): Boolean = synchronized(lock) {
        if (disposed.contains(resource)) return@synchronized false
        active = resource
        retained = RetainedResource(resource, state)
        true
    }

    fun invalidate(
        resource: R,
        cancelFirst: Boolean,
    ) {
        disposeClaimed(claimForDisposal(listOf(resource)), cancelFirst)
    }

    fun cancelActive() {
        val owned = synchronized(lock) { listOfNotNull(active, retained?.resource) }
        disposeClaimed(claimForDisposal(owned), cancelFirst = true)
    }

    fun closeAll() {
        val owned = synchronized(lock) { listOfNotNull(active, retained?.resource) }
        disposeClaimed(claimForDisposal(owned), cancelFirst = false)
    }

    private fun claimForDisposal(resources: List<R>): List<R> = synchronized(lock) {
        resources.filter { resource ->
            if (disposed.contains(resource)) {
                false
            } else {
                disposed.add(resource)
                if (active === resource) active = null
                if (retained?.resource === resource) retained = null
                true
            }
        }
    }

    private fun disposeClaimed(
        resources: List<R>,
        cancelFirst: Boolean,
    ) {
        resources.forEach { resource ->
            try {
                if (cancelFirst) cancelResource(resource)
            } finally {
                closeResource(resource)
            }
        }
    }
}

/** Identity-based membership that never keeps discarded native wrappers alive. */
private class WeakIdentitySet<T : Any> {
    private val queue = ReferenceQueue<T>()
    private val references = HashSet<IdentityWeakReference<T>>()

    fun contains(value: T): Boolean {
        removeCollectedReferences()
        return references.contains(IdentityWeakReference(value))
    }

    fun add(value: T) {
        removeCollectedReferences()
        references.add(IdentityWeakReference(value, queue))
    }

    private fun removeCollectedReferences() {
        while (true) {
            @Suppress("UNCHECKED_CAST")
            val collected = queue.poll() as IdentityWeakReference<T>? ?: return
            references.remove(collected)
        }
    }
}

private class IdentityWeakReference<T : Any>(
    referent: T,
    queue: ReferenceQueue<T>? = null,
) : WeakReference<T>(referent, queue) {
    private val identityHash = System.identityHashCode(referent)

    override fun hashCode(): Int = identityHash

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is IdentityWeakReference<*>) return false
        val referent = get() ?: return false
        return referent === other.get()
    }
}
