// src/components/dashboard/__tests__/HomeScreen.test.tsx
import React from 'react';
import { render, screen, waitFor, fireEvent, act, cleanup } from '@test/utils';
import { http, HttpResponse } from 'msw';
import { server } from '@test/mocks/server';
import {
    mockHouseholds,
    mockLocations,
    mockPantryItems,
    mockPantryStatistics,
} from '@test/mocks/data';
import { useAppStore } from '@/stores/appStore';
import HomeScreen from '../../../../app/(app)/index';

// ── Mock external dependencies ────────────────────────
const mockPush = jest.fn();

jest.mock('expo-router', () => ({
    useRouter: () => ({ push: mockPush }),
}));

jest.mock('@/contexts/AuthContext', () => ({
    useAuth: () => ({
        user: {
            user_metadata: { full_name: 'Daniel Smith' },
            email: 'daniel@example.com',
        },
    }),
}));

// Silence SafeAreaView in tests
jest.mock('react-native-safe-area-context', () => {
    const { View } = require('react-native');
    return {
        SafeAreaView: View,
        useSafeAreaInsets: () => ({ top: 0, bottom: 0, left: 0, right: 0 }),
    };
});

const api = (path: string) => `*/api${path}`;

describe('HomeScreen (integration)', () => {
    beforeEach(() => {
        jest.clearAllMocks();
        // Set up the active household so queries fire
        useAppStore.getState().setActiveHouseholdId('hh-001');
    });

    afterEach(() => {
        cleanup();
        act(() => {
            useAppStore.getState().clearActiveHousehold();
        });
    });

    // ── Loading state ────────────────────────────────
    it('shows the skeleton while data is loading', async () => {
        // Delay responses so we can observe the loading state
        server.use(
            http.get(api('/households'), async () => {
                await new Promise((r) => setTimeout(r, 500));
                return HttpResponse.json(mockHouseholds);
            }),
            http.get(api('/households/:id/pantry-items/statistics'), async () => {
                await new Promise((r) => setTimeout(r, 500));
                return HttpResponse.json(mockPantryStatistics);
            }),
            http.get(api('/households/:id/pantry-items/expiring'), async () => {
                await new Promise((r) => setTimeout(r, 500));
                return HttpResponse.json([]);
            }),
            http.get(api('/households/:id/locations'), async () => {
                await new Promise((r) => setTimeout(r, 500));
                return HttpResponse.json(mockLocations);
            }),
        );

        render(<HomeScreen />);

        // Skeleton's spinner should be visible initially
        expect(screen.getByLabelText('Loading')).toBeTruthy();
    });

    // ── Loaded state with data ───────────────────────
    it('renders the greeting with the user first name after data loads', async () => {
        render(<HomeScreen />);

        await waitFor(() => {
            expect(screen.getByText(/Daniel/)).toBeTruthy();
        });
    });

    it('renders the household name in the pill', async () => {
        render(<HomeScreen />);

        await waitFor(() => {
            expect(screen.getByText('Doe Family')).toBeTruthy();
        });
    });

    it('renders pantry statistics after loading', async () => {
        render(<HomeScreen />);

        await waitFor(() => {
            expect(screen.getByText(String(mockPantryStatistics.totalItems))).toBeTruthy();
            expect(screen.getByText('Items')).toBeTruthy();
            expect(screen.getByText('Locations')).toBeTruthy();
        });
    });

    it('renders location chips in quick access', async () => {
        render(<HomeScreen />);

        await waitFor(() => {
            expect(screen.getByText('Kitchen Fridge')).toBeTruthy();
            expect(screen.getByText('Pantry Shelf')).toBeTruthy();
        });
    });

    it('renders the expiring items section with data from MSW', async () => {
        render(<HomeScreen />);

        await waitFor(() => {
            // The default handler returns [mockPantryItems[0]] for expiring
            expect(screen.getByText('Expiring Soon')).toBeTruthy();
        });
    });

    // ── Empty / positive states ──────────────────────
    it('shows the empty attention message when nothing is expiring and no low stock', async () => {
        server.use(
            http.get(api('/households/:id/pantry-items/expiring'), () => {
                return HttpResponse.json([]);
            }),
            http.get(api('/households/:id/pantry-items/statistics'), () => {
                return HttpResponse.json({ ...mockPantryStatistics, lowStockCount: 0 });
            }),
        );

        render(<HomeScreen />);

        await waitFor(() => {
            expect(screen.getByText(/Nothing needs attention/)).toBeTruthy();
        });
    });

    // ── Navigation ───────────────────────────────────
    it('navigates to pantry when the expiring soon card is tapped', async () => {
        render(<HomeScreen />);

        await waitFor(() => {
            expect(screen.getByText('Expiring Soon')).toBeTruthy();
        });

        fireEvent.press(screen.getByText('Expiring Soon'));

        expect(mockPush).toHaveBeenCalledWith('/pantry');
    });

    // ── Error resilience ─────────────────────────────
    it('renders gracefully when statistics endpoint fails', async () => {
        server.use(
            http.get(api('/households/:id/pantry-items/statistics'), () => {
                return HttpResponse.json(
                    { error_code: 'SERVER_ERROR', message: 'Internal error' },
                    { status: 500 },
                );
            }),
        );

        render(<HomeScreen />);

        // The screen should still eventually render (other sections load fine)
        await waitFor(() => {
            expect(screen.getByText(/Daniel/)).toBeTruthy();
        });
    });

    it('shows 0 items/locations when statistics returns but locations is empty', async () => {
        server.use(
            http.get(api('/households/:id/locations'), () => {
                return HttpResponse.json([]);
            }),
            http.get(api('/households/:id/pantry-items/statistics'), () => {
                return HttpResponse.json({ ...mockPantryStatistics, totalItems: 0 });
            }),
        );

        render(<HomeScreen />);

        await waitFor(() => {
            expect(screen.getByText('Pantry Overview')).toBeTruthy();
            const zeros = screen.getAllByText('0');
            expect(zeros.length).toBeGreaterThanOrEqual(2);
        });
    });

    // ── Fallback household name ──────────────────────
    it('falls back to "My Home" when active household is not in the list', async () => {
        useAppStore.getState().setActiveHouseholdId('hh-nonexistent');

        server.use(
            http.get(api('/households/:id/pantry-items/statistics'), () => {
                return HttpResponse.json(mockPantryStatistics);
            }),
            http.get(api('/households/:id/pantry-items/expiring'), () => {
                return HttpResponse.json([]);
            }),
            http.get(api('/households/:id/locations'), () => {
                return HttpResponse.json([]);
            }),
        );

        render(<HomeScreen />);

        await waitFor(() => {
            expect(screen.getByText('My Home')).toBeTruthy();
        });
    });
});