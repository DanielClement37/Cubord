// src/components/dashboard/__tests__/HouseholdPicker.test.tsx
import React from 'react';
import { render, screen, fireEvent, waitFor, act, cleanup } from '@test/utils';
import { HouseholdPicker } from '../HouseholdPicker';
import { useAppStore } from '@/stores/appStore';
import { http, HttpResponse } from 'msw';
import { server } from '@test/mocks/server';
import { mockHouseholds } from '@test/mocks/data';

// Mock safe-area (used by ScreenContainer in some paths)
jest.mock('react-native-safe-area-context', () => {
    const { View } = require('react-native');
    return {
        SafeAreaView: View,
        useSafeAreaInsets: () => ({ top: 0, bottom: 0, left: 0, right: 0 }),
    };
});

const api = (path: string) => `*/api${path}`;

describe('HouseholdPicker', () => {
    const onClose = jest.fn();

    beforeEach(() => {
        jest.clearAllMocks();
        useAppStore.getState().setActiveHouseholdId('hh-001');
    });

    afterEach(() => {
        cleanup();
        act(() => {
            useAppStore.getState().clearActiveHousehold();
        });
    });

    it('renders nothing when not visible', () => {
        render(<HouseholdPicker visible={false} onClose={onClose} />);

        expect(screen.queryByText('Select Household')).toBeNull();
    });

    it('renders the title when visible', async () => {
        render(<HouseholdPicker visible={true} onClose={onClose} />);

        await waitFor(() => {
            expect(screen.getByText('Select Household')).toBeTruthy();
        });
    });

    it('renders all households from the API', async () => {
        render(<HouseholdPicker visible={true} onClose={onClose} />);

        await waitFor(() => {
            for (const hh of mockHouseholds) {
                expect(screen.getByText(hh.name)).toBeTruthy();
            }
        });
    });

    it('highlights the active household with a checkmark', async () => {
        render(<HouseholdPicker visible={true} onClose={onClose} />);

        await waitFor(() => {
            expect(screen.getByText(mockHouseholds[0].name)).toBeTruthy();
        });

        // The active household should have the "selected" state
        const activeRow = screen.getByLabelText(`Select ${mockHouseholds[0].name}`);
        expect(activeRow.props.accessibilityState.selected).toBe(true);
    });

    it('switches household when a different row is tapped', async () => {
        render(<HouseholdPicker visible={true} onClose={onClose} />);

        await waitFor(() => {
            expect(screen.getByText(mockHouseholds[1].name)).toBeTruthy();
        });

        fireEvent.press(screen.getByLabelText(`Select ${mockHouseholds[1].name}`));

        // Store should be updated
        expect(useAppStore.getState().activeHouseholdId).toBe(mockHouseholds[1].id);
        // Modal should close
        expect(onClose).toHaveBeenCalledTimes(1);
    });

    it('closes without changing household when the active one is tapped', async () => {
        render(<HouseholdPicker visible={true} onClose={onClose} />);

        await waitFor(() => {
            expect(screen.getByText(mockHouseholds[0].name)).toBeTruthy();
        });

        fireEvent.press(screen.getByLabelText(`Select ${mockHouseholds[0].name}`));

        expect(useAppStore.getState().activeHouseholdId).toBe('hh-001');
        expect(onClose).toHaveBeenCalledTimes(1);
    });

    it('renders the "Create New Household" button', async () => {
        render(<HouseholdPicker visible={true} onClose={onClose} />);

        await waitFor(() => {
            expect(screen.getByText('Create New Household')).toBeTruthy();
        });
    });

    it('shows the create form when "Create New Household" is tapped', async () => {
        render(<HouseholdPicker visible={true} onClose={onClose} />);

        await waitFor(() => {
            expect(screen.getByText('Create New Household')).toBeTruthy();
        });

        fireEvent.press(screen.getByText('Create New Household'));

        expect(screen.getByText('Household name')).toBeTruthy();
        expect(screen.getByText('Create')).toBeTruthy();
        expect(screen.getByText('Cancel')).toBeTruthy();
    });

    it('hides the create form when Cancel is pressed', async () => {
        render(<HouseholdPicker visible={true} onClose={onClose} />);

        await waitFor(() => {
            expect(screen.getByText('Create New Household')).toBeTruthy();
        });

        fireEvent.press(screen.getByText('Create New Household'));
        expect(screen.getByText('Cancel')).toBeTruthy();

        fireEvent.press(screen.getByText('Cancel'));

        // Back to the create button
        expect(screen.getByText('Create New Household')).toBeTruthy();
    });

    it('validates name minimum length in the create form', async () => {
        render(<HouseholdPicker visible={true} onClose={onClose} />);

        await waitFor(() => {
            expect(screen.getByText('Create New Household')).toBeTruthy();
        });

        fireEvent.press(screen.getByText('Create New Household'));

        // Try creating with a single character
        fireEvent.changeText(screen.getByLabelText('Household name'), 'A');
        fireEvent.press(screen.getByText('Create'));

        expect(screen.getByText('Name must be at least 2 characters')).toBeTruthy();
    });

    it('shows a loading spinner while households are loading', async () => {
        server.use(
            http.get(api('/households'), async () => {
                await new Promise((r) => setTimeout(r, 500));
                return HttpResponse.json(mockHouseholds);
            }),
        );

        render(<HouseholdPicker visible={true} onClose={onClose} />);

        expect(screen.getByLabelText('Loading')).toBeTruthy();
    });
});