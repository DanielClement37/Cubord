import React from 'react';
import { View, StyleSheet } from 'react-native';
import { Spinner } from '@/components/ui';
import { palette } from '@/styles/colors';
import { spacing, radius } from '@/styles/tokens';

/**
 * Skeleton placeholder that matches the pantry list layout
 * while data is loading.
 */
export function PantrySkeleton() {
    return (
        <View style={styles.container}>
            {/* Title placeholder */}
            <View style={[styles.bar, { width: '35%', height: 28 }]} />

            {/* Search bar placeholder */}
            <View style={[styles.bar, { width: '100%', height: 44, borderRadius: radius.md }]} />

            {/* Filter chips placeholder */}
            <View style={styles.chipRow}>
                <View style={[styles.bar, { width: 70, height: 28, borderRadius: radius.full }]} />
                <View style={[styles.bar, { width: 90, height: 28, borderRadius: radius.full }]} />
                <View style={[styles.bar, { width: 80, height: 28, borderRadius: radius.full }]} />
            </View>

            {/* Location group placeholders */}
            <View style={[styles.bar, { width: '50%', height: 20, marginTop: spacing.md }]} />
            <View style={styles.cardPlaceholder} />
            <View style={styles.cardPlaceholder} />

            <View style={[styles.bar, { width: '40%', height: 20, marginTop: spacing.lg }]} />
            <View style={styles.cardPlaceholder} />

            {/* Centered spinner */}
            <View style={styles.spinnerRow}>
                <Spinner size="md" />
            </View>
        </View>
    );
}

const styles = StyleSheet.create({
    container: {
        gap: spacing.sm + 2,
        paddingTop: spacing.sm,
    },
    bar: {
        height: 20,
        backgroundColor: palette.cream300,
        borderRadius: radius.sm,
    },
    chipRow: {
        flexDirection: 'row',
        gap: spacing.sm,
    },
    cardPlaceholder: {
        height: 72,
        backgroundColor: palette.cream200,
        borderRadius: radius.md,
    },
    spinnerRow: {
        alignItems: 'center',
        paddingVertical: spacing.lg,
    },
});