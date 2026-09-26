package de.djouhri.cockpit.di

import javax.inject.Qualifier

/** Markiert den anwendungsweiten CoroutineScope (SupervisorJob, lebt so lange wie der Prozess). */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class ApplicationScope
