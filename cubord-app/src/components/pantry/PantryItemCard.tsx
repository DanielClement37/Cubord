import React from 'react';
import { View, Pressable, StyleSheet } from 'react-native';
import { Text, ProductImage } from '@/components/ui';
import { ExpirationStatusBadge } from './ExpirationStatusBadge';
import { palette } from '@/styles/colors';
import { spacing, radius, shadow } from '@/styles/tokens';
import { getExpirationStatus } from '@/utils/expirationStatus';
import type { PantryItemResponse } from '@/types';

interface PantryItemCardProps {
    item: PantryItemResponse;
    onPress?: () => void;
}

/**
 * Card showing a single pantry item: product image, name, brand,
 * expiration status badge, and quantity chip.
 *
 * Background tints based on expiration status.
 */
export function PantryItemCard({ item, onPress }: PantryItemCardProps) {
    const status = getExpirationStatus(item.expirationDate);
    const bgColor =
        status === 'expired'
            ? palette.red50
            : status === 'expiring'
                ? palette.amber50
                : palette.white;
    const borderColor =
        status === 'expired'
            ? palette.red100
            : status === 'expiring'
                ? palette.amber100
                : palette.cream300;

    return (
        <Pressable
            onPress={onPress}
            style={({ pressed }) => [
                styles.container,
                { backgroundColor: bgColor, borderColor },
                pressed && styles.pressed,
            ]}
            accessibilityRole="button"
            accessibilityLabel={`${item.product.name}, quantity ${item.quantity ?? 0}`}
        >
            <ProductImage
                imageUrl={item.product.imageSmallUrl ?? item.product.imageUrl}
                name={item.product.name}
                category={item.product.category}
                size={48}
                style={styles.image}
            />

            <View style={styles.info}>
                <Text size="md" weight="semibold" numberOfLines={1}>
                    {item.product.name}
                </Text>
                {item.product.brand ? (
                    <Text size="sm" color="secondary" numberOfLines={1}>
                        {item.product.brand}
                    </Text>
                ) : null}
                <View style={styles.statusRow}>
                    <ExpirationStatusBadge expirationDate={item.expirationDate} size="sm" />
                </View>
            </View>

            <View style={styles.qtyChip}>
                <Text size="sm" weight="semibold" color="secondary">
                    Qty: {item.quantity ?? 0}
                </Text>
            </View>
        </Pressable>
    );
}

const styles = StyleSheet.create({
    container: {
        flexDirection: 'row',
        alignItems: 'center',
        padding: spacing.sm + 4,
        borderRadius: radius.md + 2,
        borderWidth: 1,
        gap: spacing.sm + 4,
        ...shadow.sm,
    },
    pressed: {
        opacity: 0.92,
        transform: [{ translateY: -1 }],
    },
    image: {
        flexShrink: 0,
    },
    info: {
        flex: 1,
        minWidth: 0,
        gap: 1,
    },
    statusRow: {
        marginTop: spacing.xs - 1,
    },
    qtyChip: {
        backgroundColor: palette.cream200,
        paddingHorizontal: spacing.sm + 2,
        paddingVertical: spacing.xs,
        borderRadius: radius.sm,
        flexShrink: 0,
    },
});