// src/components/dashboard/AddLocationModal.tsx
import React, { useState } from 'react';
import {
    View,
    Modal,
    Pressable,
    KeyboardAvoidingView,
    Platform,
    StyleSheet,
} from 'react-native';
import { Text, TextInput, Button } from '@/components/ui';
import { useCreateLocation } from '@/hooks/mutations';
import { useAppStore } from '@/stores/appStore';
import { palette } from '@/styles/colors';
import { lightTheme } from '@/styles/themes/light';
import { spacing, radius, shadow } from '@/styles/tokens';
import { showError } from '@/utils/toast';

interface AddLocationModalProps {
    visible: boolean;
    onClose: () => void;
}

export function AddLocationModal({ visible, onClose }: AddLocationModalProps) {
    const activeHouseholdId = useAppStore((s) => s.activeHouseholdId);
    const [name, setName] = useState('');
    const [description, setDescription] = useState('');
    const [error, setError] = useState('');
    const { mutate, isPending } = useCreateLocation();

    const resetForm = () => {
        setName('');
        setDescription('');
        setError('');
    };

    const handleClose = () => {
        resetForm();
        onClose();
    };

    const handleCreate = () => {
        const trimmed = name.trim();
        if (trimmed.length < 2) {
            setError('Name must be at least 2 characters');
            return;
        }
        if (!activeHouseholdId) {
            showError('No household selected', 'Please select a household first.');
            return;
        }
        setError('');
        mutate(
            {
                name: trimmed,
                description: description.trim() || null,
                householdId: activeHouseholdId,
            },
            {
                onSuccess: () => {
                    resetForm();
                    onClose();
                },
                onError: () => {
                    showError('Failed to create location', 'Please try again.');
                },
            },
        );
    };

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

                        <Text size="lg" weight="bold" align="center" style={styles.title}>
                            Add New Location
                        </Text>

                        <View style={styles.form}>
                            <TextInput
                                label="Location name"
                                placeholder="e.g. Kitchen Fridge"
                                value={name}
                                onChangeText={setName}
                                maxLength={255}
                                autoFocus
                                error={error || undefined}
                            />

                            <TextInput
                                label="Description (optional)"
                                placeholder="e.g. Main refrigerator in the kitchen"
                                value={description}
                                onChangeText={setDescription}
                                maxLength={500}
                            />
                        </View>

                        <View style={styles.buttons}>
                            <Pressable onPress={handleClose} style={styles.cancelBtn}>
                                <Text size="md" weight="medium" color="secondary">Cancel</Text>
                            </Pressable>
                            <Button
                                label="Create"
                                loading={isPending}
                                onPress={handleCreate}
                                style={styles.createBtn}
                            />
                        </View>
                    </Pressable>
                </Pressable>
            </KeyboardAvoidingView>
        </Modal>
    );
}

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
    form: {
        gap: spacing.lg,
    },
    buttons: {
        flexDirection: 'row',
        justifyContent: 'flex-end',
        alignItems: 'center',
        gap: spacing.md,
        marginTop: spacing.lg,
    },
    cancelBtn: {
        paddingVertical: spacing.sm,
        paddingHorizontal: spacing.md,
    },
    createBtn: {
        minWidth: 100,
    },
});