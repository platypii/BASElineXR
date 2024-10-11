let textTexture = null // Texture for rendering text
let textCanvas = null // Canvas for drawing text
let textCanvasCtx = null // Canvas context for drawing text
let textProgram = null // Shader program
let positionBuffer = null // Buffer for vertex positions
let texCoordBuffer = null // Buffer for texture coordinates

/**
 * Initializes text rendering resources.
 * @param {WebGLRenderingContext} gl - The WebGL rendering context.
 */
export function initTextRendering(gl) {
  console.log("Initializing text rendering")

  // Create a canvas to draw the text
  textCanvas = document.createElement('canvas')
  textCanvas.width = 1024
  textCanvas.height = 256
  textCanvasCtx = textCanvas.getContext('2d')

  // Create a texture for the text
  textTexture = gl.createTexture()
  gl.bindTexture(gl.TEXTURE_2D, textTexture)
  gl.texImage2D(
    gl.TEXTURE_2D,
    0,
    gl.RGBA,
    textCanvas.width,
    textCanvas.height,
    0,
    gl.RGBA,
    gl.UNSIGNED_BYTE,
    null
  )

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
    50, 250,
    900, 250,
    900, 50,
    50, 50,
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
}

/**
 * Updates the text canvas with the user's location or a default message.
 * @param {WebGLRenderingContext} gl - The WebGL rendering context.
 * @param {GeolocationCoordinates} userLocation - The user's geolocation coordinates.
 */
export function updateTextCanvas(gl, userLocation) {
  console.log("Updating text canvas with user location:", userLocation, textCanvasCtx)
  if (!textCanvasCtx) {
    initTextRendering(gl)
  }

  // Clear the canvas
  textCanvasCtx.clearRect(0, 0, textCanvas.width, textCanvas.height)

  // Set text properties
  const fontSize = 96
  textCanvasCtx.font = `${fontSize}px sans-serif`
  textCanvasCtx.fillStyle = 'white'
  textCanvasCtx.textAlign = 'center'
  textCanvasCtx.textBaseline = 'middle'

  // Draw the latitude and longitude or a default message
  let text = 'No GPS'
  if (userLocation) {
    text = `Lat: ${userLocation.latitude.toFixed(6)}\nLon: ${userLocation.longitude.toFixed(6)}`
  }
  const lines = text.split('\n')
  for (let i = 0; i < lines.length; i++) {
    textCanvasCtx.fillText(
      lines[i],
      textCanvas.width / 2,
      textCanvas.height / 2 + i * fontSize
    )
  }

  // Update the texture with the new text
  gl.bindTexture(gl.TEXTURE_2D, textTexture)
  gl.texImage2D(
    gl.TEXTURE_2D, 0, gl.RGBA, gl.RGBA, gl.UNSIGNED_BYTE, textCanvas
  )
}

/**
 * Draws the text texture onto the screen.
 * @param {WebGLRenderingContext} gl - The WebGL rendering context.
 * @param {number} width - The width of the rendering area.
 * @param {number} height - The height of the rendering area.
 */
export function drawTextTexture(gl, width, height) {
  if (!textProgram) return

  gl.useProgram(textProgram)

  // Enable blending for transparency
  gl.enable(gl.BLEND)
  gl.blendFunc(gl.SRC_ALPHA, gl.ONE_MINUS_SRC_ALPHA)

  // Set up the vertex positions
  const positionLocation = gl.getAttribLocation(textProgram, 'a_position')
  gl.enableVertexAttribArray(positionLocation)
  gl.bindBuffer(gl.ARRAY_BUFFER, positionBuffer)
  gl.vertexAttribPointer(positionLocation, 2, gl.FLOAT, false, 0, 0)

  // Set up the texture coordinates
  const texCoordLocation = gl.getAttribLocation(textProgram, 'a_texCoord')
  gl.enableVertexAttribArray(texCoordLocation)
  gl.bindBuffer(gl.ARRAY_BUFFER, texCoordBuffer)
  gl.vertexAttribPointer(texCoordLocation, 2, gl.FLOAT, false, 0, 0)

  // Set up the texture uniform
  const textureLocation = gl.getUniformLocation(textProgram, 'u_texture')
  gl.activeTexture(gl.TEXTURE0)
  gl.bindTexture(gl.TEXTURE_2D, textTexture)
  gl.uniform1i(textureLocation, 0)

  // Set up the projection matrix
  const resolutionLocation = gl.getUniformLocation(textProgram, 'u_resolution')
  gl.uniform2f(resolutionLocation, width, height)

  // Draw the quad
  gl.drawArrays(gl.TRIANGLE_FAN, 0, 4)

  // Disable blending and attribute arrays
  gl.disableVertexAttribArray(positionLocation)
  gl.disableVertexAttribArray(texCoordLocation)
  gl.disable(gl.BLEND)
}

/**
 * Resets the text rendering resources.
 */
export function resetTextRendering() {
  textTexture = null
  textCanvas = null
  textCanvasCtx = null
  textProgram = null
  positionBuffer = null
  texCoordBuffer = null
}

// Internal helper functions (not exported)

/**
 * Creates and compiles the shader program used for rendering the text.
 * @param {WebGLRenderingContext} gl - The WebGL rendering context.
 * @returns {WebGLProgram} - The compiled shader program.
 */
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

  // Check if the shader program was created successfully
  if (!gl.getProgramParameter(program, gl.LINK_STATUS)) {
    console.error('Unable to initialize the shader program:', gl.getProgramInfoLog(program))
    return null
  }

  return program
}

/**
 * Compiles a shader from the given source code.
 * @param {WebGLRenderingContext} gl - The WebGL rendering context.
 * @param {number} type - The type of shader (vertex or fragment).
 * @param {string} source - The shader source code.
 * @returns {WebGLShader} - The compiled shader.
 */
function compileShader(gl, type, source) {
  const shader = gl.createShader(type)
  gl.shaderSource(shader, source)
  gl.compileShader(shader)

  // Check if the shader was compiled successfully
  if (!gl.getShaderParameter(shader, gl.COMPILE_STATUS)) {
    console.error('An error occurred compiling the shaders:', gl.getShaderInfoLog(shader))
    gl.deleteShader(shader)
    return null
  }

  return shader
}
