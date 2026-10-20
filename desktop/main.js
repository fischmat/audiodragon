const { app, BrowserWindow, dialog } = require('electron')
const path = require('node:path')
const { startServer, verifyJava, waitForHealthyServer } = require('./launcher')

let server
let mainWindow

function bundledJarPath() {
  return process.env.AUDIODRAGON_SERVER_JAR || path.join(process.resourcesPath, 'server', 'audiodragon.jar')
}

function createWindow(url) {
  mainWindow = new BrowserWindow({
    width: 1280,
    height: 800,
    minWidth: 960,
    minHeight: 640,
    webPreferences: {
      contextIsolation: true,
      nodeIntegration: false,
      sandbox: true
    }
  })
  mainWindow.webContents.setWindowOpenHandler(() => ({ action: 'deny' }))
  mainWindow.webContents.on('will-navigate', (event, targetUrl) => {
    if (new URL(targetUrl).origin !== new URL(url).origin) event.preventDefault()
  })
  return mainWindow.loadURL(url)
}

async function launch() {
  try {
    await verifyJava()
    server = startServer({ jarPath: bundledJarPath() })
    const port = await server.port
    await waitForHealthyServer(port)
    await createWindow(`http://127.0.0.1:${port}`)
  } catch (error) {
    if (server) server.stop()
    dialog.showErrorBox('AudioDragon could not start', error.message)
    app.quit()
  }
}

app.whenReady().then(launch)
app.on('before-quit', () => server && server.stop())
app.on('window-all-closed', () => app.quit())
