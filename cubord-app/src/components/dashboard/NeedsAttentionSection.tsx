// src/components/dashboard/NeedsAttentionSection.tsx
import React from 'react';
import { View, StyleSheet, Pressable } from 'react-native';
import { Text, Spinner } from '@/components/ui';
import { spacing, palette, radius, shadow } from '@/styles';
import { Ionicons } from '@expo/vector-icons';
import type { PantryItemResponse, PantryStatistics } from '@/types';

interface NeedsAttentionSectionProps {
    expiringItems: PantryItemResponse[] | undefined;
    statistics: PantryStatistics | undefined;
    isLoading: boolean;
    onExpiringSoonPress?: () => void;
    onLowStockPress?: () => void;
}

export function NeedsAttentionSection({
    expiringItems,
    statistics,
    isLoading,
    onExpiringSoonPress,
    onLowStockPress,
}: NeedsAttentionSectionProps) {
    if (isLoading) {
        return (
            <View style={styles.container}>
                <Text size="sm" weight="bold" color="secondary" style={styles.sectionLabel}>
                    NEEDS ATTENTION
                </Text>
                <View style={styles.loadingContainer}>
                    <Spinner size="md" />
                </View>
            </View>
        );
    }

    const expiringCount = expiringItems?.length ?? 0;
    const lowStockCount = statistics?.lowStockCount ?? 0;
    const hasExpiring = expiringCount > 0;
    const hasLowStock = lowStockCount > 0;

    // Build subtitle from the soonest expiring item
    const soonestItem = expiringItems?.[0];
    const expiringSubtitle = soonestItem
        ? `${soonestItem.product.name} expires soon`
        : undefined;

    // If nothing needs attention, show a positive message
    if (!hasExpiring && !hasLowStock) {
        return (
            <View style={styles.container}>
                <Text size="sm" weight="bold" color="secondary" style={styles.sectionLabel}>
                    NEEDS ATTENTION
                </Text>
                <View style={styles.emptyCard}>
                    <Text size="md" weight="semibold">Nothing needs attention 🎉</Text>
                    <Text size="sm" color="secondary">Everything is fresh and stocked!</Text>
                </View>
            </View>
        );
    }

    return (
        <View style={styles.container}>
            <Text size="sm" weight="bold" color="secondary" style={styles.sectionLabel}>
                NEEDS ATTENTION
            </Text>

            {/* Expiring Soon Card */}
            {hasExpiring && (
                <Pressable style={styles.alertCard} onPress={onExpiringSoonPress}>
                    <View style={[styles.iconCircle, { backgroundColor: palette.red50 }]}>
                        <Ionicons name="alert-circle" size={22} color={palette.red400} />
                    </View>
                    <View style={styles.alertContent}>
                        <View style={styles.alertTitleRow}>
                            <Text size="md" weight="semibold">Expiring Soon</Text>
                            <Text size="md" weight="bold" style={{ color: palette.red400 }}>
                                {' '}({expiringCount})
                            </Text>
                        </View>
                        {expiringSubtitle && (
                            <Text size="sm" color="secondary" style={styles.alertSubtitle}>
                                {expiringSubtitle}
                            </Text>
                        )}
                    </View>
                    <Ionicons name="chevron-forward" size={20} color={palette.sand300} />
                </Pressable>
            )}

            {/* Low Stock Card */}
            {hasLowStock && (
                <Pressable style={styles.alertCard} onPress={onLowStockPress}>
                    <View style={[styles.iconCircle, { backgroundColor: palette.amber50 }]}>
                        <Ionicons name="alert-circle" size={22} color={palette.amber400} />
                    </View>
                    <View style={styles.alertContent}>
                        <View style={styles.alertTitleRow}>
                            <Text size="md" weight="semibold">Low Stock</Text>
                            <Text size="md" weight="bold" style={{ color: palette.amber400 }}>
                                {' '}({lowStockCount})
                            </Text>
                        </View>
                        <Text size="sm" color="secondary" style={styles.alertSubtitle}>
                            {lowStockCount === 1 ? '1 item' : `${lowStockCount} items`} running low
                        </Text>
                    </View>
                    <Ionicons name="chevron-forward" size={20} color={palette.sand300} />
                </Pressable>
            )}
        </View>
    );
}

const styles = StyleSheet.create({
    container: {
        gap: spacing.sm + 2,
    },
    sectionLabel: {
        letterSpacing: 1.2,
        marginBottom: 2,
    },
    loadingContainer: {
        alignItems: 'center',
        paddingVertical: spacing.xl,
    },
    alertCard: {
        flexDirection: 'row',
        alignItems: 'center',
        backgroundColor: '#FFFFFF',
        borderRadius: radius.lg,
        paddingVertical: spacing.md + 2,
        paddingHorizontal: spacing.md,
        gap: spacing.sm + 4,
        ...shadow.sm,
    },
    iconCircle: {
        width: 40,
        height: 40,
        borderRadius: radius.full,
        justifyContent: 'center',
        alignItems: 'center',
    },
    alertContent: {
        flex: 1,
        gap: 2,
    },
    alertTitleRow: {
        flexDirection: 'row',
        alignItems: 'center',
    },
    alertSubtitle: {
        marginTop: 1,
    },
    emptyCard: {
        backgroundColor: '#FFFFFF',
        borderRadius: radius.lg,
        paddingVertical: spacing.lg,
        paddingHorizontal: spacing.md,
        alignItems: 'center',
        gap: spacing.xs + 2,
        ...shadow.sm,
    },
});