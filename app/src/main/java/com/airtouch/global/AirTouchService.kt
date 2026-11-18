package com.airtouch.global

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.hardware.display.DisplayManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.Display
import android.view.Surface
import android.view.WindowManager
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleService
import com.airtouch.MainActivity
import com.airtouch.camera.CameraManager
import com.airtouch.control.AdaptiveRate
import com.airtouch.control.ControlPipeline
import com.airtouch.control.Profile
import com.airtouch.control.SmartProfiles
import com.airtouch.gesture.GestureClassifier
import com.airtouch.detection.HandDetectionEngine
import com.airtouch.gesture.Gesture
import com.airtouch.model.HandFrame
import com.airtouch.mouse.VirtualMouse
import com.airtouch.settings.ProfileStore
import com.airtouch.settings.SettingsManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Controle global: mantém a câmera e o reconhecimento ativos com o app em segundo plano
 * (serviço em primeiro plano, com notificação fixa e botão Parar) e envia os resultados
 * ao [AirTouchAccessibilityService], que age sobre os outros apps.
 *
 * Nada é gravado nem enviado: cada quadro é analisado e descartado.
 */
class AirTouchService : LifecycleService() {

    data class Status(
        val running: Boolean = false,
        val handCount: Int = 0,
        val gesture: Gesture = Gesture.NONE,
        val locked: Boolean = false,
        val fps: Int = 0,
        val error: String? = null,
        /** Perfil em uso (automático pelo app em primeiro plano, se ligado). */
        val profile: String = "",
    )

