const mohawkServiceUUID = 'ba5e0001-da9b-4622-b128-1e4f5022ab01'
const mohawkCharacteristicUUID = 'ba5e0002-ad0c-4fe2-af23-55995ce8eb02'

export function initBluetooth() {
  if ('bluetooth' in navigator) {
    navigator.bluetooth.getAvailability().then((isBluetoothAvailable) => {
      if (isBluetoothAvailable) {
        console.log('Bluetooth is available')
        connectMohawkGPS()
      } else {
        console.error('Bluetooth is not available')
      }
    })
  } else {
    console.error('Bluetooth is not supported')
  }
}

// Function to initiate connection with the Mohawk Bluetooth GPS device
async function connectMohawkGPS() {
  try {
    console.log('Requesting Bluetooth device...')
    const device = await navigator.bluetooth.requestDevice({
      filters: [{ name: 'Mohawk' }],
      optionalServices: [mohawkServiceUUID]
    })

    console.log('Connecting to GATT server...')
    const server = await device.gatt.connect()

    console.log('Getting Mohawk service...')
    const service = await server.getPrimaryService(mohawkServiceUUID)

    console.log('Getting Mohawk characteristic...')
    const characteristic = await service.getCharacteristic(mohawkCharacteristicUUID)

    console.log('Starting notifications...')
    characteristic.addEventListener('characteristicvaluechanged', handleCharacteristicValueChanged)
    await characteristic.startNotifications()

    console.log('Connected and notifications started')
  } catch (error) {
    console.error('Connection failed', error)
  }
}

// Handler for characteristic value changes
function handleCharacteristicValueChanged(event) {
  const value = event.target.value // DataView
  if (value.getUint8(0) === 'L'.charCodeAt(0) && value.byteLength === 20) {
    // Unpack location data
    const littleEndian = true
    const time1 = value.getUint8(1)
    const time2 = value.getUint8(2)
    const time3 = value.getUint8(3)
    const tenths = (time1 << 16) + (time2 << 8) + time3
    const lsb = tenths * 100
    let now = Date.now()
    const checkbit = 1 << 23
    if ((now & checkbit) > 0 && (lsb & checkbit) === 0) {
      now += checkbit
    }
    if ((now & checkbit) === 0 && (lsb & checkbit) > 0) {
      now -= checkbit
    }
    const shift = 100 << 24
    const millis = Math.floor(now / shift) * shift + lsb
    const lat = value.getInt32(4, littleEndian) * 1e-6 // microdegrees to degrees
    const lng = value.getInt32(8, littleEndian) * 1e-6 // microdegrees to degrees
    const alt = getShort(value, 12, littleEndian) * 0.1 + 3176.8 // decimeters to meters
    const vN = getShort(value, 14, littleEndian) * 0.01 // cm/s to m/s
    const vE = getShort(value, 16, littleEndian) * 0.01 // cm/s to m/s
    const climb = getShort(value, 18, littleEndian) * 0.01 // cm/s to m/s

    // Validate location
    if (validateLocation(lat, lng)) {
      const location = {
        time: millis,
        latitude: lat,
        longitude: lng,
        altitude: alt,
        climb: climb,
        velocityNorth: vN,
        velocityEast: vE
      }
      console.log('Mohawk -> App: GPS', location)
      // Handle location update (e.g., update UI, send to server, etc.)
    } else {
      console.warn('Invalid location:', lat, lng)
    }
  } else {
    const decoder = new TextDecoder()
    console.warn('Mohawk -> App: Unknown data', decoder.decode(value.buffer))
  }
}

// Helper function to read a 16-bit integer and handle special value
function getShort(dataView, index, littleEndian) {
  const value = dataView.getInt16(index, littleEndian)
  if (value === 32767) return NaN
  else return value
}

// Simple location validation function
function validateLocation(lat, lng) {
  return lat >= -90 && lat <= 90 && lng >= -180 && lng <= 180
}
