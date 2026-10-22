import '@testing-library/jest-dom/vitest'
import { cleanup, configure } from '@testing-library/react'
import { afterEach } from 'vitest'

// Lazily loaded routes (date picker, admin area) take a moment to transform on their first import
configure({ asyncUtilTimeout: 5000 })

afterEach(() => {
  cleanup()
})
