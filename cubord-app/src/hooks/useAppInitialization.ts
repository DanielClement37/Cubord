// src/hooks/useAppInitialization.ts
import { useEffect, useState } from 'react';
import { useHouseholds } from '@/hooks/queries/useHouseholds';
import { useAppStore } from '@/stores/appStore';

export type InitStatus = 'loading' | 'ready' | 'needs-household' | 'error';

/**
 * Runs once after authentication is confirmed.
 *
 * 1. Fetches user's households
 * 2. Validates or selects the active household
 * 3. Returns a status the layout can gate on
 */
export function useAppInitialization() {
    const { data: households, isLoading, isError, error, refetch } = useHouseholds();
    const activeHouseholdId = useAppStore((s) => s.activeHouseholdId);
    const setActiveHouseholdId = useAppStore((s) => s.setActiveHouseholdId);

    const [status, setStatus] = useState<InitStatus>('loading');

    useEffect(() => {
        console.log('[useAppInitialization]', {
            isLoading,
            isError,
            error: isError ? String(error) : undefined,
            householdCount: households?.length,
            activeHouseholdId,
        });

        if (isLoading) return;

        if (isError || !households) {
            setStatus('error');
            return;
        }

        if (households.length === 0) {
            setStatus('needs-household');
            return;
        }

        const storedIsValid = households.some((h) => h.id === activeHouseholdId);

        if (!activeHouseholdId || !storedIsValid) {
            setActiveHouseholdId(households[0].id);
        }

        setStatus('ready');
    }, [households, isLoading, isError, activeHouseholdId, setActiveHouseholdId]);

    return { status, households, refetch };
}
