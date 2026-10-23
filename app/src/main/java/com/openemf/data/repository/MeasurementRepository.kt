package com.openemf.data.repository

import com.openemf.data.database.*
import com.openemf.sensors.api.*
import androidx.room.withTransaction
import com.openemf.data.export.RawObservationCsv
import com.openemf.recording.RecordingStateStore
import java.io.Writer
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.ensureActive
import java.time.Instant
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.*

/**
 * Atomic capture storage, durable observation histories, and local raw export.
 */
@Singleton
class MeasurementRepository @Inject constructor(
    private val database: OpenEMFDatabase,
    private val measurementDao: MeasurementDao,
    private val placeDao: PlaceDao,
    private val observationDao: ObservationDao,
    private val recordingState: RecordingStateStore
) {
    // ==================== Measurements ====================

    /**
     * Save a measurement with optional location.
     */
    suspend fun saveMeasurement(
        measurement: Measurement,
        latitude: Double? = null,
        longitude: Double? = null,
        accuracy: Float? = null
    ) {
        // Find matching place if location provided
        val placeId = if (latitude != null && longitude != null) {
            findNearestPlace(latitude, longitude)?.id
        } else null

        val entity = measurement.toEntity(latitude, longitude, accuracy, placeId)
        database.withTransaction {
            // SharedFlow replay or a repeated save must not duplicate or replace a capture.
            if (measurementDao.insertCaptureSummary(entity) == -1L) return@withTransaction
            observationDao.insertCapture(CaptureEntity(measurement.id, measurement.captureMode,
                measurement.recordingSessionId, measurement.capturedElapsedRealtimeMs,
                measurement.bootCount, measurement.context.isForeground, measurement.context.motionState))
            observationDao.insertAcquisitions(listOf(Modality.WIFI, Modality.BLUETOOTH, Modality.CELLULAR).map { modality ->
                val report = measurement.acquisition[modality] ?: AcquisitionReport(
                    AcquisitionStatus.UNAVAILABLE, "Acquisition status not supplied")
                RadioAcquisitionEntity(measurement.id, modality, report.status, report.detail)
            })
            observationDao.insertObservations(measurement.observationEntities())
            placeId?.let { updatePlaceStats(it) }
        }
    }

    /**
     * Get all measurements as Flow.
     */
    fun getAllMeasurements(): Flow<List<MeasurementEntity>> =
        measurementDao.getAllFlow()

    /**
     * Get recent measurements.
     */
    fun getRecentMeasurements(limit: Int = 50): Flow<List<MeasurementEntity>> =
        measurementDao.getRecentFlow(limit)

    /**
     * Get measurements with location for map display.
     */
    fun getMeasurementsWithLocation(): Flow<List<MeasurementEntity>> =
        measurementDao.getWithLocationFlow()

    /**
     * Get measurements for a specific place.
     */
    fun getMeasurementsForPlace(placeId: String): Flow<List<MeasurementEntity>> =
        measurementDao.getByPlaceFlow(placeId)

    /**
     * Get summary statistics.
     */
    suspend fun getStatistics(): MeasurementStats {
        return database.withTransaction {
            val stats = measurementDao.statistics()
            MeasurementStats(stats.totalCount, stats.averageScore?.toInt(), stats.minScore,
                stats.maxScore, stats.mostRecentTimestamp, measurementDao.mostFrequentDominantType(), stats.scoredCount)
        }
    }

    /**
     * Delete old measurements (data retention).
     */
    suspend fun deleteOlderThan(days: Int) {
        require(days >= 0)
        val cutoff = Instant.now().minusSeconds(days * 24 * 60 * 60L)
        database.withTransaction {
            measurementDao.deleteOlderThan(cutoff)
            placeDao.getAll().forEach { updatePlaceStats(it.id) }
        }
    }

    /**
     * Delete all measurements (clear history).
     */
    suspend fun deleteAllMeasurements() {
        database.withTransaction {
            check(!recordingState.state.value.active) { "Stop recording before clearing history" }
            measurementDao.deleteAll() // Foreign keys also remove observations, captures, and statuses.
            database.recordingSessionDao().deleteAll()
            placeDao.getAll().forEach { place ->
                placeDao.updateStats(place.id, 0, 0, null)
            }
        }
    }

    suspend fun deleteMeasurement(id: String) = database.withTransaction {
        val placeId = measurementDao.getById(id)?.placeId
        measurementDao.deleteById(id)
        placeId?.let { updatePlaceStats(it) }
    }

    fun getSources(): Flow<List<SourceHistorySummary>> = observationDao.sources()

    fun getObservations(measurementId: String) = observationDao.forMeasurement(measurementId)
    fun observeSourceChart(type: SourceType, sourceId: String, since: Instant) =
        observationDao.chartWindow(type, sourceId, since, 2001)
    suspend fun getAcquisitions(measurementId: String) = observationDao.getAcquisitions(measurementId)

    suspend fun getSourceHistory(type: SourceType, sourceId: String, limit: Int = 100, beforeRowId: Long? = null): List<ObservationRecord> {
        require(limit in 1..1000)
        return observationDao.sourceHistory(type, sourceId, limit, beforeRowId)
    }

    /** A single read transaction provides a consistent snapshot across bounded export pages. */
    suspend fun exportRawObservations(writer: Writer): Long = database.withTransaction {
        RawObservationCsv.writeHeader(writer)
        val through = observationDao.maxRowId()
        var after = 0L
        var count = 0L
        while (true) {
            kotlinx.coroutines.currentCoroutineContext().ensureActive()
            val rows = observationDao.exportPage(after, through, 500)
            if (rows.isEmpty()) break
            rows.forEach { RawObservationCsv.writeRecord(writer, it) }
            count += rows.size
            after = rows.last().observation.rowId
        }
        writer.flush()
        count
    }

    // ==================== Places ====================

    /**
     * Get all places.
     */
    fun getAllPlaces(): Flow<List<PlaceEntity>> = placeDao.getAllFlow()

    /**
     * Add a new place.
     */
    suspend fun addPlace(
        name: String,
        icon: PlaceIcon,
        latitude: Double,
        longitude: Double,
        radiusMeters: Float = 100f
    ): PlaceEntity {
        val place = PlaceEntity(
            id = UUID.randomUUID().toString(),
            name = name,
            icon = icon.name,
            latitude = latitude,
            longitude = longitude,
            radiusMeters = radiusMeters,
            createdAt = Instant.now()
        )
        placeDao.insert(place)
        return place
    }

    /**
     * Update place details.
     */
    suspend fun updatePlace(place: PlaceEntity) {
        placeDao.update(place)
    }

    /**
     * Delete a place.
     */
    suspend fun deletePlace(place: PlaceEntity) {
        placeDao.delete(place)
    }

    /**
     * Find the nearest place within radius.
     */
    suspend fun findNearestPlace(latitude: Double, longitude: Double): PlaceEntity? {
        val places = placeDao.getAll()
        return places
            .map { place ->
                val distance = haversineDistance(
                    latitude, longitude,
                    place.latitude, place.longitude
                )
                place to distance
            }
            .filter { (place, distance) -> distance <= place.radiusMeters }
            .minByOrNull { (_, distance) -> distance }
            ?.first
    }

    /**
     * Update statistics for a place.
     */
    private suspend fun updatePlaceStats(placeId: String) {
        val count = measurementDao.getCountForPlace(placeId)
        val avgScore = measurementDao.getAverageScoreForPlace(placeId)?.toInt() ?: 0
        placeDao.updateStats(placeId, count, avgScore, Instant.now())
    }

    /**
     * Get places with their statistics for display.
     */
    fun getPlacesWithStats(): Flow<List<PlaceWithStats>> =
        placeDao.getAllFlow().map { places ->
            places.map { place ->
                PlaceWithStats(
                    place = place,
                    icon = PlaceIcon.entries.find { it.name == place.icon } ?: PlaceIcon.OTHER
                )
            }
        }

    // ==================== Helpers ====================

    /**
     * Haversine formula for distance between two points in meters.
     */
    private fun haversineDistance(
        lat1: Double, lon1: Double,
        lat2: Double, lon2: Double
    ): Float {
        val r = 6371000.0 // Earth radius in meters

        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)

        val a = sin(dLat / 2).pow(2) +
                cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) *
                sin(dLon / 2).pow(2)

        val c = 2 * atan2(sqrt(a), sqrt(1 - a))

        return (r * c).toFloat()
    }

    private fun Measurement.toEntity(
        latitude: Double?,
        longitude: Double?,
        accuracy: Float?,
        placeId: String?
    ): MeasurementEntity {
        return MeasurementEntity(
            id = id,
            timestamp = timestamp,
            eScore = eScore.score,
            exposureLevel = eScore.level.name,
            cumulativeDbm = eScore.cumulativeDbm,
            wifiCount = wifiSources.size,
            bluetoothCount = bluetoothSources.size,
            cellularCount = cellularSources.size,
            wifiPercent = eScore.breakdown[SourceType.WIFI] ?: 0,
            bluetoothPercent = eScore.breakdown[SourceType.BLUETOOTH] ?: 0,
            cellularPercent = eScore.breakdown[SourceType.CELLULAR] ?: 0,
            latitude = latitude,
            longitude = longitude,
            accuracy = accuracy,
            placeId = placeId,
            dominantSourceName = eScore.dominantSource?.name,
            dominantSourceType = eScore.dominantSource?.type?.name,
            dominantSourcePercent = eScore.dominantSource?.contributionPercent,
            indexAvailable = eScore.sourceCount > 0
        )
    }
}

/**
 * Summary statistics for measurements.
 */
data class MeasurementStats(
    val totalCount: Int,
    val averageScore: Int?,
    val minScore: Int?,
    val maxScore: Int?,
    val mostRecentTimestamp: Instant?,
    val dominantSourceType: String?,
    val scoredCount: Int
)

/**
 * Place with resolved icon.
 */
data class PlaceWithStats(
    val place: PlaceEntity,
    val icon: PlaceIcon
)
