import React from 'react';
import { View, StyleSheet, ViewProps } from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';
import { globalStyles } from '@/styles';
import { lightTheme } from '@/styles/themes/light';

export interface ScreenContainerProps extends ViewProps {
    /** Whether to wrap in SafeAreaView (default: true) */
    safeArea?: boolean;
    children: React.ReactNode;
}

export function ScreenContainer({
                                    safeArea = true,
                                    style,
                                    children,
                                    ...rest
                                }: ScreenContainerProps) {
    const Wrapper = safeArea ? SafeAreaView : View;

    return (
        <Wrapper
            style={[styles.container, style]}
            {...rest}
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
        paddingVertical: globalStyles.screenPaddingVertical,
    },
});