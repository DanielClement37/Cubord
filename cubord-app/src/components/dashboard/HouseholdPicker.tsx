// src/components/dashboard/HouseholdPicker.tsx
import React, { useState } from 'react';
import {
    View,
    Modal,
    Pressable,
    FlatList,
    KeyboardAvoidingView,
    Platform,
    StyleSheet,
} from 'react-native';
import { useQueryClient } from '@tanstack/react-query';
import { Ionicons } from '@expo/vector-icons';
import { Text, TextInput, Button, Spinner } from '@/components/ui';
import { useHouseholds } from '@/hooks/queries';
import { useCreateHousehold } from '@/hooks/mutations';
import { useAppStore } from '@/stores/appStore';
import type { HouseholdResponse } from '@/types';
import { palette } from '@/styles/colors';
import { lightTheme } from '@/styles/themes/light';
import { spacing, radius, shadow } from '@/styles/tokens';
import { showError } from '@/utils/toast';

// ── Sub-views ────────────────────────────────────────

interface HouseholdRowProps {
    household: HouseholdResponse;
    isActive: boolean;
    onPress: () => void;
}

function HouseholdRow({ household, isActive, onPress }: HouseholdRowProps) {
    return (
        <Pressable
            style={[styles.row, isActive && styles.rowActive]}
            onPress={onPress}
            accessibilityRole="button"
            accessibilityLabel={`Select ${household.name}`}
            accessibilityState={{ selected: isActive }}
        >
            <View style={styles.rowIcon}>
                <Ionicons name="home" size={18} color={palette.sage600} />
            </View>

            <View style={styles.rowContent}>
                <Text size="md" weight={isActive ? 'bold' : 'semibold'}>
                    {household.name}
                </Text>
                <Text size="sm" color="secondary">Owner</Text>
            </View>

            {isActive && (
                <View style={styles.checkCircle}>
                    <Ionicons name="checkmark-circle" size={24} color={palette.sage500} />
                </View>
            )}
        </Pressable>
    );
}

// ── Create Household View ────────────────────────────

interface CreateViewProps {
    onCreated: () => void;
    onCancel: () => void;
}

function CreateHouseholdView({ onCreated, onCancel }: CreateViewProps) {
    const [name, setName] = useState('');
    const [error, setError] = useState('');
    const { mutate, isPending } = useCreateHousehold();

    const handleCreate = () => {
        const trimmed = name.trim();
        if (trimmed.length < 2) {
            setError('Name must be at least 2 characters');
            return;
        }
        setError('');
        mutate(
            { name: trimmed },
            {
                onSuccess: () => {
                    setName('');
                    setError('');
                    onCreated();
                },
                onError: () => {
                    showError('Failed to create household', 'Please try again.');
                },
            },
        );
    };

    return (
        <View style={styles.createView}>
            <Text size="lg" weight="bold" align="center" style={styles.createTitle}>
                Create New Household
            </Text>

            <TextInput
                label="Household name"
                placeholder="e.g. Beach House"
                value={name}
                onChangeText={setName}
                maxLength={50}
                autoFocus
                error={error || undefined}
            />

            <View style={styles.createButtons}>
                <Pressable onPress={onCancel} style={styles.cancelBtn}>
                    <Text size="md" weight="medium" color="secondary">Cancel</Text>
                </Pressable>
                <Button
                    label="Create"
                    loading={isPending}
                    onPress={handleCreate}
                    style={styles.createBtn}
                />
            </View>
        </View>
    );
}

// ── Main Picker ──────────────────────────────────────

interface HouseholdPickerProps {
    visible: boolean;
    onClose: () => void;
}

