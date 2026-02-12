// app/_layout.tsx
import 'react-native-gesture-handler'; // Must be at the very top!
import { useState } from 'react';
import { Slot } from 'expo-router';
import { QueryClientProvider } from '@tanstack/react-query';
import { AuthProvider } from '@/contexts/AuthContext';
import { configureGoogle } from '@/boot/googleConfig';
import { GestureHandlerRootView } from 'react-native-gesture-handler';
import { createQueryClient } from '@/lib/queryClient';

configureGoogle();                       // run once on bundle

export default function Root() {
    const [queryClient] = useState(createQueryClient);

    return (
        <GestureHandlerRootView style={{ flex: 1 }}>
            <QueryClientProvider client={queryClient}>
                <AuthProvider>
                    <Slot />
                </AuthProvider>
            </QueryClientProvider>
        </GestureHandlerRootView>
    );
}