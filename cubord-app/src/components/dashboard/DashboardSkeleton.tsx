// src/components/dashboard/DashboardSkeleton.tsx
import React from 'react';
import { View, StyleSheet } from 'react-native';
import { Spinner } from '@/components/ui';
import { spacing, palette, radius } from '@/styles';

/** Full-screen loading placeholder that matches the dashboard layout */
export function DashboardSkeleton() {
    return (
        <View style={styles.container}>
            {/* Greeting placeholder */}
            <View style={styles.skeletonBar} />
            <View style={[styles.skeletonBar, { width: '40%', height: 28 }]} />

            {/* Overview card placeholder */}
            <View style={styles.skeletonCard}>
                <Spinner size="md" />
            </View>

            {/* Needs attention placeholder */}
            <View style={[styles.skeletonBar, { width: '50%', marginTop: spacing.lg }]} />
            <View style={styles.skeletonAlertRow} />
            <View style={styles.skeletonAlertRow} />
        </View>
    );
}

const styles = StyleSheet.create({
    container: {
        gap: spacing.md,
        paddingTop: spacing.md,
    },
    skeletonBar: {
        height: 20,
        width: '60%',
        backgroundColor: palette.cream300,
        borderRadius: radius.sm,
    },
    skeletonCard: {
        height: 140,
        backgroundColor: palette.cream200,
        borderRadius: radius.lg,
        justifyContent: 'center',
        alignItems: 'center',
        marginTop: spacing.sm,
    },
    skeletonAlertRow: {
        height: 64,
        backgroundColor: palette.cream200,
        borderRadius: radius.lg,
    },
});