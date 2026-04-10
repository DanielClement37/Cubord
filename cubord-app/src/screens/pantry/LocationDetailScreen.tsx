import React, { useState, useCallback, useMemo } from 'react';
import {
    View,
    ScrollView,
    StyleSheet,
    RefreshControl,
    Pressable,
} from 'react-native';
import { useRouter } from 'expo-router';
import { useQueryClient } from '@tanstack/react-query';
import { usePantryItemsByLocation } from '@/hooks/queries';
import { ScreenContainer, Text } from '@/components/ui';
import {
    SearchBar,
    PantryItemCard,
    EmptyPantryState,
} from '@/components/pantry';
import { Spinner } from '@/components/ui';
import { palette } from '@/styles/colors';
import { spacing, radius, shadow } from '@/styles/tokens';
import { Ionicons } from '@expo/vector-icons';
import { getDaysUntilExpiry } from '@/utils/expirationStatus';
import type { LocationResponse, PantryItemResponse } from '@/types';

type SortMode = 'name' | 'expiration';

interface LocationDetailScreenProps {
    location: LocationResponse;
    onBack: () => void;
    onItemPress: (item: PantryItemResponse) => void;
}

export function LocationDetailScreen({
                                         location,
                                         onBack,
                                         onItemPress,
                                     }: LocationDetailScreenProps) {
    const router = useRouter();
    const queryClient = useQueryClient();

    const [sortMode, setSortMode] = useState<SortMode>('name');
    const [searchQuery, setSearchQuery] = useState('');
    const [showSortPicker, setShowSortPicker] = useState(false);

    // ── Query ────────────────────────────────
    const {
        data: items = [],
        isLoading,
        isRefetching,
    } = usePantryItemsByLocation(location.id);

    // ── Search ───────────────────────────────
    const searched = useMemo(() => {
        if (!searchQuery) return items;
        const q = searchQuery.toLowerCase();
        return items.filter(
            (i) =>
                i.product.name.toLowerCase().includes(q) ||
                (i.product.brand?.toLowerCase().includes(q) ?? false) ||
                (i.product.category?.toLowerCase().includes(q) ?? false),
        );
    }, [items, searchQuery]);

    // ── Sort ─────────────────────────────────
    const sorted = useMemo(() => {
        const list = [...searched];
        if (sortMode === 'name') {
            list.sort((a, b) =>
                a.product.name.localeCompare(b.product.name),
            );
        } else {
            // Expiration: soonest first, nulls last
            list.sort((a, b) => {
                const dA = getDaysUntilExpiry(a.expirationDate);
                const dB = getDaysUntilExpiry(b.expirationDate);
                if (dA === null && dB === null) return 0;
                if (dA === null) return 1;
                if (dB === null) return -1;
                return dA - dB;
            });
        }
        return list;
    }, [searched, sortMode]);

    // ── Pull to refresh ──────────────────────
    const onRefresh = useCallback(() => {
        queryClient.invalidateQueries({
            queryKey: ['location-pantry-items', location.id],
        });
    }, [queryClient, location.id]);

    const handleSearchChange = useCallback((q: string) => {
        setSearchQuery(q);
    }, []);

    const handleFabPress = useCallback(() => {
        router.push('/scan');
    }, [router]);

    const sortLabel = sortMode === 'name' ? 'Name' : 'Expiration';

    return (
        <ScreenContainer>
            {/* Header */}
            <View style={styles.header}>
                <Pressable
                    onPress={onBack}
                    hitSlop={12}
                    style={styles.backButton}
                    accessibilityRole="button"
                    accessibilityLabel="Go back"
                >
                    <Ionicons
                        name="chevron-back"
                        size={22}
                        color={palette.sand800}
                    />
                    <Text size="md" weight="semibold">
                        Back
                    </Text>
                </Pressable>

                <Pressable hitSlop={12} accessibilityLabel="More options">
                    <Ionicons
                        name="ellipsis-horizontal"
                        size={22}
                        color={palette.sand500}
                    />
                </Pressable>
            </View>

            {/* Location name & count */}
            <Text size="xl" weight="bold" style={styles.title}>
                {location.name}
            </Text>
            <Text size="sm" color="secondary" style={styles.subtitle}>
                {items.length} {items.length === 1 ? 'item' : 'items'}
            </Text>

            {/* Search */}
            <View style={styles.searchRow}>
                <SearchBar onSearch={handleSearchChange} />
            </View>

            {/* Sort */}
            <View style={styles.sortRow}>
                <Text size="sm" color="secondary">
                    Sort by:
                </Text>
                <Pressable
                    onPress={() => setShowSortPicker((v) => !v)}
                    style={styles.sortButton}
                    accessibilityRole="button"
                    accessibilityLabel={`Sort by ${sortLabel}`}
                >
                    <Text size="sm" weight="semibold">
                        {sortLabel}
                    </Text>
                    <Ionicons
                        name="chevron-down"
                        size={14}
                        color={palette.sand500}
                    />
                </Pressable>
            </View>

            {/* Sort picker dropdown */}
            {showSortPicker && (
                <View style={styles.sortDropdown}>
                    <Pressable
                        onPress={() => {
                            setSortMode('name');
                            setShowSortPicker(false);
                        }}
                        style={[
                            styles.sortOption,
                            sortMode === 'name' && styles.sortOptionActive,
                        ]}
                    >
                        <Text
                            size="md"
                            weight={sortMode === 'name' ? 'semibold' : 'regular'}
                        >
                            Name
                        </Text>
                    </Pressable>
                    <Pressable
                        onPress={() => {
                            setSortMode('expiration');
                            setShowSortPicker(false);
                        }}
                        style={[
                            styles.sortOption,
                            sortMode === 'expiration' && styles.sortOptionActive,
                        ]}
                    >
                        <Text
                            size="md"
                            weight={
                                sortMode === 'expiration'
                                    ? 'semibold'
                                    : 'regular'
                            }
                        >
                            Expiration
                        </Text>
                    </Pressable>
                </View>
            )}

            {/* Items list */}
            {isLoading ? (
                <View style={styles.loadingContainer}>
                    <Spinner size="md" />
                </View>
            ) : (
                <ScrollView
                    showsVerticalScrollIndicator={false}
                    contentContainerStyle={styles.scroll}
                    refreshControl={
                        <RefreshControl
                            refreshing={isRefetching}
                            onRefresh={onRefresh}
                        />
                    }
                >
                    {sorted.length === 0 ? (
                        <EmptyPantryState
                            variant="items"
                            onAction={handleFabPress}
                        />
                    ) : (
                        sorted.map((item) => (
                            <PantryItemCard
                                key={item.id}
                                item={item}
                                onPress={() => onItemPress(item)}
                            />
                        ))
                    )}
                </ScrollView>
            )}

            {/* FAB */}
            <Pressable
                onPress={handleFabPress}
                style={styles.fab}
                accessibilityRole="button"
                accessibilityLabel="Add item"
            >
                <Ionicons name="add" size={28} color={palette.white} />
            </Pressable>
        </ScreenContainer>
    );
}

