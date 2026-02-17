import React from 'react';
import { Text as RNText, TextProps as RNTextProps, StyleSheet } from 'react-native';
import { fontSize, fontFamily, spacing } from '@/styles';
import { lightTheme } from '@/styles/themes/light';

// ── Size / weight / color variant types ──────────────
type TextSize = 'sm' | 'md' | 'lg' | 'xl';
type TextWeight = 'regular' | 'medium' | 'semibold' | 'bold';
type TextColor = 'primary' | 'secondary' | 'inverse' | 'error' | 'success' | 'warning';

export interface TextProps extends RNTextProps {
    /** Predefined font size */
    size?: TextSize;
    /** Font weight mapped to NunitoSans variant */
    weight?: TextWeight;
    /** Semantic color shortcut */
    color?: TextColor;
    /** Center the text */
    align?: 'left' | 'center' | 'right';
    children: React.ReactNode;
}

const sizeMap: Record<TextSize, number> = {
    sm: fontSize.sm,
    md: fontSize.md,
    lg: fontSize.lg,
    xl: fontSize.xl,
};

const weightFamilyMap: Record<TextWeight, string> = {
    regular:  fontFamily.regular,
    medium:   fontFamily.medium,
    semibold: fontFamily.semibold,
    bold:     fontFamily.bold,
};

const colorMap: Record<TextColor, string> = {
    primary:   lightTheme.colorText,
    secondary: lightTheme.colorTextSecondary,
    inverse:   lightTheme.colorTextInverse,
    error:     lightTheme.colorError,
    success:   lightTheme.colorSuccess,
    warning:   lightTheme.colorWarning,
};

export function Text({
                         size = 'md',
                         weight = 'regular',
                         color = 'primary',
                         align,
                         style,
                         children,
                         ...rest
                     }: TextProps) {
    return (
        <RNText
            style={[
                {
                    fontSize: sizeMap[size],
                    fontFamily: weightFamilyMap[weight],
                    color: colorMap[color],
                    textAlign: align,
                },
                style,
            ]}
            accessibilityRole="text"
            {...rest}
        >
            {children}
        </RNText>
    );
}