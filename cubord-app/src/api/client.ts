import Constants from 'expo-constants';
import { supabase } from '@/services/supabase';

// ---------------------------------------------------------------------------
// Configuration
// ---------------------------------------------------------------------------

const { API_BASE_URL } = Constants.expoConfig!.extra as {
    API_BASE_URL: string;
};

if (!API_BASE_URL) {
    throw new Error(
        '[api/client] API_BASE_URL is not set. Add it to your .env and app.config.ts extra.',
    );
}

// ---------------------------------------------------------------------------
// ApiError
// ---------------------------------------------------------------------------

/**
 * Typed error for non-2xx HTTP responses.
 * Carries the status code and the parsed (or raw) error body so callers
 * can make decisions based on server-provided details.
 */
export class ApiError extends Error {
    constructor(
        public readonly status: number,
        public readonly body: unknown,
    ) {
        const message =
            typeof body === 'object' && body !== null && 'message' in body
                ? String((body as Record<string, unknown>).message)
                : `Request failed with status ${status}`;

        super(message);
        this.name = 'ApiError';
    }
}

// ---------------------------------------------------------------------------
// 401 Handler
// ---------------------------------------------------------------------------

/**
 * Called when the server returns a 401 Unauthorized response.
 * Signs the user out and lets the auth state change propagate
 * (which triggers a redirect to the sign-in screen via AuthContext).
 */
async function handleUnauthorized(): Promise<void> {
    console.warn('[api/client] 401 Unauthorized — signing out');
    const { supabase } = await import('@/services/supabase');
    const { useAppStore } = await import('@/stores/appStore');
    useAppStore.getState().clearActiveHousehold();
    await supabase.auth.signOut();
}

// ---------------------------------------------------------------------------
// Types
// ---------------------------------------------------------------------------

export type HttpMethod = 'GET' | 'POST' | 'PUT' | 'PATCH' | 'DELETE';

export interface RequestOptions extends Omit<RequestInit, 'method' | 'body' | 'headers'> {
    /** Extra headers merged on top of the defaults. */
    headers?: Record<string, string>;
    /**
     * If `false`, the Authorization header will be omitted.
     * Useful for public / unauthenticated endpoints.
     * @default true
     */
    authenticated?: boolean;
}

/**
 * Spring Boot Page response shape.
 */
export interface PageResponse<T> {
    content: T[];
    totalElements: number;
    totalPages: number;
    number: number;
    size: number;
}


// ---------------------------------------------------------------------------
// Token helper
// ---------------------------------------------------------------------------

/**
 * Reads the current JWT from the Supabase session.
 *
 * TODO: Once the Zustand auth store is implemented (Phase 1 — Step 4),
 *       replace this with a synchronous read from the store to avoid the
 *       extra async hop on every request.
 */
async function getAccessToken(): Promise<string | null> {
    const { data: { session } } = await supabase.auth.getSession();
    return session?.access_token ?? null;
}

// ---------------------------------------------------------------------------
// Fetch wrapper
// ---------------------------------------------------------------------------

/**
 * Typed fetch wrapper that talks to the backend API.
 *
 * @template T  The expected shape of the JSON response body.
 * @param path    Path relative to `API_BASE_URL` (e.g. `/items` or `/items/123`).
 * @param method  HTTP method.
 * @param body    Optional JSON-serializable request body (ignored for GET/DELETE).
 * @param options Extra options (headers, abort signal, etc.).
 *
 * @throws {ApiError}  On non-2xx responses.
 * @throws {TypeError} On network failures (offline, DNS, CORS, etc.).
 */
export async function apiClient<T = unknown>(
    path: string,
    method: HttpMethod = 'GET',
    body?: unknown,
    options: RequestOptions = {},
): Promise<T> {
    const { headers: extraHeaders = {}, authenticated = true, ...rest } = options;

    // ---- Build headers ---------------------------------------------------
    const headers: Record<string, string> = {
        'Content-Type': 'application/json',
        Accept: 'application/json',
        ...extraHeaders,
    };

    if (authenticated) {
        const token = await getAccessToken();
        if (token) {
            headers['Authorization'] = `Bearer ${token}`;
        }
    }

    // ---- Build URL -------------------------------------------------------
    // Ensure no double-slash between base and path
    const url = `${API_BASE_URL.replace(/\/+$/, '')}/${path.replace(/^\/+/, '')}`;

    // ---- Execute request -------------------------------------------------
    let response: Response;

    try {
        response = await fetch(url, {
            method,
            headers,
            body: body !== undefined ? JSON.stringify(body) : undefined,
            ...rest,
        });
    } catch (error) {
        // Network-level failure (offline, DNS, timeout, etc.)
        throw new TypeError(
            `[api/client] Network error while requesting ${method} ${path}: ${
                error instanceof Error ? error.message : String(error)
            }`,
        );
    }

    // ---- Parse response --------------------------------------------------
    // Try to parse JSON regardless of status so we can surface the error body.
    let data: unknown;
    const contentType = response.headers.get('content-type') ?? '';

    if (contentType.includes('application/json')) {
        data = await response.json();
    } else {
        // Non-JSON responses (e.g. 204 No Content, plain-text errors)
        const text = await response.text();
        data = text || null;
    }

    if (!response.ok) {
        if (response.status === 401) {
            await handleUnauthorized();
        }
        throw new ApiError(response.status, data);
    }

    return data as T;
}