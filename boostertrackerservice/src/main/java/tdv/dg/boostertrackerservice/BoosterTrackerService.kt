package tdv.dg.boostertrackerservice

import android.app.Service
import android.content.*
import android.os.Binder
import android.os.IBinder
import android.os.SystemClock
import android.util.Log
import kotlinx.serialization.ImplicitReflectionSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.parseList
import kotlinx.serialization.stringify
import java.lang.Exception
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.locks.ReentrantLock


const val SAVE_INTERVAL_MILLS = 30_000
/* Честно говоря не понял насчет сохранения времени. должны ли бустры идти пока сервис убит?
Если должны - тогда время считать не от uptime а от Date наверное, с обновлением форсом последнего времени если юзер что меняет.
 ну и возможно еще получение реального времени с сервера NTP допустим
*/

class BoosterTrackerService : Service(), BoosterTracker {
    inner class BoosterTrackerBinder : Binder() {
        fun getService(): BoosterTrackerService {
            return this@BoosterTrackerService
        }
    }

    private val lockChanges = ReentrantLock()

    private val scheduleTaskExecutor = Executors.newScheduledThreadPool(5)
    private val binder: BoosterTrackerBinder = BoosterTrackerBinder()
    private var receiver: BroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val action = intent.action
            if (action == Intent.ACTION_TIME_CHANGED || action == Intent.ACTION_TIMEZONE_CHANGED) {
                updateBoosters()
            }
        }
    }

    private var listeners: Set<BoosterListener> = emptySet()
    private var trackers: Map<String, ServiceTrackableObject> = emptyMap()

    private var lastUptimeDate: Long? = null
    private var saveElapsed: Long = 0

    private val intent: IntentFilter
        get() {

            val intent = IntentFilter()
            intent.addAction(Intent.ACTION_TIME_TICK)
            intent.addAction(Intent.ACTION_TIMEZONE_CHANGED)
            intent.addAction(Intent.ACTION_TIME_CHANGED)
            return intent
        }

    private var timeReceiver: BroadcastReceiver? = null


    fun unscubscribe() {
        unregisterReceiver(receiver)
    }

    fun subscribe() {
        registerReceiver(receiver, this.intent)
    }

    override fun onBind(intent: Intent?): IBinder? {
        return binder
    }

    override fun onCreate() {
        super.onCreate()
        this.subscribe()
        scheduleTaskExecutor.scheduleAtFixedRate(object : Runnable {
            override fun run() {
                updateBoosters()
            }
        }, 0, 1, TimeUnit.SECONDS)
        load()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_STICKY
    }


    override fun onDestroy() {
        unscubscribe()
        scheduleTaskExecutor.shutdownNow()
        save()
        super.onDestroy()
        Log.v("SERVICE", "ON DESTROY!")
    }



    //region Update cycle
    fun updateBoosters() {
        Log.v("SERVICE", "ON UPDATE!")
        val currentDate = SystemClock.uptimeMillis()
        if (lastUptimeDate == null) {
            lastUptimeDate = currentDate
            return
        }
        val elapsed = currentDate - lastUptimeDate!!
        lastUptimeDate = currentDate
        if (elapsed > 0) {
            saveElapsed += elapsed
            trackers.values.forEach { tracker ->
                tracker.elapsedMilliseconds = tracker.elapsedMilliseconds + elapsed
            }

            sendNotifications()
        } else {
            saveElapsed += SAVE_INTERVAL_MILLS
        }

        if (saveElapsed >= SAVE_INTERVAL_MILLS) {
            saveElapsed = 0
            save()
        }
    }

    private fun sendNotifications() {
        try {
            lockChanges.lock()
            var hadRemoved = false
            trackers = trackers.filter { tracker ->
                var result = true
                val elapsedSeconds = TimeUnit.MILLISECONDS.toSeconds(
                    tracker.value.elapsedMilliseconds
                ).toInt()

                if (tracker.value.duration != null) {
                    result = elapsedSeconds < tracker.value.duration!!
                    hadRemoved = result
                }
                listeners.forEach { listener ->
                    if (result) {
                        listener.onTimeUpdate(
                            tracker.key,
                            if (tracker.value.duration != null) tracker.value.duration!! - elapsedSeconds else 0
                        )
                    } else {
                        listener.onTimeEnd(tracker.key)
                    }
                }

                result
            }
            if (hadRemoved) {
                save()
            }
        } finally {
            lockChanges.unlock()
        }

    }
    //endregion

    //region I/O
    fun load() {
        val sharedPreferences =
            getSharedPreferences("booster_service_settings", Context.MODE_PRIVATE)
        val saved = sharedPreferences.getString("saved_trackers", null)
        if (saved != null) {
            trackers = decodeString(saved).map { it.productId to it }.toMap()
        }
    }

    fun save() {
        val sharedPreferences =
            getSharedPreferences("booster_service_settings", Context.MODE_PRIVATE).edit()
        sharedPreferences.putString("saved_trackers", encodeObjects(trackers.values.toList()))
        sharedPreferences.apply()
    }
    //endregion

    //region Interface implementation
    override fun track(booster: Trackable) {
        try {
            lockChanges.lock()
            if (!trackers.containsKey(booster.productId)) {
                val trackable = ServiceTrackableObject(
                    booster.productId,
                    booster.duration
                )
                trackers = trackers.plus(Pair(trackable.productId, trackable))
                Log.v("SERVICE", "NEW Trackable:\n" + encodeObjects(trackers.values.toList()))

            }
        } finally {
            lockChanges.unlock()
        }
    }

    override fun activeBoosterIds(): Set<String> {
        return trackers.filterValues { tracker ->
            if (tracker.duration != null) {
                TimeUnit.MILLISECONDS.toSeconds(
                    tracker.elapsedMilliseconds
                ) >= tracker.duration
            } else true
        }.map { t -> t.key }.toHashSet()
    }

    @Synchronized
    override fun add(listener: BoosterListener) {
        listeners = listeners.plus(listener)
        saveElapsed += SAVE_INTERVAL_MILLS
    }

    @Synchronized
    override fun remove(listener: BoosterListener) {
        listeners = listeners.minus(listener)
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }
    //endregion


    //region Serialization
    @UseExperimental(ImplicitReflectionSerializer::class)
    private fun decodeString(stringValue: String): List<ServiceTrackableObject> {
        try {
            return Json.parseList(stringValue)
        } catch (ex: Exception) {
            return emptyList()
        }

    }


    @UseExperimental(ImplicitReflectionSerializer::class)
    private fun encodeObjects(trackable: List<ServiceTrackableObject>): String {
        try {
            return Json.stringify(trackable)
        } catch (ex: Exception) {
            return "{}"
        }

    }
    //endregion

}