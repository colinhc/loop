package com.example

import android.content.Context
import androidx.room.*
import kotlinx.coroutines.flow.Flow
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory

@Entity(tableName = "workouts")
data class Workout(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val name: String,
    val totalSets: Int,
    val setPreferencesJson: String
)

class WorkoutTypeConverters {
    private val moshi = Moshi.Builder()
        .add(KotlinJsonAdapterFactory())
        .build()
    private val listType = Types.newParameterizedType(List::class.java, SetPreferences::class.java)
    private val adapter = moshi.adapter<List<SetPreferences>>(listType)

    @TypeConverter
    fun fromSetPreferencesList(list: List<SetPreferences>?): String {
        return adapter.toJson(list ?: emptyList())
    }

    @TypeConverter
    fun toSetPreferencesList(json: String?): List<SetPreferences> {
        if (json.isNullOrEmpty()) return emptyList()
        return try {
            adapter.fromJson(json) ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }
}

@Dao
interface WorkoutDao {
    @Query("SELECT * FROM workouts ORDER BY id DESC")
    fun getAllWorkouts(): Flow<List<Workout>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertWorkout(workout: Workout): Long

    @Delete
    suspend fun deleteWorkout(workout: Workout)

    @Query("DELETE FROM workouts WHERE id = :id")
    suspend fun deleteById(id: Int)
}

@Database(entities = [Workout::class], version = 1, exportSchema = false)
@TypeConverters(WorkoutTypeConverters::class)
abstract class WorkoutDatabase : RoomDatabase() {
    abstract fun workoutDao(): WorkoutDao

    companion object {
        @Volatile
        private var INSTANCE: WorkoutDatabase? = null

        fun getDatabase(context: Context): WorkoutDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    WorkoutDatabase::class.java,
                    "workout_database"
                )
                .fallbackToDestructiveMigration()
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}

class WorkoutRepository(private val workoutDao: WorkoutDao) {
    val allWorkouts: Flow<List<Workout>> = workoutDao.getAllWorkouts()

    suspend fun insert(workout: Workout): Long {
        return workoutDao.insertWorkout(workout)
    }

    suspend fun delete(workout: Workout) {
        workoutDao.deleteWorkout(workout)
    }

    suspend fun deleteById(id: Int) {
        workoutDao.deleteById(id)
    }
}