const styles = StyleSheet.create({
    header: {
        flexDirection: 'row',
        alignItems: 'center',
        justifyContent: 'space-between',
        marginBottom: spacing.sm,
    },
    backButton: {
        flexDirection: 'row',
        alignItems: 'center',
        gap: spacing.xs,
    },
    title: {
        marginBottom: spacing.xs - 2,
    },
    subtitle: {
        marginBottom: spacing.sm,
    },
    searchRow: {
        marginBottom: spacing.sm + 2,
    },
    sortRow: {
        flexDirection: 'row',
        alignItems: 'center',
        gap: spacing.sm,
        marginBottom: spacing.sm,
    },
    sortButton: {
        flexDirection: 'row',
        alignItems: 'center',
        gap: spacing.xs,
        paddingHorizontal: spacing.sm + 4,
        paddingVertical: spacing.sm - 2,
        borderRadius: radius.sm,
        borderWidth: 1,
        borderColor: palette.sand100,
        backgroundColor: palette.white,
    },
    sortDropdown: {
        backgroundColor: palette.white,
        borderWidth: 1,
        borderColor: palette.cream300,
        borderRadius: radius.md,
        marginTop: -spacing.sm + 2,
        marginBottom: spacing.sm,
        overflow: 'hidden',
    },
    sortOption: {
        paddingHorizontal: spacing.md,
        paddingVertical: spacing.sm + 4,
        borderBottomWidth: 1,
        borderBottomColor: palette.cream200,
    },
    sortOptionActive: {
        backgroundColor: palette.sage50,
    },
    loadingContainer: {
        flex: 1,
        alignItems: 'center',
        justifyContent: 'center',
        paddingVertical: spacing.xxl,
    },
    scroll: {
        gap: spacing.sm,
        paddingBottom: spacing.xxl + spacing.xxl,
    },
    fab: {
        position: 'absolute',
        bottom: spacing.md,
        right: spacing.md,
        width: 54,
        height: 54,
        borderRadius: radius.lg - 4,
        backgroundColor: palette.sage500,
        alignItems: 'center',
        justifyContent: 'center',
        ...shadow.lg,
    },
});