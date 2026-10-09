package com.pomodoro.domain

/** Stable settings IDs, independent of filenames, playback and filesystem access. */
enum class AlarmSound(val id: String, val label: String) {
    ORIGINAL("original", "Original phase alarm"),
    FUNNY("funny", "Funny alarm"),
    CLASSIC_CLOCK("classic-clock", "Classic alarm clock"),
    KITCHEN_TIMER("kitchen-timer", "Kitchen timer"),
    LOFI("lofi", "Lo-fi alarm"),
    HAPPY_BELLS("happy-bells", "Happy bells"),
    URGENT_TONE("urgent-tone", "Urgent tone"),
    SIMPLE_ALARM("simple-alarm", "Simple alarm"),
    DIGITAL_CLOCK("digital-clock", "Digital alarm clock"),
    DIGITAL_CLOCK_ALT("digital-clock-alt", "Digital alarm clock — alternate");

    companion object {
        fun fromId(id: String?): AlarmSound = entries.firstOrNull { it.id == id } ?: ORIGINAL
    }
}
