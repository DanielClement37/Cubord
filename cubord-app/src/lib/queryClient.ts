// src/lib/queryClient.ts
import { QueryClient } from '@tanstack/react-query';

export function createQueryClient(): QueryClient {
    return new QueryClient({
        defaultOptions: {
            queries: {
                staleTime: 5 * 60 * 1000,      // 5 minutes
                gcTime: 30 * 60 * 1000,         // 30 minutes
                retry: 2,
                refetchOnWindowFocus: true,
                refetchOnReconnect: true,
            },
        },
    });
}