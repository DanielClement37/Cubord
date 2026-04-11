import React, { useState, useCallback, useRef, useEffect, useMemo } from 'react';
import {
    View,
    ScrollView,
    StyleSheet,
    Pressable,
} from 'react-native';
import { Gesture, GestureDetector } from 'react-native-gesture-handler';
import Animated, {
    useSharedValue,
    useAnimatedStyle,
    withSequence,
    withTiming,
} from 'react-native-reanimated';
import { useAppStore } from '@/stores/appStore';
import { useUpdatePantryItem, useDeletePantryItem } from '@/hooks/mutations';
import { useLocations } from '@/hooks/queries';
import { ScreenContainer, Text, TextInput, ProductImage } from '@/components/ui';
import {
    ExpirationStatusBadge,
    QuantityStepper,
    DeleteConfirmation,
    ZeroQuantityModal,
} from '@/components/pantry';
import { LocationPicker, ExpirationDatePicker } from '@/components/scan';
import { palette } from '@/styles/colors';
import { spacing, radius } from '@/styles/tokens';
import { Ionicons } from '@expo/vector-icons';
import type { PantryItemResponse } from '@/types';
import {runOnJS} from "react-native-worklets";

/** Horizontal swipe distance threshold to trigger an increment/decrement. */
const SWIPE_THRESHOLD = 50;

/** Debounce delay for syncing quantity changes to the server. */
const QUANTITY_DEBOUNCE_MS = 600;

interface ItemDetailScreenProps {
    item: PantryItemResponse;
    onBack: () => void;
}

// ── Helpers ──────────────────────────────────

/**
 * Formats an ISO date string (YYYY-MM-DD) to a more readable form.
 * Example: "2026-04-15" → "Apr 15, 2026"
 */
function formatDate(iso: string): string {
    const parts = iso.split('-');
    if (parts.length !== 3) return iso;
    const [y, m, d] = parts.map(Number);
    const date = new Date(y, m - 1, d);
    return date.toLocaleDateString('en-US', {
        month: 'short',
        day: 'numeric',
        year: 'numeric',
    });
}

/** Parses "YYYY-MM-DD" → Date at local midnight, or null. */
function parseIsoDate(iso: string | null): Date | null {
    if (!iso) return null;
    const parts = iso.split('-');
    if (parts.length !== 3) return null;
    const [y, m, d] = parts.map(Number);
    if (isNaN(y) || isNaN(m) || isNaN(d)) return null;
    return new Date(y, m - 1, d);
}

/** Date → "YYYY-MM-DD" */
function toIsoString(date: Date | null): string | null {
    if (!date) return null;
    const y = date.getFullYear();
    const m = String(date.getMonth() + 1).padStart(2, '0');
    const d = String(date.getDate()).padStart(2, '0');
    return `${y}-${m}-${d}`;
}

