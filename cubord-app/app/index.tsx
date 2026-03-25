// app/index.tsx
import { Redirect } from 'expo-router';
import { useAuth } from '@/contexts/AuthContext';

export default function Index() {
    const { session, loading } = useAuth();

    // Don't redirect until we know the auth state
    if (loading) return null;

    if (session) {
        return <Redirect href="/(app)" />;
    } else {
        return <Redirect href="/(auth)/signin" />;
    }
}