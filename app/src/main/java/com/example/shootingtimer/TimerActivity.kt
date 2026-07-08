package com.example.shootingtimer

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.SoundPool
import android.os.Build
import android.os.Bundle
import android.view.Gravity
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch

class TimerActivity : AppCompatActivity() {

    private lateinit var tvTimer: TextView
    private lateinit var tvPhase: TextView
    private lateinit var tvCycle: TextView
    private lateinit var tvNextPhase: TextView
    private lateinit var btnBack: Button
    private lateinit var rootLayout: LinearLayout

    private val viewModel: TimerViewModel by viewModels()

    private var soundPool: SoundPool? = null
    private var beepSound: Int = 0
    private var startWorkSound: Int = 0
    private var holdFinishSound: Int = 0

    private var lastPhase: TimerPhase? = null

    private lateinit var notificationHelper: NotificationHelper

    // Цвета темы
    private var bgColor: Int = 0
    private var textPrimary: Int = 0
    private var textDarkGray: Int = 0
    private var textGray: Int = 0
    private var accent: Int = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        bgColor = ContextCompat.getColor(this, R.color.background)
        textPrimary = ContextCompat.getColor(this, R.color.text_primary)
        textDarkGray = ContextCompat.getColor(this, R.color.text_dark_gray)
        textGray = ContextCompat.getColor(this, R.color.text_gray)
        accent = ContextCompat.getColor(this, R.color.accent)

        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        setupUI()

        val insetsController = androidx.core.view.WindowInsetsControllerCompat(window, rootLayout)

        val isLightTheme = (resources.configuration.uiMode and
                android.content.res.Configuration.UI_MODE_NIGHT_MASK) !=
                android.content.res.Configuration.UI_MODE_NIGHT_YES

        insetsController.isAppearanceLightStatusBars = isLightTheme
        insetsController.isAppearanceLightNavigationBars = isLightTheme
        insetsController.systemBarsBehavior =
            androidx.core.view.WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE

        notificationHelper = NotificationHelper(this)
        requestNotificationPermission()

        initSoundPool()
        observeSoundEvents()

        viewModel.init(
            preparationTime = intent.getLongExtra("PREPARATION_TIME", 10),
            workTime = intent.getLongExtra("WORK_TIME", 20),
            holdTime = intent.getLongExtra("HOLD_TIME", 3),
            restTime = intent.getLongExtra("REST_TIME", 40),
            totalCycles = intent.getIntExtra("CYCLES_COUNT", 10)
        )

        volumeControlStream = AudioManager.STREAM_MUSIC

