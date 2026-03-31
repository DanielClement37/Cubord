import React, { useCallback, useState } from 'react';
import { ScrollView, StyleSheet, RefreshControl } from 'react-native';
import { useRouter } from 'expo-router';
import { useQueryClient } from '@tanstack/react-query';
import { useAuth } from '@/contexts/AuthContext';
import { useAppStore } from '@/stores/appStore';
import { useHouseholds } from '@/hooks/queries';
import { usePantryStatistics } from '@/hooks/queries';
import { useExpiringItems } from '@/hooks/queries';
import { useLocations } from '@/hooks/queries';
import { ScreenContainer } from '@/components/ui';
import {
    GreetingHeader,
    PantryOverviewCard,
    NeedsAttentionSection,
    QuickAccessSection,
    DashboardSkeleton,
    HouseholdPicker,
} from '@/components/dashboard';
import { spacing } from '@/styles';

export default function HomeScreen() {
    const { user } = useAuth();
    const router = useRouter();
    const queryClient = useQueryClient();
    const activeHouseholdId = useAppStore((s) => s.activeHouseholdId);
    const [pickerVisible, setPickerVisible] = useState(false);

    // ── Queries ──────────────────────────────
    const { data: households } = useHouseholds();
    const {
        data: statistics,
        isLoading: statsLoading,
        isRefetching: statsRefetching,
    } = usePantryStatistics(activeHouseholdId ?? undefined);
    const {
        data: expiringItems,
        isLoading: expiringLoading,
        isRefetching: expiringRefetching,
    } = useExpiringItems(activeHouseholdId ?? undefined);
    const {
        data: locations,
        isLoading: locationsLoading,
        isRefetching: locationsRefetching,
    } = useLocations(activeHouseholdId ?? undefined);

    // ── Derived data ─────────────────────────
    const activeHousehold = households?.find((h) => h.id === activeHouseholdId);
    const householdName = activeHousehold?.name ?? 'My Home';
    const userName = user?.user_metadata?.full_name ?? user?.email ?? 'there';

    const totalItems = statistics?.totalItems;
    const locationsCount = locations?.length;

    const isAnyLoading = statsLoading || expiringLoading || locationsLoading;
    const isRefreshing = statsRefetching || expiringRefetching || locationsRefetching;

    // ── Pull to refresh ──────────────────────
    const onRefresh = useCallback(() => {
        queryClient.invalidateQueries({ queryKey: ['pantry-items', activeHouseholdId] });
        queryClient.invalidateQueries({ queryKey: ['locations', activeHouseholdId] });
        queryClient.invalidateQueries({ queryKey: ['households'] });
    }, [queryClient, activeHouseholdId]);

    // ── Handlers ─────────────────────────────
    const handleHouseholdPress = () => {
        setPickerVisible(true);
    };

    const handleExpiringSoonPress = () => {
        router.push('/pantry');
    };

    const handleLowStockPress = () => {
        router.push('/pantry');
    };

    const handleLocationPress = () => {
        router.push('/pantry');
    };

    const handleAddLocationPress = () => {
        // TODO: Navigate to add location flow
    };

    return (
        <ScreenContainer>
            <ScrollView
                showsVerticalScrollIndicator={false}
                contentContainerStyle={styles.scroll}
                refreshControl={
                    <RefreshControl refreshing={isRefreshing} onRefresh={onRefresh} />
                }
            >
                {isAnyLoading && !isRefreshing ? (
                    <DashboardSkeleton />
                ) : (
                    <>
                        <GreetingHeader
                            userName={userName}
                            householdName={householdName}
                            onHouseholdPress={handleHouseholdPress}
                        />

                        <PantryOverviewCard
                            totalItems={totalItems}
                            locationsCount={locationsCount}
                            isLoading={statsLoading || locationsLoading}
                        />

                        <NeedsAttentionSection
                            expiringItems={expiringItems}
                            statistics={statistics}
                            isLoading={expiringLoading || statsLoading}
                            onExpiringSoonPress={handleExpiringSoonPress}
                            onLowStockPress={handleLowStockPress}
                        />

                        <QuickAccessSection
                            locations={locations}
                            isLoading={locationsLoading}
                            onLocationPress={handleLocationPress}
                            onAddLocationPress={handleAddLocationPress}
                        />
                    </>
                )}
            </ScrollView>

            <HouseholdPicker
                visible={pickerVisible}
                onClose={() => setPickerVisible(false)}
            />
        </ScreenContainer>
    );
}

const styles = StyleSheet.create({
    scroll: {
        gap: spacing.lg,
        paddingBottom: spacing.xxl,
    },
});