export function HouseholdPicker({ visible, onClose }: HouseholdPickerProps) {
    const queryClient = useQueryClient();
    const { data: households, isLoading } = useHouseholds();
    const activeHouseholdId = useAppStore((s) => s.activeHouseholdId);
    const setActiveHouseholdId = useAppStore((s) => s.setActiveHouseholdId);
    const [mode, setMode] = useState<'select' | 'create'>('select');

    const handleSelect = (household: HouseholdResponse) => {
        if (household.id === activeHouseholdId) {
            onClose();
            return;
        }

        setActiveHouseholdId(household.id);

        // Invalidate all household-scoped queries so the dashboard refreshes
        queryClient.invalidateQueries({ queryKey: ['pantry-items'] });
        queryClient.invalidateQueries({ queryKey: ['locations'] });
        onClose();
    };

    const handleCreated = () => {
        setMode('select');
        onClose();
    };

    const handleClose = () => {
        setMode('select');
        onClose();
    };

    const renderItem = ({ item }: { item: HouseholdResponse }) => (
        <HouseholdRow
            household={item}
            isActive={item.id === activeHouseholdId}
            onPress={() => handleSelect(item)}
        />
    );

    return (
        <Modal
            visible={visible}
            transparent
            animationType="fade"
            onRequestClose={handleClose}
        >
            <KeyboardAvoidingView
                behavior={Platform.OS === 'ios' ? 'padding' : 'height'}
                style={styles.keyboardView}
            >
                <Pressable style={styles.backdrop} onPress={handleClose}>
                    <Pressable
                        style={styles.sheet}
                        onPress={() => {}} // prevent close on sheet tap
                    >
                        {/* Handle bar */}
                        <View style={styles.handleRow}>
                            <View style={styles.handle} />
                        </View>

                        {mode === 'select' ? (
                            <>
                                {/* Title */}
                                <Text size="lg" weight="bold" align="center" style={styles.title}>
                                    Select Household
                                </Text>

                                {/* Household list */}
                                {isLoading ? (
                                    <View style={styles.loadingContainer}>
                                        <Spinner size="md" />
                                    </View>
                                ) : (
                                    <FlatList
                                        data={households}
                                        keyExtractor={(h) => h.id}
                                        renderItem={renderItem}
                                        style={styles.list}
                                        ItemSeparatorComponent={() => <View style={styles.separator} />}
                                        scrollEnabled={(households?.length ?? 0) > 4}
                                    />
                                )}

                                {/* Divider */}
                                <View style={styles.divider} />

                                {/* Create new button */}
                                <Pressable
                                    style={styles.createRow}
                                    onPress={() => setMode('create')}
                                    accessibilityRole="button"
                                    accessibilityLabel="Create New Household"
                                >
                                    <Ionicons name="add" size={20} color={palette.sand500} />
                                    <Text size="md" weight="semibold" color="secondary">
                                        Create New Household
                                    </Text>
                                </Pressable>
                            </>
                        ) : (
                            <CreateHouseholdView
                                onCreated={handleCreated}
                                onCancel={() => setMode('select')}
                            />
                        )}
                    </Pressable>
                </Pressable>
            </KeyboardAvoidingView>
        </Modal>
    );
}

// ── Styles ───────────────────────────────────────────

const styles = StyleSheet.create({
    keyboardView: {
        flex: 1,
    },
    backdrop: {
        flex: 1,
        backgroundColor: 'rgba(0,0,0,0.45)',
        justifyContent: 'center',
        alignItems: 'center',
        padding: spacing.lg,
    },
    sheet: {
        backgroundColor: lightTheme.colorSurface,
        borderRadius: radius.lg,
        paddingHorizontal: spacing.lg,
        paddingBottom: spacing.lg,
        width: '100%',
        maxWidth: 400,
        maxHeight: '70%',
        ...shadow.lg,
    },
    handleRow: {
        alignItems: 'center',
        paddingTop: spacing.sm,
        paddingBottom: spacing.md,
    },
    handle: {
        width: 40,
        height: 4,
        borderRadius: 2,
        backgroundColor: palette.sand200,
    },
    title: {
        marginBottom: spacing.md + 4,
    },
    loadingContainer: {
        paddingVertical: spacing.xl,
        alignItems: 'center',
    },
    list: {
        flexGrow: 0,
    },
    row: {
        flexDirection: 'row',
        alignItems: 'center',
        paddingVertical: spacing.md,
        paddingHorizontal: spacing.sm,
        borderRadius: radius.md,
        gap: spacing.sm + 4,
    },
    rowActive: {
        backgroundColor: palette.sage50,
    },
    rowIcon: {
        width: 40,
        height: 40,
        borderRadius: radius.full,
        backgroundColor: palette.cream200,
        justifyContent: 'center',
        alignItems: 'center',
    },
    rowContent: {
        flex: 1,
        gap: 1,
    },
    checkCircle: {
        marginLeft: spacing.xs,
    },
    separator: {
        height: 1,
        backgroundColor: palette.cream300,
        marginHorizontal: spacing.xs,
    },
    divider: {
        height: 1,
        backgroundColor: palette.cream300,
        marginTop: spacing.md,
        marginBottom: spacing.md,
    },
    createRow: {
        flexDirection: 'row',
        alignItems: 'center',
        justifyContent: 'center',
        gap: spacing.sm,
        paddingVertical: spacing.md,
        borderWidth: 1.5,
        borderColor: palette.cream300,
        borderStyle: 'dashed',
        borderRadius: radius.md,
    },
    createView: {
        gap: spacing.lg,
    },
    createTitle: {
        marginBottom: spacing.xs,
    },
    createButtons: {
        flexDirection: 'row',
        justifyContent: 'flex-end',
        alignItems: 'center',
        gap: spacing.md,
    },
    cancelBtn: {
        paddingVertical: spacing.sm,
        paddingHorizontal: spacing.md,
    },
    createBtn: {
        minWidth: 100,
    },
});