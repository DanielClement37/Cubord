// app/_layout.tsx
import 'react-native-gesture-handler'; // Must be at the very top!
import { Slot } from 'expo-router';
import { AuthProvider } from '@contexts/AuthContext';
import { configureGoogle } from '@boot/googleConfig';
import { GestureHandlerRootView } from 'react-native-gesture-handler';

configureGoogle();                       // run once on bundle

export default function Root() {
    return (
        <GestureHandlerRootView style={{ flex: 1 }}>
            <AuthProvider>
                {/* Always render a Slot first to avoid navigation before mounting */}
                <Slot />
            </AuthProvider>
        </GestureHandlerRootView>
    );
}
