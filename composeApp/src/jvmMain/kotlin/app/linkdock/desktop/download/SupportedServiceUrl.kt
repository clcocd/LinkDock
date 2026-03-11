package app.linkdock.desktop.download

import app.linkdock.desktop.domain.ServiceType
import java.net.URI

fun getUnsupportedServiceUrlMessage(service: ServiceType?, rawUrl: String): String? {
    val url = rawUrl.trim()

    if (service == null || url.isBlank()) {
        return null
    }

    val uri = runCatching { URI(url) }.getOrNull()
        ?: return "올바른 URL 형식이 아닙니다. 브라우저 주소창의 전체 주소를 붙여넣어 주세요."

    val scheme = uri.scheme?.lowercase()
    if (scheme != "http" && scheme != "https") {
        return "올바른 URL 형식이 아닙니다. http:// 또는 https:// 로 시작하는 전체 주소를 붙여넣어 주세요."
    }

    val host = uri.host?.lowercase()
        ?: return "올바른 URL 형식이 아닙니다. 브라우저 주소창의 전체 주소를 붙여넣어 주세요."

    val path = uri.path.orEmpty()

    return when (service) {
        ServiceType.ZAN -> when {
            host != "zan-live.com" && host != "www.zan-live.com" ->
                "현재 선택한 서비스는 ZAN입니다. zan-live.com 주소만 사용할 수 있습니다."

            path != "/live/play" && !path.startsWith("/live/play/") ->
                "Streamlink가 지원하는 ZAN 주소가 아닙니다. /live/play 로 시작하는 시청 페이지 URL을 넣어 주세요."

            else -> null
        }

        ServiceType.SPWN -> when {
            host != "spwn.jp" && host != "virtual.spwn.jp" ->
                "현재 선택한 서비스는 SPWN입니다. spwn.jp 또는 virtual.spwn.jp 주소만 사용할 수 있습니다."

            path != "/events" &&
                    !path.startsWith("/events/") &&
                    path != "/_events" &&
                    !path.startsWith("/_events/") ->
                "Streamlink가 지원하는 SPWN 주소가 아닙니다. /events/ 또는 /_events/ 로 시작하는 이벤트 페이지 URL을 넣어 주세요."

            else -> null
        }
    }
}

fun getServiceUrlPlaceholder(service: ServiceType?): String = when (service) {
    ServiceType.ZAN -> "예: https://www.zan-live.com/live/play/..."
    ServiceType.SPWN -> "예: https://spwn.jp/events/..."
    null -> "서비스를 먼저 선택하세요"
}

fun getServiceUrlGuideText(service: ServiceType?, rawUrl: String): String {
    val unsupportedMessage = getUnsupportedServiceUrlMessage(service, rawUrl)
    if (unsupportedMessage != null) {
        return unsupportedMessage
    }

    return when (service) {
        ServiceType.ZAN ->
            "ZAN은 Streamlink가 지원하는 시청 페이지 URL만 사용할 수 있습니다."

        ServiceType.SPWN ->
            "SPWN은 Streamlink가 지원하는 이벤트 페이지 URL만 사용할 수 있습니다."

        null ->
            "서비스를 먼저 선택한 뒤, 브라우저 주소창의 전체 URL을 붙여넣어 주세요."
    }
}