import React, { useState, useMemo, useCallback } from 'react';
import {
    View,
    StyleSheet,
    ScrollView,
    Pressable,
    
} from 'react-native';
import { DatePickerModal } from '@/components/ui/DatePickerModal';
import { Text, Button, ScreenContainer } from '@/components/ui';
import { palette } from '@/styles/colors';
import { spacing, radius } from '@/styles/tokens';
import { useLocations } from '@/hooks/queries/useLocations';
import { useAppStore } from '@/stores/appStore';
import { createPantryItem } from '@/api/pantryItems';
import { useMutation, useQueryClient } from '@tanstack/react-query';
import type { ProductResponse, CreatePantryItemRequest } from '@/types';
import Toast from 'react-native-toast-message';
import * as Haptics from 'expo-haptics';
import { PANTRY_UNITS } from "@/utils/pantryUnits";
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

    // ── Expiration date state ────────────────────────
    const guessedDate = useMemo(
        () =>
            guessExpirationDate(
                product.name,
                product.category,
                product.defaultExpirationDays,
            ),
        [product],
    );

    const [expirationDate, setExpirationDate] = useState<Date | null>(
        guessedDate,
    );
    const [showDatePicker, setShowDatePicker] = useState(false);

    // Auto-select first location
    React.useEffect(() => {
        if (locations.length > 0 && !selectedLocationId) {
            setSelectedLocationId(locations[0].id);
        }
    }, [locations, selectedLocationId]);

    const selectedLocation = useMemo(
        () => locations.find((l) => l.id === selectedLocationId),
        [locations, selectedLocationId],
    );

    const addMutation = useMutation({
        mutationFn: createPantryItem,
        onSuccess: async () => {
            await Haptics.notificationAsync(Haptics.NotificationFeedbackType.Success);
            await queryClient.invalidateQueries({ queryKey: ['pantryItems'] });
            await queryClient.invalidateQueries({ queryKey: ['pantryStatistics'] });
            Toast.show({ type: 'success', text1: 'Added to Pantry', text2: product.name });
            onAddedToPantry();
        },
        onError: (error) => {
            Toast.show({ type: 'error', text1: 'Failed to add', text2: error.message });
        },
    });

    const handleDateChange = useCallback(
        (selectedDate: Date) => {
            setExpirationDate(selectedDate);
            setShowDatePicker(false);
        },
        [],
    );

    const formattedDate = useMemo(() => {
        if (!expirationDate) return '';
        return expirationDate.toISOString().split('T')[0];
    }, [expirationDate]);

    const handleAddToPantry = useCallback(() => {
        if (!selectedLocationId) {
            Toast.show({ type: 'error', text1: 'Select a location' });
            return;
        }

        const request: CreatePantryItemRequest = {
            upc: product.upc,
            locationId: selectedLocationId,
            quantity,
            unitOfMeasure: unit,
            expirationDate: formattedDate || null,
            purchaseDate: new Date().toISOString().split('T')[0],
        };

        addMutation.mutate(request);
    }, [product.upc, selectedLocationId, quantity, unit, formattedDate, addMutation]);

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
                    <View style={styles.productIcon}>
                        <Text size="xl">🥗</Text>
                    </View>
                    <View style={styles.productInfo}>
                        <Text size="lg" weight="bold" numberOfLines={2}>
                            {product.name}
                        </Text>
                        {product.brand && (
                            <Text size="sm" color="secondary">{product.brand}</Text>
                        )}
                        {product.category && (
                            <Text size="sm" color="secondary">{product.category}</Text>
                        )}
                        {product.upc && (
                            <Text size="sm" color="secondary">UPC: {product.upc}</Text>
                        )}
                    </View>
                </View>

                {/* Confirmation prompt */}
                <View style={styles.confirmRow}>
                    <Text size="sm" color="secondary">
                        Is this information correct?
                    </Text>
                </View>

                {/* Location Picker */}
                <Text size="sm" weight="medium" color="secondary" style={styles.fieldLabel}>
                    Location *
                </Text>
                <Pressable
                    style={styles.pickerButton}
                    onPress={() => setShowLocationPicker(!showLocationPicker)}
                >
                    <Text size="md">{selectedLocation?.name || 'Select location'}</Text>
                    <Text size="md" color="secondary">▼</Text>
                </Pressable>
                {showLocationPicker && (
                    <View style={styles.dropdownList}>
                        <ScrollView style={{ maxHeight: 160 }} nestedScrollEnabled>
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
                        </ScrollView>
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
                                onPress={() => setQuantity(Math.max(1, quantity - 1))}
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

                    {/* ── Unit Dropdown ── */}
                    <View style={{ flex: 1, marginLeft: spacing.md }}>
                        <Text size="sm" weight="medium" color="secondary" style={styles.fieldLabel}>
                            Unit
                        </Text>
                        <Pressable
                            style={styles.pickerButton}
                            onPress={() => setShowUnitPicker(!showUnitPicker)}
                        >
                            <Text size="md">{unit}</Text>
                            <Text size="md" color="secondary">▼</Text>
                        </Pressable>
                    </View>
                </View>

                {showUnitPicker && (
                    <View style={styles.dropdownList}>
                        <ScrollView style={{ maxHeight: 200 }} nestedScrollEnabled>
                            {PANTRY_UNITS.map((u) => (
                                <Pressable
                                    key={u}
                                    style={[
                                        styles.dropdownItem,
                                        u === unit && styles.dropdownItemSelected,
                                    ]}
                                    onPress={() => {
                                        setUnit(u);
                                        setShowUnitPicker(false);
                                    }}
                                >
                                    <Text
                                        size="md"
                                        weight={u === unit ? 'semibold' : 'regular'}
                                    >
                                        {u}
                                    </Text>
                                </Pressable>
                            ))}
                        </ScrollView>
                    </View>
                )}

                {/* ── Expiration Date (Calendar Picker) ── */}
                <Text size="sm" weight="medium" color="secondary" style={[styles.fieldLabel, { marginTop: spacing.md }]}>
                    Expiration Date
                </Text>
                <Pressable
                    style={styles.pickerButton}
                    onPress={() => setShowDatePicker(!showDatePicker)}
                >
                    <Text
                        size="md"
                        color={expirationDate ? 'primary' : 'secondary'}
                    >
                        {expirationDate
                            ? formattedDate
                            : 'Tap to select a date'}
                    </Text>
                    <Text size="md" color="secondary">📅</Text>
                </Pressable>

                {expirationDate && guessedDate && (
                    <Text size="sm" color="secondary" style={{ marginBottom: spacing.xs }}>
                        Estimated from product type
                    </Text>
                )}

                {expirationDate && (
                    <Pressable
                        onPress={() => setExpirationDate(null)}
                        hitSlop={8}
                        style={{ marginBottom: spacing.sm }}
                    >
                        <Text size="sm" weight="medium" style={{ color: palette.red400 }}>
                            Clear date
                        </Text>
                    </Pressable>
                )}

                {showDatePicker && (
                    <DatePickerModal
                        visible={showDatePicker}
                        value={expirationDate ?? new Date()}
                        minimumDate={new Date()}
                        onConfirm={handleDateChange}
                        onCancel={() => setShowDatePicker(false)}
                    />
                )}

                {/* Actions */}
                <Button
                    label="Add to Pantry"
                    onPress={handleAddToPantry}
                    loading={addMutation.isPending}
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
    productCard: {
        flexDirection: 'row',
        backgroundColor: palette.white,
        borderRadius: radius.md,
        padding: spacing.md,
        marginBottom: spacing.md,
        borderWidth: 1,
        borderColor: palette.cream300,
    },
    productIcon: {
        width: 56,
        height: 56,
        borderRadius: radius.sm,
        backgroundColor: palette.cream200,
        alignItems: 'center',
        justifyContent: 'center',
        marginRight: spacing.md,
    },
    productInfo: {
        flex: 1,
        gap: 2,
    },
    confirmRow: {
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
    unitDropdownOverlay: {
        position: 'absolute',
        top: '100%',
        left: 0,
        right: 0,
        zIndex: 20,
        elevation: 5,
        shadowColor: '#000',
        shadowOffset: { width: 0, height: 2 },
        shadowOpacity: 0.1,
        shadowRadius: 4,
    },
    quantityRow: {
        flexDirection: 'row',
        alignItems: 'flex-start',
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
    datePickerContainer: {
        backgroundColor: palette.white,
        borderRadius: radius.md,
        borderWidth: 1,
        borderColor: palette.cream300,
        padding: spacing.sm,
        marginBottom: spacing.md,
    },
});