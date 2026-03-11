package app.linkdock.desktop.release

data class ReleaseNoteEntry(
    val version: String,
    val title: String,
    val items: List<String>
)

object AppReleaseNotes {
    private val notes = listOf(
        ReleaseNoteEntry(
            version = "1.8.1",
            title = "입력 안내와 상태 안내를 더 이해하기 쉽게 다듬었습니다.",
            items = listOf(
                "서비스별로 어떤 페이지 주소를 넣어야 하는지 더 쉽게 확인할 수 있습니다.",
                "잘못된 주소를 입력하면 바로 알아보기 쉬운 안내가 표시됩니다.",
                "Streamlink가 없을 때 필요한 다음 행동을 화면에서 바로 확인할 수 있습니다."
            )
        )
    )

    fun find(version: String): ReleaseNoteEntry? {
        return notes.firstOrNull { it.version == version }
    }
}