export function ItemDetailScreen({ item, onBack }: ItemDetailScreenProps) {
    const householdId = useAppStore((s) => s.activeHouseholdId) ?? '';

    // ── Queries ──────────────────────────────
    const { data: locations = [] } = useLocations(householdId || undefined);

    // ── Local state — view mode ──────────────
    const [quantity, setQuantity] = useState(item.quantity ?? 1);
    const [showDeleteConfirm, setShowDeleteConfirm] = useState(false);
    const [showZeroModal, setShowZeroModal] = useState(false);

    // ── Local state — edit mode ──────────────
    const [isEditing, setIsEditing] = useState(false);
    const [editLocationId, setEditLocationId] = useState(item.location.id);
    const [showLocationPicker, setShowLocationPicker] = useState(false);
    const [editExpirationDate, setEditExpirationDate] = useState<Date | null>(
        parseIsoDate(item.expirationDate),
    );
    const [showDatePicker, setShowDatePicker] = useState(false);
    const [editNotes, setEditNotes] = useState(item.notes ?? '');

    // ── Mutations ────────────────────────────
    const updateMutation = useUpdatePantryItem({
        householdId,
        silent: true,
    });

    const saveMutation = useUpdatePantryItem({
        householdId,
        onSuccess: (updated) => {
            setIsEditing(false);
            // Sync local state with the response
            setEditLocationId(updated.location.id);
            setEditExpirationDate(parseIsoDate(updated.expirationDate));
            setEditNotes(updated.notes ?? '');
        },
    });

    const deleteMutation = useDeletePantryItem({
        householdId,
        onSuccess: onBack,
    });

    // ── Debounced quantity sync ──────────────
    const debounceRef = useRef<ReturnType<typeof setTimeout> | null>(null);

    const syncQuantity = useCallback(
        (newQty: number) => {
            if (debounceRef.current) clearTimeout(debounceRef.current);

            debounceRef.current = setTimeout(() => {
                updateMutation.mutate({
                    id: item.id,
                    data: { quantity: newQty },
                });
            }, QUANTITY_DEBOUNCE_MS);
        },
        [item.id, updateMutation],
    );

    useEffect(() => {
        return () => {
            if (debounceRef.current) clearTimeout(debounceRef.current);
        };
    }, []);

    // ── Quantity handlers ────────────────────
    const handleIncrement = useCallback(() => {
        setQuantity((prev) => {
            const next = prev + 1;
            syncQuantity(next);
            return next;
        });
    }, [syncQuantity]);

    const handleDecrement = useCallback(() => {
        setQuantity((prev) => {
            if (prev <= 1) {
                setShowZeroModal(true);
                return prev;
            }
            const next = prev - 1;
            syncQuantity(next);
            return next;
        });
    }, [syncQuantity]);

    const handleZeroRemove = useCallback(() => {
        setShowZeroModal(false);
        deleteMutation.mutate(item.id);
    }, [deleteMutation, item.id]);

    const handleZeroKeep = useCallback(() => {
        setShowZeroModal(false);
    }, []);

    // ── Delete handlers ──────────────────────
    const handleConfirmDelete = useCallback(() => {
        deleteMutation.mutate(item.id);
    }, [deleteMutation, item.id]);

    // ── Edit handlers ────────────────────────
    const handleToggleEdit = useCallback(() => {
        if (isEditing) {
            // Cancel — reset to original values
            setEditLocationId(item.location.id);
            setEditExpirationDate(parseIsoDate(item.expirationDate));
            setEditNotes(item.notes ?? '');
            setShowLocationPicker(false);
            setShowDatePicker(false);
        }
        setIsEditing((prev) => !prev);
    }, [isEditing, item]);

    const handleSave = useCallback(() => {
        const newExpDate = toIsoString(editExpirationDate);
        const hasLocationChanged = editLocationId !== item.location.id;
        const hasExpirationChanged = newExpDate !== (item.expirationDate ?? null);
        const hasNotesChanged = editNotes.trim() !== (item.notes ?? '');

        if (!hasLocationChanged && !hasExpirationChanged && !hasNotesChanged) {
            setIsEditing(false);
            return;
        }

        saveMutation.mutate({
            id: item.id,
            data: {
                ...(hasLocationChanged ? { locationId: editLocationId } : {}),
                ...(hasExpirationChanged ? { expirationDate: newExpDate } : {}),
                ...(hasNotesChanged ? { notes: editNotes.trim() || null } : {}),
            },
        });
    }, [editLocationId, editExpirationDate, editNotes, item, saveMutation]);

    const handleDateChange = useCallback((date: Date | null) => {
        setEditExpirationDate(date);
        if (date) setShowDatePicker(false);
    }, []);

    // ── Gesture: swipe on quantity row ───────
    const qtyScale = useSharedValue(1);

    const pulseAnimation = useCallback(() => {
        qtyScale.value = withSequence(
            withTiming(1.15, { duration: 100 }),
            withTiming(1, { duration: 100 }),
        );
    }, [qtyScale]);

    const swipeGesture = Gesture.Pan()
        .activeOffsetX([-SWIPE_THRESHOLD, SWIPE_THRESHOLD])
        .failOffsetY([-20, 20])
        .onEnd((event) => {
            'worklet';
            if (event.translationX > SWIPE_THRESHOLD) {
                runOnJS(handleIncrement)();
                pulseAnimation();
            } else if (event.translationX < -SWIPE_THRESHOLD) {
                runOnJS(handleDecrement)();
                pulseAnimation();
            }
        });

    const qtyAnimatedStyle = useAnimatedStyle(() => ({
        transform: [{ scale: qtyScale.value }],
    }));

    // ── Derived data ─────────────────────────
    const product = item.product;
    // In view mode, show the current edit-state values (so they update after a save)
    const displayLocationName = useMemo(() => {
        const loc = locations.find((l) => l.id === editLocationId);
        return loc?.name ?? item.location.name;
    }, [locations, editLocationId, item.location.name]);

    const displayExpirationDate = useMemo(
        () => toIsoString(editExpirationDate),
        [editExpirationDate],
    );
    const displayNotes = editNotes.trim() || item.notes;

    const hasExpiration = !!displayExpirationDate;
    const formattedDate = displayExpirationDate
        ? formatDate(displayExpirationDate)
        : null;

    return (
        <ScreenContainer>
            {/* Header */}
            <View style={styles.header}>
                <Pressable
                    onPress={onBack}
                    hitSlop={12}
                    style={styles.backButton}
                    accessibilityRole="button"
                    accessibilityLabel="Go back"
                >
                    <Ionicons
                        name="chevron-back"
                        size={22}
                        color={palette.sand800}
                    />
                    <Text size="md" weight="semibold">
                        Back
                    </Text>
                </Pressable>

                <Pressable
                    onPress={handleToggleEdit}
                    style={[
                        styles.editButton,
                        isEditing && styles.editButtonActive,
                    ]}
                    hitSlop={8}
                    accessibilityRole="button"
                    accessibilityLabel={isEditing ? 'Cancel editing' : 'Edit item'}
                >
                    <Text
                        size="sm"
                        weight="semibold"
                        style={isEditing ? { color: palette.red400 } : undefined}
                    >
                        {isEditing ? 'Cancel' : 'Edit'}
                    </Text>
                </Pressable>
            </View>

            <ScrollView
                showsVerticalScrollIndicator={false}
                contentContainerStyle={styles.scroll}
                keyboardShouldPersistTaps="handled"
            >
                {/* Product image */}
                <View style={styles.imageContainer}>
                    <ProductImage
                        imageUrl={product.imageUrl}
                        name={product.name}
                        category={product.category}
                        size={120}
                        style={styles.productImage}
                    />
                </View>

                {/* Product info (read-only — product data is not editable here) */}
                <View style={styles.productInfo}>
                    <Text size="xl" weight="bold" align="center">
                        {product.name}
                    </Text>
                    {product.brand ? (
                        <Text
                            size="md"
                            color="secondary"
                            align="center"
                            style={styles.brand}
                        >
                            {product.brand}
                        </Text>
                    ) : null}
                    {product.category ? (
                        <Text size="sm" color="secondary" align="center">
                            {product.category}
                        </Text>
                    ) : null}
                </View>

                {/* ── Edit mode form ──────────────────── */}
                {isEditing ? (
                    <View style={styles.editSection}>
                        {/* Location picker */}
                        <LocationPicker
                            locations={locations}
                            selectedLocationId={editLocationId}
                            onSelectLocation={(id) => {
                                setEditLocationId(id);
                                setShowLocationPicker(false);
                            }}
                            showPicker={showLocationPicker}
                            onTogglePicker={() =>
                                setShowLocationPicker((v) => !v)
                            }
                        />

                        {/* Expiration date picker */}
                        <ExpirationDatePicker
                            expirationDate={editExpirationDate}
                            onDateChange={handleDateChange}
                            showPicker={showDatePicker}
                            onTogglePicker={() =>
                                setShowDatePicker((v) => !v)
                            }
                        />

                        {/* Notes */}
                        <TextInput
                            label="Notes"
                            placeholder="Add notes (optional)"
                            value={editNotes}
                            onChangeText={setEditNotes}
                            multiline
                            numberOfLines={3}
                            style={styles.notesInput}
                        />

                        {/* Save button */}
                        <Pressable
                            onPress={handleSave}
                            style={[
                                styles.saveButton,
                                saveMutation.isPending && styles.saveButtonDisabled,
                            ]}
                            disabled={saveMutation.isPending}
                            accessibilityRole="button"
                            accessibilityLabel="Save changes"
                        >
                            <Ionicons
                                name="checkmark"
                                size={18}
                                color={palette.white}
                                style={{ marginRight: spacing.xs }}
                            />
                            <Text
                                size="md"
                                weight="semibold"
                                style={{ color: palette.white }}
                            >
                                {saveMutation.isPending
                                    ? 'Saving…'
                                    : 'Save Changes'}
                            </Text>
                        </Pressable>
                    </View>
                ) : (
                    /* ── View mode detail rows ────────── */
                    <View style={styles.detailSection}>
                        {/* Location */}
                        <View style={styles.detailRow}>
                            <View style={styles.detailLeft}>
                                <Ionicons
                                    name="location-outline"
                                    size={18}
                                    color={palette.sand500}
                                />
                                <Text size="md" weight="semibold">
                                    Location
                                </Text>
                            </View>
                            <View style={styles.detailRight}>
                                <Text size="md" color="secondary">
                                    {displayLocationName}
                                </Text>
                            </View>
                        </View>

                        {/* Quantity — with swipe gesture */}
                        <GestureDetector gesture={swipeGesture}>
                            <Animated.View
                                style={[styles.detailRow, styles.quantityRow]}
                            >
                                <View style={styles.detailLeft}>
                                    <Ionicons
                                        name="cube-outline"
                                        size={18}
                                        color={palette.sand500}
                                    />
                                    <Text size="md" weight="semibold">
                                        Quantity
                                    </Text>
                                </View>
                                <Animated.View style={qtyAnimatedStyle}>
                                    <QuantityStepper
                                        value={quantity}
                                        onIncrement={handleIncrement}
                                        onDecrement={handleDecrement}
                                        min={1}
                                    />
                                </Animated.View>
                            </Animated.View>
                        </GestureDetector>

                        {/* Swipe hint */}
                        <View style={styles.swipeHint}>
                            <Ionicons
                                name="swap-horizontal"
                                size={12}
                                color={palette.sand300}
                            />
                            <Text
                                size="sm"
                                color="secondary"
                                style={{ color: palette.sand300 }}
                            >
                                Swipe row to adjust quantity
                            </Text>
                        </View>

                        {/* Expiration date */}
                        {hasExpiration && (
                            <View style={styles.detailRow}>
                                <View style={styles.detailLeft}>
                                    <Ionicons
                                        name="time-outline"
                                        size={18}
                                        color={palette.sand500}
                                    />
                                    <View>
                                        <Text size="md" weight="semibold">
                                            Expiration Date
                                        </Text>
                                        <View style={styles.expirationBadge}>
                                            <ExpirationStatusBadge
                                                expirationDate={
                                                    displayExpirationDate
                                                }
                                                size="md"
                                            />
                                        </View>
                                    </View>
                                </View>
                                <Text size="md" color="secondary">
                                    {formattedDate}
                                </Text>
                            </View>
                        )}

                        {/* UPC */}
                        {product.upc ? (
                            <View
                                style={[
                                    styles.detailRow,
                                    !displayNotes && styles.detailRowLast,
                                ]}
                            >
                                <View style={styles.detailLeft}>
                                    <Ionicons
                                        name="barcode-outline"
                                        size={18}
                                        color={palette.sand500}
                                    />
                                    <Text size="md" weight="semibold">
                                        UPC
                                    </Text>
                                </View>
                                <Text
                                    size="md"
                                    color="secondary"
                                    style={styles.upcText}
                                >
                                    {product.upc}
                                </Text>
                            </View>
                        ) : null}

                        {/* Notes */}
                        {displayNotes ? (
                            <View
                                style={[styles.detailRow, styles.detailRowLast]}
                            >
                                <View style={styles.detailLeft}>
                                    <Ionicons
                                        name="document-text-outline"
                                        size={18}
                                        color={palette.sand500}
                                    />
                                    <Text size="md" weight="semibold">
                                        Notes
                                    </Text>
                                </View>
                                <Text
                                    size="sm"
                                    color="secondary"
                                    style={styles.viewNotesText}
                                    numberOfLines={3}
                                >
                                    {displayNotes}
                                </Text>
                            </View>
                        ) : null}
                    </View>
                )}

                {/* Delete section — always visible */}
                <View style={styles.deleteSection}>
                    <DeleteConfirmation
                        isConfirming={showDeleteConfirm}
                        productName={product.name}
                        isDeleting={deleteMutation.isPending}
                        onRequestDelete={() => setShowDeleteConfirm(true)}
                        onConfirmDelete={handleConfirmDelete}
                        onCancel={() => setShowDeleteConfirm(false)}
                    />
                </View>
            </ScrollView>

            {/* Zero-quantity modal */}
            <ZeroQuantityModal
                visible={showZeroModal}
                productName={product.name}
                onKeep={handleZeroKeep}
                onRemove={handleZeroRemove}
            />
        </ScreenContainer>
    );
}

