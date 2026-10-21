// AC-DEV-03: the production bundle must not contain any code of the dev toolbar (FS-DEV, D-26).
import { readdir, readFile } from 'node:fs/promises'
import { join } from 'node:path'

const dist = join(import.meta.dirname, '..', 'dist')
const markers = ['data-dev-toolbar', '/dev/login-as']

const files = await readdir(dist, { recursive: true })
const found = []
for (const file of files.filter((name) => /\.(js|css|html)$/.test(name))) {
  const content = await readFile(join(dist, file), 'utf8')
  found.push(...markers.filter((marker) => content.includes(marker)).map((marker) => `${file}: ${marker}`))
}

if (found.length > 0) {
  console.error(`Dev toolbar code found in the production bundle:\n${found.join('\n')}`)
  process.exit(1)
}
console.log('No dev toolbar code in the production bundle.')
