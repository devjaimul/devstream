package com.devsstream.cafepos.di

import javax.inject.Qualifier

// A scope that lives as long as the process, for work no screen should be able to cancel
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class AppScope
