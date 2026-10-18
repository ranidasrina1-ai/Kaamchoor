package com.kaamchor.ui.call

import android.content.Context
import org.webrtc.AudioSource
import org.webrtc.AudioTrack
import org.webrtc.Camera2Enumerator
import org.webrtc.CameraEnumerator
import org.webrtc.DataChannel
import org.webrtc.DefaultVideoDecoderFactory
import org.webrtc.DefaultVideoEncoderFactory
import org.webrtc.EglBase
import org.webrtc.IceCandidate
import org.webrtc.MediaConstraints
import org.webrtc.MediaStream
import org.webrtc.PeerConnection
import org.webrtc.PeerConnectionFactory
import org.webrtc.RtpReceiver
import org.webrtc.SdpObserver
import org.webrtc.SessionDescription
import org.webrtc.SurfaceTextureHelper
import org.webrtc.SurfaceViewRenderer
import org.webrtc.VideoCapturer
import org.webrtc.VideoSource
import org.webrtc.VideoTrack
import org.json.JSONArray
import org.json.JSONObject

/**
 * Native WebRTC mesh engine (1-on-1 and small groups, max 4 participants).
 * One [PeerConnection] per remote user; signaling is relayed by the backend
 * over Socket.IO.
 */
class WebRtcEngine(private val context: Context) {

    val eglBase: EglBase = EglBase.create()

    private lateinit var factory: PeerConnectionFactory
    private var audioSource: AudioSource? = null
    private var audioTrack: AudioTrack? = null
    private var videoSource: VideoSource? = null
    private var videoTrack: VideoTrack? = null
    private var videoCapturer: VideoCapturer? = null

    private val peers = mutableMapOf<Int, PeerConnection>()
    private var iceServers: List<PeerConnection.IceServer> = emptyList()

    var localRenderer: SurfaceViewRenderer? = null
    var remoteRenderer: SurfaceViewRenderer? = null

    var onIceCandidate: ((targetUserId: Int, candidate: JSONObject) -> Unit)? = null

    fun initialize(iceServerJson: List<Map<String, Any?>>) {
        PeerConnectionFactory.initialize(
            PeerConnectionFactory.InitializationOptions.builder(context.applicationContext)
                .createInitializationOptions()
        )

        val encoderFactory = DefaultVideoEncoderFactory(eglBase.eglBaseContext, true, true)
        val decoderFactory = DefaultVideoDecoderFactory(eglBase.eglBaseContext)
        factory = PeerConnectionFactory.builder()
            .setVideoEncoderFactory(encoderFactory)
            .setVideoDecoderFactory(decoderFactory)
            .createPeerConnectionFactory()

        iceServers = iceServerJson.mapNotNull { server ->
            val urls = server["urls"] ?: return@mapNotNull null
            val urlList = when (urls) {
                is List<*> -> urls.map { it.toString() }
                else -> listOf(urls.toString())
            }
            val builder = PeerConnection.IceServer.builder(urlList)
            (server["username"] as? String)?.let { builder.setUsername(it) }
            (server["credential"] as? String)?.let { builder.setPassword(it) }
            builder.createIceServer()
        }
    }

    fun createLocalMedia(withVideo: Boolean) {
        val constraints = MediaConstraints()
        audioSource = factory.createAudioSource(constraints)
        audioTrack = factory.createAudioTrack("audio0", audioSource)

        if (withVideo) {
            videoCapturer = createCameraCapturer(Camera2Enumerator(context))
            videoSource = factory.createVideoSource(false)
            videoCapturer?.initialize(
                SurfaceTextureHelper.create("CaptureThread", eglBase.eglBaseContext),
                context,
                videoSource!!.capturerObserver,
            )
            videoCapturer?.startCapture(1280, 720, 30)
            videoTrack = factory.createVideoTrack("video0", videoSource)

            localRenderer?.let {
                it.setMirror(true)
                videoTrack?.addSink(it)
            }
        }
    }

    private fun createCameraCapturer(enumerator: CameraEnumerator): VideoCapturer? {
        val names = enumerator.deviceNames
        for (name in names) {
            if (enumerator.isFrontFacing(name)) {
                enumerator.createCapturer(name, null)?.let { return it }
            }
        }
        for (name in names) {
            enumerator.createCapturer(name, null)?.let { return it }
        }
        return null
    }

