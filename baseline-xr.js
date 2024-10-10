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
  const lineThickness = 2 // Set the grid line thickness in pixels
  const gridSpacing = 200 // Set the spacing between grid lines
  const transparency = 0.4 // Set the transparency of the grid lines

  // Enable scissor test to draw the grid lines
  gl.enable(gl.SCISSOR_TEST)

  // Draw vertical grid lines
  for (let x = 0; x < width; x += gridSpacing) {
    gl.scissor(x, 0, lineThickness, height)
    gl.clearColor(0, 1, 0, transparency) // Semi-transparent green color
    gl.clear(gl.COLOR_BUFFER_BIT)
  }

  // Draw horizontal grid lines
  for (let y = 0; y < height; y += gridSpacing) {
    gl.scissor(0, y, width, lineThickness)
    gl.clear(gl.COLOR_BUFFER_BIT)
  }

  // Now draw the AR scene in the center (without the grid covering it)
  // gl.scissor(0, 0, width, height) // Reset scissor to cover the entire viewport
  // let time = Date.now()
  // gl.clearColor(Math.cos(time / 2000), Math.cos(time / 4000), Math.cos(time / 6000), 0.5) // Color-changing AR content
  // gl.clear(gl.COLOR_BUFFER_BIT | gl.DEPTH_BUFFER_BIT)

  // Disable scissor test after rendering
  gl.disable(gl.SCISSOR_TEST)

  // Display the viewer's pose
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
