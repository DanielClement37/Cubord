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
    /** Throws on failure so caller can show toast / alert */
    signInWithGoogle: () => Promise<void>;
    signOut: () => Promise<void>;
}

const AuthContext = createContext<AuthCtx>({} as AuthCtx);

export const AuthProvider = ({ children }: { children: ReactNode }) => {
    const [session, setSession] = useState<Session | null>(null);
    const { signInWithGoogle: googleSignIn, signOut } = useGoogleAuth();

    /** ───────────────────────────
     *  1.  Bootstrap + listener
     *  ─────────────────────────── */
    useEffect(() => {
        // A) grab any cached session on mount
        supabase.auth.getSession().then(({ data }) => setSession(data.session));

        // B) subscribe to all future changes
        const {
            data: { subscription },
        } = supabase.auth.onAuthStateChange((_event, newSession) => {
            setSession(newSession);
        });

        // C) clean up
        return () => subscription.unsubscribe();
    }, []);

    /** ───────────────────────────
     *  2.  Wrapper for Google sign‑in
     *  ─────────────────────────── */
    const signInWithGoogle = useCallback(async () => {
        try {
            await googleSignIn(); // listener will populate session
        } catch (err) {
            console.error('[AuthContext] Google sign‑in failed:', err);
            throw err; // let caller decide how to present error
        }
    }, [googleSignIn]);

    return (
        <AuthContext.Provider value={{ session, signInWithGoogle, signOut }}>
            {children}
        </AuthContext.Provider>
    );
};

export const useAuth = () => useContext(AuthContext);
