import React, { useState, useMemo, useCallback } from 'react';
import {
    View,
    StyleSheet,
    ScrollView,
    Pressable,
} from 'react-native';
import { Text, Button, TextInput, ScreenContainer } from '@/components/ui';
import { QuantityUnitRow, LocationPicker, ExpirationDatePicker } from '@/components/scan';
import { palette } from '@/styles/colors';
import { spacing, radius } from '@/styles/tokens';
import { useLocations } from '@/hooks/queries/useLocations';
import { useAppStore } from '@/stores/appStore';
import { createProduct } from '@/api/products';
import { createPantryItem } from '@/api/pantryItems';
import { useMutation, useQueryClient } from '@tanstack/react-query';
import type { ProductResponse } from '@/types';
import Toast from 'react-native-toast-message';
import * as Haptics from 'expo-haptics';

const CATEGORIES = [
    'DAIRY', 'PRODUCE', 'MEAT', 'SEAFOOD', 'BAKERY', 'FROZEN',
    'CANNED', 'SNACKS', 'BEVERAGES', 'CONDIMENTS', 'SPICES', 'GRAINS', 'OTHER',
];

interface ManualEntryScreenProps {
    upc: string;
    onAddedToPantry: () => void;
    onScanAnother: () => void;
    onGoBack: () => void;
}

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
    const [showUnitPicker, setShowUnitPicker] = useState(false);
    const [expirationDate, setExpirationDate] = useState<Date | null>(null);
    const [showDatePicker, setShowDatePicker] = useState(false);

    // Validation
    const [errors, setErrors] = useState<Record<string, string>>({});

    React.useEffect(() => {
        if (locations.length > 0 && !selectedLocationId) {
            setSelectedLocationId(locations[0].id);
        }
    }, [locations, selectedLocationId]);

    const createProductMutation = useMutation({ mutationFn: createProduct });

    const createPantryItemMutation = useMutation({
        mutationFn: createPantryItem,
        onSuccess: async () => {
            await Haptics.notificationAsync(Haptics.NotificationFeedbackType.Success);
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

    const formattedDate = useMemo(() => {
        return expirationDate ? expirationDate.toISOString().split('T')[0] : '';
    }, [expirationDate]);

    const handleDateChange = useCallback((date: Date | null) => {
        setExpirationDate(date);
        if (date) setShowDatePicker(false);
    }, []);

    const handleAddToPantry = useCallback(async () => {
        if (!validate()) return;

        try {
            const product: ProductResponse = await createProductMutation.mutateAsync({
                upc,
                name: name.trim(),
                brand: brand.trim() || null,
                category: category || null,
            });

            createPantryItemMutation.mutate({
                productId: product.id,
                locationId: selectedLocationId,
                quantity,
                unitOfMeasure: unit,
                expirationDate: formattedDate || null,
                purchaseDate: new Date().toISOString().split('T')[0],
            });
        } catch (error) {
            Toast.show({
                type: 'error',
                text1: 'Failed to create product',
                text2: error instanceof Error ? error.message : 'Unknown error',
            });
        }
    }, [validate, upc, name, brand, category, selectedLocationId, quantity, unit, formattedDate, createProductMutation, createPantryItemMutation]);

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
                    <TextInput label="Brand" placeholder="Brand" value={brand} onChangeText={setBrand} />
                </View>

                {/* Category */}
                <Text size="sm" weight="medium" color="secondary" style={[styles.categoryLabel, { marginTop: spacing.md }]}>
                    Category
                </Text>
                <Pressable style={styles.categoryPicker} onPress={() => setShowCategoryPicker(!showCategoryPicker)}>
                    <Text size="md" color={category ? undefined : 'secondary'}>
                        {category || 'Select Category'}
                    </Text>
                    <Text size="md" color="secondary">▼</Text>
                </Pressable>
                {showCategoryPicker && (
                    <View style={styles.categoryDropdown}>
                        <ScrollView style={{ maxHeight: 200 }} nestedScrollEnabled>
                            {CATEGORIES.map((cat) => (
                                <Pressable
                                    key={cat}
                                    style={[styles.categoryItem, cat === category && styles.categoryItemSelected]}
                                    onPress={() => { setCategory(cat); setShowCategoryPicker(false); }}
                                >
                                    <Text size="md" weight={cat === category ? 'semibold' : 'regular'}>{cat}</Text>
                                </Pressable>
                            ))}
                        </ScrollView>
                    </View>
                )}

                <LocationPicker
                    locations={locations}
                    selectedLocationId={selectedLocationId}
                    onSelectLocation={(id) => {
                        setSelectedLocationId(id);
                        setShowLocationPicker(false);
                        if (errors.location) setErrors((e) => ({ ...e, location: '' }));
                    }}
                    showPicker={showLocationPicker}
                    onTogglePicker={() => setShowLocationPicker(!showLocationPicker)}
                    error={errors.location}
                    style={{ marginTop: spacing.md }}
                />

                <QuantityUnitRow
                    quantity={quantity}
                    onQuantityChange={setQuantity}
                    unit={unit}
                    onUnitChange={(u) => { setUnit(u); setShowUnitPicker(false); }}
                    showUnitPicker={showUnitPicker}
                    onToggleUnitPicker={() => setShowUnitPicker(!showUnitPicker)}
                />

                <ExpirationDatePicker
                    expirationDate={expirationDate}
                    onDateChange={handleDateChange}
                    showPicker={showDatePicker}
                    onTogglePicker={() => setShowDatePicker(!showDatePicker)}
                    style={{ marginTop: spacing.md }}
                />

                {/* Actions */}
                <Button label="Add to Pantry" onPress={handleAddToPantry} loading={isLoading} style={{ marginTop: spacing.lg }} />
                <Button label="Scan Another Item" variant="ghost" onPress={onScanAnother} style={{ marginTop: spacing.sm }} />
            </ScrollView>
        </ScreenContainer>
    );
}

const styles = StyleSheet.create({
    scroll: { flex: 1 },
    scrollContent: { paddingBottom: spacing.xxl },
    header: { marginBottom: spacing.lg },
    upcBadge: {
        backgroundColor: palette.cream200,
        borderRadius: radius.sm,
        paddingHorizontal: spacing.md,
        paddingVertical: spacing.sm,
        alignSelf: 'flex-start',
        marginBottom: spacing.lg,
    },
    categoryLabel: {
        marginBottom: spacing.xs,
    },
    categoryPicker: {
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
    categoryDropdown: {
        backgroundColor: palette.white,
        borderWidth: 1,
        borderColor: palette.cream300,
        borderRadius: radius.md,
        marginTop: -spacing.sm,
        marginBottom: spacing.md,
        overflow: 'hidden',
    },
    categoryItem: {
        paddingHorizontal: spacing.md,
        paddingVertical: spacing.sm + 4,
        borderBottomWidth: 1,
        borderBottomColor: palette.cream200,
    },
    categoryItemSelected: {
        backgroundColor: palette.sage50,
    },
});