// src/test/utils.tsx
import React, { ReactElement } from 'react';
import { render, RenderOptions } from '@testing-library/react-native';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';

/**
 * Creates a fresh QueryClient configured for tests:
 *  - retries disabled so failures surface immediately
 *  - gcTime 0 so each test starts clean
 */
function createTestQueryClient(): QueryClient {
    return new QueryClient({
        defaultOptions: {
            queries: {
                retry: false,
                gcTime: 0,
            },
            mutations: {
                retry: false,
            },
        },
    });
}

/**
 * Wrapper that provides all necessary context providers for tests.
 * Extend this as new global providers are added (e.g., AuthProvider, ThemeProvider).
 */
function AllProviders({ children }: { children: React.ReactNode }) {
    const queryClient = createTestQueryClient();

    return (
        <QueryClientProvider client={queryClient}>
            {children}
        </QueryClientProvider>
    );
}

/**
 * Custom render function that wraps the component under test
 * with all required providers and a fresh QueryClient.
 *
 * Usage:
 *   import { render, screen } from '@test/utils';
 *
 *   render(<MyComponent />);
 *   expect(screen.getByText('Hello')).toBeTruthy();
 */
function customRender(
    ui: ReactElement,
    options?: Omit<RenderOptions, 'wrapper'>,
) {
    return render(ui, { wrapper: AllProviders, ...options });
}

// Re-export everything from RNTL, so tests only need one import
export * from '@testing-library/react-native';

// Override the default `render` with our wrapped version
export { customRender as render };