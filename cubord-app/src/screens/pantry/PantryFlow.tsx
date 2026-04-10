import React, { useState, useCallback, useEffect } from 'react';
import { useLocalSearchParams } from 'expo-router';
import { PantryListScreen } from './PantryListScreen';
import { LocationDetailScreen } from './LocationDetailScreen';
import type { LocationResponse, PantryItemResponse } from '@/types';

type PantryFlowScreen =
    | { name: 'list' }
    | { name: 'location'; location: LocationResponse }
    | { name: 'detail'; item: PantryItemResponse; fromLocation?: LocationResponse };

/**
 * Pantry flow coordinator — manages navigation between:
 *   List → Location Detail → Item Detail
 *
 * Follows the same state-machine pattern used by ScanFlow.
 */
export function PantryFlow() {
    const params = useLocalSearchParams<{
        locationId?: string;
        locationName?: string;
    }>();

    const [currentScreen, setCurrentScreen] = useState<PantryFlowScreen>({
        name: 'list',
    });

    // ── Deep link: dashboard may push with locationId/locationName ────
    useEffect(() => {
        if (params.locationId && params.locationName) {
            setCurrentScreen({
                name: 'location',
                location: {
                    id: params.locationId,
                    name: params.locationName,
                    // Partial — the screen will fetch fresh data via the query hook
                    description: null,
                    householdId: '',
                    householdName: null,
                    createdAt: '',
                    updatedAt: '',
                },
            });
        }
    }, [params.locationId, params.locationName]);

    // ── Navigation helpers ────────────────────────────────────────────
    const goToList = useCallback(() => {
        setCurrentScreen({ name: 'list' });
    }, []);

    const goToLocation = useCallback((location: LocationResponse) => {
        setCurrentScreen({ name: 'location', location });
    }, []);

    const goToItemDetail = useCallback(
        (item: PantryItemResponse, fromLocation?: LocationResponse) => {
            setCurrentScreen({ name: 'detail', item, fromLocation });
        },
        [],
    );

    const goBack = useCallback(() => {
        if (currentScreen.name === 'detail' && currentScreen.fromLocation) {
            setCurrentScreen({
                name: 'location',
                location: currentScreen.fromLocation,
            });
        } else if (currentScreen.name === 'detail' || currentScreen.name === 'location') {
            setCurrentScreen({ name: 'list' });
        }
    }, [currentScreen]);

    // ── Render ────────────────────────────────────────────────────────
    switch (currentScreen.name) {
        case 'list':
            return (
                <PantryListScreen
                    onLocationPress={goToLocation}
                    onItemPress={(item) => goToItemDetail(item)}
                />
            );

        case 'location':
            return (
                <LocationDetailScreen
                    location={currentScreen.location}
                    onBack={goToList}
                    onItemPress={(item) =>
                        goToItemDetail(item, currentScreen.location)
                    }
                />
            );

        case 'detail':
            // TODO: Step 8 — ItemDetailScreen
            // For now, fall back to location or list
            goBack();
            return null;
    }
}