import React, { useState, useCallback } from 'react';
import { ScannerScreen } from './ScannerScreen';
import { ConfirmItemScreen } from './ConfirmItemScreen';
import { ProductLookupScreen } from './ProductLookupScreen';
import { ManualEntryScreen } from './ManualEntryScreen';
import type { ProductResponse } from '@/types';

type ScanFlowScreen =
    | { name: 'scanner' }
    | { name: 'confirm-item'; product: ProductResponse }
    | { name: 'product-lookup'; upc?: string }
    | { name: 'manual-entry'; upc: string };

/**
 * Scan flow coordinator.
 *
 * Happy path:
 *   Scanner → GET /products/upc/{code} → 200 → ConfirmItemScreen → POST /pantry-items { upc }
 *
 * Unknown product:
 *   Scanner → GET /products/upc/{code} → 404 → ProductLookupScreen
 *     → user picks a search result → ConfirmItemScreen
 *     → user chooses manual entry → ManualEntryScreen
 */
export function ScanFlow() {
    const [currentScreen, setCurrentScreen] = useState<ScanFlowScreen>({ name: 'scanner' });

    const goToScanner = useCallback(() => {
        setCurrentScreen({ name: 'scanner' });
    }, []);

    const handleProductFound = useCallback((product: ProductResponse) => {
        setCurrentScreen({ name: 'confirm-item', product });
    }, []);

    const handleProductNotFound = useCallback((upc: string) => {
        setCurrentScreen({ name: 'product-lookup', upc });
    }, []);

    const handleGoToProductLookup = useCallback(() => {
        setCurrentScreen({ name: 'product-lookup' });
    }, []);

    const handleGoToManualEntry = useCallback((upc: string) => {
        setCurrentScreen({ name: 'manual-entry', upc });
    }, []);

    switch (currentScreen.name) {
        case 'scanner':
            return (
                <ScannerScreen
                    onProductFound={handleProductFound}
                    onProductNotFound={handleProductNotFound}
                    onSearchProduct={handleGoToProductLookup}
                />
            );

        case 'product-lookup':
            return (
                <ProductLookupScreen
                    upc={currentScreen.upc}
                    onProductSelected={handleProductFound}
                    onCreateManually={handleGoToManualEntry}
                    onScanAnother={goToScanner}
                    onGoBack={goToScanner}
                />
            );

        case 'confirm-item':
            return (
                <ConfirmItemScreen
                    product={currentScreen.product}
                    onAddedToPantry={goToScanner}
                    onScanAnother={goToScanner}
                    onGoBack={goToScanner}
                />
            );

        case 'manual-entry':
            return (
                <ManualEntryScreen
                    upc={currentScreen.upc}
                    onAddedToPantry={goToScanner}
                    onScanAnother={goToScanner}
                    onGoBack={goToScanner}
                />
            );
    }
}