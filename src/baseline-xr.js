let xrButton = document.getElementById('xr-button')
let xrSession = null
let xrRefSpace = null
let gl = null // WebGL context
let userLocation = null
let textTexture = null // Texture for rendering text
let textCanvas = null // Canvas for drawing text
let textCanvasCtx = null // Canvas context for drawing text

// Variables for shaders and buffers
let textProgram = null
let positionBuffer = null
let texCoordBuffer = null

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
    initTextRendering() // Initialize text rendering resources
    session.requestAnimationFrame(onXRFrame)
  })
}

function onLocationReceived(position) {
  userLocation = position.coords
  updateTextCanvas() // Update the text canvas when location is received
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
  textTexture = null
  textCanvas = null
  textCanvasCtx = null
}

function initTextRendering() {
  console.log("Initializing text rendering")
  // Create a canvas to draw the text
  textCanvas = document.createElement('canvas')
  textCanvas.width = 1024
  textCanvas.height = 256
  textCanvasCtx = textCanvas.getContext('2d')

  // Create a texture for the text
  textTexture = gl.createTexture()
  gl.bindTexture(gl.TEXTURE_2D, textTexture)
  gl.texImage2D(gl.TEXTURE_2D, 0, gl.RGBA, textCanvas.width, textCanvas.height, 0, gl.RGBA, gl.UNSIGNED_BYTE, null)

  // Set up texture parameters
  gl.texParameteri(gl.TEXTURE_2D, gl.TEXTURE_WRAP_S, gl.CLAMP_TO_EDGE)
  gl.texParameteri(gl.TEXTURE_2D, gl.TEXTURE_WRAP_T, gl.CLAMP_TO_EDGE)
  gl.texParameteri(gl.TEXTURE_2D, gl.TEXTURE_MIN_FILTER, gl.LINEAR)
  gl.texParameteri(gl.TEXTURE_2D, gl.TEXTURE_MAG_FILTER, gl.LINEAR)

  // Compile shaders and create program
  textProgram = createTextProgram(gl)

  // Create buffers
  positionBuffer = gl.createBuffer()
  texCoordBuffer = gl.createBuffer()

  // Set up the quad positions (screen coordinates)
  const positions = [
    50,  250,
    900, 250,
    900, 50,
    50,  50,
  ]

  gl.bindBuffer(gl.ARRAY_BUFFER, positionBuffer)
  gl.bufferData(gl.ARRAY_BUFFER, new Float32Array(positions), gl.STATIC_DRAW)

  // Set up the texture coordinates
  const texCoords = [
    0, 1,
    1, 1,
    1, 0,
    0, 0,
  ]

  gl.bindBuffer(gl.ARRAY_BUFFER, texCoordBuffer)
  gl.bufferData(gl.ARRAY_BUFFER, new Float32Array(texCoords), gl.STATIC_DRAW)

  updateTextCanvas()
}

