package io.github.smyrna_pergamum.exercisetracker.data

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import androidx.room.Update
import androidx.room.Upsert
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json

private val json = Json { ignoreUnknownKeys = true }

class Converters {
    // Explicit types: KSP cannot see the serializers the compiler plugin generates.
    private val strings: KSerializer<List<String>> = ListSerializer(String.serializer())
    private val items: KSerializer<List<WorkoutItem>> = ListSerializer(WorkoutItem.serializer())

    @TypeConverter fun stringsTo(v: List<String>): String = json.encodeToString(strings, v)
    @TypeConverter fun stringsFrom(v: String): List<String> = json.decodeFromString(strings, v)
    @TypeConverter fun itemsTo(v: List<WorkoutItem>): String = json.encodeToString(items, v)
    @TypeConverter fun itemsFrom(v: String): List<WorkoutItem> = json.decodeFromString(items, v)
    @TypeConverter fun restTo(v: RestState?): String? = v?.let { json.encodeToString(RestState.serializer(), it) }
    @TypeConverter fun restFrom(v: String?): RestState? = v?.let { json.decodeFromString(RestState.serializer(), it) }
}

@Database(
    entities = [Routine::class, Workout::class, SetLog::class, Activity::class, TrackPoint::class, StepDay::class, StepState::class,
        WaterDay::class, Mindful::class, Reminder::class, WeightEntry::class],
    version = 4,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun workouts(): WorkoutDao
    abstract fun activities(): ActivityDao
    abstract fun steps(): StepDao
    abstract fun health(): HealthDao

    companion object {
        const val NAME = "tracker.db"
        /** 1 → 2: step counter tables and steps per activity. Existing records are kept. */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE activity ADD COLUMN steps INTEGER")
                db.execSQL("CREATE TABLE IF NOT EXISTS step_day (date TEXT NOT NULL, steps INTEGER NOT NULL, PRIMARY KEY(date))")
                db.execSQL("CREATE TABLE IF NOT EXISTS step_state (id INTEGER NOT NULL, counter INTEGER NOT NULL, time INTEGER NOT NULL, PRIMARY KEY(id))")
            }
        }

        /** 2 → 3: water, breathing sessions and reminders. */
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS water_day (date TEXT NOT NULL, glasses INTEGER NOT NULL, PRIMARY KEY(date))")
                db.execSQL("CREATE TABLE IF NOT EXISTS mindful (id TEXT NOT NULL, startedAt INTEGER NOT NULL, durationSec INTEGER NOT NULL, PRIMARY KEY(id))")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_mindful_startedAt ON mindful (startedAt)")
                db.execSQL("CREATE TABLE IF NOT EXISTS reminder (type TEXT NOT NULL, hour INTEGER NOT NULL, minute INTEGER NOT NULL, days INTEGER NOT NULL, intervalH INTEGER NOT NULL, enabled INTEGER NOT NULL, PRIMARY KEY(type))")
            }
        }

        /** 3 → 4: body weight log. */
        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS weight (date TEXT NOT NULL, kgEnc TEXT NOT NULL, PRIMARY KEY(date))")
            }
        }

        fun create(context: Context) = Room.databaseBuilder(context, AppDatabase::class.java, NAME)
            .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4).build()
    }
}

@Dao
interface WorkoutDao {
    @Query("SELECT * FROM routine ORDER BY createdAt DESC") fun routines(): Flow<List<Routine>>
    @Query("SELECT * FROM routine") suspend fun allRoutines(): List<Routine>
    @Upsert suspend fun upsertRoutine(r: Routine)
    @Query("DELETE FROM routine WHERE id = :id") suspend fun deleteRoutine(id: String)

    @Query("SELECT * FROM workout WHERE status = 'ACTIVE' LIMIT 1") fun activeFlow(): Flow<Workout?>
    @Query("SELECT * FROM workout WHERE status = 'ACTIVE' LIMIT 1") suspend fun active(): Workout?
    @Query("SELECT * FROM workout WHERE id = :id") fun workout(id: String): Flow<Workout?>
    @Query("SELECT * FROM workout WHERE id = :id") suspend fun get(id: String): Workout?
    @Query("SELECT * FROM workout WHERE status = 'FINISHED' ORDER BY startedAt DESC") fun finished(): Flow<List<Workout>>
    @Query("SELECT * FROM workout") suspend fun all(): List<Workout>
    @Insert suspend fun insert(w: Workout)
    @Update suspend fun update(w: Workout)
    @Query("DELETE FROM workout WHERE id = :id") suspend fun delete(id: String)

