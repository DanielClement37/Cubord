// src/components/scan/LocationPicker.tsx
import React from 'react';
import { View, ScrollView, Pressable, StyleSheet, ViewStyle } from 'react-native';
import { Text } from '@/components/ui';
import { palette } from '@/styles/colors';
import { spacing, radius } from '@/styles/tokens';
import type { LocationResponse } from '@/types';

interface LocationPickerProps {
    locations: LocationResponse[];
    selectedLocationId: string;
    onSelectLocation: (id: string) => void;
    showPicker: boolean;
    onTogglePicker: () => void;
    error?: string;
    style?: ViewStyle;
}

export function LocationPicker({
                                   locations,
                                   selectedLocationId,
                                   onSelectLocation,
                                   showPicker,
                                   onTogglePicker,
                                   error,
                                   style,
                               }: LocationPickerProps) {
    const selectedLocation = locations.find((l) => l.id === selectedLocationId);

    return (
        <View style={style}>
            <Text size="sm" weight="medium" color="secondary" style={styles.label}>
                Location *
            </Text>
            <Pressable
                style={[styles.picker, error ? { borderColor: palette.red400 } : null]}
                onPress={onTogglePicker}
            >
                <Text size="md">{selectedLocation?.name || 'Select location'}</Text>
                <Text size="md" color="secondary">▼</Text>
            </Pressable>
            {error && (
                <Text size="sm" color="error" style={styles.error}>{error}</Text>
            )}
            {showPicker && (
                <View style={styles.dropdown}>
                    <ScrollView style={{ maxHeight: 160 }} nestedScrollEnabled>
                        {locations.map((loc) => (
                            <Pressable
                                key={loc.id}
                                style={[styles.dropdownItem, loc.id === selectedLocationId && styles.dropdownItemSelected]}
                                onPress={() => onSelectLocation(loc.id)}
                            >
                                <Text size="md" weight={loc.id === selectedLocationId ? 'semibold' : 'regular'}>
                                    {loc.name}
                                </Text>
                            </Pressable>
                        ))}
                    </ScrollView>
                </View>
            )}
        </View>
    );
}

const styles = StyleSheet.create({
    label: {
        marginBottom: spacing.xs,
    },
    picker: {
        flexDirection: 'row',
        justifyContent: 'space-between',
        alignItems: 'center',
        height: 48,
        borderWidth: 1.5,
        borderColor: palette.cream300,
        borderRadius: radius.md,
        paddingHorizontal: spacing.md,
        backgroundColor: palette.white,
        marginBottom: spacing.md,
    },
    error: {
        marginTop: -spacing.sm,
        marginBottom: spacing.sm,
    },
    dropdown: {
        backgroundColor: palette.white,
        borderWidth: 1,
        borderColor: palette.cream300,
        borderRadius: radius.md,
        marginTop: -spacing.sm,
        marginBottom: spacing.md,
        overflow: 'hidden',
    },
    dropdownItem: {
        paddingHorizontal: spacing.md,
        paddingVertical: spacing.sm + 4,
        borderBottomWidth: 1,
        borderBottomColor: palette.cream200,
    },
    dropdownItemSelected: {
        backgroundColor: palette.sage50,
    },
});