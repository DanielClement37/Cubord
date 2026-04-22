// src/stores/__tests__/appStore.test.ts
import { useAppStore } from '@/stores/appStore';

// Mock expo-secure-store so tests run in a plain Node/Jest environment
jest.mock('expo-secure-store', () => {
    const store: Record<string, string> = {};
    return {
        getItemAsync: jest.fn((key: string) => Promise.resolve(store[key] ?? null)),
        setItemAsync: jest.fn((key: string, value: string) => {
            store[key] = value;
            return Promise.resolve();
        }),
        deleteItemAsync: jest.fn((key: string) => {
            delete store[key];
            return Promise.resolve();
        }),
    };
});

describe('useAppStore', () => {
    // Reset the store to its initial state before each test
    beforeEach(() => {
        useAppStore.setState({ activeHouseholdId: null });
    });

    it('has null as the initial activeHouseholdId', () => {
        const { activeHouseholdId } = useAppStore.getState();
        expect(activeHouseholdId).toBeNull();
    });

    it('setActiveHouseholdId updates the value', () => {
        useAppStore.getState().setActiveHouseholdId('household-123');
        expect(useAppStore.getState().activeHouseholdId).toBe('household-123');
    });

    it('setActiveHouseholdId can switch between households', () => {
        useAppStore.getState().setActiveHouseholdId('household-1');
        expect(useAppStore.getState().activeHouseholdId).toBe('household-1');

        useAppStore.getState().setActiveHouseholdId('household-2');
        expect(useAppStore.getState().activeHouseholdId).toBe('household-2');
    });

    it('clearActiveHousehold resets to null', () => {
        useAppStore.getState().setActiveHouseholdId('household-123');
        useAppStore.getState().clearActiveHousehold();
        expect(useAppStore.getState().activeHouseholdId).toBeNull();
    });

    it('has null as the initial accessToken', () => {
        expect(useAppStore.getState().accessToken).toBeNull();
    });

    it('setAccessToken updates the value', () => {
        useAppStore.getState().setAccessToken('jwt-token-abc');
        expect(useAppStore.getState().accessToken).toBe('jwt-token-abc');
    });

    it('setAccessToken can be set to null', () => {
        useAppStore.getState().setAccessToken('jwt-token-abc');
        useAppStore.getState().setAccessToken(null);
        expect(useAppStore.getState().accessToken).toBeNull();
    });

    it('clearActiveHousehold also clears accessToken', () => {
        useAppStore.getState().setAccessToken('jwt-token-abc');
        useAppStore.getState().setActiveHouseholdId('household-123');
        useAppStore.getState().clearActiveHousehold();
        expect(useAppStore.getState().accessToken).toBeNull();
        expect(useAppStore.getState().activeHouseholdId).toBeNull();
    });
});