import React, { useState, useMemo, useCallback } from 'react';
import {
    View,
    StyleSheet,
    ScrollView,
    Pressable,
} from 'react-native';
import {Text, Button, TextInput, ScreenContainer, ProductImage} from '@/components/ui';
import { QuantityUnitRow, LocationPicker, ExpirationDatePicker } from '@/components/scan';
import { palette } from '@/styles/colors';
import { spacing, radius, shadow } from '@/styles/tokens';
import { useLocations } from '@/hooks/queries/useLocations';
import { useAppStore } from '@/stores/appStore';
import { createPantryItem } from '@/api/pantryItems';
import { useMutation, useQueryClient } from '@tanstack/react-query';
import type { ProductResponse } from '@/types';
import Toast from 'react-native-toast-message';
import * as Haptics from 'expo-haptics';
import { guessExpirationDate } from "@/utils/guessExpirationDays";

interface ConfirmItemScreenProps {
    product: ProductResponse;
    onAddedToPantry: () => void;
    onScanAnother: () => void;
    onGoBack: () => void;
}

export function ConfirmItemScreen({
                                      product,
                                      onAddedToPantry,
                                      onScanAnother,
                                      onGoBack,
                                  }: ConfirmItemScreenProps) {
    const householdId = useAppStore((s) => s.activeHouseholdId);
    const { data: locations = [] } = useLocations(householdId ?? undefined);
    const queryClient = useQueryClient();

    const [selectedLocationId, setSelectedLocationId] = useState('');
    const [showLocationPicker, setShowLocationPicker] = useState(false);
    const [quantity, setQuantity] = useState(1);
    const [unit, setUnit] = useState('Unit');
    const [showUnitPicker, setShowUnitPicker] = useState(false);

    // ── Editable product fields ──────────────────────
    const [isEditing, setIsEditing] = useState(false);
    const [editName, setEditName] = useState(product.name);
    const [editBrand, setEditBrand] = useState(product.brand ?? '');
    const [editCategory, setEditCategory] = useState(product.category ?? '');

    // ── Expiration date state ────────────────────────
    const guessedDate = useMemo(
        () => guessExpirationDate(product.name, product.category, product.defaultExpirationDays),
        [product],
    );

    const [expirationDate, setExpirationDate] = useState<Date | null>(guessedDate);
    const [showDatePicker, setShowDatePicker] = useState(false);

    React.useEffect(() => {
        if (locations.length > 0 && !selectedLocationId) {
            setSelectedLocationId(locations[0].id);
        }
    }, [locations, selectedLocationId]);

    const addMutation = useMutation({
        mutationFn: createPantryItem,
        onSuccess: async () => {
            await Haptics.notificationAsync(Haptics.NotificationFeedbackType.Success);
            await queryClient.invalidateQueries({ queryKey: ['pantryItems'] });
            await queryClient.invalidateQueries({ queryKey: ['pantryStatistics'] });
            Toast.show({ type: 'success', text1: 'Added to Pantry', text2: editName });
            onAddedToPantry();
        },
        onError: (error) => {
            Toast.show({ type: 'error', text1: 'Failed to add', text2: error.message });
        },
    });

    const formattedDate = useMemo(() => {
        return expirationDate ? expirationDate.toISOString().split('T')[0] : '';
    }, [expirationDate]);

    const handleAddToPantry = useCallback(() => {
        if (!selectedLocationId) {
            Toast.show({ type: 'error', text1: 'Select a location' });
            return;
        }

        addMutation.mutate({
            upc: product.upc,
            locationId: selectedLocationId,
            quantity,
            unitOfMeasure: unit,
            expirationDate: formattedDate || null,
            purchaseDate: new Date().toISOString().split('T')[0],
        });
    }, [product.upc, selectedLocationId, quantity, unit, formattedDate, addMutation]);

    const handleDateChange = useCallback((date: Date | null) => {
        setExpirationDate(date);
        if (date) setShowDatePicker(false);
    }, []);

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
                        Scanned Item - Found
                    </Text>
                </View>

                {/* Product Info Card */}
                <View style={styles.productCard}>
                    <ProductImage
                        imageUrl={product.imageUrl}
                        name={product.name}
                        category={product.category}
                        size={56}
                        style={styles.productIcon}
                    />
                    <View style={styles.productInfo}>
                        {isEditing ? (
                            <>
                                <TextInput label="Name" value={editName} onChangeText={setEditName} placeholder="Product name" />
                                <View style={{ marginTop: spacing.sm }}>
                                    <TextInput label="Brand" value={editBrand} onChangeText={setEditBrand} placeholder="Brand (optional)" />
                                </View>
                                <View style={{ marginTop: spacing.sm }}>
                                    <TextInput label="Category" value={editCategory} onChangeText={setEditCategory} placeholder="Category (optional)" />
                                </View>
                            </>
                        ) : (
                            <>
                                <Text size="lg" weight="bold" numberOfLines={2}>{editName}</Text>
                                {editBrand ? <Text size="sm" color="secondary">{editBrand}</Text> : null}
                                {editCategory ? <Text size="sm" color="secondary">{editCategory}</Text> : null}
                                {product.upc ? <Text size="sm" color="secondary">UPC: {product.upc}</Text> : null}
                            </>
                        )}
                    </View>
                </View>

                {/* Confirmation prompt with Edit pill */}
                <View style={styles.confirmRow}>
                    <Text size="sm" color="secondary" style={styles.confirmText}>
                        Is this information{'\n'}correct?
                    </Text>
                    <Pressable
                        style={({ pressed }) => [styles.editPill, pressed && styles.editPillPressed]}
                        onPress={() => setIsEditing(!isEditing)}
                    >
                        <Text size="sm" weight="semibold" style={styles.editPillText}>
                            {isEditing ? 'Done' : 'Edit'}
                        </Text>
                    </Pressable>
                </View>

                <LocationPicker
                    locations={locations}
                    selectedLocationId={selectedLocationId}
                    onSelectLocation={(id) => { setSelectedLocationId(id); setShowLocationPicker(false); }}
                    showPicker={showLocationPicker}
                    onTogglePicker={() => setShowLocationPicker(!showLocationPicker)}
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
                    showEstimateHint={!!guessedDate}
                    style={{ marginTop: spacing.md }}
                />

                {/* Actions */}
                <Button label="Add to Pantry" onPress={handleAddToPantry} loading={addMutation.isPending} style={{ marginTop: spacing.lg }} />
                <Button label="Scan Another Item" variant="ghost" onPress={onScanAnother} style={{ marginTop: spacing.sm }} />
            </ScrollView>
        </ScreenContainer>
    );
}

