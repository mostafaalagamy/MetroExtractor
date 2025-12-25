package project.pipepipe.extractor

import project.pipepipe.extractor.base.CookieExtractor
import project.pipepipe.shared.infoitem.SupportedServiceInfo

abstract class StreamingService(val serviceId: Int) {
    abstract val serviceInfo: SupportedServiceInfo

    abstract suspend fun getCookieExtractor(): CookieExtractor
    abstract fun route(url: String): Extractor<*,*>?
}
