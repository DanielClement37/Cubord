import { createClient } from '@supabase/supabase-js';
import Constants from 'expo-constants';

const { SUPABASE_URL, SUPABASE_ANON_KEY } = Constants.expoConfig!.extra as {
    SUPABASE_URL: string;
    SUPABASE_ANON_KEY: string;
};

export const supabase = createClient(SUPABASE_URL, SUPABASE_ANON_KEY, {
    auth: {
        persistSession: true,
        autoRefreshToken: true
    }
});