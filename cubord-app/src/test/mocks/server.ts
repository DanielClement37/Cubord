// src/test/mocks/server.ts
import { setupServer } from 'msw/native';
import { handlers } from './handlers';

/**
 * MSW server instance for intercepting network requests in tests.
 * Lifecycle is managed in jest.setup.ts.
 */
export const server = setupServer(...handlers);