// app/(auth)/_layout.tsx
import { Slot, Redirect } from 'expo-router';
import { useAuth } from '@contexts/AuthContext';

export default function AuthLayout() {
    const { session } = useAuth();

    // If already authenticated, redirect to app
    if (session) {
        return <Redirect href="/(app)" />;
    }

    // If not authenticated, render the auth routes
    return <Slot />;
}