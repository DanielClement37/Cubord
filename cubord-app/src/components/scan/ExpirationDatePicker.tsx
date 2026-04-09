// src/components/scan/ExpirationDatePicker.tsx
import React, { useMemo } from 'react';
import {Pressable, StyleSheet, TextStyle} from 'react-native';
import { DatePickerModal } from '@/components/ui/DatePickerModal';
import { Text } from '@/components/ui';
import { palette } from '@/styles/colors';
import { spacing, radius } from '@/styles/tokens';

interface ExpirationDatePickerProps {
    expirationDate: Date | null;
    onDateChange: (date: Date | null) => void;
    showPicker: boolean;
    onTogglePicker: () => void;
    showEstimateHint?: boolean;
    style?: TextStyle;
}

export function ExpirationDatePicker({
                                         expirationDate,
                                         onDateChange,
                                         showPicker,
                                         onTogglePicker,
                                         showEstimateHint = false,
                                         style,
                                     }: ExpirationDatePickerProps) {
    const formattedDate = useMemo(() => {
        return expirationDate ? expirationDate.toISOString().split('T')[0] : '';
    }, [expirationDate]);

    return (
        <>
            <Text size="sm" weight="medium" color="secondary" style={[styles.label, style]}>
                Expiration Date
            </Text>
            <Pressable style={styles.picker} onPress={onTogglePicker}>
                <Text size="md" color={expirationDate ? 'primary' : 'secondary'}>
                    {expirationDate ? formattedDate : 'Tap to select a date'}
                </Text>
                <Text size="md" color="secondary">📅</Text>
            </Pressable>

            {expirationDate && showEstimateHint && (
                <Text size="sm" color="secondary" style={styles.hint}>
                    Estimated from product type
                </Text>
            )}

            {expirationDate && (
                <Pressable onPress={() => onDateChange(null)} hitSlop={8} style={styles.clearButton}>
                    <Text size="sm" weight="medium" style={styles.clearText}>Clear date</Text>
                </Pressable>
            )}

            {showPicker && (
                <DatePickerModal
                    visible={showPicker}
                    value={expirationDate ?? new Date()}
                    minimumDate={new Date()}
                    onConfirm={(date) => onDateChange(date)}
                    onCancel={onTogglePicker}
                />
            )}
        </>
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
    hint: {
        marginBottom: spacing.xs,
    },
    clearButton: {
        marginBottom: spacing.sm,
    },
    clearText: {
        color: palette.red400,
    },
});