import { drawTextTexture, updateTextCanvas } from './canvas.js'

let xrButton = /** @type {HTMLButtonElement} */ (document.getElementById('xr-button'))
let xrSession = null
let xrRefSpace = null
let gl = null // WebGL context
let userLocation = null

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

  // Request user's location on page load
  if (navigator.geolocation) {
    navigator.geolocation.getCurrentPosition(onLocationReceived, onLocationError)
  } else {
    console.error("Geolocation is not supported by this browser.")
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
      domOverlay: { root: document.getElementById('overlay') }
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
    updateTextCanvas(gl, userLocation)
    session.requestAnimationFrame(onXRFrame)
  })
}

function onLocationReceived(position) {
  userLocation = position.coords
  if (gl) {
    updateTextCanvas(gl, userLocation) // Update the text canvas when location is received
  }
}

function onLocationError(error) {
  console.error("Error getting location:", error)
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
  const lineThickness = 1 // Set the grid line thickness in pixels
  const gridSpacing = 500 // Set the spacing between grid lines
  const transparency = 0.3 // Set the transparency of the grid lines

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

  // Disable scissor test after rendering
  gl.disable(gl.SCISSOR_TEST)

  // Draw the text texture
  drawTextTexture(gl, width, height)

  // Display the viewer's pose
  let pose = frame.getViewerPose(xrRefSpace)
  if (pose) {
    const p = pose.transform.position
    document.getElementById('pose').innerText = "Position: " +
      p.x.toFixed(3) + ", " + p.y.toFixed(3) + ", " + p.z.toFixed(3)
  } else {
    document.getElementById('pose').innerText = "Position: (null pose)"
  }

  // Display the user's location in the DOM (optional)
  if (userLocation) {
    document.getElementById('location').innerText = "Location: Latitude " +
      userLocation.latitude.toFixed(6) + ", Longitude " + userLocation.longitude.toFixed(6)
  } else {
    document.getElementById('location').innerText = "Location: (unknown)"
  }
}

initXR()
