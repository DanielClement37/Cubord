import React, { useState, useCallback, useRef, useEffect } from 'react';
import {
    View,
    StyleSheet,
    ScrollView,
    Pressable,
    ActivityIndicator,
} from 'react-native';
import { Text, Button, TextInput, ScreenContainer } from '@/components/ui';
import { palette } from '@/styles/colors';
import { spacing, radius } from '@/styles/tokens';
import { searchProducts } from '@/api/products';
import type { ProductResponse } from '@/types';

interface ProductLookupScreenProps {
    upc: string;
    onProductSelected: (product: ProductResponse) => void;
    onCreateManually: (upc: string) => void;
    onScanAnother: () => void;
    onGoBack: () => void;
}

export function ProductLookupScreen({
    upc,
    onProductSelected,
    onCreateManually,
    onScanAnother,
    onGoBack,
}: ProductLookupScreenProps) {
    const [query, setQuery] = useState('');
    const [results, setResults] = useState<ProductResponse[]>([]);
    const [selectedProduct, setSelectedProduct] = useState<ProductResponse | null>(null);
    const [isSearching, setIsSearching] = useState(false);
    const [hasSearched, setHasSearched] = useState(false);
    const debounceRef = useRef<ReturnType<typeof setTimeout> | null>(null);

    // Debounced search — waits 400ms after the user stops typing
    useEffect(() => {
        if (debounceRef.current) clearTimeout(debounceRef.current);

        const trimmed = query.trim();
        if (trimmed.length < 2) {
            setResults([]);
            setHasSearched(false);
            setIsSearching(false);
            return;
        }

        setIsSearching(true);

        debounceRef.current = setTimeout(async () => {
            try {
                const page = await searchProducts(trimmed, 0, 20);
                setResults(page.content);
                setHasSearched(true);
            } catch {
                setResults([]);
                setHasSearched(true);
            } finally {
                setIsSearching(false);
            }
        }, 400);

        return () => {
            if (debounceRef.current) clearTimeout(debounceRef.current);
        };
    }, [query]);

    const handleQueryChange = useCallback((text: string) => {
        setQuery(text);
        setSelectedProduct(null);
    }, []);

    const handleContinue = useCallback(() => {
        if (selectedProduct) {
            onProductSelected(selectedProduct);
        }
    }, [selectedProduct, onProductSelected]);

    return (
        <ScreenContainer edges={['top', 'left', 'right', 'bottom']}>
            <ScrollView
                style={styles.scroll}
                contentContainerStyle={styles.scrollContent}
                showsVerticalScrollIndicator={false}
                keyboardShouldPersistTaps="handled"
            >
                {/* Header */}
                <View style={styles.header}>
                    <Pressable onPress={onGoBack} hitSlop={12}>
                        <Text size="md" weight="semibold" style={{ color: palette.sage700 }}>
                            ← Back
                        </Text>
                    </Pressable>
                </View>

                {/* Product Not Found Banner */}
                <View style={styles.notFoundBanner}>
                    <View style={styles.barcodeIcon}>
                        <Text size="xl">❓</Text>
                    </View>
                    <View style={styles.notFoundText}>
                        <Text size="lg" weight="bold">Product Not Found</Text>
                        <Text size="sm" color="secondary" style={{ marginTop: spacing.xs }}>
                            We couldn't match this barcode.{'\n'}Try a quick search first.
                        </Text>
                    </View>
                </View>

                {/* UPC Badge */}
                <View style={styles.upcBadge}>
                    <Text size="sm" color="secondary">UPC: {upc}</Text>
                </View>

                {/* Search Input */}
                <TextInput
                    placeholder="Search by name or brand"
                    value={query}
                    onChangeText={handleQueryChange}
                    autoFocus
                />

                {/* Results */}
                <View style={styles.resultsSection}>
                    {isSearching && (
                        <View style={styles.loadingContainer}>
                            <ActivityIndicator size="small" color={palette.sage500} />
                            <Text size="sm" color="secondary" style={{ marginLeft: spacing.sm }}>
                                Searching…
                            </Text>
                        </View>
                    )}

                    {!isSearching && hasSearched && (
                        <Text size="md" weight="semibold" style={{ marginBottom: spacing.sm }}>
                            Results
                        </Text>
                    )}

                    {!isSearching && results.map((product) => {
                        const isSelected = selectedProduct?.id === product.id;
                        return (
                            <Pressable
                                key={product.id}
                                style={[
                                    styles.resultRow,
                                    isSelected && styles.resultRowSelected,
                                ]}
                                onPress={() => setSelectedProduct(product)}
                            >
                                <View style={[
                                    styles.radioOuter,
                                    isSelected && styles.radioOuterSelected,
                                ]}>
                                    {isSelected && <View style={styles.radioInner} />}
                                </View>
                                <View style={styles.resultInfo}>
                                    <Text size="md" weight="semibold" numberOfLines={1}>
                                        {product.name}
                                    </Text>
                                    {product.brand && (
                                        <Text size="sm" color="secondary" numberOfLines={1}>
                                            {product.brand}
                                            {product.category ? ` - ${product.category}` : ''}
                                        </Text>
                                    )}
                                </View>
                            </Pressable>
                        );
                    })}

                    {!isSearching && hasSearched && results.length === 0 && (
                        <Text size="sm" color="secondary" align="center" style={{ paddingVertical: spacing.md }}>
                            No products found. Try a different search or create one manually.
                        </Text>
                    )}
                </View>

                {/* Create manually link */}
                <Pressable
                    style={styles.createManuallyButton}
                    onPress={() => onCreateManually(upc)}
                >
                    <Text size="md" weight="semibold" style={{ color: palette.sage700 }}>
                        + Create new product manually
                    </Text>
                </Pressable>

                {/* Actions */}
                <Button
                    label="Continue"
                    onPress={handleContinue}
                    disabled={!selectedProduct}
                    style={{ marginTop: spacing.lg }}
                />
                <Button
                    label="Scan Another Item"
                    variant="ghost"
                    onPress={onScanAnother}
                    style={{ marginTop: spacing.sm }}
                />
            </ScrollView>
        </ScreenContainer>
    );
}

