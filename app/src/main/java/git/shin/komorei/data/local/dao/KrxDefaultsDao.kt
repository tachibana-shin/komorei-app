package git.shin.komorei.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import git.shin.komorei.data.local.entity.KrxDefaultsEntity

/** Read/write access to the persisted Krx defaults (see [KrxDefaultsEntity]). */
@Dao
interface KrxDefaultsDao {

    @Query("SELECT * FROM krx_defaults WHERE `key` = :key")
    suspend fun get(key: String): KrxDefaultsEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: KrxDefaultsEntity)

    @Query("DELETE FROM krx_defaults WHERE `key` = :key")
    suspend fun delete(key: String)

    /** Deletes every row whose key starts with [prefix] — e.g. `"vi.fake-source."`
     *  wipes all of that source's named-scope settings (source-id namespacing
     *  guarantees a source can never match another source's prefix). */
    @Query("DELETE FROM krx_defaults WHERE `key` LIKE :prefix || '%'")
    suspend fun deleteByPrefix(prefix: String)
}