const test = require('node:test')
const assert = require('node:assert/strict')
const { EventEmitter } = require('node:events')
const {
  assertSupportedJava,
  extractServerPort,
  parseJavaMajorVersion,
  serverArguments,
  startServer,
  stopChild,
  waitForHealthyServer
} = require('../launcher')

test('parses and validates Java 21 and later', () => {
  assert.equal(parseJavaMajorVersion('openjdk version "21.0.5"'), 21)
  assert.equal(parseJavaMajorVersion('java version "1.8.0_392"'), 8)
  assert.equal(assertSupportedJava('openjdk version "23"'), 23)
  assert.throws(() => assertSupportedJava('openjdk version "17.0.12"'), /Java 21/)
})

test('builds a loopback, random-port server command', () => {
  assert.deepEqual(serverArguments('/tmp/audiodragon.jar'), [
    '-jar', '/tmp/audiodragon.jar', '--server.port=0', '--server.address=127.0.0.1'
  ])
})

test('extracts only valid server-port announcements', () => {
  assert.equal(extractServerPort('info\nAUDIODRAGON_SERVER_PORT=43127\n'), 43127)
  assert.equal(extractServerPort('AUDIODRAGON_SERVER_PORT=0\n'), null)
  assert.equal(extractServerPort('server started on 43127'), null)
})

test('stops an active child process and ignores an exited one', () => {
  const active = { killed: false, exitCode: null, kill: (signal) => { active.signal = signal } }
  stopChild(active)
  assert.equal(active.signal, 'SIGTERM')
  const stopped = { killed: false, exitCode: 0, kill: () => assert.fail('must not kill') }
  stopChild(stopped)
})

test('resolves server startup after its port announcement', async () => {
  const child = new EventEmitter()
  child.stdout = new EventEmitter()
  child.stderr = new EventEmitter()
  child.killed = false
  child.exitCode = null
  child.kill = () => { child.killed = true }
  const running = startServer({ jarPath: '/tmp/server.jar', spawnImpl: () => child })
  child.stdout.emit('data', Buffer.from('AUDIODRAGON_SERVER_PORT=43127\n'))
  assert.equal(await running.port, 43127)
})

test('reports a server that exits before announcing its port', async () => {
  const child = new EventEmitter()
  child.stdout = new EventEmitter()
  child.stderr = new EventEmitter()
  child.killed = false
  child.exitCode = null
  child.kill = () => { child.killed = true }
  const running = startServer({ jarPath: '/tmp/server.jar', spawnImpl: () => child })
  child.emit('exit', 1)
  await assert.rejects(running.port, /stopped before startup.*exit code 1/)
})

test('retries readiness checks until the server is healthy', async () => {
  let attempts = 0
  await waitForHealthyServer(12345, {
    timeoutMs: 1000,
    retryMs: 1,
    healthCheckImpl: async () => {
      attempts += 1
      if (attempts < 2) throw new Error('not ready')
    }
  })
  assert.equal(attempts, 2)
})

test('fails with a useful timeout when the server never becomes healthy', async () => {
  await assert.rejects(
    waitForHealthyServer(12345, {
      timeoutMs: 5,
      retryMs: 1,
      healthCheckImpl: async () => { throw new Error('connection refused') }
    }),
    /did not become ready/
  )
})