        observeState()
        setupListeners()
    }

    private fun setupUI() {
        rootLayout = LinearLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.MATCH_PARENT
            )
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setBackgroundColor(bgColor)
        }

        ViewCompat.setOnApplyWindowInsetsListener(rootLayout) { view, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(16, systemBars.top + 16, 16, systemBars.bottom + 16)
            insets
        }

        tvPhase = TextView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = 16 }
            text = getString(R.string.phase_preparation)
            textSize = 32f
            setTextColor(textPrimary)
            setTypeface(null, Typeface.BOLD)
        }
        rootLayout.addView(tvPhase)

        tvTimer = TextView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = 16 }
            text = getString(R.string.timer_default)
            textSize = 72f
            setTextColor(textPrimary)
            setTypeface(null, Typeface.BOLD)
        }
        rootLayout.addView(tvTimer)

        tvCycle = TextView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = 8 }
            text = getString(R.string.cycle_format, 1, 3)
            textSize = 24f
            setTextColor(textDarkGray)
        }
        rootLayout.addView(tvCycle)

        tvNextPhase = TextView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = 32 }
            textSize = 16f
            setTextColor(textGray)
        }
        rootLayout.addView(tvNextPhase)

        btnBack = Button(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = 32 }
            text = getString(R.string.btn_finish)
            textSize = 16f
            minWidth = 200
            background = ContextCompat.getDrawable(this@TimerActivity, R.drawable.btn_rounded)
            setTextColor(Color.WHITE)
            isAllCaps = false
            stateListAnimator = null
            setPadding(64, 24, 64, 24)
        }
        rootLayout.addView(btnBack)

        setContentView(rootLayout)
    }

    private fun setupListeners() {
        btnBack.setOnClickListener {
            viewModel.stop()
            val intent = Intent(this, MainActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            startActivity(intent)
            finish()
        }
    }

    private fun observeSoundEvents() {
        lifecycleScope.launch {
            viewModel.soundEvents.collect { event ->
                when (event) {
                    SoundEvent.BEEP -> playSound(beepSound)
                    SoundEvent.START_WORK -> playSound(startWorkSound)
                    SoundEvent.HOLD_FINISH -> playSound(if (holdFinishSound != 0) holdFinishSound else beepSound)
                    SoundEvent.TRAINING_COMPLETE -> playSound(beepSound)
                }
            }
        }
    }

    private fun observeState() {
        lifecycleScope.launch {
            viewModel.timerState.collect { state ->
                updateTimerDisplay(state.secondsLeft)
                updateCycle(state.currentCycle, state.totalCycles)
                updateNextPhase(state.nextPhase, state.nextPhaseSeconds)

                if (state.phase != lastPhase) {
                    updatePhaseUI(state.phase)

                    if (state.phase == TimerPhase.COMPLETED) {
                        onTrainingCompleted()
                        notificationHelper.sendTrainingCompleted()
                    }

                    lastPhase = state.phase
                }
            }
        }
    }

    private fun updateTimerDisplay(seconds: Long) {
        val minutes = seconds / 60
        val remainingSeconds = seconds % 60
        tvTimer.text = String.format("%02d:%02d", minutes, remainingSeconds)
    }

    private fun updateCycle(current: Int, total: Int) {
        tvCycle.text = getString(R.string.cycle_format, current, total)
    }

    private fun updateNextPhase(nextPhase: TimerPhase, seconds: Long) {
        if (nextPhase == TimerPhase.COMPLETED) {
            tvNextPhase.text = getString(R.string.next_phase_end)
        } else {
            val name = getPhaseName(nextPhase)
            val minutes = seconds / 60
            val remainingSeconds = seconds % 60
            tvNextPhase.text = getString(
                R.string.next_phase_format,
                name,
                String.format("%02d:%02d", minutes, remainingSeconds)
            )
        }
    }

    private fun getPhaseName(phase: TimerPhase): String = when (phase) {
        TimerPhase.PREPARATION -> getString(R.string.phase_preparation)
        TimerPhase.WORK -> getString(R.string.phase_work)
        TimerPhase.HOLD -> getString(R.string.phase_hold)
        TimerPhase.REST -> getString(R.string.phase_rest)
        TimerPhase.COMPLETED -> getString(R.string.phase_completed)
    }

    private fun updatePhaseUI(phase: TimerPhase) {
        tvPhase.text = getPhaseName(phase)
        tvPhase.setTextColor(getPhaseColor(phase))
    }

    private fun getPhaseColor(phase: TimerPhase): Int = when (phase) {
        TimerPhase.PREPARATION -> Color.BLUE
        TimerPhase.WORK -> Color.parseColor("#FF9800")
        TimerPhase.HOLD -> Color.RED
        TimerPhase.REST -> Color.GREEN
        TimerPhase.COMPLETED -> textDarkGray
    }

    private fun onTrainingCompleted() {
        tvNextPhase.text = ""
        btnBack.text = getString(R.string.btn_back_to_settings)
        btnBack.background = ContextCompat.getDrawable(this, R.drawable.btn_rounded)
        btnBack.setTextColor(Color.WHITE)

        btnBack.setOnClickListener {
            viewModel.stop()
            val intent = Intent(this, MainActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            startActivity(intent)
            finish()
        }
    }

    private fun playSound(soundId: Int) {
        if (soundId != 0) {
            try { soundPool?.play(soundId, 1f, 1f, 0, 0, 1f) } catch (_: Exception) {}
        }
    }

    private fun initSoundPool() {
        val audioAttributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_MEDIA)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()

        soundPool = SoundPool.Builder()
            .setMaxStreams(4)
            .setAudioAttributes(audioAttributes)
            .build()

        try {
            beepSound = soundPool?.load(this, R.raw.beep, 1) ?: 0
            startWorkSound = soundPool?.load(this, R.raw.start_work, 1) ?: 0
            holdFinishSound = soundPool?.load(this, R.raw.hold_finish, 1) ?: 0
        } catch (_: Exception) { }
    }

    override fun onDestroy() {
        super.onDestroy()
        soundPool?.release()
        soundPool = null
    }

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) {}

    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
}