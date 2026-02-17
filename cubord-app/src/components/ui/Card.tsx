import React from 'react';
import { Pressable, View, StyleSheet, ViewProps, PressableProps } from 'react-native';
import { spacing, radius, shadow } from '@/styles';
import { lightTheme } from '@/styles/themes/light';

type CardVariant = 'default' | 'elevated';

export interface CardProps extends ViewProps {
    /** Visual variant */
    variant?: CardVariant;
    /** When provided, card becomes pressable */
    onPress?: PressableProps['onPress'];
    children: React.ReactNode;
}

export function Card({
                         variant = 'default',
                         onPress,
                         style,
                         children,
                         ...rest
                     }: CardProps) {
    const elevationStyle = variant === 'elevated' ? shadow.md : shadow.sm;

    const cardStyles = [
        styles.base,
        elevationStyle,
        style,
    ];

    if (onPress) {
        return (
            <Pressable
                onPress={onPress}
                accessibilityRole="button"
                style={({ pressed }) => [
                    ...cardStyles,
                    pressed && styles.pressed,
                ]}
            >
                {children}
            </Pressable>
        );
    }

    return (
        <View style={cardStyles} {...rest}>
            {children}
        </View>
    );
}

const styles = StyleSheet.create({
    base: {
        backgroundColor: lightTheme.colorSurface,
        borderRadius: radius.lg,
        padding: spacing.md,
    },
    pressed: {
        opacity: 0.92,
    },
});