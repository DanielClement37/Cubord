// app/index.tsx
import { Redirect } from 'expo-router';
import { useAuth } from '@contexts/AuthContext';

export default function Index() {
    const { session } = useAuth();

    // Redirect based on authentication status
    if (session) {
        return <Redirect href="/(app)" />;
    } else {
        return <Redirect href="/(auth)/signin" />;
    }
}