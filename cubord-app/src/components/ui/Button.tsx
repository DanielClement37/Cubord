
import React from 'react';
import {
    Pressable,
    PressableProps,
    StyleSheet,
    View,
} from 'react-native';
import { Text } from './Text';
import { Spinner } from './Spinner';
import { spacing, radius, fontFamily, fontSize, shadow } from '@/styles';
import { palette } from '@/styles/colors';
import { lightTheme } from '@/styles/themes/light';

type ButtonVariant = 'primary' | 'secondary' | 'ghost' | 'danger';

export interface ButtonProps extends Omit<PressableProps, 'children'> {
    /** Visual variant */
    variant?: ButtonVariant;
    /** Button label */
    label: string;
    /** Show spinner & disable interaction */
    loading?: boolean;
    /** Left icon render function */
    leftIcon?: React.ReactNode;
}

interface VariantStyle {
    bg: string;
    bgPressed: string;
    text: string;
    borderColor?: string;
}

const variants: Record<ButtonVariant, VariantStyle> = {
    primary: {
        bg: lightTheme.colorPrimary,
        bgPressed: lightTheme.colorPrimaryDark,
        text: palette.white,
    },
    secondary: {
        bg: 'transparent',
        bgPressed: palette.sage50,
        text: lightTheme.colorPrimary,
        borderColor: lightTheme.colorPrimary,
    },
    ghost: {
        bg: 'transparent',
        bgPressed: palette.cream200,
        text: lightTheme.colorText,
    },
    danger: {
        bg: lightTheme.colorError,
        bgPressed: palette.red500,
        text: palette.white,
    },
};

export function Button({
                           variant = 'primary',
                           label,
                           loading = false,
                           disabled = false,
                           leftIcon,
                           style,
                           ...rest
                       }: ButtonProps) {
    const v = variants[variant];
    const isDisabled = disabled || loading;

    return (
        <Pressable
            disabled={isDisabled}
            accessibilityRole="button"
            accessibilityLabel={label}
            accessibilityState={{ disabled: isDisabled, busy: loading }}
            style={({ pressed }) => [
                styles.base,
                {
                    backgroundColor: pressed ? v.bgPressed : v.bg,
                    borderColor: v.borderColor ?? 'transparent',
                    borderWidth: v.borderColor ? 1.5 : 0,
                    opacity: isDisabled && !loading ? 0.45 : 1,
                },
                style as any,
            ]}
            {...rest}
        >
            {loading ? (
                <Spinner
                    size="sm"
                    color={v.text}
                />
            ) : (
                <View style={styles.content}>
                    {leftIcon && <View style={styles.iconLeft}>{leftIcon}</View>}
                    <Text
                        size="md"
                        weight="semibold"
                        style={{ color: v.text }}
                    >
                        {label}
                    </Text>
                </View>
            )}
        </Pressable>
    );
}

const styles = StyleSheet.create({
    base: {
        height: 48,
        borderRadius: radius.md,
        alignItems: 'center',
        justifyContent: 'center',
        paddingHorizontal: spacing.lg,
    },
    content: {
        flexDirection: 'row',
        alignItems: 'center',
    },
    iconLeft: {
        marginRight: spacing.sm,
    },
});