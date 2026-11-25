package com.example.data.model

enum class MediaType {
    VIDEO,
    AUDIO
}

enum class LibraryFilter(val label: String) {
    ALL("전체"),
    VIDEO("동영상"),
    AUDIO("오디오"),
    FAVORITES("즐겨찾기")
}

enum class SortOption(val label: String) {
    RECENT("최근 재생순"),
    DATE_ADDED("추가된 순"),
    TITLE("이름순"),
    DURATION("재생 시간순")
}

enum class AspectRatioMode(val label: String) {
    FIT("화면에 맞춤"),
    FILL("꽉 채우기"),
    ZOOM("확대 자르기"),
    RATIO_16_9("16:9 고정"),
    RATIO_4_3("4:3 고정")
}

enum class RepeatModeOption(val label: String) {
    OFF("반복 안 함"),
    ONE("한 곡/영상 반복"),
    ALL("전체 반복")
}

enum class PopupSizePreset(val label: String, val widthDp: Float) {
    SMALL("작게", 180f),
    MEDIUM("보통", 260f),
    LARGE("크게", 340f)
}

data class SleepTimerOption(val label: String, val minutes: Int, val endOfTrack: Boolean = false)

val defaultSleepTimerOptions = listOf(
    SleepTimerOption("꺼짐", 0),
    SleepTimerOption("15분 후", 15),
    SleepTimerOption("30분 후", 30),
    SleepTimerOption("45분 후", 45),
    SleepTimerOption("60분 후", 60),
    SleepTimerOption("현재 미디어 종료 후", 0, endOfTrack = true)
)
