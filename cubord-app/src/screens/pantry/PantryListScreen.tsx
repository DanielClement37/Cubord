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
import { useAppStore } from '@/stores/appStore';
import {
    useLocations,
    usePantryItems,
    usePantryStatistics,
    useExpiringItems,
    useLowStockItems,
} from '@/hooks/queries';
import { ScreenContainer, Text } from '@/components/ui';
import {
    SearchBar,
    FilterChips,
    LocationGroupHeader,
    PantryItemCard,
    EmptyPantryState,
    PantrySkeleton,
    type FilterChipOption,
} from '@/components/pantry';
import { palette } from '@/styles/colors';
import { spacing, radius, shadow } from '@/styles/tokens';
import { Ionicons } from '@expo/vector-icons';
import type { LocationResponse, PantryItemResponse } from '@/types';

/** Max items shown per location group before the "show more" link. */
const ITEMS_PER_GROUP = 3;

/** Page size for the initial pantry items fetch. */
const PAGE_SIZE = 100;

type FilterId = 'all' | 'expiring' | 'low-stock';

interface PantryListScreenProps {
    onLocationPress: (location: LocationResponse) => void;
    onItemPress: (item: PantryItemResponse) => void;
}

export function PantryListScreen({
                                     onLocationPress,
                                     onItemPress,
                                 }: PantryListScreenProps) {
    const router = useRouter();
    const queryClient = useQueryClient();
    const householdId = useAppStore((s) => s.activeHouseholdId);

    // ── Local state ──────────────────────────
    const [activeFilter, setActiveFilter] = useState<FilterId>('all');
    const [searchQuery, setSearchQuery] = useState('');
    const [expandedLocations, setExpandedLocations] = useState<
        Record<string, boolean>
    >({});

    // ── Queries ──────────────────────────────
    const {
        data: pantryData,
        isLoading: itemsLoading,
        isRefetching: itemsRefetching,
    } = usePantryItems(householdId ?? undefined, 0, PAGE_SIZE);

    const {
        data: locations,
        isLoading: locationsLoading,
        isRefetching: locationsRefetching,
    } = useLocations(householdId ?? undefined);

    const {
        data: statistics,
    } = usePantryStatistics(householdId ?? undefined);

    const {
        data: expiringItems,
    } = useExpiringItems(householdId ?? undefined);

    const {
        data: lowStockItems,
    } = useLowStockItems(householdId ?? undefined);

    const allItems = pantryData?.content ?? [];
    const isLoading = itemsLoading || locationsLoading;
    const isRefreshing = itemsRefetching || locationsRefetching;

    // ── Filter chip options ──────────────────
    const expiringCount = expiringItems?.length ?? statistics?.expiringCount ?? 0;
    const lowStockCount = lowStockItems?.length ?? statistics?.lowStockCount ?? 0;

    const filterOptions: FilterChipOption[] = useMemo(
        () => [
            { id: 'all', label: `All (${allItems.length})` },
            { id: 'expiring', label: `Expiring (${expiringCount})` },
            { id: 'low-stock', label: `Low Stock (${lowStockCount})` },
        ],
        [allItems.length, expiringCount, lowStockCount],
    );

    // ── Build expiring / low-stock ID sets for fast lookup ───────────
    const expiringIdSet = useMemo(
        () => new Set((expiringItems ?? []).map((i) => i.id)),
        [expiringItems],
    );
    const lowStockIdSet = useMemo(
        () => new Set((lowStockItems ?? []).map((i) => i.id)),
        [lowStockItems],
    );

    // ── Filtered & searched items ────────────
    const filteredItems = useMemo(() => {
        let items = allItems;

        // Apply filter
        if (activeFilter === 'expiring') {
            items = items.filter((i) => expiringIdSet.has(i.id));
        } else if (activeFilter === 'low-stock') {
            items = items.filter((i) => lowStockIdSet.has(i.id));
        }

        // Apply search
        if (searchQuery) {
            const q = searchQuery.toLowerCase();
            items = items.filter(
                (i) =>
                    i.product.name.toLowerCase().includes(q) ||
                    (i.product.brand?.toLowerCase().includes(q) ?? false) ||
                    (i.product.category?.toLowerCase().includes(q) ?? false),
            );
        }

        return items;
    }, [allItems, activeFilter, searchQuery, expiringIdSet, lowStockIdSet]);

    // ── Group by location ────────────────────
    const groupedByLocation = useMemo(() => {
        const groups: Record<
            string,
            { location: LocationResponse; items: PantryItemResponse[] }
        > = {};

        // Pre-populate with all locations so empty ones can still show
        for (const loc of locations ?? []) {
            groups[loc.id] = { location: loc, items: [] };
        }

        for (const item of filteredItems) {
            const locId = item.location.id;
            if (!groups[locId]) {
                groups[locId] = { location: item.location, items: [] };
            }
            groups[locId].items.push(item);
        }

        return groups;
    }, [filteredItems, locations]);

    // ── Expand/collapse ──────────────────────
    // Default: all locations expanded
    const isExpanded = useCallback(
        (locationId: string) => expandedLocations[locationId],
        [expandedLocations],
    );

    const toggleExpanded = useCallback((locationId: string) => {
        setExpandedLocations((prev) => ({
            ...prev,
            [locationId]: !prev[locationId],
        }));
    }, []);

    // ── Pull to refresh ──────────────────────
    const onRefresh = useCallback(() => {
        if (!householdId) return;
        queryClient.invalidateQueries({ queryKey: ['pantry-items', householdId] });
        queryClient.invalidateQueries({ queryKey: ['locations', householdId] });
    }, [queryClient, householdId]);

    // ── Handlers ─────────────────────────────
    const handleSearchChange = useCallback((query: string) => {
        setSearchQuery(query);
    }, []);

    const handleFabPress = useCallback(() => {
        router.push('/scan');
    }, [router]);

    // ── Render ───────────────────────────────
    const isEmpty = allItems.length === 0 && !isLoading;
    const noLocations = (locations?.length ?? 0) === 0 && !isLoading;

    return (
        <ScreenContainer>
            {isLoading && !isRefreshing ? (
                <PantrySkeleton />
            ) : (
                <>
                    <ScrollView
                        showsVerticalScrollIndicator={false}
                        contentContainerStyle={styles.scroll}
                        refreshControl={
                            <RefreshControl
                                refreshing={isRefreshing}
                                onRefresh={onRefresh}
                            />
                        }
                    >
                        {/* Title */}
                        <Text size="xl" weight="bold">
                            Pantry
                        </Text>

                        {/* Search */}
                        <SearchBar onSearch={handleSearchChange} />

                        {/* Filter chips */}
                        <FilterChips
                            options={filterOptions}
                            activeId={activeFilter}
                            onSelect={(id) => setActiveFilter(id as FilterId)}
                        />

                        {/* Empty states */}
                        {noLocations && (
                            <EmptyPantryState variant="locations" />
                        )}

                        {!noLocations && isEmpty && (
                            <EmptyPantryState
                                variant="items"
                                onAction={handleFabPress}
                            />
                        )}

                        {/* Location groups */}
                        {!isEmpty &&
                            Object.values(groupedByLocation).map(
                                ({ location, items }) => {
                                    // Hide empty groups when a filter is active
                                    if (
                                        activeFilter !== 'all' &&
                                        items.length === 0
                                    ) {
                                        return null;
                                    }

                                    const expanded = isExpanded(location.id);
                                    const visibleItems = expanded
                                        ? items.slice(0, ITEMS_PER_GROUP)
                                        : [];
                                    const hiddenCount = Math.max(
                                        0,
                                        items.length - ITEMS_PER_GROUP,
                                    );

                                    return (
                                        <View
                                            key={location.id}
                                            style={styles.locationGroup}
                                        >
                                            <LocationGroupHeader
                                                name={location.name}
                                                itemCount={items.length}
                                                isExpanded={expanded}
                                                onToggleExpand={() =>
                                                    toggleExpanded(location.id)
                                                }
                                                onViewAll={() =>
                                                    onLocationPress(location)
                                                }
                                            />

                                            {expanded && (
                                                <View style={styles.itemList}>
                                                    {visibleItems.map(
                                                        (item) => (
                                                            <PantryItemCard
                                                                key={item.id}
                                                                item={item}
                                                                onPress={() =>
                                                                    onItemPress(
                                                                        item,
                                                                    )
                                                                }
                                                            />
                                                        ),
                                                    )}

                                                    {hiddenCount > 0 && (
                                                        <Pressable
                                                            onPress={() =>
                                                                onLocationPress(
                                                                    location,
                                                                )
                                                            }
                                                            style={
                                                                styles.moreLink
                                                            }
                                                        >
                                                            <Text
                                                                size="sm"
                                                                weight="semibold"
                                                                style={{
                                                                    color: palette.sage500,
                                                                }}
                                                            >
                                                                +{hiddenCount}{' '}
                                                                more item
                                                                {hiddenCount >
                                                                1
                                                                    ? 's'
                                                                    : ''}
                                                            </Text>
                                                        </Pressable>
                                                    )}
                                                </View>
                                            )}
                                        </View>
                                    );
                                },
                            )}
                    </ScrollView>

                    {/* FAB */}
                    <Pressable
                        onPress={handleFabPress}
                        style={styles.fab}
                        accessibilityRole="button"
                        accessibilityLabel="Add item"
                    >
                        <Ionicons name="add" size={28} color={palette.white} />
                    </Pressable>
                </>
            )}
        </ScreenContainer>
    );
}

const styles = StyleSheet.create({
    scroll: {
        gap: spacing.md,
        paddingBottom: spacing.xxl + spacing.xxl,
    },
    locationGroup: {
        marginBottom: spacing.xs,
    },
    itemList: {
        gap: spacing.sm,
    },
    moreLink: {
        alignItems: 'center',
        paddingVertical: spacing.sm,
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