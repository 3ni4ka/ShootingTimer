package com.example.shootingtimer

import android.content.Intent
import android.content.SharedPreferences
import android.graphics.Color
import android.graphics.Typeface
import android.os.Build
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.view.WindowInsetsController
import android.view.WindowManager
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import java.util.Locale

class MainActivity : AppCompatActivity() {

    companion object {
        private const val PREFS_NAME = "ShootingTimerPrefs"
        private const val MIN_PREPARATION = 5
        private const val MIN_WORK = 10
        private const val MIN_HOLD = 1
        private const val MIN_REST = 5
        private const val MIN_CYCLES = 1
    }

    // Поля ввода
    private lateinit var etPreparation: EditText
    private lateinit var etWork: EditText
    private lateinit var etHold: EditText
    private lateinit var etRest: EditText
    private lateinit var etCycles: EditText

    private lateinit var tvTotalTimeCalc: TextView

    // SharedPreferences для сохранения
    private lateinit var sharedPreferences: SharedPreferences

    private var bgColor: Int = 0
    private var textPrimary: Int = 0
    private var textSecondary: Int = 0
    private var textHint: Int = 0
    private var accent: Int = 0

    private lateinit var scrollView: ScrollView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Инициализация цветов темы
        bgColor = ContextCompat.getColor(this, R.color.background)
        textPrimary = ContextCompat.getColor(this, R.color.text_primary)
        textSecondary = ContextCompat.getColor(this, R.color.text_secondary)
        textHint = ContextCompat.getColor(this, R.color.text_hint)
        accent = ContextCompat.getColor(this, R.color.accent)

