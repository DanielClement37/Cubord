
import React from 'react';
import { ActivityIndicator, StyleSheet, View, ViewProps } from 'react-native';
import { themes } from '@/styles';

type SpinnerSize = 'sm' | 'md' | 'lg';

export interface SpinnerProps extends ViewProps {
    /** Predefined size */
    size?: SpinnerSize;
    /** Override color */
    color?: string;
}

const sizeMap: Record<SpinnerSize, 'small' | 'large'> = {
    sm: 'small',
    md: 'small',
    lg: 'large',
};

const scaleMap: Record<SpinnerSize, number> = {
    sm: 0.75,
    md: 1,
    lg: 1,
};

export function Spinner({
                            size = 'md',
                            color = themes.light.colorPrimary,
                            style,
                            ...rest
                        }: SpinnerProps) {
    return (
        <View
            style={[styles.container, style]}
            accessibilityRole="progressbar"
            accessibilityLabel="Loading"
            {...rest}
        >
            <ActivityIndicator
                size={sizeMap[size]}
                color={color}
                style={{ transform: [{ scale: scaleMap[size] }] }}
            />
        </View>
    );
}

const styles = StyleSheet.create({
    container: {
        alignItems: 'center',
        justifyContent: 'center',
    },
});