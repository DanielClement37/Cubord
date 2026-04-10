import React from 'react';
import {  Pressable, StyleSheet, ScrollView } from 'react-native';
import { Text } from '@/components/ui';
import { palette } from '@/styles/colors';
import { spacing, radius } from '@/styles/tokens';

export interface FilterChipOption {
    id: string;
    label: string;
}

interface FilterChipsProps {
    options: FilterChipOption[];
    activeId: string;
    onSelect: (id: string) => void;
}

/**
 * Horizontal row of filter chips. Active chip is dark, inactive is light.
 */
export function FilterChips({ options, activeId, onSelect }: FilterChipsProps) {
    return (
        <ScrollView
            horizontal
            showsHorizontalScrollIndicator={false}
            contentContainerStyle={styles.container}
        >
            {options.map((option) => {
                const isActive = option.id === activeId;
                return (
                    <Pressable
                        key={option.id}
                        onPress={() => onSelect(option.id)}
                        style={[
                            styles.chip,
                            isActive ? styles.chipActive : styles.chipInactive,
                        ]}
                        accessibilityRole="button"
                        accessibilityState={{ selected: isActive }}
                        accessibilityLabel={option.label}
                    >
                        <Text
                            size="sm"
                            weight="semibold"
                            style={{
                                color: isActive ? palette.cream100 : palette.sand800,
                            }}
                        >
                            {option.label}
                        </Text>
                    </Pressable>
                );
            })}
        </ScrollView>
    );
}

const styles = StyleSheet.create({
    container: {
        flexDirection: 'row',
        gap: spacing.sm,
    },
    chip: {
        paddingHorizontal: spacing.md,
        paddingVertical: spacing.sm - 2,
        borderRadius: radius.full,
    },
    chipActive: {
        backgroundColor: palette.sand800,
    },
    chipInactive: {
        backgroundColor: palette.cream300,
    },
});