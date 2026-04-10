import React from 'react';
import { View, Pressable, StyleSheet } from 'react-native';
import { Text } from '@/components/ui';
import { palette } from '@/styles/colors';
import { radius } from '@/styles/tokens';
import { Ionicons } from '@expo/vector-icons';
import * as Haptics from 'expo-haptics';

interface QuantityStepperProps {
    value: number;
    onIncrement: () => void;
    onDecrement: () => void;
    /** Minimum value (inclusive). Default: 0 */
    min?: number;
}

/**
 * A compact +/− stepper for adjusting quantity.
 * Provides haptic feedback on each press.
 */
export function QuantityStepper({
                                    value,
                                    onIncrement,
                                    onDecrement,
                                    min = 0,
                                }: QuantityStepperProps) {
    const isAtMin = value <= min;

    const handleDecrement = () => {
        Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Light);
        onDecrement();
    };

    const handleIncrement = () => {
        Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Light);
        onIncrement();
    };

    return (
        <View style={styles.container}>
            <Pressable
                onPress={handleDecrement}
                style={[
                    styles.button,
                    styles.buttonLeft,
                    isAtMin && styles.buttonDisabled,
                ]}
                disabled={isAtMin}
                accessibilityRole="button"
                accessibilityLabel="Decrease quantity"
            >
                <Ionicons
                    name="remove"
                    size={18}
                    color={isAtMin ? palette.sand300 : palette.sage700}
                />
            </Pressable>

            <View style={styles.valueContainer}>
                <Text size="md" weight="bold">
                    {value}
                </Text>
            </View>

            <Pressable
                onPress={handleIncrement}
                style={[styles.button, styles.buttonRight]}
                accessibilityRole="button"
                accessibilityLabel="Increase quantity"
            >
                <Ionicons name="add" size={18} color={palette.sage700} />
            </Pressable>
        </View>
    );
}

const styles = StyleSheet.create({
    container: {
        flexDirection: 'row',
        alignItems: 'center',
    },
    button: {
        width: 36,
        height: 36,
        alignItems: 'center',
        justifyContent: 'center',
        borderWidth: 1,
        borderColor: palette.sand100,
    },
    buttonLeft: {
        borderTopLeftRadius: radius.sm + 2,
        borderBottomLeftRadius: radius.sm + 2,
        backgroundColor: palette.sage50,
    },
    buttonRight: {
        borderTopRightRadius: radius.sm + 2,
        borderBottomRightRadius: radius.sm + 2,
        backgroundColor: palette.sage50,
    },
    buttonDisabled: {
        backgroundColor: palette.cream200,
    },
    valueContainer: {
        width: 42,
        height: 36,
        alignItems: 'center',
        justifyContent: 'center',
        borderTopWidth: 1,
        borderBottomWidth: 1,
        borderColor: palette.sand100,
        backgroundColor: palette.white,
    },
});