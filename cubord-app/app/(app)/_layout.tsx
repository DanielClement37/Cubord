// app/(app)/_layout.tsx
import { Redirect } from 'expo-router';
import { Tabs } from 'expo-router';
import { View, ActivityIndicator, StyleSheet } from 'react-native';
import { useAuth } from '@/contexts/AuthContext';
import { useAppInitialization } from '@/hooks/useAppInitialization';
import { palette } from '@/styles/colors';
import { fontFamily, fontSize, radius, shadow } from '@/styles/tokens';
import {Text, Button, HomeTabIcon, ScanTabIcon, PantryTabIcon, ProfileTabIcon} from '@/components/ui';
import {WelcomeScreen} from "@/screens/WelcomeScreen";
import {useSafeAreaInsets} from "react-native-safe-area-context";

const ACTIVE_COLOR = palette.sage700;
const INACTIVE_COLOR = palette.sand400;
const TAB_BAR_BG = palette.cream100;

export default function AppLayout() {
    const { session } = useAuth();
    const insets = useSafeAreaInsets();

    if (!session) {
        return <Redirect href="/signin" />;
    }

    // Gate: initialization must complete before showing tabs
    const { status, refetch } = useAppInitialization();

    if (status === 'loading') {
        return (
            <View style={styles.center}>
                <ActivityIndicator size="large" color={palette.sage700} />
            </View>
        );
    }

    if (status === 'error') {
        return (
            <View style={styles.center}>
                <Text size="lg" weight="semibold" align="center">
                    Couldn't connect to server
                </Text>
                <Text
                    size="md"
                    color="secondary"
                    align="center"
                    style={{ marginTop: 8, marginBottom: 24 }}
                >
                    Check your connection and try again
                </Text>
                <Button label="Retry" onPress={() => refetch()} />
            </View>
        );
    }

    if (status === 'needs-household') {
        return <WelcomeScreen />;
    }

    return (
        <Tabs
            screenOptions={{
                headerShown: false,
                tabBarActiveTintColor: ACTIVE_COLOR,
                tabBarInactiveTintColor: INACTIVE_COLOR,
                tabBarLabelStyle: {
                    fontFamily: fontFamily.semibold,
                    fontSize: fontSize.xs,
                },
                tabBarStyle: {
                    backgroundColor: TAB_BAR_BG,
                    borderTopWidth: 3,
                    borderColor: palette.cream200,
                    borderRadius: radius.md,
                    height: 56 + insets.bottom,
                    paddingBottom: insets.bottom,
                    paddingTop: 6,
                    ...shadow.sm,
                },
            }}
        >
            <Tabs.Screen
                name="index"
                options={{
                    title: 'Home',
                    tabBarIcon: ({ focused, color }) => (
                        <HomeTabIcon focused={focused} color={color} />
                    ),
                }}
            />
            <Tabs.Screen
                name="scan"
                options={{
                    title: 'Scan',
                    tabBarIcon: ({ focused, color }) => (
                        <ScanTabIcon focused={focused} color={color} />
                    ),
                }}
            />
            <Tabs.Screen
                name="pantry"
                options={{
                    title: 'Pantry',
                    tabBarIcon: ({ focused, color }) => (
                        <PantryTabIcon focused={focused} color={color} />
                    ),
                }}
            />
            <Tabs.Screen
                name="profile"
                options={{
                    title: 'Profile',
                    tabBarIcon: ({ focused, color }) => (
                        <ProfileTabIcon focused={focused} color={color} />
                    ),
                }}
            />
        </Tabs>
    );
}

const styles = StyleSheet.create({
    center: {
        flex: 1,
        justifyContent: 'center',
        alignItems: 'center',
        backgroundColor: palette.cream100,
    },
});