import React from 'react';
import { View, StyleSheet } from 'react-native';
import { Text } from '@/components/ui';
import { palette } from '@/styles/colors';
import { spacing, radius } from '@/styles/tokens';
import { Ionicons } from '@expo/vector-icons';
import {
    getExpirationStatus,
    getExpirationLabel,
    type ExpirationStatus,
} from '@/utils/expirationStatus';

interface ExpirationStatusBadgeProps {
    expirationDate: string | null | undefined;
    /** Override the size — 'sm' for item cards, 'md' for detail views. */
    size?: 'sm' | 'md';
}

interface StatusConfig {
    icon: keyof typeof Ionicons.glyphMap;
    color: string;
    bg: string;
}

const STATUS_CONFIG: Record<ExpirationStatus, StatusConfig> = {
    expired: {
        icon: 'alert-circle',
        color: palette.red400,
        bg: palette.red50,
    },
    expiring: {
        icon: 'warning',
        color: palette.amber400,
        bg: palette.amber50,
    },
    fresh: {
        icon: 'checkmark-circle',
        color: palette.green300,
        bg: palette.green50,
    },
    none: {
        icon: 'remove-circle-outline',
        color: palette.sand400,
        bg: palette.cream200,
    },
};

export function ExpirationStatusBadge({
                                          expirationDate,
                                          size = 'sm',
                                      }: ExpirationStatusBadgeProps) {
    const status = getExpirationStatus(expirationDate);
    const label = getExpirationLabel(expirationDate);
    const config = STATUS_CONFIG[status];
    const iconSize = size === 'sm' ? 12 : 14;

    return (
        <View
            style={[
                styles.container,
                { backgroundColor: config.bg },
                size === 'md' && styles.containerMd,
            ]}
            accessibilityRole="text"
            accessibilityLabel={label}
        >
            <Ionicons name={config.icon} size={iconSize} color={config.color} />
            <Text
                size="sm"
                weight="semibold"
                style={{ color: config.color }}
            >
                {label}
            </Text>
        </View>
    );
}

const styles = StyleSheet.create({
    container: {
        flexDirection: 'row',
        alignItems: 'center',
        alignSelf: 'flex-start',
        gap: spacing.xs,
        paddingHorizontal: spacing.sm,
        paddingVertical: spacing.xs - 1,
        borderRadius: radius.full,
    },
    containerMd: {
        paddingHorizontal: spacing.sm + 4,
        paddingVertical: spacing.xs + 1,
    },
});