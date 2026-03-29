package com.biketracker.di

import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

// AppNotificationManager is @Singleton and injected directly; this module exists
// for any future notification-related bindings.
@Module
@InstallIn(SingletonComponent::class)
object NotificationModule
