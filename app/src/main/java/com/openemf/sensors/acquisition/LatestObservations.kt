package com.openemf.sensors.acquisition

import com.openemf.sensors.api.Source

/** A scan window keeps the latest reading per observed identity, not the strongest reading. */
internal class LatestObservations<T : Source> {
    private val sources = linkedMapOf<String, T>()

    fun add(sample: T) {
        val previous = sources[sample.id]
        val oldTime = previous?.observation?.elapsedRealtimeMs
        val newTime = sample.observation.elapsedRealtimeMs
        if (previous == null || oldTime == null || (newTime != null && newTime >= oldTime)) {
            sources[sample.id] = sample
        }
    }

    fun snapshot(): List<T> = sources.values.toList()
}
