import { encode } from 'uqr'

/** QR code as plain SVG (no innerHTML, SEC-07); dark modules use the text colour token. */
export function QrCode({ value, label }: { value: string; label: string }) {
  const { data, size } = encode(value, { ecc: 'M', border: 2 })
  let path = ''
  data.forEach((row, y) => {
    row.forEach((dark, x) => {
      if (dark) {
        path += `M${String(x)} ${String(y)}h1v1h-1z`
      }
    })
  })
  return (
    <svg viewBox={`0 0 ${String(size)} ${String(size)}`} role="img" aria-label={label} className="qr-code">
      <rect width={size} height={size} className="fill-surface" />
      <path d={path} className="fill-text" />
    </svg>
  )
}
