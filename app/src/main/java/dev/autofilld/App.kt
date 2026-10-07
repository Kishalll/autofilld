package dev.autofilld

import android.app.Application
import androidx.room.Room
import dev.autofilld.data.AppDatabase
import dev.autofilld.data.ProfileRepository
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class App : Application() {

    lateinit var profileRepository: ProfileRepository
        private set

    /** Single background thread for autofill fill/save work. */
    val ioExecutor: ExecutorService = Executors.newSingleThreadExecutor()

    override fun onCreate() {
        super.onCreate()
        val database = Room.databaseBuilder(this, AppDatabase::class.java, "autofilld.db").build()
        profileRepository = ProfileRepository(database.profileDao())
    }
}