        sharedPreferences = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        setupUI()
        setupEdgeToEdgeAndStatusBar()
        loadSavedValues()
    }

    private fun setupEdgeToEdgeAndStatusBar() {
        WindowCompat.setDecorFitsSystemWindows(window, false)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.insetsController?.let { controller ->
                controller.setSystemBarsAppearance(
                    WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS,
                    WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS
                )
                controller.systemBarsBehavior =
                    WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }
        } else {
            @Suppress("DEPRECATION")
            window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR or
                    View.SYSTEM_UI_FLAG_LAYOUT_STABLE or
                    View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
            @Suppress("DEPRECATION")
            window.statusBarColor = Color.TRANSPARENT
            @Suppress("DEPRECATION")
            window.navigationBarColor = Color.TRANSPARENT
        }

        ViewCompat.setOnApplyWindowInsetsListener(scrollView) { view, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(
                16,
                systemBars.top + 16,
                16,
                systemBars.bottom + 16
            )
            insets
        }
    }

    private fun setupUI() {
        scrollView = ScrollView(this)
        scrollView.layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.MATCH_PARENT
        )
        scrollView.setBackgroundColor(bgColor)

        val mainLayout = LinearLayout(this)
        mainLayout.layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        )
        mainLayout.orientation = LinearLayout.VERTICAL

        val titleTextView = TextView(this)
        titleTextView.layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply {
            bottomMargin = 64
        }
        titleTextView.text = getString(R.string.setting_timer)
        titleTextView.textSize = 24f
        titleTextView.setTextColor(textPrimary)
        titleTextView.setTypeface(null, Typeface.BOLD)
        titleTextView.gravity = Gravity.CENTER
        mainLayout.addView(titleTextView)

        etPreparation = createAndAddField(mainLayout, getString(R.string.preparation), getString(R.string.sec), MIN_PREPARATION)
        etWork = createAndAddField(mainLayout, getString(R.string.work), getString(R.string.sec), MIN_WORK)
        etHold = createAndAddField(mainLayout, getString(R.string.hold), getString(R.string.sec), MIN_HOLD)
        etRest = createAndAddField(mainLayout, getString(R.string.rest), getString(R.string.sec), MIN_REST)
        etCycles = createAndAddField(mainLayout, getString(R.string.cycles), getString(R.string.pcs), MIN_CYCLES)

        tvTotalTimeCalc = TextView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = 48
                bottomMargin = 8
            }
            textSize = 16f
            setTextColor(textSecondary)
            gravity = Gravity.CENTER
        }
        mainLayout.addView(tvTotalTimeCalc)

        val btnStart = Button(this)
        btnStart.layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply {
            gravity = Gravity.CENTER_HORIZONTAL
            topMargin = 32
        }
        btnStart.text = getString(R.string.btn_start_timer)
        btnStart.textSize = 18f
        btnStart.setTextColor(Color.WHITE)
        btnStart.background = ContextCompat.getDrawable(this, R.drawable.btn_start)
        btnStart.setPadding(48, 24, 48, 24)
        btnStart.isAllCaps = false
        btnStart.stateListAnimator = null
        btnStart.setOnClickListener {
            if (validateInputs()) {
                saveValues()
                startTimerActivity()
            }
        }
        mainLayout.addView(btnStart)

        scrollView.addView(mainLayout)
        setContentView(scrollView)
    }

    private fun createAndAddField(
        parent: LinearLayout,
        label: String,
        hint: String,
        minValue: Int
    ): EditText {
        val container = LinearLayout(this)
        container.layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        )
        container.orientation = LinearLayout.VERTICAL

        val labelTextView = TextView(this)
        labelTextView.layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        )
        labelTextView.text = label
        labelTextView.textSize = 16f
        labelTextView.setTextColor(textSecondary)
        labelTextView.gravity = Gravity.CENTER
        container.addView(labelTextView)

        val rowLayout = LinearLayout(this)
        rowLayout.layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply {
            marginStart = 32
            marginEnd = 32
        }
        rowLayout.orientation = LinearLayout.HORIZONTAL
        rowLayout.gravity = Gravity.CENTER_VERTICAL

        val btnSize = (48 * resources.displayMetrics.density).toInt()

        val btnMinus = Button(this)
        btnMinus.layoutParams = LinearLayout.LayoutParams(btnSize, btnSize)
        btnMinus.text = "−"
        btnMinus.textSize = 24f
        btnMinus.setTextColor(Color.WHITE)
        btnMinus.background = ContextCompat.getDrawable(this, R.drawable.btn_stepper)
        btnMinus.setPadding(0, 0, 0, 0)
        btnMinus.includeFontPadding = false
        btnMinus.gravity = Gravity.CENTER
        btnMinus.isAllCaps = false
        btnMinus.stateListAnimator = null

        val editText = EditText(this)
        editText.layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        editText.inputType = android.text.InputType.TYPE_CLASS_NUMBER
        editText.gravity = Gravity.CENTER
        editText.textSize = 18f
        editText.setTextColor(textPrimary)
        editText.setHintTextColor(textHint)
        editText.hint = hint

        val btnPlus = Button(this)
        btnPlus.layoutParams = LinearLayout.LayoutParams(btnSize, btnSize)
        btnPlus.text = "+"
        btnPlus.textSize = 24f
        btnPlus.setTextColor(Color.WHITE)
        btnPlus.background = ContextCompat.getDrawable(this, R.drawable.btn_stepper)
        btnPlus.setPadding(0, 0, 0, 0)
        btnPlus.includeFontPadding = false
        btnPlus.gravity = Gravity.CENTER
        btnPlus.isAllCaps = false
        btnPlus.stateListAnimator = null

        rowLayout.addView(btnMinus)
        rowLayout.addView(editText)
        rowLayout.addView(btnPlus)

        container.addView(rowLayout)
        parent.addView(container)

        btnMinus.setOnClickListener {
            val value = editText.text.toString().toIntOrNull() ?: minValue
            editText.setText(String.format(Locale.getDefault(), "%d", (value - 1).coerceAtLeast(minValue)))
            saveValues()
            updateTotalTimeCalculation()
        }

        btnPlus.setOnClickListener {
            val value = editText.text.toString().toIntOrNull() ?: minValue
            editText.setText(String.format(Locale.getDefault(), "%d", value + 1))
            saveValues()
            updateTotalTimeCalculation()
        }

        editText.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                updateTotalTimeCalculation()
            }
        })

        editText.setOnFocusChangeListener { _, hasFocus ->
            if (!hasFocus) saveValues()
        }

        return editText
    }

    private fun loadSavedValues() {
        etPreparation.setText(sharedPreferences.getLong("preparation_time", MIN_PREPARATION.toLong()).toString())
        etWork.setText(sharedPreferences.getLong("work_time", MIN_WORK.toLong()).toString())
        etHold.setText(sharedPreferences.getLong("hold_time", MIN_HOLD.toLong()).toString())
        etRest.setText(sharedPreferences.getLong("rest_time", MIN_REST.toLong()).toString())
        etCycles.setText(sharedPreferences.getInt("cycles_count", MIN_CYCLES).toString())
        updateTotalTimeCalculation()
    }

    private fun updateTotalTimeCalculation() {
        if (!::tvTotalTimeCalc.isInitialized) return

        val prep = etPreparation.text.toString().toLongOrNull() ?: 0L
        val work = etWork.text.toString().toLongOrNull() ?: 0L
        val hold = etHold.text.toString().toLongOrNull() ?: 0L
        val rest = etRest.text.toString().toLongOrNull() ?: 0L
        val cycles = etCycles.text.toString().toIntOrNull() ?: 0

        val totalSeconds = prep + (cycles * (work + hold)) + ((cycles - 1).coerceAtLeast(0) * rest)
        
        val minutes = totalSeconds / 60
        val seconds = totalSeconds % 60
        val timeString = String.format(Locale.getDefault(), "%02d:%02d", minutes, seconds)
        
        tvTotalTimeCalc.text = getString(R.string.total_training_time_prefix, timeString)
    }

    private fun saveValues() {
        sharedPreferences.edit {
            putLong("preparation_time", etPreparation.text.toString().toLongOrNull() ?: MIN_PREPARATION.toLong())
            putLong("work_time", etWork.text.toString().toLongOrNull() ?: MIN_WORK.toLong())
            putLong("hold_time", etHold.text.toString().toLongOrNull() ?: MIN_HOLD.toLong())
            putLong("rest_time", etRest.text.toString().toLongOrNull() ?: MIN_REST.toLong())
            putInt("cycles_count", etCycles.text.toString().toIntOrNull() ?: MIN_CYCLES)
        }
    }

    private fun validateInputs(): Boolean {
        val prep = etPreparation.text.toString().toIntOrNull()
        val work = etWork.text.toString().toIntOrNull()
        val hold = etHold.text.toString().toIntOrNull()
        val rest = etRest.text.toString().toIntOrNull()
        val cycles = etCycles.text.toString().toIntOrNull()

        if (prep == null || work == null || hold == null || rest == null || cycles == null) {
            showError(getString(R.string.error_fill_fields))
            return false
        }

        when {
            prep < MIN_PREPARATION -> showError(getString(R.string.error_preparation, MIN_PREPARATION))
            work < MIN_WORK -> showError(getString(R.string.error_work, MIN_WORK))
            hold < MIN_HOLD -> showError(getString(R.string.error_hold, MIN_HOLD))
            rest < MIN_REST -> showError(getString(R.string.error_rest, MIN_REST))
            cycles < MIN_CYCLES -> showError(getString(R.string.error_cycles, MIN_CYCLES))
            else -> return true
        }
        return false
    }

    private fun showError(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    private fun startTimerActivity() {
        val intent = Intent(this, TimerActivity::class.java).apply {
            putExtra("PREPARATION_TIME", etPreparation.text.toString().toLongOrNull() ?: MIN_PREPARATION.toLong())
            putExtra("WORK_TIME", etWork.text.toString().toLongOrNull() ?: MIN_WORK.toLong())
            putExtra("HOLD_TIME", etHold.text.toString().toLongOrNull() ?: MIN_HOLD.toLong())
            putExtra("REST_TIME", etRest.text.toString().toLongOrNull() ?: MIN_REST.toLong())
            putExtra("CYCLES_COUNT", etCycles.text.toString().toIntOrNull() ?: MIN_CYCLES)
        }
        startActivity(intent)
    }

    override fun onPause() {
        super.onPause()
        if (::sharedPreferences.isInitialized) saveValues()
    }
}