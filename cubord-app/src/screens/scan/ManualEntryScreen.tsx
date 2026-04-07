import React, { useState, useCallback } from 'react';
import {
    View,
    StyleSheet,
    ScrollView,
    Pressable,
    Platform,
} from 'react-native';
import { Text, Button, TextInput, ScreenContainer } from '@/components/ui';
import { palette } from '@/styles/colors';
import { spacing, radius } from '@/styles/tokens';
import { useLocations } from '@/hooks/queries/useLocations';
import { useAppStore } from '@/stores/appStore';
import { createProduct } from '@/api/products';
import { createPantryItem } from '@/api/pantryItems';
import { useMutation, useQueryClient } from '@tanstack/react-query';
import type { ProductRequest, ProductResponse, CreatePantryItemRequest } from '@/types';
import Toast from 'react-native-toast-message';
import * as Haptics from 'expo-haptics';

interface ManualEntryScreenProps {
    upc: string;
    onAddedToPantry: () => void;
    onScanAnother: () => void;
    onGoBack: () => void;
}

const CATEGORIES = [
    'Select Category',
    'DAIRY',
    'PRODUCE',
    'MEAT',
    'SEAFOOD',
    'BAKERY',
    'FROZEN',
    'CANNED',
    'SNACKS',
    'BEVERAGES',
    'CONDIMENTS',
    'SPICES',
    'GRAINS',
    'OTHER',
];

