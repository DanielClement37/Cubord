// app/_layout.tsx
import { Slot } from 'expo-router';
import { AuthProvider } from '@contexts/AuthContext';
import { configureGoogle } from '@boot/googleConfig';

configureGoogle();                       // run once on bundle

export default function Root() {
    return (
        <AuthProvider>
            {/* Always render a Slot first to avoid navigation before mounting */}
            <Slot />
        </AuthProvider>
    );
}
