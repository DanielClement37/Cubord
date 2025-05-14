// src/contexts/AuthContext.tsx
import React, {
    createContext,
    useContext,
    useState,
    useCallback,
    useEffect,
    ReactNode,
} from 'react';
import { Session } from '@supabase/supabase-js';
import { useGoogleAuth } from '@hooks/useGoogleAuth';
import { supabase } from '@services/supabase';

interface AuthCtx {
    session: Session | null;
    signInWithGoogle: () => Promise<void>;
    signOut: () => Promise<void>;
}

const AuthContext = createContext<AuthCtx>({} as AuthCtx);

export const AuthProvider = ({ children }: { children: ReactNode }) => {
    const [session, setSession] = useState<Session | null>(null);
    const { signInWithGoogle: googleSignIn, signOut } = useGoogleAuth();

    useEffect(() => {
        supabase.auth.getSession().then(({ data }) => setSession(data.session));

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

    return (
        <AuthContext.Provider value={{ session, signInWithGoogle, signOut }}>
            {children}
        </AuthContext.Provider>
    );
};

export const useAuth = () => useContext(AuthContext);
