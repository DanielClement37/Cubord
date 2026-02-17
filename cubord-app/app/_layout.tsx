// app/_layout.tsx
import 'react-native-gesture-handler'; // Must be at the very top!
import { useState } from 'react';
import { Slot } from 'expo-router';
import { QueryClientProvider } from '@tanstack/react-query';
import { AuthProvider } from '@/contexts/AuthContext';
import { configureGoogle } from '@/boot/googleConfig';
import { GestureHandlerRootView } from 'react-native-gesture-handler';
import { createQueryClient } from '@/lib/queryClient';
import Toast from 'react-native-toast-message';
import { toastConfig } from '@/components/ui';
import {
    useFonts,
    NunitoSans_400Regular,
    NunitoSans_500Medium,
    NunitoSans_600SemiBold,
    NunitoSans_700Bold,
} from '@expo-google-fonts/nunito-sans';
import * as SplashScreen from 'expo-splash-screen';

// Keep splash screen visible while fonts load
SplashScreen.preventAutoHideAsync();

configureGoogle();                       // run once on bundle

export default function Root() {
    const [queryClient] = useState(createQueryClient);

    const [fontsLoaded, fontError] = useFonts({
        'NunitoSans-Regular':  NunitoSans_400Regular,
        'NunitoSans-Medium':   NunitoSans_500Medium,
        'NunitoSans-SemiBold': NunitoSans_600SemiBold,
        'NunitoSans-Bold':     NunitoSans_700Bold,
    });

    // Hide splash once fonts are ready (or if there's an error — don't block forever)
    if (fontsLoaded || fontError) {
        SplashScreen.hideAsync();
    }

    if (!fontsLoaded && !fontError) {
        return null; // render nothing while loading
    }

    return (
        <GestureHandlerRootView style={{ flex: 1 }}>
            <QueryClientProvider client={queryClient}>
                <AuthProvider>
                    <Slot />
                </AuthProvider>
            </QueryClientProvider>
            <Toast config={toastConfig} />
        </GestureHandlerRootView>
    );
}