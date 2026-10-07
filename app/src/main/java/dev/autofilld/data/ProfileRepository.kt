package dev.autofilld.data

import dev.autofilld.fill.FieldType

class ProfileRepository(private val dao: ProfileDao) {

    suspend fun getProfile(): Profile = dao.getById(Profile.SINGLE_ROW_ID) ?: Profile()

    suspend fun saveProfile(profile: Profile) = dao.upsert(profile.copy(id = Profile.SINGLE_ROW_ID))

    suspend fun applyUpdates(updates: Map<FieldType, String>) {
        dao.upsert(ProfileMerge.merge(getProfile(), updates))
    }
}
