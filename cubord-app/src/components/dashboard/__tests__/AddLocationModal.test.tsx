// src/components/dashboard/__tests__/AddLocationModal.test.tsx
import React from 'react';
import { render, screen, fireEvent, waitFor, act, cleanup } from '@test/utils';
import { AddLocationModal } from '../AddLocationModal';
import { useAppStore } from '@/stores/appStore';
import { http, HttpResponse } from 'msw';
import { server } from '@test/mocks/server';

jest.mock('react-native-safe-area-context', () => {
    const { View } = require('react-native');
    return {
        SafeAreaView: View,
        useSafeAreaInsets: () => ({ top: 0, bottom: 0, left: 0, right: 0 }),
    };
});

const api = (path: string) => `*/api${path}`;

describe('AddLocationModal', () => {
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
        render(<AddLocationModal visible={false} onClose={onClose} />);

        expect(screen.queryByText('Add New Location')).toBeNull();
    });

    it('renders the title when visible', () => {
        render(<AddLocationModal visible={true} onClose={onClose} />);

        expect(screen.getByText('Add New Location')).toBeTruthy();
    });

    it('renders name and description inputs', () => {
        render(<AddLocationModal visible={true} onClose={onClose} />);

        expect(screen.getByText('Location name')).toBeTruthy();
        expect(screen.getByText('Description (optional)')).toBeTruthy();
    });

    it('renders Cancel and Create buttons', () => {
        render(<AddLocationModal visible={true} onClose={onClose} />);

        expect(screen.getByText('Cancel')).toBeTruthy();
        expect(screen.getByText('Create')).toBeTruthy();
    });

    it('calls onClose when Cancel is pressed', () => {
        render(<AddLocationModal visible={true} onClose={onClose} />);

        fireEvent.press(screen.getByText('Cancel'));

        expect(onClose).toHaveBeenCalledTimes(1);
    });

    it('validates name minimum length', () => {
        render(<AddLocationModal visible={true} onClose={onClose} />);

        fireEvent.changeText(screen.getByLabelText('Location name'), 'A');
        fireEvent.press(screen.getByText('Create'));

        expect(screen.getByText('Name must be at least 2 characters')).toBeTruthy();
        expect(onClose).not.toHaveBeenCalled();
    });

    it('validates that an empty name shows the error', () => {
        render(<AddLocationModal visible={true} onClose={onClose} />);

        fireEvent.press(screen.getByText('Create'));

        expect(screen.getByText('Name must be at least 2 characters')).toBeTruthy();
    });

    it('clears the validation error once the user types a valid name and resubmits', async () => {
        render(<AddLocationModal visible={true} onClose={onClose} />);

        // Trigger validation error
        fireEvent.press(screen.getByText('Create'));
        expect(screen.getByText('Name must be at least 2 characters')).toBeTruthy();

        // Fix the name
        fireEvent.changeText(screen.getByLabelText('Location name'), 'Garage Freezer');
        fireEvent.press(screen.getByText('Create'));

        // Error should be gone, modal should close on success
        await waitFor(() => {
            expect(screen.queryByText('Name must be at least 2 characters')).toBeNull();
        });
    });

    it('sends a POST request and closes the modal on success', async () => {
        render(<AddLocationModal visible={true} onClose={onClose} />);

        fireEvent.changeText(screen.getByLabelText('Location name'), 'Garage Freezer');
        fireEvent.changeText(
            screen.getByLabelText('Description (optional)'),
            'Deep freezer in the garage',
        );
        fireEvent.press(screen.getByText('Create'));

        await waitFor(() => {
            expect(onClose).toHaveBeenCalledTimes(1);
        });
    });

    it('sends the correct payload to the API', async () => {
        let capturedBody: Record<string, unknown> | null = null;

        server.use(
            http.post(api('/households/:householdId/locations'), async ({ request }) => {
                capturedBody = (await request.json()) as Record<string, unknown>;
                return HttpResponse.json(
                    {
                        id: 'loc-new',
                        name: capturedBody.name,
                        description: capturedBody.description,
                        householdId: capturedBody.householdId,
                        householdName: 'Doe Family',
                        createdAt: new Date().toISOString(),
                        updatedAt: new Date().toISOString(),
                    },
                    { status: 201 },
                );
            }),
        );

        render(<AddLocationModal visible={true} onClose={onClose} />);

        fireEvent.changeText(screen.getByLabelText('Location name'), 'Garage Freezer');
        fireEvent.changeText(
            screen.getByLabelText('Description (optional)'),
            'Deep freezer',
        );
        fireEvent.press(screen.getByText('Create'));

        await waitFor(() => {
            expect(capturedBody).toEqual({
                name: 'Garage Freezer',
                description: 'Deep freezer',
                householdId: 'hh-001',
            });
        });
    });

    it('sends null description when the field is left empty', async () => {
        let capturedBody: Record<string, unknown> | null = null;

        server.use(
            http.post(api('/households/:householdId/locations'), async ({ request }) => {
                capturedBody = (await request.json()) as Record<string, unknown>;
                return HttpResponse.json(
                    {
                        id: 'loc-new',
                        name: capturedBody.name,
                        description: null,
                        householdId: capturedBody.householdId,
                        householdName: 'Doe Family',
                        createdAt: new Date().toISOString(),
                        updatedAt: new Date().toISOString(),
                    },
                    { status: 201 },
                );
            }),
        );

        render(<AddLocationModal visible={true} onClose={onClose} />);

        fireEvent.changeText(screen.getByLabelText('Location name'), 'Pantry Shelf');
        fireEvent.press(screen.getByText('Create'));

        await waitFor(() => {
            expect(capturedBody).toEqual({
                name: 'Pantry Shelf',
                description: null,
                householdId: 'hh-001',
            });
        });
    });

    it('does not close the modal when the API returns an error', async () => {
        server.use(
            http.post(api('/households/:householdId/locations'), () => {
                return HttpResponse.json(
                    { error_code: 'SERVER_ERROR', message: 'Internal error' },
                    { status: 500 },
                );
            }),
        );

        render(<AddLocationModal visible={true} onClose={onClose} />);

        fireEvent.changeText(screen.getByLabelText('Location name'), 'Garage Freezer');
        fireEvent.press(screen.getByText('Create'));

        // Wait long enough for the mutation to settle
        await waitFor(() => {
            expect(onClose).not.toHaveBeenCalled();
        });
    });

    it('resets the form fields when reopened after Cancel', () => {
        const { rerender } = render(
            <AddLocationModal visible={true} onClose={onClose} />,
        );

        fireEvent.changeText(screen.getByLabelText('Location name'), 'Temp Name');
        fireEvent.press(screen.getByText('Cancel'));

        // Simulate reopening the modal
        rerender(<AddLocationModal visible={true} onClose={onClose} />);

        // The input should be empty again (form reset on close)
        const input = screen.getByLabelText('Location name');
        expect(input.props.value).toBe('');
    });
});