// src/hooks/useGoogleAuth.ts
import { useCallback } from 'react';
import {
    GoogleSignin,
    isSuccessResponse,
    isErrorWithCode,
    statusCodes
} from '@react-native-google-signin/google-signin';
import { supabase } from '@services/supabase';

export function useGoogleAuth() {
    /** Google → Supabase */
    const signInWithGoogle = useCallback(async () => {
        try {
            await GoogleSignin.hasPlayServices({ showPlayServicesUpdateDialog: true });
            const result = await GoogleSignin.signIn();

            if (!isSuccessResponse(result)) {
                return;
            }
            const idToken = result.data.idToken;
            const { accessToken } = await GoogleSignin.getTokens();

            const { data, error } = await supabase.auth.signInWithIdToken({
                provider: 'google',
                token: idToken!,
                access_token: accessToken
            });

            if (error) throw error;
            return data;
        } catch (err) {
            if (isErrorWithCode(err)) {
                switch (err.code) {
                    case statusCodes.SIGN_IN_CANCELLED:
                        break;
                    case statusCodes.IN_PROGRESS:
                        console.warn('Google sign‑in already in progress');
                        break;
                    case statusCodes.PLAY_SERVICES_NOT_AVAILABLE:
                        alert('Google Play Services not available or outdated');
                        break;
                    default:
                        console.error('[GoogleSignIn]', err.message);
                }
            } else {
                console.error('[GoogleSignIn]', err);
            }
            throw err;
        }
    }, []);

    const signOut = useCallback(async () => {
        await GoogleSignin.signOut().catch(() => {});
        await supabase.auth.signOut();
    }, []);


    return { signInWithGoogle, signOut };
}
