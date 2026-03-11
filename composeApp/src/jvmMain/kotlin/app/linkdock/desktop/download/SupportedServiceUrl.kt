package app.linkdock.desktop.download

import app.linkdock.desktop.domain.ServiceType
import java.net.URI

fun getUnsupportedServiceUrlMessage(service: ServiceType?, rawUrl: String): String? {
    val url = rawUrl.trim()

    if (service == null || url.isBlank()) {
        return null
    }

    val uri = runCatching { URI(url) }.getOrNull()
        ?: return "올바른 주소 형식이 아닙니다. 브라우저 주소창의 전체 주소를 붙여넣어 주세요."

    val scheme = uri.scheme?.lowercase()
    if (scheme != "http" && scheme != "https") {
        return "주소가 올바르지 않습니다. http:// 또는 https:// 로 시작하는 전체 주소를 넣어 주세요."
    }

    val host = uri.host?.lowercase()
        ?: return "올바른 주소 형식이 아닙니다. 브라우저 주소창의 전체 주소를 붙여넣어 주세요."

    val path = uri.path.orEmpty()

    return when (service) {
        ServiceType.ZAN -> when {
            host != "zan-live.com" && host != "www.zan-live.com" ->
                "ZAN 주소가 아닙니다. zan-live.com 주소를 넣어 주세요."

            path != "/live/play" && !path.startsWith("/live/play/") ->
                "이 주소는 ZAN 시청 페이지가 아닌 것 같습니다. 시청 페이지 주소를 넣어 주세요."

            else -> null
        }

        ServiceType.SPWN -> when {
            host != "spwn.jp" && host != "virtual.spwn.jp" ->
                "SPWN 주소가 아닙니다. spwn.jp 또는 virtual.spwn.jp 주소를 넣어 주세요."

            path != "/events" &&
                    !path.startsWith("/events/") &&
                    path != "/_events" &&
                    !path.startsWith("/_events/") ->
                "이 주소는 SPWN 이벤트 페이지가 아닌 것 같습니다. 이벤트 페이지 주소를 넣어 주세요."

            else -> null
        }
    }
}

fun getServiceUrlPlaceholder(service: ServiceType?): String = when (service) {
    ServiceType.ZAN -> "예: https://www.zan-live.com/live/play/..."
    ServiceType.SPWN -> "예: https://spwn.jp/events/..."
    null -> "서비스를 먼저 선택하세요"
}

fun getServiceUrlHintMessage(service: ServiceType?, rawUrl: String): String {
    val unsupportedMessage = getUnsupportedServiceUrlMessage(service, rawUrl)
    if (unsupportedMessage != null) {
        return unsupportedMessage
    }

    return when (service) {
        ServiceType.ZAN ->
            "ZAN은 시청 페이지 주소를 넣어 주세요. 예: zan-live.com/live/play/..."

        ServiceType.SPWN ->
            "SPWN은 이벤트 페이지 주소를 넣어 주세요. 예: spwn.jp/events/..."

        null ->
            "서비스를 먼저 선택한 뒤, 브라우저 주소창의 전체 주소를 붙여넣어 주세요."
    }
}