const styles = StyleSheet.create({
    scroll: { flex: 1 },
    scrollContent: { paddingBottom: spacing.xxl },
    header: { marginBottom: spacing.md },
    notFoundBanner: {
        flexDirection: 'row',
        alignItems: 'center',
        marginBottom: spacing.md,
    },
    barcodeIcon: {
        width: 64,
        height: 64,
        borderRadius: radius.md,
        backgroundColor: palette.cream200,
        alignItems: 'center',
        justifyContent: 'center',
        marginRight: spacing.md,
    },
    notFoundText: {
        flex: 1,
    },
    upcBadge: {
        backgroundColor: palette.cream200,
        borderRadius: radius.sm,
        paddingHorizontal: spacing.md,
        paddingVertical: spacing.sm,
        alignSelf: 'flex-start',
        marginBottom: spacing.lg,
    },
    resultsSection: {
        marginTop: spacing.lg,
    },
    loadingContainer: {
        flexDirection: 'row',
        alignItems: 'center',
        justifyContent: 'center',
        paddingVertical: spacing.md,
    },
    resultRow: {
        flexDirection: 'row',
        alignItems: 'center',
        backgroundColor: palette.white,
        borderRadius: radius.md,
        paddingVertical: spacing.md,
        paddingHorizontal: spacing.md,
        marginBottom: spacing.sm,
        borderWidth: 1.5,
        borderColor: palette.cream300,
    },
    resultRowSelected: {
        borderColor: palette.sage400,
        backgroundColor: palette.sage50,
    },
    radioOuter: {
        width: 22,
        height: 22,
        borderRadius: 11,
        borderWidth: 2,
        borderColor: palette.sand300,
        alignItems: 'center',
        justifyContent: 'center',
        marginRight: spacing.md,
    },
    radioOuterSelected: {
        borderColor: palette.sage500,
    },
    radioInner: {
        width: 12,
        height: 12,
        borderRadius: 6,
        backgroundColor: palette.sage500,
    },
    resultInfo: {
        flex: 1,
    },
    createManuallyButton: {
        paddingVertical: spacing.md,
        marginTop: spacing.sm,
    },
});