package app.linkdock.desktop.release

data class ReleaseNoteEntry(
    val version: String,
    val title: String,
    val items: List<String>
)

object AppReleaseNotes {
    private val notes = listOf(
        ReleaseNoteEntry(
            version = "1.10.4",
            title = "VOD 선택 드롭다운의 사용성을 더 자연스럽게 다듬었습니다.",
            items = listOf(
                "다운로드할 VOD 선택 필드의 클릭 영역을 개선해 더 쉽게 열 수 있도록 정리했습니다.",
                "드롭다운 메뉴 폭과 선택 영역을 다듬어 항목을 더 편하게 고를 수 있습니다.",
                "VOD 선택 UI가 윈도우 환경에서도 더 자연스럽게 보이도록 보완했습니다."
            )
        ),
        ReleaseNoteEntry(
            version = "1.10.3",
            title = "다운로드의 품질 선택을 더 안정적으로 개선했습니다.",
            items = listOf(
                "일부 항목에서 기대한 화질보다 낮게 선택되던 문제를 수정했습니다.",
                "VOD를 선택한 뒤 다운로드 흐름이 실제 정보와 더 정확하게 맞도록 정리했습니다."
            )
        ),
        ReleaseNoteEntry(
            version = "1.10.2",
            title = "다운로드 파일을 더 알아보기 쉽게 정리했습니다.",
            items = listOf(
                "여러 항목이 있는 VOD에서 선택한 항목 이름이 저장 파일명에 함께 반영되도록 개선했습니다.",
                "다운로드 후에도 어떤 항목을 받았는지 파일명만 보고 더 쉽게 구분할 수 있습니다.",
                "선택한 항목이 다른 경우 저장 파일명이 겹쳐 보이던 흐름을 줄이도록 정리했습니다."
            )
        ),
        ReleaseNoteEntry(
            version = "1.10.1",
            title = "윈도우 환경에서 Streamlink 상태 확인 흐름을 더 정확하게 다듬었습니다.",
            items = listOf(
                "업데이트가 필요 없는 경우에도 실패로 보이던 문제를 수정했습니다.",
                "Streamlink가 이미 최신 상태일 때 상태 안내가 실제 상황에 맞게 표시되도록 보완했습니다.",
                "설치 및 실행 확인 문구가 더 자연스럽게 이어지도록 정리했습니다."
            )
        ),
        ReleaseNoteEntry(
            version = "1.10",
            title = "SPWN 멀티파트 선택과 다운로드 안내 흐름을 더 자연스럽게 정리했습니다.",
            items = listOf(
                "여러 항목이 있는 SPWN VOD에서 받을 항목을 화면에서 선택할 수 있도록 개선했습니다.",
                "항목 확인, 선택, 다운로드로 이어지는 흐름을 정리해 다음 행동을 더 알기 쉽게 안내합니다.",
                "버튼, 상태 문구, 안내 메시지를 다듬어 전체 다운로드 흐름이 더 자연스럽게 보이도록 정리했습니다."
            )
        ),
        ReleaseNoteEntry(
            version = "1.9",
            title = "설치와 상태 확인 흐름을 더 안정적으로 정리했습니다.",
            items = listOf(
                "Streamlink 설치와 업데이트 진행 중 상태 안내가 더 일관되게 보이도록 정리했습니다.",
                "설치 후 확인 흐름을 다듬어 전체 동작이 더 안정적으로 이어지도록 개선했습니다."
            )
        ),
        ReleaseNoteEntry(
            version = "1.8.8",
            title = "설치 완료 상태 안내를 더 자연스럽게 다듬었습니다.",
            items = listOf(
                "설치 후 완료 메시지와 상태 안내가 더 깔끔하게 보이도록 정리했습니다.",
                "플러그인 적용 흐름의 중복 처리를 정리해 내부 동작을 더 안정적으로 다듬었습니다."
            )
        ),
        ReleaseNoteEntry(
            version = "1.8.7",
            title = "입력 오류 표시와 상태 안내를 더 정확하게 다듬었습니다.",
            items = listOf(
                "URL 입력 오류가 다른 입력칸에 잘못 표시되지 않도록 정리했습니다.",
                "다운로드를 시작할 수 없는 경우 필요한 다음 행동을 더 알기 쉽게 안내합니다.",
                "설치 상태 요약에서 Streamlink와 FFmpeg 상태를 더 정확하게 확인할 수 있습니다."
            )
        ),
        ReleaseNoteEntry(
            version = "1.8.6",
            title = "앱 내부 흐름을 더 안정적으로 정리했습니다.",
            items = listOf(
                "설정, 설치 확인, 다운로드 흐름을 정리해 전체 동작을 더 안정적으로 다듬었습니다."
            )
        ),
        ReleaseNoteEntry(
            version = "1.8.5",
            title = "변경 이력 보기를 개선했습니다.",
            items = listOf(
                "변경 이력 표시 방식을 더 알맞게 정리했습니다."
            )
        ),
        ReleaseNoteEntry(
            version = "1.8.4",
            title = "앱 설정 저장을 더 안정적으로 개선했습니다.",
            items = listOf(
                "저장 경로와 일부 앱 설정이 보다 안정적으로 유지되도록 개선했습니다."
            )
        ),
        ReleaseNoteEntry(
            version = "1.8.3",
            title = "설치와 다운로드 흐름을 더 자연스럽게 개선했습니다.",
            items = listOf(
                "Streamlink 설치 버튼에서 FFmpeg도 함께 확인하고 필요한 경우 같이 설치할 수 있습니다.",
                "FFmpeg가 없는 상태에서는 다운로드를 시작하지 않도록 개선했습니다.",
                "다운로드 시 오디오 누락이나 병합 문제를 줄이기 위해 FFmpeg 연동을 보강했습니다.",
                "직접 설치한 Streamlink와 FFmpeg도 더 잘 인식하도록 개선했습니다.",
                "설치 확인과 업데이트 흐름을 정리해 전체 동작을 더 안정적으로 다듬었습니다."
            )
        ),
        ReleaseNoteEntry(
            version = "1.8.2",
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

    fun recent(limit: Int = 5): List<ReleaseNoteEntry> {
        return notes.take(limit)
    }

    fun all(): List<ReleaseNoteEntry> {
        return notes
    }
}