import React from 'react';
import { View, StyleSheet, ViewProps } from 'react-native';
import { Text } from './Text';
import { spacing, radius } from '@/styles';
import { palette } from '@/styles/colors';
import { lightTheme } from '@/styles/themes/light';

type BadgeVariant = 'safe' | 'warning' | 'danger' | 'neutral';

export interface BadgeProps extends ViewProps {
    /** Semantic variant */
    variant?: BadgeVariant;
    /** Label text inside the badge */
    label: string;
}

interface BadgeColors {
    bg: string;
    text: string;
}

const variantColors: Record<BadgeVariant, BadgeColors> = {
    safe: {
        bg: palette.green50,
        text: palette.green400,
    },
    warning: {
        bg: palette.amber50,
        text: palette.amber400,
    },
    danger: {
        bg: palette.red50,
        text: palette.red400,
    },
    neutral: {
        bg: palette.cream300,
        text: lightTheme.colorTextSecondary,
    },
};

export function Badge({
                          variant = 'neutral',
                          label,
                          style,
                          ...rest
                      }: BadgeProps) {
    const colors = variantColors[variant];

    return (
        <View
            style={[
                styles.container,
                { backgroundColor: colors.bg },
                style,
            ]}
            accessibilityRole="text"
            accessibilityLabel={label}
            {...rest}
        >
            <Text size="sm" weight="semibold" style={{ color: colors.text }}>
                {label}
            </Text>
        </View>
    );
}

const styles = StyleSheet.create({
    container: {
        alignSelf: 'flex-start',
        paddingHorizontal: spacing.sm,
        paddingVertical: spacing.xs,
        borderRadius: radius.full,
    },
});