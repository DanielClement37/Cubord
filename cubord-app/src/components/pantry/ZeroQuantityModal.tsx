import React from 'react';
import { View, Modal, Pressable, StyleSheet } from 'react-native';
import { Text, Button } from '@/components/ui';
import { palette } from '@/styles/colors';
import { spacing, radius, shadow } from '@/styles/tokens';

interface ZeroQuantityModalProps {
    visible: boolean;
    productName: string;
    /** Called when the user chooses to keep the item. */
    onKeep: () => void;
    /** Called when the user confirms removal. */
    onRemove: () => void;
}

/**
 * Modal confirmation shown when the user decrements quantity to 0.
 */
export function ZeroQuantityModal({
                                      visible,
                                      productName,
                                      onKeep,
                                      onRemove,
                                  }: ZeroQuantityModalProps) {
    return (
        <Modal
            visible={visible}
            transparent
            animationType="fade"
            statusBarTranslucent
            onRequestClose={onKeep}
        >
            <View style={styles.overlay}>
                <View style={styles.card}>
                    <Text size="lg" weight="bold" style={styles.title}>
                        Remove item?
                    </Text>
                    <Text size="md" color="secondary" style={styles.body}>
                        Setting quantity to 0 will remove "{productName}" from
                        your pantry.
                    </Text>

                    <View style={styles.actions}>
                        <Pressable
                            onPress={onKeep}
                            style={styles.keepButton}
                            accessibilityRole="button"
                            accessibilityLabel="Keep item"
                        >
                            <Text size="md" weight="semibold">Keep Item</Text>
                        </Pressable>

                        <View style={styles.removeButton}>
                            <Button
                                label="Remove"
                                variant="danger"
                                onPress={onRemove}
                            />
                        </View>
                    </View>
                </View>
            </View>
        </Modal>
    );
}

const styles = StyleSheet.create({
    overlay: {
        flex: 1,
        backgroundColor: 'rgba(0, 0, 0, 0.35)',
        alignItems: 'center',
        justifyContent: 'center',
        paddingHorizontal: spacing.lg,
    },
    card: {
        width: '100%',
        backgroundColor: palette.white,
        borderRadius: radius.lg + 2,
        padding: spacing.lg,
        ...shadow.lg,
    },
    title: {
        marginBottom: spacing.xs + 2,
    },
    body: {
        marginBottom: spacing.lg,
        lineHeight: 22,
    },
    actions: {
        flexDirection: 'row',
        gap: spacing.sm + 2,
    },
    keepButton: {
        flex: 1,
        height: 44,
        alignItems: 'center',
        justifyContent: 'center',
        borderRadius: radius.sm + 2,
        backgroundColor: palette.cream300,
    },
    removeButton: {
        flex: 1,
    },
});