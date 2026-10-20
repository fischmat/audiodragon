const fs = require('node:fs')
const path = require('node:path')
const sharp = require('sharp')

const desktopRoot = path.resolve(__dirname, '..')
const projectRoot = path.resolve(desktopRoot, '..')
const buildLibs = path.join(projectRoot, 'build', 'libs')
const resources = path.join(desktopRoot, 'resources')
const clientLogo = path.join(projectRoot, 'audiodragon-client', 'src', 'assets', 'logo.png')
const jars = fs.existsSync(buildLibs)
  ? fs.readdirSync(buildLibs).filter((file) => file.endsWith('.jar') && !file.endsWith('-plain.jar'))
  : []

if (jars.length !== 1) {
  throw new Error(`Expected exactly one Spring Boot JAR in ${buildLibs}; found ${jars.length}. Run ./build.sh first.`)
}
if (!fs.existsSync(clientLogo)) {
  throw new Error(`Could not find the frontend logo at ${clientLogo}. Initialize the frontend submodule first.`)
}

async function prepare() {
  fs.mkdirSync(resources, { recursive: true })
  fs.copyFileSync(path.join(buildLibs, jars[0]), path.join(resources, 'audiodragon.jar'))
  await sharp(clientLogo).resize(512, 512).png().toFile(path.join(resources, 'icon.png'))
}

prepare().catch((error) => {
  console.error(error)
  process.exitCode = 1
})