    @Query("SELECT * FROM set_log WHERE workoutId = :id") fun logs(id: String): Flow<List<SetLog>>
    @Query("SELECT * FROM set_log") suspend fun allLogs(): List<SetLog>
    @Query("SELECT workoutId, COUNT(*) AS n FROM set_log GROUP BY workoutId") fun setCounts(): Flow<List<SetCount>>

    /** Values from the most recent finished workout with this exercise, used to pre-fill fields. */
    @Query("""SELECT s.* FROM set_log s JOIN workout w ON w.id = s.workoutId
        WHERE s.exerciseId = :exerciseId AND w.status = 'FINISHED' ORDER BY s.completedAt DESC LIMIT 20""")
    suspend fun recentFor(exerciseId: String): List<SetLog>

    /** A second tap on the same set hits the unique index and stores nothing. */
    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun insertLog(l: SetLog): Long
    @Query("DELETE FROM set_log WHERE workoutId = :id AND itemKey = :key AND setOrder = :order") suspend fun deleteLog(id: String, key: String, order: Int)
}

data class SetCount(val workoutId: String, val n: Int)

@Dao
interface ActivityDao {
    @Query("SELECT * FROM activity WHERE status = 'FINISHED' ORDER BY startedAt DESC") fun finished(): Flow<List<Activity>>
    @Query("SELECT * FROM activity WHERE status = 'ACTIVE'") suspend fun active(): List<Activity>
    @Query("SELECT * FROM activity WHERE id = :id") fun activity(id: String): Flow<Activity?>
    @Query("SELECT * FROM activity WHERE id = :id") suspend fun get(id: String): Activity?
    @Query("SELECT * FROM activity") suspend fun all(): List<Activity>
    @Insert suspend fun insert(a: Activity)
    @Update suspend fun update(a: Activity)
    @Query("DELETE FROM activity WHERE id = :id") suspend fun delete(id: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertPoint(p: TrackPoint)
    @Query("SELECT * FROM track_point WHERE activityId = :id ORDER BY seq") suspend fun points(id: String): List<TrackPoint>
    @Query("DELETE FROM track_point WHERE activityId = :id") suspend fun deletePoints(id: String)
}

@Dao
interface StepDao {
    @Query("SELECT * FROM step_day WHERE date >= :from ORDER BY date") fun since(from: String): Flow<List<StepDay>>
    @Query("SELECT * FROM step_day") suspend fun all(): List<StepDay>
    @Query("SELECT * FROM step_day WHERE date = :date") suspend fun day(date: String): StepDay?
    @Upsert suspend fun upsertDay(d: StepDay)
    @Query("SELECT * FROM step_state WHERE id = 0") suspend fun state(): StepState?
    @Upsert suspend fun setState(s: StepState)
}

@Dao
interface HealthDao {
    @Query("SELECT * FROM water_day WHERE date = :date") fun water(date: String): Flow<WaterDay?>
    @Query("SELECT * FROM water_day WHERE date = :date") suspend fun waterOnce(date: String): WaterDay?
    @Query("SELECT * FROM water_day") fun allWater(): Flow<List<WaterDay>>
    @Query("SELECT * FROM water_day") suspend fun allWaterOnce(): List<WaterDay>
    @Upsert suspend fun setWater(d: WaterDay)

    @Insert suspend fun addMindful(m: Mindful)
    @Query("SELECT * FROM mindful ORDER BY startedAt DESC") fun mindful(): Flow<List<Mindful>>
    @Query("SELECT * FROM mindful") suspend fun allMindful(): List<Mindful>
    @Query("DELETE FROM mindful WHERE id = :id") suspend fun deleteMindful(id: String)

    @Query("SELECT * FROM reminder") fun reminders(): Flow<List<Reminder>>
    @Query("SELECT * FROM reminder") suspend fun remindersOnce(): List<Reminder>
    @Upsert suspend fun setReminder(r: Reminder)

    @Query("SELECT * FROM weight ORDER BY date") fun weights(): Flow<List<WeightEntry>>
    @Query("SELECT * FROM weight") suspend fun allWeights(): List<WeightEntry>
    @Upsert suspend fun setWeight(w: WeightEntry)
    @Query("DELETE FROM weight WHERE date = :date") suspend fun deleteWeight(date: String)
}
