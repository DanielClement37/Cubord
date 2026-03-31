// jest.setup.ts
import '@testing-library/jest-native/extend-expect';
import { server } from '@test/mocks/server';

// ── Mock @expo/vector-icons to avoid async font-loading act() warnings ──
jest.mock('@expo/vector-icons', () => {
    const { Text } = require('react-native');
    const React = require('react');

    const createMockIcon = () => {
        const Icon = (props: Record<string, unknown>) =>
            React.createElement(Text, props, props.name);
        return Icon;
    };

    return new Proxy(
        {},
        {
            get: (_target: unknown, prop: string) => {
                if (prop === '__esModule') return true;
                return createMockIcon();
            },
        },
    );
});

// ── Mock expo-constants to avoid SUPABASE_URL crash in test imports ──
jest.mock('expo-constants', () => ({
    __esModule: true,
    default: {
        expoConfig: {
            extra: {
                SUPABASE_URL: 'https://test.supabase.co',
                SUPABASE_PUBLISHABLE_KEY: 'test-anon-key',
                API_BASE_URL: 'http://localhost:8080/api',
            },
        },
    },
}));

// ── Mock Supabase client to prevent real network calls ──
jest.mock('@/services/supabase', () => ({
    supabase: {
        auth: {
            getSession: jest.fn().mockResolvedValue({ data: { session: null } }),
            onAuthStateChange: jest.fn().mockReturnValue({
                data: { subscription: { unsubscribe: jest.fn() } },
            }),
            signOut: jest.fn().mockResolvedValue({}),
        },
    },
}));

// Start an MSW server before all tests
beforeAll(() => server.listen({ onUnhandledRequest: 'warn' }));

// Reset handlers after each test (important for test isolation)
afterEach(() => server.resetHandlers());

// Clean up after all tests are done
afterAll(() => server.close());
