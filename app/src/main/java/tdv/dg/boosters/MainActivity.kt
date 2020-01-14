package tdv.dg.boosters

import android.content.ComponentName
import android.content.Intent
import android.content.ServiceConnection
import android.support.v7.app.AppCompatActivity
import android.os.Bundle
import android.os.IBinder
import android.util.Log
import android.widget.Button
import android.widget.TextView
import tdv.dg.boostertrackerservice.BoosterTrackerService
import java.util.concurrent.TimeUnit

class MainActivity : AppCompatActivity(), tdv.dg.boostertrackerservice.BoosterListener {
    class FastBooster : tdv.dg.boostertrackerservice.Trackable {
        override val productId: String
            get() = "fast_booster"
        override val duration: Int?
            get() = 10

    }

    class DayBooster : tdv.dg.boostertrackerservice.Trackable {
        override val productId: String
            get() = "day_booster"
        override val duration: Int?
            get() = TimeUnit.DAYS.toSeconds(1).toInt()

    }

    class InfinityBooster : tdv.dg.boostertrackerservice.Trackable {
        override val productId: String
            get() = "infinity_booster"
        override val duration: Int?
            get() = null

    }

    private var createButton: Button? = null
    private var mainText: TextView? = null

    val connection = object : ServiceConnection {
        override fun onServiceDisconnected(name: ComponentName?) {
            onDisconnected()
        }

        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            if (service is tdv.dg.boostertrackerservice.BoosterTrackerService.BoosterTrackerBinder) {
                onConnected((service as tdv.dg.boostertrackerservice.BoosterTrackerService.BoosterTrackerBinder).getService())
            }
        }

    }

    var boosterTracker: tdv.dg.boostertrackerservice.BoosterTrackerService? = null


    private val serviceIntent: Intent
        get() {
            val intent = Intent(this, BoosterTrackerService::class.java)
            return intent
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        setupViews()

        val intent = serviceIntent
        startService(intent)


    }

    override fun onResume() {
        super.onResume()
        connectToService()
    }

    override fun onPause() {
        super.onPause()
        unbindService(connection)
    }

    override fun onDestroy() {
        super.onDestroy()
        stopService(serviceIntent)
    }


    fun setupViews() {
        this.mainText = findViewById(R.id.connected)


        findViewById<Button>(R.id.add_fast_booster).setOnClickListener {
            boosterTracker?.track(
                FastBooster()
            )
        }
        findViewById<Button>(R.id.add_day_booster).setOnClickListener {
            boosterTracker?.track(
                DayBooster()
            )
        }
        findViewById<Button>(R.id.add_inifity_booster).setOnClickListener {
            boosterTracker?.track(
                InfinityBooster()
            )
        }

    }

    fun onConnected(service: tdv.dg.boostertrackerservice.BoosterTrackerService) {
        this.boosterTracker = service
        this.mainText?.text = "CONNECTED"

        service.add(this)

    }

    fun onDisconnected() {
        this.boosterTracker = null
        this.mainText?.text = "DISCONNECTED"
    }

    fun connectToService() {

        bindService(serviceIntent, connection, 0)
    }


    override fun onTimeUpdate(booster: String, timeLeft: Int) {
        Log.v("ACTIVITY", "ON Time updated: " + booster + " timeLeft: " + timeLeft)
    }

    override fun onTimeEnd(booster: String) {
        Log.v("ACTIVITY", "ON ended: " + booster)
    }


}
