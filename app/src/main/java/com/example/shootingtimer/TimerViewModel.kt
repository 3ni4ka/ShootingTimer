package com.example.shootingtimer

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

enum class SoundEvent {
    BEEP, START_WORK, HOLD_FINISH, TRAINING_COMPLETE
}

enum class TimerPhase {
    PREPARATION, WORK, HOLD, REST, COMPLETED
}

data class TimerState(
    val phase: TimerPhase = TimerPhase.PREPARATION,
    val secondsLeft: Long = 0,
    val currentCycle: Int = 1,
    val totalCycles: Int = 1,
    val nextPhase: TimerPhase = TimerPhase.WORK,
    val nextPhaseSeconds: Long = 0
)

class TimerViewModel : ViewModel() {

    private val _timerState = MutableStateFlow(TimerState())
    val timerState: StateFlow<TimerState> = _timerState

    private val _soundEvents = MutableSharedFlow<SoundEvent>()
    val soundEvents: SharedFlow<SoundEvent> = _soundEvents

    private var isInitialized = false
    private var timerJob: Job? = null

    private var preparationTime: Long = 0
    private var workTime: Long = 0
    private var holdTime: Long = 0
    private var restTime: Long = 0
    private var totalCycles: Int = 0

    fun init(
        preparationTime: Long,
        workTime: Long,
        holdTime: Long,
        restTime: Long,
        totalCycles: Int
    ) {
        if (isInitialized) return
        isInitialized = true

        this.preparationTime = preparationTime
        this.workTime = workTime
        this.holdTime = holdTime
        this.restTime = restTime
        this.totalCycles = totalCycles

        _timerState.value = TimerState(
            phase = TimerPhase.PREPARATION,
            secondsLeft = preparationTime,
            currentCycle = 1,
            totalCycles = totalCycles,
            nextPhase = TimerPhase.WORK,
            nextPhaseSeconds = workTime
        )

        startNextPhase()
    }

    private fun startNextPhase() {
        timerJob?.cancel()
        val state = _timerState.value

        val duration = when (state.phase) {
            TimerPhase.PREPARATION -> preparationTime
            TimerPhase.WORK -> workTime
            TimerPhase.HOLD -> holdTime
            TimerPhase.REST -> restTime
            TimerPhase.COMPLETED -> return
        }

        timerJob = viewModelScope.launch {
            if (state.phase == TimerPhase.WORK) {
                delay(1000)
                _soundEvents.emit(SoundEvent.START_WORK)
            }

            for (secondsLeft in duration downTo 0) {
                _timerState.value = _timerState.value.copy(
                    secondsLeft = secondsLeft,
                    nextPhase = getNextPhase(state.phase, state.currentCycle),
                    nextPhaseSeconds = getNextPhaseDuration(state.phase, state.currentCycle)
                )

                when (state.phase) {
                    TimerPhase.PREPARATION -> if (secondsLeft <= 2) _soundEvents.emit(SoundEvent.BEEP)
                    TimerPhase.WORK, TimerPhase.REST -> if (secondsLeft in 1..3) _soundEvents.emit(SoundEvent.BEEP)
                    TimerPhase.HOLD -> if (secondsLeft == 0L) _soundEvents.emit(SoundEvent.HOLD_FINISH)
                    else -> {}
                }

                if (secondsLeft > 0) delay(1000)
            }
            advancePhase()
        }
    }

    private fun advancePhase() {
        val state = _timerState.value

        val nextPhase = when (state.phase) {
            TimerPhase.PREPARATION -> TimerPhase.WORK
            TimerPhase.WORK -> TimerPhase.HOLD
            TimerPhase.HOLD -> if (state.currentCycle >= state.totalCycles) TimerPhase.COMPLETED else TimerPhase.REST
            TimerPhase.REST -> TimerPhase.WORK
            TimerPhase.COMPLETED -> return
        }

        val nextCycle = if (state.phase == TimerPhase.REST) state.currentCycle + 1 else state.currentCycle

        _timerState.value = state.copy(phase = nextPhase, currentCycle = nextCycle)

        // Три сигнала при завершении тренировки
        if (nextPhase == TimerPhase.COMPLETED) {
            viewModelScope.launch {
                delay(1200)
                repeat(3) {
                    _soundEvents.emit(SoundEvent.TRAINING_COMPLETE)
                    delay(400)
                }
            }
        }

        startNextPhase()
    }

    private fun getNextPhase(phase: TimerPhase, currentCycle: Int): TimerPhase = when (phase) {
        TimerPhase.PREPARATION -> TimerPhase.WORK
        TimerPhase.WORK -> TimerPhase.HOLD
        TimerPhase.HOLD -> if (currentCycle >= totalCycles) TimerPhase.COMPLETED else TimerPhase.REST
        TimerPhase.REST -> TimerPhase.WORK
        TimerPhase.COMPLETED -> TimerPhase.COMPLETED
    }

    private fun getNextPhaseDuration(phase: TimerPhase, currentCycle: Int): Long = when (getNextPhase(phase, currentCycle)) {
        TimerPhase.PREPARATION -> preparationTime
        TimerPhase.WORK -> workTime
        TimerPhase.HOLD -> holdTime
        TimerPhase.REST -> restTime
        TimerPhase.COMPLETED -> 0
    }



    fun stop() {
        timerJob?.cancel()
    }
    

    override fun onCleared() {
        super.onCleared()
        timerJob?.cancel()
    }
}