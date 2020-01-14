package tdv.dg.boostertrackerservice

import kotlinx.serialization.Serializable

@Serializable
internal class ServiceTrackableObject {
    val productId: String
    val duration: Int?

    constructor(id: String, duration: Int?) {
        this.productId = id
        this.duration = duration
    }

    var elapsedMilliseconds: Long = 0


}
