import React from 'react';
import { View, StyleSheet } from 'react-native';
import { Text, Button } from '@/components/ui';
import { palette } from '@/styles/colors';
import { spacing, radius } from '@/styles/tokens';

type EmptyVariant = 'items' | 'locations';

interface EmptyPantryStateProps {
    variant?: EmptyVariant;
    onAction?: () => void;
}

const CONTENT: Record<EmptyVariant, {
    emoji: string;
    title: string;
    subtitle: string;
    actionLabel: string;
}> = {
    items: {
        emoji: '📦',
        title: 'Your pantry is empty',
        subtitle: 'Scan a barcode or add an item manually to get started.',
        actionLabel: 'Scan First Item',
    },
    locations: {
        emoji: '🏠',
        title: 'No locations yet',
        subtitle: 'Create a location like "Kitchen Pantry" to organize your items.',
        actionLabel: 'Add Location',
    },
};

/**
 * Full-area empty state with an emoji illustration, title, subtitle,
 * and a CTA button. Used when the pantry or a location has no items.
 */
export function EmptyPantryState({
                                     variant = 'items',
                                     onAction,
                                 }: EmptyPantryStateProps) {
    const content = CONTENT[variant];

    return (
        <View style={styles.container}>
            <View style={styles.emojiCircle}>
                <Text size="xl" style={styles.emoji}>{content.emoji}</Text>
            </View>

            <Text size="lg" weight="bold" align="center" style={styles.title}>
                {content.title}
            </Text>

            <Text size="md" color="secondary" align="center" style={styles.subtitle}>
                {content.subtitle}
            </Text>

            {onAction && (
                <Button
                    label={content.actionLabel}
                    onPress={onAction}
                    style={styles.cta}
                />
            )}
        </View>
    );
}

const styles = StyleSheet.create({
    container: {
        alignItems: 'center',
        justifyContent: 'center',
        paddingHorizontal: spacing.xl,
        paddingVertical: spacing.xxl + spacing.md,
    },
    emojiCircle: {
        width: 80,
        height: 80,
        borderRadius: radius.lg,
        backgroundColor: palette.sage50,
        alignItems: 'center',
        justifyContent: 'center',
        marginBottom: spacing.md,
    },
    emoji: {
        fontSize: 36,
    },
    title: {
        marginBottom: spacing.xs + 2,
    },
    subtitle: {
        marginBottom: spacing.lg,
        lineHeight: 22,
    },
    cta: {
        minWidth: 180,
    },
});