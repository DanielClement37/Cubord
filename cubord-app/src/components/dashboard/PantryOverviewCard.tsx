// src/components/dashboard/PantryOverviewCard.tsx
import React from 'react';
import { View, StyleSheet } from 'react-native';
import { Text, Card, Spinner } from '@/components/ui';
import { spacing, palette, radius } from '@/styles';

interface PantryOverviewCardProps {
    totalItems: number | undefined;
    locationsCount: number | undefined;
    isLoading: boolean;
}

export function PantryOverviewCard({ totalItems, locationsCount, isLoading }: PantryOverviewCardProps) {
    return (
        <Card variant="elevated">
            <Text size="lg" weight="bold" style={styles.title}>Pantry Overview</Text>

            {isLoading ? (
                <View style={styles.loadingRow}>
                    <Spinner size="md" />
                </View>
            ) : (
                <View style={styles.statsRow}>
                    <View style={[styles.statBox, { backgroundColor: palette.sage50 }]}>
                        <Text size="xl" weight="bold" style={{ color: palette.sage600 }}>
                            {totalItems ?? 0}
                        </Text>
                        <Text size="sm" color="secondary">Items</Text>
                    </View>

                    <View style={[styles.statBox, { backgroundColor: palette.cream200 }]}>
                        <Text size="xl" weight="bold" style={{ color: palette.sand700 }}>
                            {locationsCount ?? 0}
                        </Text>
                        <Text size="sm" color="secondary">Locations</Text>
                    </View>
                </View>
            )}
        </Card>
    );
}

const styles = StyleSheet.create({
    title: {
        marginBottom: spacing.md,
    },
    statsRow: {
        flexDirection: 'row',
        gap: spacing.md,
    },
    statBox: {
        flex: 1,
        alignItems: 'center',
        paddingVertical: spacing.md,
        borderRadius: radius.md,
    },
    loadingRow: {
        alignItems: 'center',
        paddingVertical: spacing.lg,
    },
});