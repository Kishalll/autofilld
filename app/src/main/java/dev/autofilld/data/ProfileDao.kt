package dev.autofilld.data

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert

@Dao
interface ProfileDao {

    @Query("SELECT * FROM profile WHERE id = :id")
    suspend fun getById(id: Int): Profile?

    @Upsert
    suspend fun upsert(profile: Profile)
}
