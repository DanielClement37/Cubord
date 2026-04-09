// src/components/scan/QuantityUnitRow.tsx
import React from 'react';
import { View, ScrollView, Pressable, StyleSheet } from 'react-native';
import { Text } from '@/components/ui';
import { palette } from '@/styles/colors';
import { spacing, radius } from '@/styles/tokens';
import { PANTRY_UNITS } from '@/utils/pantryUnits';

interface QuantityUnitRowProps {
    quantity: number;
    onQuantityChange: (value: number) => void;
    unit: string;
    onUnitChange: (value: string) => void;
    showUnitPicker: boolean;
    onToggleUnitPicker: () => void;
}

export function QuantityUnitRow({
                                    quantity,
                                    onQuantityChange,
                                    unit,
                                    onUnitChange,
                                    showUnitPicker,
                                    onToggleUnitPicker,
                                }: QuantityUnitRowProps) {
    return (
        <>
            <View style={styles.row}>
                <View style={{ flex: 1 }}>
                    <Text size="sm" weight="medium" color="secondary" style={styles.label}>
                        Quantity
                    </Text>
                    <View style={styles.stepper}>
                        <Pressable style={styles.stepperButton} onPress={() => onQuantityChange(Math.max(1, quantity - 1))}>
                            <Text size="lg" weight="bold">−</Text>
                        </Pressable>
                        <Text size="md" weight="semibold" style={styles.stepperValue}>{quantity}</Text>
                        <Pressable style={styles.stepperButton} onPress={() => onQuantityChange(quantity + 1)}>
                            <Text size="lg" weight="bold">+</Text>
                        </Pressable>
                    </View>
                </View>

                <View style={{ flex: 1, marginLeft: spacing.md }}>
                    <Text size="sm" weight="medium" color="secondary" style={styles.label}>
                        Unit
                    </Text>
                    <Pressable style={styles.picker} onPress={onToggleUnitPicker}>
                        <Text size="md">{unit}</Text>
                        <Text size="md" color="secondary">▼</Text>
                    </Pressable>
                </View>
            </View>

            {showUnitPicker && (
                <View style={styles.dropdown}>
                    <ScrollView style={{ maxHeight: 200 }} nestedScrollEnabled>
                        {PANTRY_UNITS.map((u) => (
                            <Pressable
                                key={u}
                                style={[styles.dropdownItem, u === unit && styles.dropdownItemSelected]}
                                onPress={() => onUnitChange(u)}
                            >
                                <Text size="md" weight={u === unit ? 'semibold' : 'regular'}>{u}</Text>
                            </Pressable>
                        ))}
                    </ScrollView>
                </View>
            )}
        </>
    );
}

const styles = StyleSheet.create({
    row: {
        flexDirection: 'row',
        alignItems: 'flex-start',
    },
    label: {
        marginBottom: spacing.xs,
    },
    stepper: {
        flexDirection: 'row',
        alignItems: 'center',
        height: 48,
        borderWidth: 1.5,
        borderColor: palette.cream300,
        borderRadius: radius.md,
        backgroundColor: palette.white,
        overflow: 'hidden',
    },
    stepperButton: {
        width: 48,
        height: 48,
        alignItems: 'center',
        justifyContent: 'center',
    },
    stepperValue: {
        flex: 1,
        textAlign: 'center',
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