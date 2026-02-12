import { http, HttpResponse } from 'msw';
import { server } from '@/test/mocks/server';
import { apiClient, ApiError } from '@/api/client';

// ---------------------------------------------------------------------------
// Mock expo-constants so API_BASE_URL is always defined in tests
// ---------------------------------------------------------------------------
const TEST_BASE_URL = 'https://api.test.cubord.com';

jest.mock('expo-constants', () => ({
    __esModule: true,
    default: {
        expoConfig: {
            extra: {
                API_BASE_URL: TEST_BASE_URL,
            },
        },
    },
}));

// ---------------------------------------------------------------------------
// Mock supabase auth — returns a fake JWT by default
// ---------------------------------------------------------------------------
const FAKE_TOKEN = 'test-jwt-token';

jest.mock('@services/supabase', () => ({
    supabase: {
        auth: {
            getSession: jest.fn().mockResolvedValue({
                data: { session: { access_token: FAKE_TOKEN } },
            }),
        },
    },
}));

// Get a reference so individual tests can override
import { supabase } from '@/services/supabase';
const mockGetSession = supabase.auth.getSession as jest.Mock;

// ---------------------------------------------------------------------------
// Tests
// ---------------------------------------------------------------------------

describe('apiClient', () => {
    // ------ Successful requests -------------------------------------------

    it('makes a GET request and returns parsed JSON', async () => {
        server.use(
            http.get(`${TEST_BASE_URL}/items`, () =>
                HttpResponse.json([{ id: 1, name: 'Widget' }]),
            ),
        );

        const data = await apiClient<{ id: number; name: string }[]>('/items');

        expect(data).toEqual([{ id: 1, name: 'Widget' }]);
    });

    it('sends a POST body as JSON', async () => {
        let capturedBody: unknown;

        server.use(
            http.post(`${TEST_BASE_URL}/items`, async ({ request }) => {
                capturedBody = await request.json();
                return HttpResponse.json({ id: 2 }, { status: 201 });
            }),
        );

        const result = await apiClient<{ id: number }>('/items', 'POST', { name: 'Gadget' });

        expect(capturedBody).toEqual({ name: 'Gadget' });
        expect(result).toEqual({ id: 2 });
    });

    // ------ Headers -------------------------------------------------------

    it('includes Authorization header with JWT', async () => {
        let authHeader: string | null = null;

        server.use(
            http.get(`${TEST_BASE_URL}/me`, ({ request }) => {
                authHeader = request.headers.get('authorization');
                return HttpResponse.json({ id: 1 });
            }),
        );

        await apiClient('/me');

        expect(authHeader).toBe(`Bearer ${FAKE_TOKEN}`);
    });

    it('omits Authorization header when authenticated is false', async () => {
        let authHeader: string | null = null;

        server.use(
            http.get(`${TEST_BASE_URL}/public`, ({ request }) => {
                authHeader = request.headers.get('authorization');
                return HttpResponse.json({ ok: true });
            }),
        );

        await apiClient('/public', 'GET', undefined, { authenticated: false });

        expect(authHeader).toBeNull();
    });

    it('omits Authorization header when no session exists', async () => {
        mockGetSession.mockResolvedValueOnce({
            data: { session: null },
        });

        let authHeader: string | null = null;

        server.use(
            http.get(`${TEST_BASE_URL}/items`, ({ request }) => {
                authHeader = request.headers.get('authorization');
                return HttpResponse.json([]);
            }),
        );

        await apiClient('/items');

        expect(authHeader).toBeNull();
    });

    it('sets default Content-Type and Accept headers', async () => {
        let contentType: string | null = null;
        let accept: string | null = null;

        server.use(
            http.get(`${TEST_BASE_URL}/items`, ({ request }) => {
                contentType = request.headers.get('content-type');
                accept = request.headers.get('accept');
                return HttpResponse.json([]);
            }),
        );

        await apiClient('/items');

        expect(contentType).toBe('application/json');
        expect(accept).toBe('application/json');
    });

    it('allows extra headers to be merged', async () => {
        let customHeader: string | null = null;

        server.use(
            http.get(`${TEST_BASE_URL}/items`, ({ request }) => {
                customHeader = request.headers.get('x-custom');
                return HttpResponse.json([]);
            }),
        );

        await apiClient('/items', 'GET', undefined, {
            headers: { 'X-Custom': 'hello' },
        });

        expect(customHeader).toBe('hello');
    });

    // ------ Error handling -------------------------------------------------

    it('throws ApiError for non-2xx JSON responses', async () => {
        server.use(
            http.get(`${TEST_BASE_URL}/items/999`, () =>
                HttpResponse.json(
                    { message: 'Not found', code: 'ITEM_NOT_FOUND' },
                    { status: 404 },
                ),
            ),
        );

        const error = await apiClient('/items/999').catch((e) => e);

        expect(error).toBeInstanceOf(ApiError);
        expect((error as ApiError).status).toBe(404);
        expect((error as ApiError).body).toEqual({
            message: 'Not found',
            code: 'ITEM_NOT_FOUND',
        });
        expect((error as ApiError).message).toBe('Not found');
    });

    it('throws ApiError with generic message for non-JSON error responses', async () => {
        server.use(
            http.get(`${TEST_BASE_URL}/bad`, () =>
                new HttpResponse('Internal Server Error', {
                    status: 500,
                    headers: { 'Content-Type': 'text/plain' },
                }),
            ),
        );

        const error = await apiClient('/bad').catch((e) => e);

        expect(error).toBeInstanceOf(ApiError);
        expect((error as ApiError).status).toBe(500);
    });

    it('throws TypeError on network failure', async () => {
        server.use(
            http.get(`${TEST_BASE_URL}/offline`, () => HttpResponse.error()),
        );

        const error = await apiClient('/offline').catch((e) => e);

        expect(error).toBeInstanceOf(TypeError);
        expect((error as TypeError).message).toContain('Network error');
    });

    // ------ URL construction -----------------------------------------------

    it('handles paths with or without leading slash', async () => {
        server.use(
            http.get(`${TEST_BASE_URL}/items`, () => HttpResponse.json([])),
        );

        // Both forms should hit the same URL without double-slash
        await expect(apiClient('/items')).resolves.toEqual([]);
        await expect(apiClient('items')).resolves.toEqual([]);
    });

    it('handles 204 No Content responses', async () => {
        server.use(
            http.delete(`${TEST_BASE_URL}/items/1`, () =>
                new HttpResponse(null, { status: 204 }),
            ),
        );

        const result = await apiClient('/items/1', 'DELETE');

        expect(result).toBeNull();
    });
});
