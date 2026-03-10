import React from 'react';
import { View, StyleSheet, ViewProps } from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';
import { globalStyles } from '@/styles';
import { lightTheme } from '@/styles/themes/light';

export interface ScreenContainerProps extends ViewProps {
    /** Whether to wrap in SafeAreaView (default: true) */
    safeArea?: boolean;
    children: React.ReactNode;
    centered?: boolean;
    /** Safe area edges to respect (default: ['top', 'left', 'right']) */
    edges?: ('top' | 'bottom' | 'left' | 'right')[];
}

export function ScreenContainer({
                                    safeArea = true,
                                    style,
                                    children,
                                    centered,
                                    edges = ['top', 'left', 'right'],
                                    ...rest
                                }: ScreenContainerProps) {
    const Wrapper = safeArea ? SafeAreaView : View;
    const wrapperProps = safeArea ? { edges, ...rest } : rest;

    return (
        <Wrapper
            style={[
                styles.container,
                centered && styles.centered,
                style,
            ]}
            {...wrapperProps}
        >
            {children}
        </Wrapper>
    );
}

const styles = StyleSheet.create({
    container: {
        flex: 1,
        backgroundColor: lightTheme.colorBackground,
        paddingHorizontal: globalStyles.screenPaddingHorizontal,
        paddingTop: globalStyles.screenPaddingVertical,
    },
    centered: {
        justifyContent: 'center',
        alignItems: 'center',
    },
});