export function ManualEntryScreen({
                                      upc,
                                      onAddedToPantry,
                                      onScanAnother,
                                      onGoBack,
                                  }: ManualEntryScreenProps) {
    const householdId = useAppStore((s) => s.activeHouseholdId);
    const { data: locations = [] } = useLocations(householdId ?? undefined);
    const queryClient = useQueryClient();

    // Product fields
    const [name, setName] = useState('');
    const [brand, setBrand] = useState('');
    const [category, setCategory] = useState('');
    const [showCategoryPicker, setShowCategoryPicker] = useState(false);

    // Pantry item fields
    const [selectedLocationId, setSelectedLocationId] = useState('');
    const [showLocationPicker, setShowLocationPicker] = useState(false);
    const [quantity, setQuantity] = useState(1);
    const [unit, setUnit] = useState('Unit');
    const [expirationDate, setExpirationDate] = useState('');

    // Validation
    const [errors, setErrors] = useState<Record<string, string>>({});

    // Auto-select first location
    React.useEffect(() => {
        if (locations.length > 0 && !selectedLocationId) {
            setSelectedLocationId(locations[0].id);
        }
    }, [locations, selectedLocationId]);

    const selectedLocation = locations.find((l) => l.id === selectedLocationId);

    const createProductMutation = useMutation({
        mutationFn: createProduct,
    });

    const createPantryItemMutation = useMutation({
        mutationFn: createPantryItem,
        onSuccess: async () => {
            Haptics.notificationAsync(Haptics.NotificationFeedbackType.Success);
            await queryClient.invalidateQueries({ queryKey: ['pantryItems'] });
            await queryClient.invalidateQueries({ queryKey: ['pantryStatistics'] });
            Toast.show({ type: 'success', text1: 'Added to Pantry', text2: name });
            onAddedToPantry();
        },
        onError: (error) => {
            Toast.show({ type: 'error', text1: 'Failed to add', text2: error.message });
        },
    });

    const isLoading = createProductMutation.isPending || createPantryItemMutation.isPending;

    const validate = useCallback((): boolean => {
        const newErrors: Record<string, string> = {};
        if (!name.trim()) newErrors.name = 'Product name is required';
        if (!selectedLocationId) newErrors.location = 'Select a location';
        setErrors(newErrors);
        return Object.keys(newErrors).length === 0;
    }, [name, selectedLocationId]);

    const handleAddToPantry = useCallback(async () => {
        if (!validate()) return;

        try {
            // 1. Create the product (backend doesn't know about this UPC yet)
            const productRequest: ProductRequest = {
                upc,
                name: name.trim(),
                brand: brand.trim() || null,
                category: category || null,
            };

            const product: ProductResponse = await createProductMutation.mutateAsync(productRequest);

            // 2. Create pantry item — use productId since we just created it,
            //    but upc would also work now that the product exists
            const pantryItemRequest: CreatePantryItemRequest = {
                productId: product.id,
                locationId: selectedLocationId,
                quantity,
                unitOfMeasure: unit,
                expirationDate: expirationDate || null,
                purchaseDate: new Date().toISOString().split('T')[0],
            };

            createPantryItemMutation.mutate(pantryItemRequest);
        } catch (error) {
            Toast.show({
                type: 'error',
                text1: 'Failed to create product',
                text2: error instanceof Error ? error.message : 'Unknown error',
            });
        }
    }, [validate, upc, name, brand, category, selectedLocationId, quantity, unit, expirationDate, createProductMutation, createPantryItemMutation]);

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
                    <Text size="xl" weight="bold" style={{ marginTop: spacing.sm }}>
                        Product Not Found
                    </Text>
                    <Text size="sm" color="secondary" style={{ marginTop: spacing.xs }}>
                        We couldn't match this barcode.{'\n'}Add details below to save it.
                    </Text>
                </View>

                {/* UPC display */}
                <View style={styles.upcBadge}>
                    <Text size="sm" color="secondary">UPC: {upc}</Text>
                </View>

                {/* Product Name */}
                <TextInput
                    label="Product Name *"
                    placeholder="Product Name"
                    value={name}
                    onChangeText={(v) => {
                        setName(v);
                        if (errors.name) setErrors((e) => ({ ...e, name: '' }));
                    }}
                    error={errors.name}
                />

                {/* Brand */}
                <View style={{ marginTop: spacing.md }}>
                    <TextInput
                        label="Brand"
                        placeholder="Brand"
                        value={brand}
                        onChangeText={setBrand}
                    />
                </View>

                {/* Category */}
                <Text size="sm" weight="medium" color="secondary" style={[styles.fieldLabel, { marginTop: spacing.md }]}>
                    Category
                </Text>
                <Pressable
                    style={styles.pickerButton}
                    onPress={() => setShowCategoryPicker(!showCategoryPicker)}
                >
                    <Text size="md" color={category ? undefined : 'secondary'}>
                        {category || 'Select Category'}
                    </Text>
                    <Text size="md" color="secondary">▼</Text>
                </Pressable>
                {showCategoryPicker && (
                    <View style={styles.dropdownList}>
                        <ScrollView style={{ maxHeight: 200 }} nestedScrollEnabled>
                            {CATEGORIES.filter((c) => c !== 'Select Category').map((cat) => (
                                <Pressable
                                    key={cat}
                                    style={[
                                        styles.dropdownItem,
                                        cat === category && styles.dropdownItemSelected,
                                    ]}
                                    onPress={() => {
                                        setCategory(cat);
                                        setShowCategoryPicker(false);
                                    }}
                                >
                                    <Text
                                        size="md"
                                        weight={cat === category ? 'semibold' : 'regular'}
                                    >
                                        {cat}
                                    </Text>
                                </Pressable>
                            ))}
                        </ScrollView>
                    </View>
                )}

                {/* Location Picker */}
                <Text size="sm" weight="medium" color="secondary" style={[styles.fieldLabel, { marginTop: spacing.md }]}>
                    Location *
                </Text>
                <Pressable
                    style={[styles.pickerButton, errors.location ? { borderColor: palette.red400 } : null]}
                    onPress={() => setShowLocationPicker(!showLocationPicker)}
                >
                    <Text size="md">
                        {selectedLocation?.name || 'Select location'}
                    </Text>
                    <Text size="md" color="secondary">▼</Text>
                </Pressable>
                {errors.location && (
                    <Text size="sm" color="error" style={{ marginTop: -spacing.sm, marginBottom: spacing.sm }}>
                        {errors.location}
                    </Text>
                )}
                {showLocationPicker && (
                    <View style={styles.dropdownList}>
                        {locations.map((loc) => (
                            <Pressable
                                key={loc.id}
                                style={[
                                    styles.dropdownItem,
                                    loc.id === selectedLocationId && styles.dropdownItemSelected,
                                ]}
                                onPress={() => {
                                    setSelectedLocationId(loc.id);
                                    setShowLocationPicker(false);
                                    if (errors.location) setErrors((e) => ({ ...e, location: '' }));
                                }}
                            >
                                <Text
                                    size="md"
                                    weight={loc.id === selectedLocationId ? 'semibold' : 'regular'}
                                >
                                    {loc.name}
                                </Text>
                            </Pressable>
                        ))}
                    </View>
                )}

                {/* Quantity + Unit */}
                <View style={styles.quantityRow}>
                    <View style={{ flex: 1 }}>
                        <Text size="sm" weight="medium" color="secondary" style={styles.fieldLabel}>
                            Quantity
                        </Text>
                        <View style={styles.quantityStepper}>
                            <Pressable
                                style={styles.stepperButton}
                                onPress={() => setQuantity(Math.max(0, quantity - 1))}
                            >
                                <Text size="lg" weight="bold">−</Text>
                            </Pressable>
                            <Text size="md" weight="semibold" style={styles.quantityValue}>
                                {quantity}
                            </Text>
                            <Pressable
                                style={styles.stepperButton}
                                onPress={() => setQuantity(quantity + 1)}
                            >
                                <Text size="lg" weight="bold">+</Text>
                            </Pressable>
                        </View>
                    </View>
                    <View style={{ flex: 1, marginLeft: spacing.md }}>
                        <TextInput
                            label="Unit"
                            value={unit}
                            onChangeText={setUnit}
                        />
                    </View>
                </View>

                {/* Expiration Date */}
                <TextInput
                    label="Expiration Date"
                    placeholder="MM-DD-YYYY"
                    value={expirationDate}
                    onChangeText={setExpirationDate}
                    keyboardType={Platform.OS === 'ios' ? 'numbers-and-punctuation' : 'default'}
                    style={{ marginTop: spacing.md }}
                />

                {/* Actions */}
                <Button
                    label="Add to Pantry"
                    onPress={handleAddToPantry}
                    loading={isLoading}
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
    scroll: {
        flex: 1,
    },
    scrollContent: {
        paddingBottom: spacing.xxl,
    },
    header: {
        marginBottom: spacing.lg,
    },
    upcBadge: {
        backgroundColor: palette.cream200,
        borderRadius: radius.sm,
        paddingHorizontal: spacing.md,
        paddingVertical: spacing.sm,
        alignSelf: 'flex-start',
        marginBottom: spacing.lg,
    },
    fieldLabel: {
        marginBottom: spacing.xs,
    },
    pickerButton: {
        flexDirection: 'row',
        justifyContent: 'space-between',
        alignItems: 'center',
        height: 48,
        borderWidth: 1.5,
        borderColor: palette.cream300,
        borderRadius: radius.md,
        paddingHorizontal: spacing.md,
        backgroundColor: palette.white,
        marginBottom: spacing.md,
    },
    dropdownList: {
        backgroundColor: palette.white,
        borderWidth: 1,
        borderColor: palette.cream300,
        borderRadius: radius.md,
        marginTop: -spacing.sm,
        marginBottom: spacing.md,
        overflow: 'hidden',
    },
    dropdownItem: {
        paddingHorizontal: spacing.md,
        paddingVertical: spacing.sm + 4,
        borderBottomWidth: 1,
        borderBottomColor: palette.cream200,
    },
    dropdownItemSelected: {
        backgroundColor: palette.sage50,
    },
    quantityRow: {
        flexDirection: 'row',
        alignItems: 'flex-end',
        marginTop: spacing.md,
    },
    quantityStepper: {
        flexDirection: 'row',
        alignItems: 'center',
        height: 48,
        borderWidth: 1.5,
        borderColor: palette.cream300,
        borderRadius: radius.md,
        backgroundColor: palette.white,
        overflow: 'hidden',
    },
    stepperButton: {
        width: 48,
        height: 48,
        alignItems: 'center',
        justifyContent: 'center',
    },
    quantityValue: {
        flex: 1,
        textAlign: 'center',
    },
});