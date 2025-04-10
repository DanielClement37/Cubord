// app/(app)/_layout.tsx
import { Slot, Redirect } from 'expo-router';
import { useAuth } from '@contexts/AuthContext';

export default function AppLayout() {
    const { session } = useAuth();

    // If not authenticated, redirect to signin
    if (!session) {
        return <Redirect href="/signin" />;
    }

    // If authenticated, render the app routes
    return <Slot />;
}