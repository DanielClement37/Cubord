## Overview

Welcome to the Cubord App frontend codebase! This documentation will help new team members understand our frontend architecture, development workflow, and how to contribute effectively to the project.

## Tech Stack

Our frontend is built with the following technologies:

- **React Native** (v0.76.9) - Cross-platform mobile app framework
- **Expo** (v52.0.46) - React Native development platform
- **TypeScript** (v5.3.3) - For type-safe JavaScript
- **React Navigation** - For navigation between screens
- **Zustand** (v5.0.3) - For state management
- **Supabase** (v2.49.4) - For backend integration
- **Google Sign-In** - For authentication

## Project Structure

The project follows a well-organized structure:
```

|  
├── app/                    # Main application directory using file-based routing  
│   ├── (app)/              # Main app screens (authenticated routes)  
│   ├── (auth)/             # Authentication screens  
│   ├── _layout.tsx         # Root navigation layout  
│   └── index.tsx           # Entry point  
├── src/                    # Source code  
│   ├── boot/               # Startup code  
│   ├── components/         # Reusable UI components  
│   ├── contexts/           # React contexts  
│   ├── hooks/              # Custom React hooks  
│   ├── screens/            # Screen components  
│   └── services/           # API and backend services  
├── assets/                 # Static assets  
└── tsconfig.json           # TypeScript configuration
```

## Key Architectural Concepts

### 1. File-Based Routing

We use Expo Router, which implements file-based routing. This means:

- File structure in the `app/` directory determines the app's navigation structure
- files define navigation containers `_layout.tsx`
- Special directories like and represent route groups `(app)``(auth)`

### 2. Path Aliases

To keep imports clean, we've configured path aliases in : `tsconfig.json`
```json
{  
  "paths": {  
    "@components/*": ["src/components/*"],  
    "@services/*":   ["src/services/*"],  
    "@store/*":      ["src/store/*"],  
    "@hooks/*":      ["src/hooks/*"],  
    "@types/*":      ["src/types/*"],  
    "@contexts/*":   ["src/contexts/*"],  
    "@boot/*":       ["src/boot/*"]  
  }  
}
```


Always use these aliases instead of relative imports for better code readability.

### 3. Authentication Flow

The app implements an authentication flow using:

- for managing auth state and functionality `AuthContext`
- Route guards via navigation to separate authenticated and unauthenticated screens
- Integration with Google Sign-In and Supabase for auth services

## Getting Started

### Prerequisites

- Node.js (LTS version recommended)
- npm or yarn
- iOS Simulator or Android Emulator (optional)
- Expo Go app on a physical device (for testing without emulators)

### Setup Instructions

1. **Clone the repository**
    
2. **Install dependencies**
    

npm install

3. **Start the development server**

npx expo start

4. **Running the app**
    - Press `i` to open in iOS simulator
    - Press `a` to open in Android emulator
    - Scan the QR code with Expo Go app on your physical device

## Development Workflow

### Component Development

- Create reusable components in `src/components/`
- Follow our component structure:
    - Each component should be in its own folder with an for export `index.tsx`
    - Include styles in the same file or in a separate `styles.ts` file
    - Add tests in a `__tests__` folder

### Screen Development

- Create screens in the appropriate location in `app/` based on routing needs
- Use components from to build screens `src/components/`
- Implement navigation using Expo Router patterns

### Authentication

The app uses a custom to manage authentication: `AuthContext`

```tsx
// Example usage in a component  
import { useAuth } from '@contexts/AuthContext';  
  
export default function HomeScreen() {  
    const { signOut } = useAuth();  
      
    return (  
        <View>  
            <Text>Welcome! You are signed in.</Text>  
            <Button title="Sign Out" onPress={signOut} />  
        </View>  
    );  
}

```

### State Management

- Use React Context for global state that rarely changes (auth, theme)
- Use Zustand for more complex global state management
- Use React's built-in state management for component-specific state

## Coding Standards

### TypeScript

- Always define proper types and interfaces
- Avoid using `any` type
- Create reusable types in `src/types/`

### Component Structure

- Use functional components with hooks
- Use proper naming conventions:
    - PascalCase for components and files containing components
    - camelCase for utils, hooks, and helper functions
- Document complex components with comments

### Styling

- Use React Native's for styling `StyleSheet`
- Consider React Native's platform-specific styling when necessary

## Testing

We use Jest and React Native Testing Library for testing:

npm test        # Run tests in watch mode

When writing tests:

- Test components in isolation
- Mock external dependencies
- Focus on user behavior rather than implementation details

## Common Issues and Solutions

### Expo Build Issues

If you encounter build issues:

npm run reset-project  # Reset the project to a clean state

### Path Alias Resolution Issues

If imports with `@` are not resolving:

- Ensure your editor is using the project's `tsconfig.json`
- Restart TypeScript server in your editor

## Useful Resources

- [Expo Documentation](https://docs.expo.dev/)
- [React Native Documentation](https://reactnative.dev/docs/getting-started)
- [TypeScript Documentation](https://www.typescriptlang.org/docs/)
- [Zustand Documentation](https://docs.pmnd.rs/zustand/getting-started/introduction)
- [Supabase Documentation](https://supabase.com/docs)

## Contribution Guidelines

1. Create a feature branch from `main`
2. Make your changes
3. Test your changes thoroughly
4. Submit a pull request with a clear description of your changes
5. Request code review from team members

## Next Steps for New Team Members

1. Run the app locally and explore the existing functionality
2. Review the code structure to get familiar with the organization
3. Pick up a small task or bug fix as your first contribution
4. Ask questions in our team channel if you get stuck