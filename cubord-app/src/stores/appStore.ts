// src/stores/appStore.ts
import { create } from 'zustand';
import { persist, createJSONStorage, StateStorage } from 'zustand/middleware';
import * as SecureStore from 'expo-secure-store';

/* ------------------------------------------------------------------ */
/*  Secure-store adapter for Zustand persist middleware                */
/* ------------------------------------------------------------------ */
const secureStoreAdapter: StateStorage = {
    getItem: (key: string) => SecureStore.getItemAsync(key),
    setItem: (key: string, value: string) => SecureStore.setItemAsync(key, value),
    removeItem: (key: string) => SecureStore.deleteItemAsync(key),
};

/* ------------------------------------------------------------------ */
/*  State & actions types                                              */
/* ------------------------------------------------------------------ */
interface AppState {
    /** Currently selected household */
    activeHouseholdId: string | null;

    /** Cached Supabase access token for synchronous reads */
    accessToken: string | null;

    /** Switch to a different household */
    setActiveHouseholdId: (id: string) => void;

    /** Update the cached access token (called from AuthContext) */
    setAccessToken: (token: string | null) => void;

    /** Reset on sign-out */
    clearActiveHousehold: () => void;
}

/* ------------------------------------------------------------------ */
/*  Store                                                              */
/* ------------------------------------------------------------------ */
export const useAppStore = create<AppState>()(
    persist(
        (set) => ({
            activeHouseholdId: null,
            accessToken: null,

            setActiveHouseholdId: (id) => set({ activeHouseholdId: id }),

            setAccessToken: (token) => set({ accessToken: token }),

            clearActiveHousehold: () => set({ activeHouseholdId: null, accessToken: null }),
        }),
        {
            name: 'app-store',
            storage: createJSONStorage(() => secureStoreAdapter),
            // Only persist the data we need to survive restarts
            partialize: (state) => ({ activeHouseholdId: state.activeHouseholdId }),
        },
    ),
);