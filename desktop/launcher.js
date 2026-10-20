const { execFile, spawn } = require('node:child_process')
const http = require('node:http')

const SERVER_PORT_PREFIX = 'AUDIODRAGON_SERVER_PORT='
const MINIMUM_JAVA_VERSION = 21

function parseJavaMajorVersion(output) {
  const match = output.match(/version\s+"(\d+)(?:\.(\d+))?/)
  if (!match) return null
  return match[1] === '1' ? Number(match[2]) : Number(match[1])
}

function assertSupportedJava(output) {
  const major = parseJavaMajorVersion(output)
  if (!major || major < MINIMUM_JAVA_VERSION) {
    throw new Error(`AudioDragon requires Java ${MINIMUM_JAVA_VERSION} or newer on PATH.`)
  }
  return major
}

function verifyJava(javaCommand = 'java', execFileImpl = execFile) {
  return new Promise((resolve, reject) => {
    execFileImpl(javaCommand, ['-version'], { encoding: 'utf8' }, (error, stdout, stderr) => {
      if (error) {
        reject(new Error(`Unable to start Java. Install Java ${MINIMUM_JAVA_VERSION}+ and make sure 'java' is on PATH.`))
        return
      }
      try {
        resolve(assertSupportedJava(`${stdout}\n${stderr}`))
      } catch (validationError) {
        reject(validationError)
      }
    })
  })
}

function serverArguments(jarPath) {
  return ['-jar', jarPath, '--server.port=0', '--server.address=127.0.0.1']
}

function extractServerPort(output) {
  const match = output.match(new RegExp(`(?:^|\\n)${SERVER_PORT_PREFIX}(\\d+)(?:\\r?$|\\n)`))
  if (!match) return null
  const port = Number(match[1])
  return Number.isInteger(port) && port > 0 && port <= 65535 ? port : null
}

function stopChild(child) {
  if (child && !child.killed && child.exitCode === null) child.kill('SIGTERM')
}

function startServer({ jarPath, javaCommand = 'java', spawnImpl = spawn }) {
  const child = spawnImpl(javaCommand, serverArguments(jarPath), {
    stdio: ['ignore', 'pipe', 'pipe'],
    windowsHide: true
  })

  let output = ''
  const port = new Promise((resolve, reject) => {
    const readOutput = (chunk) => {
      output += chunk.toString()
      const value = extractServerPort(output)
      if (value) resolve(value)
    }
    child.stdout.on('data', readOutput)
    child.stderr.on('data', readOutput)
    child.once('error', (error) => reject(new Error(`Could not start AudioDragon server: ${error.message}`)))
    child.once('exit', (code) => reject(new Error(`AudioDragon server stopped before startup${code === null ? '' : ` (exit code ${code})`}.`)))
  })

  return { child, port, stop: () => stopChild(child) }
}

function healthCheck(port, requestImpl = http.get) {
  return new Promise((resolve, reject) => {
    const request = requestImpl(`http://127.0.0.1:${port}/actuator/health`, (response) => {
      response.resume()
      if (response.statusCode >= 200 && response.statusCode < 300) resolve()
      else reject(new Error(`Server health check returned ${response.statusCode}.`))
    })
    request.setTimeout(2000, () => request.destroy(new Error('Server health check timed out.')))
    request.once('error', reject)
  })
}

async function waitForHealthyServer(port, { timeoutMs = 30000, retryMs = 250, healthCheckImpl = healthCheck } = {}) {
  const deadline = Date.now() + timeoutMs
  let lastError
  while (Date.now() < deadline) {
    try {
      await healthCheckImpl(port)
      return
    } catch (error) {
      lastError = error
      await new Promise((resolve) => setTimeout(resolve, retryMs))
    }
  }
  throw new Error(`AudioDragon server did not become ready within ${Math.round(timeoutMs / 1000)} seconds.${lastError ? ` ${lastError.message}` : ''}`)
}

module.exports = {
  MINIMUM_JAVA_VERSION,
  assertSupportedJava,
  extractServerPort,
  parseJavaMajorVersion,
  serverArguments,
  startServer,
  stopChild,
  verifyJava,
  waitForHealthyServer
}
