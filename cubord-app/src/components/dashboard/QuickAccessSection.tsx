// src/components/dashboard/QuickAccessSection.tsx
import React from 'react';
import { View, StyleSheet, Pressable } from 'react-native';
import { Text, Spinner } from '@/components/ui';
import { spacing, palette, radius, shadow } from '@/styles';
import { Ionicons } from '@expo/vector-icons';
import type { LocationResponse } from '@/types';

interface QuickAccessSectionProps {
    locations: LocationResponse[] | undefined;
    isLoading: boolean;
    onLocationPress?: (location: LocationResponse) => void;
    onAddLocationPress?: () => void;
}

export function QuickAccessSection({
                                       locations,
                                       isLoading,
                                       onLocationPress,
                                       onAddLocationPress,
                                   }: QuickAccessSectionProps) {
    return (
        <View style={styles.container}>
            <Text size="sm" weight="bold" color="secondary" style={styles.sectionLabel}>
                QUICK ACCESS
            </Text>

            {isLoading ? (
                <View style={styles.loadingContainer}>
                    <Spinner size="md" />
                </View>
            ) : (
                <View style={styles.chipRow}>
                    {locations?.map((location) => (
                        <Pressable
                            key={location.id}
                            style={styles.locationChip}
                            onPress={() => onLocationPress?.(location)}
                        >
                            <Text size="sm" weight="bold" style={styles.locationName}>
                                {location.name}
                            </Text>
                            {/* Item count line — could be wired up later */}
                        </Pressable>
                    ))}

                    <Pressable style={styles.addChip} onPress={onAddLocationPress}>
                        <Ionicons name="add" size={20} color={palette.sand500} />
                        <Text size="sm" weight="semibold" color="secondary">
                            Add{'\n'}Location
                        </Text>
                    </Pressable>
                </View>
            )}
        </View>
    );
}

const CHIP_MIN_WIDTH = 100;

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
    chipRow: {
        flexDirection: 'row',
        flexWrap: 'wrap',
        gap: spacing.sm + 4,
    },
    locationChip: {
        backgroundColor: '#FFFFFF',
        borderRadius: radius.md,
        paddingHorizontal: spacing.md,
        paddingVertical: spacing.md,
        minWidth: CHIP_MIN_WIDTH,
        alignItems: 'center',
        justifyContent: 'center',
        ...shadow.sm,
    },
    locationName: {
        color: palette.sand700,
    },
    addChip: {
        flexDirection: 'row',
        alignItems: 'center',
        justifyContent: 'center',
        gap: spacing.xs + 2,
        borderRadius: radius.md,
        borderWidth: 1.5,
        borderColor: palette.cream400,
        borderStyle: 'dashed',
        paddingHorizontal: spacing.md,
        paddingVertical: spacing.md,
        minWidth: CHIP_MIN_WIDTH,
    },
});