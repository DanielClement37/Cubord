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

    /** Switch to a different household */
    setActiveHouseholdId: (id: string) => void;

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

            setActiveHouseholdId: (id) => set({ activeHouseholdId: id }),

            clearActiveHousehold: () => set({ activeHouseholdId: null }),
        }),
        {
            name: 'app-store',
            storage: createJSONStorage(() => secureStoreAdapter),
            // Only persist the data we need to survive restarts
            partialize: (state) => ({ activeHouseholdId: state.activeHouseholdId }),
        },
    ),
);