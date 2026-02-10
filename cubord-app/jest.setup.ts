// jest.setup.ts
import '@testing-library/jest-native/extend-expect';
import { server } from '@test/mocks/server';

// Start an MSW server before all tests
beforeAll(() => server.listen({ onUnhandledRequest: 'warn' }));

// Reset handlers after each test (important for test isolation)
afterEach(() => server.resetHandlers());

// Clean up after all tests are done
afterAll(() => server.close());
