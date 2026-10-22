package com.openemf.data.export

import android.content.Context
import android.net.Uri
import com.openemf.data.repository.MeasurementRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import javax.inject.Inject

class RawCsvExporter @Inject constructor(
    @ApplicationContext private val context: Context,
    private val repository: MeasurementRepository
) {
    suspend fun export(destination: Uri): Long = withContext(Dispatchers.IO) {
        val stream = context.contentResolver.openOutputStream(destination, "wt")
            ?: throw IOException("The selected document cannot be opened")
        stream.bufferedWriter(Charsets.UTF_8).use { repository.exportRawObservations(it) }
    }
}