function updateTextCanvas() {
  console.log("Updating text canvas with user location:", userLocation, textCanvasCtx)
  if (!textCanvasCtx) return

  // Clear the canvas
  textCanvasCtx.clearRect(0, 0, textCanvas.width, textCanvas.height)

  // Set text properties
  const fontSize = 96
  textCanvasCtx.font = `${fontSize}px sans-serif`
  textCanvasCtx.fillStyle = 'white'
  textCanvasCtx.textAlign = 'center'
  textCanvasCtx.textBaseline = 'middle'

  // Draw the latitude and longitude
  let text = 'No GPS'
  if (userLocation) {
    text = `Lat: ${userLocation.latitude.toFixed(6)}\nLon: ${userLocation.longitude.toFixed(6)}`
  }
  let lines = text.split('\n')
  for (let i = 0; i < lines.length; i++) {
    textCanvasCtx.fillText(lines[i], textCanvas.width / 2, textCanvas.height / 2 + i * fontSize)
  }

  // Update the texture with the new text
  gl.bindTexture(gl.TEXTURE_2D, textTexture)
  gl.texImage2D(gl.TEXTURE_2D, 0, gl.RGBA, gl.RGBA, gl.UNSIGNED_BYTE, textCanvas)
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
  if (textTexture) {
    drawTextTexture(width, height)
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

  // Display the user's location in the DOM (optional)
  if (userLocation) {
    document.getElementById('location').innerText = "Location: Latitude " +
      userLocation.latitude.toFixed(6) + ", Longitude " + userLocation.longitude.toFixed(6)
  } else {
    document.getElementById('location').innerText = "Location: (unknown)"
  }
}

function drawTextTexture(width, height) {
  gl.useProgram(textProgram)

  // Enable blending for transparency
  gl.enable(gl.BLEND)
  gl.blendFunc(gl.SRC_ALPHA, gl.ONE_MINUS_SRC_ALPHA)

  // Set up the vertex positions
  const positionLocation = gl.getAttribLocation(textProgram, "a_position")
  gl.enableVertexAttribArray(positionLocation)
  gl.bindBuffer(gl.ARRAY_BUFFER, positionBuffer)
  gl.vertexAttribPointer(positionLocation, 2, gl.FLOAT, false, 0, 0)

  // Set up the texture coordinates
  const texCoordLocation = gl.getAttribLocation(textProgram, "a_texCoord")
  gl.enableVertexAttribArray(texCoordLocation)
  gl.bindBuffer(gl.ARRAY_BUFFER, texCoordBuffer)
  gl.vertexAttribPointer(texCoordLocation, 2, gl.FLOAT, false, 0, 0)

  // Set up the texture uniform
  const textureLocation = gl.getUniformLocation(textProgram, "u_texture")
  gl.activeTexture(gl.TEXTURE0)
  gl.bindTexture(gl.TEXTURE_2D, textTexture)
  gl.uniform1i(textureLocation, 0)

  // Update the texture in case the text has changed
  gl.texImage2D(gl.TEXTURE_2D, 0, gl.RGBA, gl.RGBA, gl.UNSIGNED_BYTE, textCanvas)

  // Set up the projection matrix
  const resolutionLocation = gl.getUniformLocation(textProgram, "u_resolution")
  gl.uniform2f(resolutionLocation, width, height)

  // Draw the quad
  gl.drawArrays(gl.TRIANGLE_FAN, 0, 4)

  // Disable blending and attribute arrays
  gl.disableVertexAttribArray(positionLocation)
  gl.disableVertexAttribArray(texCoordLocation)
  gl.disable(gl.BLEND)
}

function createTextProgram(gl) {
  // Vertex shader
  const vsSource = `
    attribute vec2 a_position;
    attribute vec2 a_texCoord;
    uniform vec2 u_resolution;
    varying vec2 v_texCoord;

    void main() {
      // Convert the rectangle from pixels to 0.0 to 1.0
      vec2 zeroToOne = a_position / u_resolution;

      // Convert from 0 -> 1 to 0 -> 2
      vec2 zeroToTwo = zeroToOne * 2.0;

      // Convert from 0 -> 2 to -1 -> +1 (clipspace)
      vec2 clipSpace = zeroToTwo - 1.0;

      gl_Position = vec4(clipSpace * vec2(1, -1), 0, 1);
      v_texCoord = a_texCoord;
    }
  `

  // Fragment shader
  const fsSource = `
    precision mediump float;
    varying vec2 v_texCoord;
    uniform sampler2D u_texture;

    void main() {
      gl_FragColor = texture2D(u_texture, v_texCoord);
    }
  `

  const vertexShader = compileShader(gl, gl.VERTEX_SHADER, vsSource)
  const fragmentShader = compileShader(gl, gl.FRAGMENT_SHADER, fsSource)
  const program = gl.createProgram()

  gl.attachShader(program, vertexShader)
  gl.attachShader(program, fragmentShader)
  gl.linkProgram(program)

  if (!gl.getProgramParameter(program, gl.LINK_STATUS)) {
    console.error('Unable to initialize the shader program:', gl.getProgramInfoLog(program))
    return null
  }

  return program
}

function compileShader(gl, type, source) {
  const shader = gl.createShader(type)
  gl.shaderSource(shader, source)
  gl.compileShader(shader)

  if (!gl.getShaderParameter(shader, gl.COMPILE_STATUS)) {
    console.error('An error occurred compiling the shaders:', gl.getShaderInfoLog(shader))
    gl.deleteShader(shader)
    return null
  }

  return shader
}

initXR()