    companion object {
        private const val CHANNEL_ID = "airtouch_global"
        private const val NOTIFICATION_ID = 42
        private const val ACTION_STOP = "com.airtouch.action.STOP"

        private val _status = MutableStateFlow(Status())
        val status: StateFlow<Status> = _status.asStateFlow()

        @Volatile private var live: AirTouchService? = null

        /** O serviço de acessibilidade avisa qual app veio para a frente (só o nome do pacote). */
        fun onForegroundApp(packageName: String) {
            live?.applySmartProfile(packageName)
        }

        fun start(context: Context) {
            ContextCompat.startForegroundService(
                context,
                Intent(context, AirTouchService::class.java)
            )
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, AirTouchService::class.java))
        }
    }

    private var camera: CameraManager? = null
    private var detector: HandDetectionEngine? = null
    private lateinit var pipeline: ControlPipeline
    private lateinit var store: ProfileStore
    private lateinit var settingsStore: SettingsManager
    private val rate = AdaptiveRate()
    private var profiles: List<Profile> = emptyList()

    private val main = Handler(Looper.getMainLooper())
    private var displayListener: DisplayManager.DisplayListener? = null

    private var fpsWindowStart = 0L
    private var fpsCount = 0
    private var fps = 0
    @Volatile private var stopped = false

    override fun onCreate() {
        super.onCreate()
        createChannel()
        ServiceCompat.startForeground(
            this,
            NOTIFICATION_ID,
            buildNotification(),
            ServiceInfo.FOREGROUND_SERVICE_TYPE_CAMERA,
        )
        val settings = SettingsManager(this)
        settingsStore = settings
        pipeline = ControlPipeline(VirtualMouse.Config(settings.sensitivity, settings.invertScroll))
        pipeline.minScore = if (settings.qualityFilter) 0.6f else 0f
        rate.powerSaver = settings.powerSaver
        GestureClassifier.pinchScale = settings.pinchScale
        // Mesmo perfil e gestos personalizados escolhidos dentro do app.
        store = ProfileStore(this)
        profiles = store.loadProfiles()
        pipeline.dispatcher.profile = profiles.firstOrNull { it.name == store.activeName } ?: profiles.first()
        pipeline.custom.gestures = store.loadGestures()
        live = this
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        if (intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }
        if (camera == null) startPipeline()
        return START_NOT_STICKY
    }

    /** Troca o perfil conforme o app em primeiro plano (perfis inteligentes). */
    fun applySmartProfile(packageName: String) {
        if (!::pipeline.isInitialized || packageName == this.packageName) return
        val name = (if (settingsStore.autoProfile) SmartProfiles.forPackage(packageName) else null)
            ?: store.activeName
        val p = profiles.firstOrNull { it.name == name } ?: profiles.first()
        pipeline.dispatcher.profile = p
        _status.value = _status.value.copy(profile = p.name)
    }

    private fun startPipeline() {
        val a11y = AirTouchAccessibilityService.instance
        if (a11y == null) {
            _status.value = Status(error = "Ative o serviço de acessibilidade do AIR TOUCH.")
            stopSelf()
            return
        }
        stopped = false
        _status.value = Status(running = true)
        a11y.activate()

        // Carrega o modelo fora da thread principal e só então abre a câmera.
        Thread {
            try {
                val engine = HandDetectionEngine(
                    context = applicationContext,
                    maxHands = 2,
                    onResult = ::onHandFrame,
                    onError = { msg -> _status.value = _status.value.copy(error = msg) },
                )
                engine.start()
                if (stopped) {
                    engine.close()
                    return@Thread
                }
                detector = engine
                main.post {
                    if (stopped) return@post
                    val cam = CameraManager(
                        applicationContext,
                        onFrame = { bmp, ts -> detector?.detect(bmp, ts) },
                        frameGate = { t -> rate.shouldProcess(t) },
                        onError = { msg -> _status.value = _status.value.copy(error = msg) },
                    )
                    camera = cam
                    cam.start(this, previewView = null, rotation = displayRotation())
                    watchRotation(cam)
                }
            } catch (e: Exception) {
                _status.value = Status(error = "Falha ao carregar o modelo: ${e.message}")
                main.post { stopSelf() }
            }
        }.start()
    }

    private fun onHandFrame(frame: HandFrame) {
        if (stopped) return
        val now = SystemClock.uptimeMillis()
        if (now - fpsWindowStart >= 1000) {
            fps = fpsCount
            fpsCount = 0
            fpsWindowStart = now
        }
        fpsCount++

        val out = pipeline.process(frame.hands, frame.imageWidth, frame.imageHeight, now)
        rate.onResult(frame.hands.isNotEmpty(), now)
        AirTouchAccessibilityService.instance?.handleOutput(out)

        _status.value = Status(
            running = true,
            handCount = frame.hands.size,
            gesture = out.stable,
            locked = out.locked,
            fps = fps,
            error = _status.value.error,
            profile = _status.value.profile.ifEmpty { pipeline.dispatcher.profile.name },
        )
    }

    // ------------------------------------------------------------ rotação do aparelho

    @Suppress("DEPRECATION")
    private fun displayRotation(): Int {
        val wm = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        return try {
            wm.defaultDisplay.rotation
        } catch (e: Exception) {
            Surface.ROTATION_0
        }
    }

    private fun watchRotation(cam: CameraManager) {
        val dm = getSystemService(Context.DISPLAY_SERVICE) as DisplayManager
        val listener = object : DisplayManager.DisplayListener {
            override fun onDisplayAdded(displayId: Int) {}
            override fun onDisplayRemoved(displayId: Int) {}
            override fun onDisplayChanged(displayId: Int) {
                if (displayId == Display.DEFAULT_DISPLAY) cam.setTargetRotation(displayRotation())
            }
        }
        displayListener = listener
        dm.registerDisplayListener(listener, main)
    }

    // ------------------------------------------------------------ notificação

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID,
                    "Controle global ativo",
                    NotificationManager.IMPORTANCE_LOW
                )
            )
        }
    }

    private fun buildNotification() = run {
        val stopIntent = PendingIntent.getService(
            this, 0,
            Intent(this, AirTouchService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val openIntent = PendingIntent.getActivity(
            this, 1,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_menu_camera)
            .setContentTitle("AIR TOUCH — controle global ativo")
            .setContentText("A câmera está ligada para reconhecer gestos. Nada é gravado.")
            .setOngoing(true)
            .setContentIntent(openIntent)
            .addAction(android.R.drawable.ic_media_pause, "Parar", stopIntent)
            .build()
    }

    // ------------------------------------------------------------ encerramento

    override fun onDestroy() {
        stopped = true
        live = null
        displayListener?.let {
            (getSystemService(Context.DISPLAY_SERVICE) as DisplayManager).unregisterDisplayListener(it)
        }
        displayListener = null
        camera?.release()
        camera = null
        detector?.close()
        detector = null
        AirTouchAccessibilityService.instance?.deactivate()
        _status.value = Status()
        super.onDestroy()
    }
}
