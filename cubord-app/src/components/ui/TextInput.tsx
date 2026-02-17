import React, { useState } from 'react';
import {
    View,
    TextInput as RNTextInput,
    TextInputProps as RNTextInputProps,
    StyleSheet,
} from 'react-native';
import { Text } from './Text';
import { spacing, radius, fontFamily, fontSize } from '@/styles';
import { palette } from '@/styles/colors';
import { lightTheme } from '@/styles/themes/light';

export interface TextInputProps extends RNTextInputProps {
    /** Optional label above the input */
    label?: string;
    /** Error message shown below the input */
    error?: string;
    /** Visually & functionally disable the input */
    disabled?: boolean;
}

export function TextInput({
                              label,
                              error,
                              disabled = false,
                              style,
                              ...rest
                          }: TextInputProps) {
    const [focused, setFocused] = useState(false);

    const borderColor = error
        ? lightTheme.colorError
        : focused
            ? lightTheme.colorPrimary
            : lightTheme.colorBorder;

    return (
        <View style={styles.wrapper}>
            {label && (
                <Text
                    size="sm"
                    weight="medium"
                    color="secondary"
                    style={styles.label}
                >
                    {label}
                </Text>
            )}

            <RNTextInput
                editable={!disabled}
                placeholderTextColor={palette.sand300}
                accessibilityLabel={label}
                accessibilityState={{ disabled }}
                onFocus={(e) => {
                    setFocused(true);
                    rest.onFocus?.(e);
                }}
                onBlur={(e) => {
                    setFocused(false);
                    rest.onBlur?.(e);
                }}
                style={[
                    styles.input,
                    {
                        borderColor,
                        backgroundColor: disabled ? palette.cream200 : palette.white,
                        color: disabled ? palette.sand400 : lightTheme.colorText,
                    },
                    style,
                ]}
                {...rest}
            />

            {error && (
                <Text size="sm" color="error" style={styles.error}>
                    {error}
                </Text>
            )}
        </View>
    );
}

const styles = StyleSheet.create({
    wrapper: {
        gap: spacing.xs,
    },
    label: {
        marginBottom: 2,
    },
    input: {
        height: 48,
        borderWidth: 1.5,
        borderRadius: radius.md,
        paddingHorizontal: spacing.md,
        fontFamily: fontFamily.regular,
        fontSize: fontSize.md,
    },
    error: {
        marginTop: 2,
    },
});