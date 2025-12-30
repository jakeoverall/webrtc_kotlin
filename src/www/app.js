let username = ''
let roomId = null;
let localStream = null;
let isCaller = false;
let joined = false;
let selectedPeer = null;

let pendingIceCandidates = [];
let remoteDescriptionSet = false;


let pc = new RTCPeerConnection();
const usernameElem = document.getElementById('username')
const localVideo = document.getElementById("localVideo");
const remoteVideo = document.getElementById("remoteVideo");
const status = document.getElementById("status");
const roomBadge = document.getElementById("roomBadge");
const offcanvas = document.getElementById("offcanvas");

const wsProtocol = location.protocol === "https:" ? "wss" : "ws";
const ws = new WebSocket(`${wsProtocol}://${location.host}/signal`);

usernameElem.addEventListener('change', (e) => {
    username = event.target.value
})

async function getLocalStream() {
    if (localStream) return;
    localStream = await navigator.mediaDevices.getUserMedia({audio: true, video: true});
    localVideo.srcObject = localStream;
    localStream.getTracks().forEach(t => pc.addTrack(t, localStream));
}

document.getElementById("menuBtn").onclick = () => {
    offcanvas.classList.toggle("closed");
};

document.getElementById("createRoom").onclick = () => {
    roomId = Math.random().toString(36).substring(2, 8).toUpperCase();
    alert(`Room created: ${roomId}`);
    joinRoom();
};

document.getElementById("joinRoom").onclick = () => {
    roomId = document.getElementById("roomInput").value.toUpperCase();
    joinRoom();
};

async function joinRoom() {
    if (!username) return
    await getLocalStream();
    ws.send(JSON.stringify({type: "join", roomId, from: username}));
    roomBadge.innerText = `Room: ${roomId}`;
    // offcanvas.classList.add("closed");
}


ws.onmessage = async (msg) => {
    const data = JSON.parse(msg.data);

    switch (data.type) {
        case "joined":
            isCaller = data.role === "caller";
            joined = true;
            status.innerText = `Connected (${data.role})`;

            if (isCaller && data.peers.length > 0) {
                selectedPeer = data.peers[0];
                await createAndSendOffer();
            }

            break;

        case "offer":
            await pc.setRemoteDescription(JSON.parse(data.payload));
            remoteDescriptionSet = true;

            // 🔥 Flush buffered ICE
            for (const c of pendingIceCandidates) {
                await pc.addIceCandidate(c);
            }
            pendingIceCandidates = [];

            const answer = await pc.createAnswer();
            await pc.setLocalDescription(answer);

            ws.send(JSON.stringify({
                type: "answer",
                roomId,
                payload: JSON.stringify(answer)
            }));
            break;

        case "answer":
            await pc.setRemoteDescription(JSON.parse(data.payload));
            remoteDescriptionSet = true;

            // 🔥 Flush buffered ICE
            for (const c of pendingIceCandidates) {
                await pc.addIceCandidate(c);
            }
            pendingIceCandidates = [];
            break;

        case "ice":
            const candidate = JSON.parse(data.payload);

            if (remoteDescriptionSet) {
                await pc.addIceCandidate(candidate);
            } else {
                pendingIceCandidates.push(candidate);
            }
            break;

        case "user-list":
            const userList = document.getElementById("userList");
            userList.innerHTML = "";
            data.users.forEach(u => {
                const li = document.createElement("li");
                li.innerText = u;
                userList.appendChild(li);
            });
            break;

        case "error":
            status.innerText = data.message
            break;

        case "peer-joined":
            if (isCaller) {
                console.log("Peer joined — creating offer");
                const offer = await pc.createOffer();
                await pc.setLocalDescription(offer);

                ws.send(JSON.stringify({
                    type: "offer",
                    roomId,
                    payload: JSON.stringify(offer)
                }));
            }
            break;

        case "peer-left":
            resetPeerConnection();
            if(!isCaller){
                isCaller = true
            }
            remoteVideo.srcObject = null;
            break;
    }
};

async function createAndSendOffer() {
    if (!username) return
    await getLocalStream();
    const offer = await pc.createOffer();
    await pc.setLocalDescription(offer);
    ws.send(JSON.stringify({
        type: "offer",
        from: username,
        to: selectedPeer,
        roomId,
        payload: JSON.stringify(offer)
    }));
}


pc.onicecandidate = e => {
    if (!e.candidate) return;

    ws.send(JSON.stringify({
        type: "ice",
        roomId,
        payload: JSON.stringify(e.candidate)
    }));
};


pc.ontrack = e => {
    remoteVideo.srcObject = e.streams[0];
};

document.getElementById("toggleAudio").onclick = () => {
    localStream.getAudioTracks().forEach(t => t.enabled = !t.enabled);
};

document.getElementById("toggleVideo").onclick = () => {
    localStream.getVideoTracks().forEach(t => t.enabled = !t.enabled);
};

const videoSelect = document.getElementById("videoSelect");
const audioSelect = document.getElementById("audioSelect");

async function loadDevices() {
    const devices = await navigator.mediaDevices.enumerateDevices();

    videoSelect.innerHTML = "";
    audioSelect.innerHTML = "";

    devices.forEach(d => {
        const option = document.createElement("option");
        option.value = d.deviceId;
        option.text = d.label || d.kind;

        if (d.kind === "videoinput") videoSelect.appendChild(option);
        if (d.kind === "audioinput") audioSelect.appendChild(option);
    });
}

async function replaceTrack(kind, deviceId) {
    const constraints =
        kind === "video"
            ? {video: {deviceId}}
            : {audio: {deviceId}};

    const newStream = await navigator.mediaDevices.getUserMedia(constraints);
    const newTrack = newStream.getTracks()[0];

    const sender = pc.getSenders().find(s => s.track?.kind === kind);
    if (sender) sender.replaceTrack(newTrack);

    localStream.removeTrack(localStream.getTracks().find(t => t.kind === kind));
    localStream.addTrack(newTrack);
    localVideo.srcObject = localStream;
}

videoSelect.onchange = () => replaceTrack("video", videoSelect.value);
audioSelect.onchange = () => replaceTrack("audio", audioSelect.value);

function resetPeerConnection() {
    pc.close();
    pc = new RTCPeerConnection();

    remoteDescriptionSet = false;
    pendingIceCandidates = [];

    pc.onicecandidate = e => {
        if (e.candidate) {
            ws.send(JSON.stringify({
                type: "ice",
                roomId,
                payload: JSON.stringify(e.candidate)
            }));
        }
    };

    pc.ontrack = e => {
        remoteVideo.srcObject = e.streams[0];
    };

    if (localStream) {
        localStream.getTracks().forEach(t => pc.addTrack(t, localStream));
    }

    status.innerText = `Connection Reset`;


}

loadDevices()