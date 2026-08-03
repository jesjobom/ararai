package com.jesjobom.ararai.engine

import java.util.IdentityHashMap

internal data class RetainedResource<R : Any, S>(
    val resource: R,
    val state: S,
)

internal class RetainedResourceOwner<R : Any, S>(
    private val cancelResource: (R) -> Unit,
    private val closeResource: (R) -> Unit,
) {
    private val lock = Any()
    private val disposed = IdentityHashMap<R, Unit>()
    private var active: R? = null
    private var retained: RetainedResource<R, S>? = null

    fun retained(): RetainedResource<R, S>? = synchronized(lock) { retained }

    fun activate(resource: R): Boolean = synchronized(lock) {
        if (disposed.containsKey(resource)) return@synchronized false
        active = resource
        true
    }

    fun retain(
        resource: R,
        state: S,
    ): Boolean = synchronized(lock) {
        if (disposed.containsKey(resource)) return@synchronized false
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
            if (disposed.containsKey(resource)) {
                false
            } else {
                disposed[resource] = Unit
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