const styles = StyleSheet.create({
    scroll: { flex: 1 },
    scrollContent: { paddingBottom: spacing.xxl },
    header: { marginBottom: spacing.lg },
    productCard: {
        flexDirection: 'row',
        backgroundColor: palette.white,
        borderRadius: radius.lg,
        padding: spacing.md,
        marginBottom: spacing.sm,
        borderWidth: 1,
        borderColor: palette.cream300,
        ...shadow.sm,
    },
    productIcon: {
        marginRight: spacing.md,
    },
    productInfo: { flex: 1, gap: 2 },
    confirmRow: {
        flexDirection: 'row',
        alignItems: 'center',
        justifyContent: 'space-between',
        backgroundColor: palette.cream200,
        borderRadius: radius.md,
        paddingVertical: spacing.sm + 2,
        paddingHorizontal: spacing.md,
        marginBottom: spacing.lg,
    },
    confirmText: { flex: 1 },
    editPill: {
        backgroundColor: palette.sage100,
        borderRadius: radius.full,
        paddingHorizontal: spacing.md,
        paddingVertical: spacing.sm,
        borderWidth: 1,
        borderColor: palette.sage200,
    },
    editPillPressed: { backgroundColor: palette.sage200 },
    editPillText: { color: palette.sage700 },
});