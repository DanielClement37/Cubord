// app/(app)/_layout.tsx
import { Redirect } from 'expo-router';
import { Tabs } from 'expo-router';
import { useAuth } from '@/contexts/AuthContext';
import { palette } from '@/styles/colors';
import {fontFamily, fontSize, radius, shadow} from '@/styles/tokens';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import {
    HomeTabIcon,
    ScanTabIcon,
    PantryTabIcon,
    ProfileTabIcon,
} from '@/components/ui/TabIcons';

const ACTIVE_COLOR = palette.sage700;
const INACTIVE_COLOR = palette.sand400;
const TAB_BAR_BG = palette.cream100;

export default function AppLayout() {
    const { session } = useAuth();
    const insets = useSafeAreaInsets();

    if (!session) {
        return <Redirect href="/signin" />;
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
                    tabBarIcon: ({focused, color}) => (
                        <HomeTabIcon focused={focused} color={color}/>
                    ),
                }}
            />
            <Tabs.Screen
                name="scan"
                options={{
                    title: 'Scan',
                    tabBarIcon: ({focused, color}) => (
                        <ScanTabIcon focused={focused} color={color}/>
                    ),
                }}
            />
            <Tabs.Screen
                name="pantry"
                options={{
                    title: 'Pantry',
                    tabBarIcon: ({focused, color}) => (
                        <PantryTabIcon focused={focused} color={color}/>
                    ),
                }}
            />
            <Tabs.Screen
                name="profile"
                options={{
                    title: 'Profile',
                    tabBarIcon: ({focused, color}) => (
                        <ProfileTabIcon focused={focused} color={color}/>
                    ),
                }}
            />
        </Tabs>
    );
}