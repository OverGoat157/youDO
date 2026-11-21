import { useSyncExternalStore } from 'react';
import {
  apiFetch, jsonRequest, setAccessToken, setRefreshHandler,
  setSessionExpiredHandler, setPasswordChangeRequiredHandler,
} from './client';

export type Role = 'VIEWER' | 'EDITOR' | 'ADMIN';

export interface Account {
  id: string;
  name: string;
  login: string;
  roles: Role[];
  mustChangePassword: boolean;

  active: boolean;
  credentialsVersion: number;
}

export type SessionStatus = 'restoring' | 'anonymous' | 'authenticated' | 'password-change-required';

export interface SessionState {
  status: SessionStatus;
  user: Account | null;
  notice: string | null;
}

export interface LoginInput {
  login: string;
  password: string;
  clientType: 'WEB';
}

export interface AuthSession {
  login(input: LoginInput): Promise<{ user: Account }>;
  clear(): void;
}

interface UserPayload {
  id: string;
  name: string;
  login: string;
  roles: string[];
  mustChangePassword: boolean;
  active?: boolean;
  credentialsVersion?: number;
}

interface TokenPayload {
  accessToken: string;
  tokenType?: string;
  expiresIn?: number;
  user: UserPayload;
}

const KNOWN_ROLES: Role[] = ['VIEWER', 'EDITOR', 'ADMIN'];

export function normalizeAccount(user: UserPayload): Account {
  return {
    id: user.id,
    name: user.name,
    login: user.login,
    roles: (user.roles ?? []).filter((role): role is Role => KNOWN_ROLES.includes(role as Role)),
    mustChangePassword: Boolean(user.mustChangePassword),
    active: user.active ?? true,
    credentialsVersion: user.credentialsVersion ?? 0,
  };
}

class SessionStore {
  private state: SessionState = { status: 'restoring', user: null, notice: null };
  private listeners = new Set<() => void>();
  private restoreStarted = false;

  subscribe = (listener: () => void): (() => void) => {
    this.listeners.add(listener);
    return () => { this.listeners.delete(listener); };
  };

  getSnapshot = (): SessionState => this.state;

  private emit(next: SessionState): void {
    this.state = next;
    for (const listener of this.listeners) listener();
  }

  isAuthenticated(): boolean {
    return this.state.user !== null;
  }


  login = async (input: LoginInput): Promise<{ user: Account }> => {
    const payload = await apiFetch<TokenPayload>('/api/auth/login', jsonRequest('POST', {
      login: input.login.trim().toLowerCase(),
      password: input.password,
      clientType: 'WEB',
    }));
    setAccessToken(payload.accessToken);
    const user = normalizeAccount(payload.user);
    this.emit({
      status: user.mustChangePassword ? 'password-change-required' : 'authenticated',
      user,
      notice: null,
    });
    return { user };
  };


  restore = async (): Promise<void> => {
    if (this.restoreStarted) return;
    this.restoreStarted = true;
    try {
      const payload = await apiFetch<TokenPayload>('/api/auth/refresh', jsonRequest('POST', { clientType: 'WEB' }));
      setAccessToken(payload.accessToken);
      const user = await this.fetchMe(payload.user);
      this.emit({
        status: user.mustChangePassword ? 'password-change-required' : 'authenticated',
        user,
        notice: null,
      });
    } catch {
      setAccessToken(null);
      this.emit({ status: 'anonymous', user: null, notice: null });
    }
  };

  private fetchMe(fallback?: UserPayload): Promise<Account> {
    return apiFetch<UserPayload>('/api/auth/me')
      .then(payload => normalizeAccount(payload))
      .catch(error => {
        if (fallback) return normalizeAccount(fallback);
        throw error;
      });
  }

  logout = async (): Promise<void> => {
    try {
      await apiFetch<void>('/api/auth/logout', jsonRequest('POST', { clientType: 'WEB' }));
    } catch {

    }
    this.clear(null);
  };

  clear = (notice: string | null = null): void => {
    setAccessToken(null);
    this.emit({ status: 'anonymous', user: null, notice });
  };

  setUser = (user: Account): void => {
    this.emit({
      status: user.mustChangePassword ? 'password-change-required' : 'authenticated',
      user,
      notice: this.state.notice,
    });
  };

  requireChangePassword = (): void => {
    const user = this.state.user;
    if (!user) return;
    this.setUser({ ...user, mustChangePassword: true });
  };

  markRestored(): void {
    this.restoreStarted = true;
  }
}

export const session = new SessionStore();

setSessionExpiredHandler(() => {
  if (session.isAuthenticated()) session.clear('Сессия истекла. Войдите снова.');
});

setPasswordChangeRequiredHandler(() => {
  session.requireChangePassword();
});

setRefreshHandler(async () => {
  try {
    const payload = await apiFetch<TokenPayload>('/api/auth/refresh', jsonRequest('POST', { clientType: 'WEB' }));
    setAccessToken(payload.accessToken);
    session.setUser(normalizeAccount(payload.user));
    return payload.accessToken;
  } catch {
    return null;
  }
});

export function useSession(): SessionState {
  return useSyncExternalStore(session.subscribe, session.getSnapshot, session.getSnapshot);
}

export function hasRole(user: Account | null, role: Role): boolean {
  return Boolean(user?.roles.includes(role));
}

export function canEdit(user: Account | null): boolean {
  return hasRole(user, 'EDITOR') || hasRole(user, 'ADMIN');
}

export function isAdmin(user: Account | null): boolean {
  return hasRole(user, 'ADMIN');
}
