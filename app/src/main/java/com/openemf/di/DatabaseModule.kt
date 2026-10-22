package com.openemf.di

import android.content.Context
import androidx.room.Room
import com.openemf.data.database.*
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(
        @ApplicationContext context: Context
    ): OpenEMFDatabase {
        return Room.databaseBuilder(
            context,
            OpenEMFDatabase::class.java,
            "openemf_signal_history"
        )
            .build()
    }

    @Provides
    fun provideMeasurementDao(database: OpenEMFDatabase): MeasurementDao {
        return database.measurementDao()
    }

    @Provides
    fun providePlaceDao(database: OpenEMFDatabase): PlaceDao {
        return database.placeDao()
    }

    @Provides
    fun provideObservationDao(database: OpenEMFDatabase): ObservationDao = database.observationDao()
}
