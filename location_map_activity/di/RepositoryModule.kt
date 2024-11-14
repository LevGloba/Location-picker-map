package di
import com.capstime.timecapsule.data.*
import com.capstime.timecapsule.domain.*

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.android.components.ActivityComponent
import dagger.hilt.android.scopes.ActivityScoped

/*RepositoryViewModule.kt
* 08.12.2023 Globa Lev*/

@Module
@InstallIn(ActivityComponent::class)
abstract class RepositoryActivityModule{
    @Binds
    @ActivityScoped
    abstract fun provideGeocoderString(geocoderStringImpl: GeocoderStringImpl): GeocoderString
}

