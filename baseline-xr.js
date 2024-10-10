let xrButton = document.getElementById('xr-button')
let xrSession = null
let xrRefSpace = null
let gl = null // webgl scene
let userLocation = null // To store user's location
let textTexture = null // Texture for rendering text
let textCanvas = null // Canvas for drawing text
let textCanvasCtx = null // Canvas context for drawing text

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

  // Request user's location
  if (navigator.geolocation) {
    navigator.geolocation.getCurrentPosition(onLocationReceived, onLocationError)
  } else {
    console.error("Geolocation is not supported by this browser.")
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

function onLocationReceived(position) {
  userLocation = position.coords
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

function updateTextCanvas() {
  if (!textCanvasCtx) return

  // Clear the canvas
  textCanvasCtx.clearRect(0, 0, textCanvas.width, textCanvas.height)

  // Set text properties
  textCanvasCtx.font = '48px sans-serif'
  textCanvasCtx.fillStyle = 'white'
  textCanvasCtx.textAlign = 'center'
  textCanvasCtx.textBaseline = 'middle'

  // Draw the latitude and longitude
  let text = `Lat: ${userLocation.latitude.toFixed(6)}\nLon: ${userLocation.longitude.toFixed(6)}`
  let lines = text.split('\n')
  for (let i = 0; i < lines.length; i++) {
    textCanvasCtx.fillText(lines[i], textCanvas.width / 2, textCanvas.height / 2 + i * 50)
  }

  // Update the texture with the new text
  gl.bindTexture(gl.TEXTURE_2D, textTexture)
  gl.texImage2D(gl.TEXTURE_2D, 0, gl.RGBA, gl.RGBA, gl.UNSIGNED_BYTE, textCanvas)
}

function drawTextTexture() {
  // Set up the viewport and orthographic projection
  gl.viewport(0, 0, gl.drawingBufferWidth, gl.drawingBufferHeight)
  gl.matrixMode(gl.PROJECTION)
  gl.loadIdentity()
  gl.ortho(0, gl.drawingBufferWidth, 0, gl.drawingBufferHeight, -1, 1)
  gl.matrixMode(gl.MODELVIEW)
  gl.loadIdentity()

  // Enable blending for transparency
  gl.enable(gl.BLEND)
  gl.blendFunc(gl.SRC_ALPHA, gl.ONE_MINUS_SRC_ALPHA)

  // Bind the text texture
  gl.bindTexture(gl.TEXTURE_2D, textTexture)
  gl.enable(gl.TEXTURE_2D)

  // Update the texture in case the text has changed
  gl.texImage2D(gl.TEXTURE_2D, 0, gl.RGBA, gl.RGBA, gl.UNSIGNED_BYTE, textCanvas)

  // Draw a quad with the text texture
  gl.begin(gl.QUADS)

  // Set texture coordinates and vertex positions
  gl.texCoord2f(0.0, 1.0)
  gl.vertex2f(50, 50)

  gl.texCoord2f(1.0, 1.0)
  gl.vertex2f(450, 50)

  gl.texCoord2f(1.0, 0.0)
  gl.vertex2f(450, 250)

  gl.texCoord2f(0.0, 0.0)
  gl.vertex2f(50, 250)

  gl.end()

  // Disable textures and blending
  gl.disable(gl.TEXTURE_2D)
  gl.disable(gl.BLEND)
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

  // Disable scissor test after rendering
  gl.disable(gl.SCISSOR_TEST)

  // Draw the text texture
  if (textTexture && userLocation) {
    drawTextTexture()
  }

  // Display the viewer's pose
  let pose = frame.getViewerPose(xrRefSpace)
  if (pose) {
    const p = pose.transform.position
    document.getElementById('pose').innerText = "Position: " +
      p.x.toFixed(3) + ", " + p.y.toFixed(3) + ", " + p.z.toFixed(3)
  } else {
    document.getElementById('pose').innerText = "Position: (null pose)"
  }

  // Display the user's location
  if (userLocation) {
    document.getElementById('location').innerText = "Location: Latitude " +
        userLocation.latitude.toFixed(6) + ", Longitude " + userLocation.longitude.toFixed(6)
  } else {
    document.getElementById('location').innerText = "Location: (unknown)"
  }
}

initXR()
