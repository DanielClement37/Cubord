import React from 'react';
import { View, StyleSheet } from 'react-native';
import { Text } from './Text';
import { spacing, radius, shadow, fontFamily, fontSize } from '@/styles';
import { palette } from '@/styles/colors';
import { lightTheme } from '@/styles/themes/light';
import type { BaseToastProps } from 'react-native-toast-message';

/* ── accent colour per toast type ──────────────────── */
const accents = {
    success: lightTheme.colorSuccess,
    error:   lightTheme.colorError,
    info:    lightTheme.colorPrimary,
} as const;

type ToastType = keyof typeof accents;

/** Shared layout used by every toast variant. */
function BaseToast({ text1, text2, type }: BaseToastProps & { type: ToastType }) {
    const accent = accents[type];

    return (
        <View style={[styles.container, shadow.md]}>
            {/* Left accent bar */}
            <View style={[styles.accent, { backgroundColor: accent }]} />

            {/* Text content */}
            <View style={styles.body}>
                {text1 ? (
                    <Text size="md" weight="semibold" style={styles.title} numberOfLines={1}>
                        {text1}
                    </Text>
                ) : null}
                {text2 ? (
                    <Text size="sm" color="secondary" numberOfLines={2}>
                        {text2}
                    </Text>
                ) : null}
            </View>
        </View>
    );
}

export const toastConfig = {
    success: (props: BaseToastProps) => <BaseToast {...props} type="success" />,
    error:   (props: BaseToastProps) => <BaseToast {...props} type="error" />,
    info:    (props: BaseToastProps) => <BaseToast {...props} type="info" />,
};

/* ── Styles ────────────────────────────────────────── */
const styles = StyleSheet.create({
    container: {
        flexDirection: 'row',
        alignItems: 'center',
        width: '90%',
        minHeight: 56,
        backgroundColor: lightTheme.colorSurface,
        borderRadius: radius.md,
        overflow: 'hidden',
    },
    accent: {
        width: 5,
        alignSelf: 'stretch',
    },
    body: {
        flex: 1,
        paddingVertical: spacing.sm,
        paddingHorizontal: spacing.md,
    },
    title: {
        marginBottom: 2,
    },
});