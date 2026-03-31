// src/components/dashboard/GreetingHeader.tsx
import React from 'react';
import { View, StyleSheet, Pressable } from 'react-native';
import { Text } from '@/components/ui';
import { spacing, palette, radius, fontSize } from '@/styles';
import { Ionicons } from '@expo/vector-icons';

interface GreetingHeaderProps {
    userName: string;
    householdName: string;
    onHouseholdPress: () => void;
}

function getGreeting(): string {
    const hour = new Date().getHours();
    if (hour < 12) return 'Good morning';
    if (hour < 18) return 'Good afternoon';
    return 'Good evening';
}

export function GreetingHeader({ userName, householdName, onHouseholdPress }: GreetingHeaderProps) {
    const greeting = getGreeting();
    const firstName = userName.split(' ')[0];

    return (
        <View style={styles.container}>
            <View style={styles.topRow}>
                <Text
                    size="xl"
                    weight="bold"
                    style={styles.greeting}
                    numberOfLines={1}
                >
                    {greeting}, {firstName}
                </Text>
                <View style={styles.avatar}>
                    <Ionicons name="person" size={22} color={palette.sand400} />
                </View>
            </View>

            <Pressable style={styles.householdPill} onPress={onHouseholdPress}>
                <Ionicons name="home" size={14} color={palette.sage600} />
                <Text size="sm" weight="semibold" style={styles.householdText}>
                    {householdName}
                </Text>
                <Ionicons name="chevron-down" size={12} color={palette.sand400} />
            </Pressable>
        </View>
    );
}

const styles = StyleSheet.create({
    container: {
        gap: spacing.sm + 2,
    },
    topRow: {
        flexDirection: 'row',
        justifyContent: 'space-between',
        alignItems: 'center',
    },
    greeting: {
        flex: 1,
        marginRight: spacing.md,
    },
    avatar: {
        width: 48,
        height: 48,
        borderRadius: radius.full,
        backgroundColor: palette.cream300,
        justifyContent: 'center',
        alignItems: 'center',
    },
    householdPill: {
        flexDirection: 'row',
        alignItems: 'center',
        alignSelf: 'flex-start',
        gap: 6,
        backgroundColor: palette.cream200,
        paddingHorizontal: 14,
        paddingVertical: 8,
        borderRadius: radius.full,
    },
    householdText: {
        color: palette.sand700,
    },
});