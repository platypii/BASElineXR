let xrButton = document.getElementById('xr-button')
let xrSession = null
let xrRefSpace = null
let gl = null // webgl scene

function checkSupportedState() {
  navigator.xr.isSessionSupported('immersive-ar').then((supported) => {
    if (supported) {
      xrButton.innerHTML = 'Enter AR'
    } else {
      xrButton.innerHTML = 'AR not found'
    }

    xrButton.disabled = !supported
  })
}

function initXR() {
  if (!window.isSecureContext) {
    let message = "WebXR unavailable due to insecure context"
    document.getElementById("warning-zone").innerText = message
  }

  if (navigator.xr) {
    xrButton.addEventListener('click', onButtonClicked)
    navigator.xr.addEventListener('devicechange', checkSupportedState)
    checkSupportedState()
  }
}

function onButtonClicked() {
  if (!xrSession) {
      // Ask for an optional DOM Overlay, see https://immersive-web.github.io/dom-overlays/
      navigator.xr.requestSession('immersive-ar', {
          optionalFeatures: ['dom-overlay'],
          domOverlay: {root: document.getElementById('overlay')}
      }).then(onSessionStarted, onRequestSessionError)
  } else {
    xrSession.end()
  }
}

function onSessionStarted(session) {
  xrSession = session
  xrButton.innerHTML = 'Exit AR'

  // Show which type of DOM Overlay got enabled (if any)
  if (session.domOverlayState) {
    document.getElementById('session-info').innerHTML = 'DOM Overlay type: ' + session.domOverlayState.type
  }

  session.addEventListener('end', onSessionEnded)
  let canvas = document.createElement('canvas')
  gl = canvas.getContext('webgl', {
    xrCompatible: true
  })
  session.updateRenderState({ baseLayer: new XRWebGLLayer(session, gl) })
  session.requestReferenceSpace('local').then((refSpace) => {
    xrRefSpace = refSpace
    session.requestAnimationFrame(onXRFrame)
  })
}

function onRequestSessionError(ex) {
  alert("Failed to start immersive AR session.")
  console.error(ex.message)
}

function onEndSession(session) {
  session.end()
}

function onSessionEnded(event) {
  xrSession = null
  xrButton.innerHTML = 'Enter AR'
  document.getElementById('session-info').innerHTML = ''
  gl = null
}

function onXRFrame(t, frame) {
  let session = frame.session
  session.requestAnimationFrame(onXRFrame)

  gl.bindFramebuffer(gl.FRAMEBUFFER, session.renderState.baseLayer.framebuffer)

  const width = session.renderState.baseLayer.framebufferWidth
  const height = session.renderState.baseLayer.framebufferHeight

  // Clear the entire view to green first to create the border
  gl.disable(gl.SCISSOR_TEST) // Disable scissor test to clear the whole view
  gl.clearColor(0, 1, 0, 1) // Green color for the border
  gl.clear(gl.COLOR_BUFFER_BIT | gl.DEPTH_BUFFER_BIT)

  // Now enable scissor test to draw the AR scene within the center
  gl.enable(gl.SCISSOR_TEST)
  gl.scissor(width / 4, height / 4, width / 2, height / 2) // AR scene in the center
  let time = Date.now()
  gl.clearColor(Math.cos(time / 2000), Math.cos(time / 4000), Math.cos(time / 6000), 0.5) // Color-changing AR content
  gl.clear(gl.COLOR_BUFFER_BIT | gl.DEPTH_BUFFER_BIT)

  let pose = frame.getViewerPose(xrRefSpace)
  if (pose) {
    const p = pose.transform.position
    document.getElementById('pose').innerText = "Position: " +
      p.x.toFixed(3) + ", " + p.y.toFixed(3) + ", " + p.z.toFixed(3)
  } else {
    document.getElementById('pose').innerText = "Position: (null pose)"
  }
}

initXR()