// ── Styles ───────────────────────────────────

const styles = StyleSheet.create({
    header: {
        flexDirection: 'row',
        alignItems: 'center',
        justifyContent: 'space-between',
        marginBottom: spacing.md,
    },
    backButton: {
        flexDirection: 'row',
        alignItems: 'center',
        gap: spacing.xs,
    },
    editButton: {
        paddingHorizontal: spacing.md,
        paddingVertical: spacing.sm - 2,
        borderRadius: radius.sm,
        borderWidth: 1,
        borderColor: palette.sand100,
        backgroundColor: palette.white,
    },
    editButtonActive: {
        borderColor: palette.red100,
        backgroundColor: palette.red50,
    },
    scroll: {
        paddingBottom: spacing.xxl + spacing.lg,
    },
    imageContainer: {
        alignItems: 'center',
        marginBottom: spacing.lg,
    },
    productImage: {
        borderRadius: radius.lg,
    },
    productInfo: {
        alignItems: 'center',
        marginBottom: spacing.lg,
    },
    brand: {
        marginTop: spacing.xs - 1,
    },

    // ── Edit mode ────────────────────────────
    editSection: {
        marginBottom: spacing.lg,
    },
    notesInput: {
        height: 80,
        textAlignVertical: 'top',
        paddingTop: spacing.sm + 4,
    },
    saveButton: {
        flexDirection: 'row',
        alignItems: 'center',
        justifyContent: 'center',
        height: 48,
        borderRadius: radius.md,
        backgroundColor: palette.sage500,
        marginTop: spacing.lg,
    },
    saveButtonDisabled: {
        opacity: 0.6,
    },

    // ── View mode ────────────────────────────
    detailSection: {
        marginBottom: spacing.lg,
    },
    detailRow: {
        flexDirection: 'row',
        alignItems: 'center',
        justifyContent: 'space-between',
        paddingVertical: spacing.md,
        borderBottomWidth: 1,
        borderBottomColor: palette.sand100,
    },
    detailRowLast: {
        borderBottomWidth: 0,
    },
    quantityRow: {
        minHeight: 56,
    },
    detailLeft: {
        flexDirection: 'row',
        alignItems: 'center',
        gap: spacing.sm + 2,
        flexShrink: 1,
    },
    detailRight: {
        flexDirection: 'row',
        alignItems: 'center',
        gap: spacing.xs,
    },
    swipeHint: {
        flexDirection: 'row',
        alignItems: 'center',
        gap: spacing.xs,
        justifyContent: 'center',
        paddingTop: spacing.xs,
        paddingBottom: spacing.sm,
    },
    expirationBadge: {
        marginTop: spacing.xs,
    },
    upcText: {
        fontFamily: 'monospace',
    },
    viewNotesText: {
        flex: 1,
        textAlign: 'right',
        marginLeft: spacing.md,
    },
    deleteSection: {
        marginTop: spacing.md,
    },
});