    fun createPeer(targetUserId: Int): PeerConnection {
        peers[targetUserId]?.let { return it }
        val config = PeerConnection.RTCConfiguration(iceServers).apply {
            sdpSemantics = PeerConnection.SdpSemantics.UNIFIED_PLAN
        }
        val pc = factory.createPeerConnection(config, object : PeerConnection.Observer {
            override fun onIceCandidate(candidate: IceCandidate?) {
                candidate ?: return
                val obj = JSONObject().apply {
                    put("sdpMid", candidate.sdpMid)
                    put("sdpMLineIndex", candidate.sdpMLineIndex)
                    put("candidate", candidate.sdp)
                }
                onIceCandidate?.invoke(targetUserId, obj)
            }
            override fun onAddStream(stream: MediaStream?) {
                stream?.videoTracks?.firstOrNull()?.addSink(remoteRenderer)
            }
            override fun onAddTrack(receiver: RtpReceiver?, streams: Array<out MediaStream>?) {
                val track = receiver?.track()
                if (track is VideoTrack) {
                    remoteRenderer?.let {
                        track.addSink(it)
                    }
                }
            }
            override fun onSignalingChange(p0: PeerConnection.SignalingState?) {}
            override fun onIceConnectionChange(p0: PeerConnection.IceConnectionState?) {}
            override fun onIceConnectionReceivingChange(p0: Boolean) {}
            override fun onIceGatheringChange(p0: PeerConnection.IceGatheringState?) {}
            override fun onIceCandidatesRemoved(p0: Array<out IceCandidate>?) {}
            override fun onRemoveStream(p0: MediaStream?) {}
            override fun onDataChannel(p0: DataChannel?) {}
            override fun onRenegotiationNeeded() {}
        }) ?: error("Failed to create PeerConnection")

        audioTrack?.let { pc.addTrack(it, listOf("stream0")) }
        videoTrack?.let { pc.addTrack(it, listOf("stream0")) }
        peers[targetUserId] = pc
        return pc
    }

    fun createOffer(targetUserId: Int, onOffer: (JSONObject) -> Unit) {
        val pc = createPeer(targetUserId)
        pc.createOffer(object : SimpleSdpObserver() {
            override fun onCreateSuccess(sdp: SessionDescription?) {
                sdp ?: return
                pc.setLocalDescription(SimpleSdpObserver(), sdp)
                onOffer(sdpToJson(sdp))
            }
        }, MediaConstraints())
    }

    fun createAnswer(targetUserId: Int, onAnswer: (JSONObject) -> Unit) {
        val pc = createPeer(targetUserId)
        pc.createAnswer(object : SimpleSdpObserver() {
            override fun onCreateSuccess(sdp: SessionDescription?) {
                sdp ?: return
                pc.setLocalDescription(SimpleSdpObserver(), sdp)
                onAnswer(sdpToJson(sdp))
            }
        }, MediaConstraints())
    }

    fun setRemoteDescription(targetUserId: Int, sdpJson: JSONObject, isOffer: Boolean) {
        val pc = createPeer(targetUserId)
        val description = SessionDescription(
            if (isOffer) SessionDescription.Type.OFFER else SessionDescription.Type.ANSWER,
            sdpJson.optString("sdp"),
        )
        pc.setRemoteDescription(SimpleSdpObserver(), description)
    }

    fun addRemoteIceCandidate(targetUserId: Int, candidate: JSONObject) {
        val pc = peers[targetUserId] ?: return
        val ice = IceCandidate(
            candidate.optString("sdpMid"),
            candidate.optInt("sdpMLineIndex"),
            candidate.optString("candidate"),
        )
        pc.addIceCandidate(ice)
    }

    fun removePeer(targetUserId: Int) {
        peers.remove(targetUserId)?.let {
            it.close()
        }
    }

    fun switchCamera() {
        (videoCapturer as? org.webrtc.CameraVideoCapturer)?.switchCamera(null)
    }

    fun setMicEnabled(enabled: Boolean) = audioTrack?.setEnabled(enabled)

    fun setCameraEnabled(enabled: Boolean) = videoTrack?.setEnabled(enabled)

    fun dispose() {
        peers.values.forEach { runCatching { it.close() } }
        peers.clear()
        runCatching { videoCapturer?.stopCapture() }
        runCatching { videoCapturer?.dispose() }
        videoTrack?.dispose()
        videoSource?.dispose()
        audioTrack?.dispose()
        audioSource?.dispose()
        factory.dispose()
        eglBase.release()
    }

    private fun sdpToJson(sdp: SessionDescription): JSONObject = JSONObject().apply {
        put("type", sdp.type.canonicalForm())
        put("sdp", sdp.description)
    }
}

private open class SimpleSdpObserver : SdpObserver {
    override fun onCreateSuccess(p0: SessionDescription?) {}
    override fun onSetSuccess() {}
    override fun onCreateFailure(p0: String?) {}
    override fun onSetFailure(p0: String?) {}
}

fun JSONArrayToStringList(array: JSONArray?): List<String> {
    if (array == null) return emptyList()
    return (0 until array.length()).map { array.optString(it) }
}
