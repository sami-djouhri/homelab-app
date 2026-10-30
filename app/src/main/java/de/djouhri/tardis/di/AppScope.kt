package de.djouhri.tardis.di

import javax.inject.Qualifier

/** Markiert den anwendungsweiten CoroutineScope (SupervisorJob, lebt so lange wie der Prozess). */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class ApplicationScope
