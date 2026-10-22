import type { LoginResult } from '../api/generated/model'

/** Where the SPA continues after a login step (7.2): the start page or the second factor. */
export function nextPath(result: LoginResult): string {
  switch (result.status) {
    case 'LOGGED_IN':
      return '/'
    case 'MFA_REQUIRED':
      return '/login/mfa'
    case 'MFA_SETUP_REQUIRED':
      return '/login/mfa?setup=1'
  }
}
