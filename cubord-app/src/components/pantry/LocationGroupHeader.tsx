import React from 'react';
import { View, Pressable, StyleSheet } from 'react-native';
import { Text } from '@/components/ui';
import { palette } from '@/styles/colors';
import { spacing, radius } from '@/styles/tokens';
import { Ionicons } from '@expo/vector-icons';
import Animated, {
    useAnimatedStyle,
    withTiming,
    Easing,
} from 'react-native-reanimated';

interface LocationGroupHeaderProps {
    name: string;
    itemCount: number;
    isExpanded: boolean;
    onToggleExpand: () => void;
    onViewAll: () => void;
}

/**
 * Collapsible section header for a storage location group.
 * Chevron rotates with an animated transition.
 */
export function LocationGroupHeader({
                                        name,
                                        itemCount,
                                        isExpanded,
                                        onToggleExpand,
                                        onViewAll,
                                    }: LocationGroupHeaderProps) {
    const chevronStyle = useAnimatedStyle(() => ({
        transform: [
            {
                rotate: withTiming(isExpanded ? '0deg' : '-90deg', {
                    duration: 200,
                    easing: Easing.out(Easing.ease),
                }),
            },
        ],
    }));

    return (
        <View style={styles.container}>
            <Ionicons
                name="location-outline"
                size={18}
                color={palette.sand500}
                style={styles.locationIcon}
            />

            <Pressable onPress={onViewAll} style={styles.nameContainer} hitSlop={4}>
                <Text size="md" weight="bold" numberOfLines={1} style={styles.name}>
                    {name}
                </Text>
            </Pressable>

            <Text size="sm" color="secondary" style={styles.count}>
                ({itemCount})
            </Text>

            <Pressable onPress={onViewAll} hitSlop={8} style={styles.viewAll}>
                <Text size="sm" weight="semibold" style={{ color: palette.sage500 }}>
                    View all →
                </Text>
            </Pressable>

            <Pressable
                onPress={onToggleExpand}
                style={styles.chevronButton}
                hitSlop={8}
                accessibilityRole="button"
                accessibilityLabel={isExpanded ? 'Collapse section' : 'Expand section'}
            >
                <Animated.View style={chevronStyle}>
                    <Ionicons name="chevron-down" size={18} color={palette.sand500} />
                </Animated.View>
            </Pressable>
        </View>
    );
}

const styles = StyleSheet.create({
    container: {
        flexDirection: 'row',
        alignItems: 'center',
        paddingVertical: spacing.sm + 2,
    },
    locationIcon: {
        marginRight: spacing.xs + 2,
    },
    nameContainer: {
        flexShrink: 1,
    },
    name: {
        borderBottomWidth: 1,
        borderBottomColor: palette.cream400,
        borderStyle: 'dashed',
    },
    count: {
        marginLeft: spacing.xs,
    },
    viewAll: {
        flex: 1,
        alignItems: 'flex-end',
        marginRight: spacing.sm,
    },
    chevronButton: {
        width: 30,
        height: 30,
        borderRadius: radius.sm,
        backgroundColor: palette.cream300,
        alignItems: 'center',
        justifyContent: 'center',
    },
});