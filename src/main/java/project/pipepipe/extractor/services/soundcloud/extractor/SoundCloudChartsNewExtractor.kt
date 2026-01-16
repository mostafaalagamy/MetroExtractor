package project.pipepipe.extractor.services.soundcloud.extractor

import project.pipepipe.extractor.Extractor
import project.pipepipe.shared.infoitem.TrendingInfo

class SoundCloudChartsNewExtractor(
    url: String,
) : Extractor<TrendingInfo, Nothing>(url) {
    override suspend fun fetchInfo(
        sessionId: String,
        currentState: project.pipepipe.shared.state.State?,
        clientResults: List<project.pipepipe.shared.job.TaskResult>?,
        cookie: String?
    ): project.pipepipe.shared.job.JobStepResult {
        TODO("Not yet implemented")
    }
}
