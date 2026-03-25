// src/contexts/AuthContext.tsx
import React, {
    createContext,
    useContext,
    useState,
    useCallback,
    useEffect,
    ReactNode,
} from 'react';
import { Session, User } from '@supabase/supabase-js';
import { useGoogleAuth } from '@/hooks/useGoogleAuth';
import { supabase } from '@/services/supabase';
import { useAppStore } from '@/stores/appStore';

interface AuthCtx {
    session: Session | null;
    user: User | null;
    loading: boolean;
    signInWithGoogle: () => Promise<void>;
    signOut: () => Promise<void>;
}

const AuthContext = createContext<AuthCtx>({} as AuthCtx);

export const AuthProvider = ({ children }: { children: ReactNode }) => {
    const [session, setSession] = useState<Session | null>(null);
    const [loading, setLoading] = useState(true);
    const { signInWithGoogle: googleSignIn, signOut: googleSignOut } = useGoogleAuth();

    useEffect(() => {
        supabase.auth.getSession().then(({ data }) => {
            setSession(data.session);
            setLoading(false);
        });

        const {
            data: { subscription },
        } = supabase.auth.onAuthStateChange((_event, newSession) => {
            setSession(newSession);
        });

        return () => subscription.unsubscribe();
    }, []);

    const signInWithGoogle = useCallback(async () => {
        try {
            await googleSignIn();
        } catch (err) {
            console.error('[AuthContext] Google sign‑in failed:', err);
            throw err;
        }
    }, [googleSignIn]);

    const signOut = useCallback(async () => {
        await googleSignOut();
        useAppStore.getState().clearActiveHousehold();
    }, [googleSignOut]);

    return (
        <AuthContext.Provider
            value={{
                session,
                user: session?.user ?? null,
                loading,
                signInWithGoogle,
                signOut,
            }}
        >
            {children}
        </AuthContext.Provider>
    );
};

export const useAuth = () => useContext(AuthContext);
