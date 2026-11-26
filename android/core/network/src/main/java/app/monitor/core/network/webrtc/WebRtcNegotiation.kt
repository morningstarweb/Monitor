package app.monitor.core.network.webrtc

import org.webrtc.IceCandidate
import org.webrtc.MediaConstraints
import org.webrtc.PeerConnection
import org.webrtc.SdpObserver
import org.webrtc.SessionDescription

internal fun WebRtcDataChannelClient.createAndSendOffer(viewerSessionId: String) {
    val pc = peerConnection ?: return
    val observer = object : SdpObserver {
        override fun onCreateSuccess(description: SessionDescription?) {
            description?.let { sdp ->
                pc.setLocalDescription(object : SdpObserver {
                    override fun onSetSuccess() {
                        sendSignal(WebRtcSignalMessage(
                            signal = "offer", viewerSessionId = viewerSessionId,
                            sdp = sdp.description, sdp_type = "offer"
                        ))
                    }
                    override fun onSetFailure(error: String?) {}
                    override fun onCreateSuccess(value: SessionDescription?) {}
                    override fun onCreateFailure(error: String?) {}
                }, sdp)
            }
        }
        override fun onSetSuccess() {}
        override fun onCreateFailure(error: String?) {}
        override fun onSetFailure(error: String?) {}
    }
    pc.createOffer(observer, MediaConstraints())
}

/** NET-03: renegotiate with iceRestart after a network change, keeping the
 * application session and encrypted transport alive. */
internal fun WebRtcDataChannelClient.createAndSendIceRestartOffer(viewerSessionId: String) {
    val pc = peerConnection ?: return
    val constraints = MediaConstraints().apply {
        mandatory.add(MediaConstraints.KeyValuePair("IceRestart", "true"))
    }
    val observer = object : SdpObserver {
        override fun onCreateSuccess(description: SessionDescription?) {
            description?.let { sdp ->
                pc.setLocalDescription(object : SdpObserver {
                    override fun onSetSuccess() {
                        sendSignal(WebRtcSignalMessage(
                            signal = "offer", viewerSessionId = viewerSessionId,
                            sdp = sdp.description, sdp_type = "offer"
                        ))
                    }
                    override fun onSetFailure(error: String?) {}
                    override fun onCreateSuccess(value: SessionDescription?) {}
                    override fun onCreateFailure(error: String?) {}
                }, sdp)
            }
        }
        override fun onSetSuccess() {}
        override fun onCreateFailure(error: String?) {}
        override fun onSetFailure(error: String?) {}
    }
    pc.createOffer(observer, constraints)
}

internal fun WebRtcDataChannelClient.handleRemoteAnswer(sdpDescription: String) {
    val pc = peerConnection ?: return
    val answer = SessionDescription(SessionDescription.Type.ANSWER, sdpDescription)
    pc.setRemoteDescription(object : SdpObserver {
        override fun onSetSuccess() {}
        override fun onSetFailure(error: String?) {}
        override fun onCreateSuccess(value: SessionDescription?) {}
        override fun onCreateFailure(error: String?) {}
    }, answer)
}

internal fun WebRtcDataChannelClient.handleRemoteCandidate(candidatePayload: IceCandidatePayload) {
    val candidate = IceCandidate(
        candidatePayload.sdpMid,
        candidatePayload.sdpMLineIndex,
        candidatePayload.candidate
    )
    peerConnection?.addIceCandidate(candidate)
}
