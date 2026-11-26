package app.monitor.core.network.webrtc

import app.monitor.core.network.ConnectionPath
import org.webrtc.PeerConnection

internal fun inspectWebRtcPath(
    peer: PeerConnection,
    publish: (ConnectionPath) -> Unit
) {
    peer.getStats { report ->
        val stats = report.statsMap.values
        val transport = stats.firstOrNull { it.type == "transport" }
        val selectedId = transport?.members?.get("selectedCandidatePairId") as? String
        val pair = stats.firstOrNull { it.id == selectedId }
            ?: stats.firstOrNull { it.type == "candidate-pair" && it.members["selected"] == true }
        val localId = pair?.members?.get("localCandidateId") as? String
        val localCandidate = stats.firstOrNull { it.id == localId }
        val relayed = localCandidate?.members?.get("candidateType") == "relay"
        publish(if (relayed) ConnectionPath.WEBRTC_TURN else ConnectionPath.WEBRTC_P2P)
    }
}
