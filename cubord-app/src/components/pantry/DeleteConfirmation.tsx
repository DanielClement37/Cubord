import React from 'react';
import { View, Pressable, StyleSheet } from 'react-native';
import { Text, Button } from '@/components/ui';
import { palette } from '@/styles/colors';
import { spacing, radius } from '@/styles/tokens';

interface DeleteConfirmationProps {
    /** Whether the confirmation panel is currently showing. */
    isConfirming: boolean;
    /** Product name to show in the confirmation message. */
    productName: string;
    /** Show spinner on the delete button. */
    isDeleting?: boolean;
    /** Called when the user taps the initial "Delete Item" button. */
    onRequestDelete: () => void;
    /** Called when the user confirms deletion. */
    onConfirmDelete: () => void;
    /** Called when the user cancels deletion. */
    onCancel: () => void;
}

/**
 * Inline delete section for the item detail screen.
 * Starts as a single "Delete Item" button, expands to a confirmation panel.
 */
export function DeleteConfirmation({
                                       isConfirming,
                                       productName,
                                       isDeleting = false,
                                       onRequestDelete,
                                       onConfirmDelete,
                                       onCancel,
                                   }: DeleteConfirmationProps) {
    if (!isConfirming) {
        return (
            <Pressable
                onPress={onRequestDelete}
                style={styles.deleteButton}
                accessibilityRole="button"
                accessibilityLabel="Delete item"
            >
                <Text size="md" weight="bold" style={{ color: palette.red400 }}>
                    Delete Item
                </Text>
            </Pressable>
        );
    }

    return (
        <View style={styles.confirmContainer}>
            <Text
                size="md"
                weight="semibold"
                align="center"
                style={{ color: palette.red400, marginBottom: spacing.sm + 4 }}
            >
                Remove "{productName}" from your pantry?
            </Text>

            <View style={styles.confirmActions}>
                <Pressable
                    onPress={onCancel}
                    style={styles.cancelButton}
                    disabled={isDeleting}
                    accessibilityRole="button"
                    accessibilityLabel="Cancel delete"
                >
                    <Text size="md" weight="semibold">Cancel</Text>
                </Pressable>

                <View style={styles.confirmDeleteButton}>
                    <Button
                        label="Delete"
                        variant="danger"
                        onPress={onConfirmDelete}
                        loading={isDeleting}
                    />
                </View>
            </View>
        </View>
    );
}

const styles = StyleSheet.create({
    deleteButton: {
        alignItems: 'center',
        justifyContent: 'center',
        paddingVertical: spacing.md,
        borderRadius: radius.md + 2,
        backgroundColor: palette.red50,
        borderWidth: 1,
        borderColor: palette.red100,
    },
    confirmContainer: {
        backgroundColor: palette.red50,
        borderRadius: radius.md + 2,
        borderWidth: 1,
        borderColor: palette.red100,
        padding: spacing.md,
    },
    confirmActions: {
        flexDirection: 'row',
        gap: spacing.sm + 2,
    },
    cancelButton: {
        flex: 1,
        height: 44,
        alignItems: 'center',
        justifyContent: 'center',
        borderRadius: radius.sm + 2,
        backgroundColor: palette.white,
        borderWidth: 1,
        borderColor: palette.sand100,
    },
    confirmDeleteButton: {
        flex: 1,
    },
});