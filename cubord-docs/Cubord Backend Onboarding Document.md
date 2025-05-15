## Introduction

Welcome to the Cubord backend development team! This document will guide you through understanding the architecture, technologies, and conventions used in our backend codebase. By the end of this guide, you should have a solid understanding of how to contribute effectively to the project.

## Table of Contents

1. [Technology Stack](#technology-stack)
2. [Project Structure](#project-structure)
3. [Domain Model](#domain-model)
4. [Authentication and Authorization](#authentication-and-authorization)
5. [API Endpoints and Controllers](#api-endpoints-and-controllers)
6. [Business Logic and Services](#business-logic-and-services)
7. [Data Access Layer](#data-access-layer)
8. [Testing Strategy](#testing-strategy)
9. [Local Development Setup](#local-development-setup)
10. [Contribution Guidelines](#contribution-guidelines)

## Technology Stack

Our backend application uses:

- **Java SDK 23**: The latest LTS version of Java, offering modern language features.
- **Spring Boot**: The foundation of our application, providing auto-configuration.
- **Spring MVC**: For building our RESTful APIs.
- **Spring Data JPA**: Simplifies data access and database operations.
- **Jakarta EE**: For Java enterprise capabilities.
- **Lombok**: Reduces boilerplate code with annotations.
- **OAuth2/JWT**: Used for secure authentication and authorization.
- **JUnit & Mockito**: For testing components.

## Project Structure

The codebase follows a standard Spring Boot application structure:

```

cubord-backend/
├── src/
│   ├── main/
│   │   ├── java/
│   │   │   └── org/cubord/cubordbackend/
│   │   │       ├── config/         # Configuration classes
│   │   │       ├── controller/     # REST API controllers
│   │   │       ├── domain/         # Entity classes
│   │   │       ├── dto/            # Data Transfer Objects
│   │   │       ├── exception/      # Custom exceptions
│   │   │       ├── repository/     # JPA repositories
│   │   │       ├── service/        # Business logic
│   │   │       └── util/           # Utility classes
│   │   └── resources/
│   │       ├── application.properties  # Main configuration
│   │       └── db/                     # Database migrations
│   └── test/
│       ├── java/                   # Test classes
│       └── resources/              # Test resources
└── pom.xml                         # Maven build file
```

## Domain Model

The application follows a domain-driven design. Key entities include:

### User

Represents users of the system. Users authenticate using OAuth2/JWT and can belong to multiple households.

### Household

Represents a group of users that share common resources. A household can have multiple locations and members.

### HouseholdMember

Represents the relationship between users and households, including roles and permissions.

### Location

Represents physical locations associated with a household.

Entity relationships are managed through JPA annotations, with appropriate mappings (OneToMany, ManyToOne, etc.).

## Authentication and Authorization

### Authentication Flow

1. The application uses OAuth2 and JWT for authentication.
2. JWT tokens are validated against a JWK set URI.
3. User information is extracted from the token and used to identify the current user.

### Authorization

- Method-level security is implemented using Spring Security annotations.
- Custom permission evaluators control access to resources based on user roles and ownership.
- The `SecurityConfig` class configures security filters and rules.

### Working with Authentication

When creating new endpoints, use Spring Security's `Authentication` object to access the current user:

```
public ResponseEntity<?> someEndpoint(Authentication authentication)
{
    JwtAuthenticationToken token = (JwtAuthenticationToken) authentication;    // Access user details and perform operations
}
```

## API Endpoints and Controllers

Controllers follow RESTful principles and are organized by domain:

- `UserController`: User profile management
- `AuthController`: Authentication-related endpoints
- `HouseholdController`: Household management
- etc.

### Controller Best Practices

1. Use appropriate HTTP methods (GET, POST, PUT, DELETE).
2. Return meaningful HTTP status codes.
3. Implement proper input validation.
4. Use DTOs for request/response data.
5. Document APIs with appropriate annotations.

Example controller structure:

```

@RestController@RequestMapping("/api/resource")@RequiredArgsConstructorpublic class ResourceController
{
    private final ResourceService service;
    @GetMapping("/{id}")
    public ResponseEntity<ResourceDTO> getResource(@PathVariable UUID id)
    {        // Implementation    }

    // Other endpoints
}
```

## Business Logic and Services

Services encapsulate business logic and are typically injected into controllers:

1. `UserService`: User-related operations
2. `HouseholdService`: Household management
3. etc.

Services follow these principles:

- Single Responsibility Principle
- Transaction management
- Error handling with appropriate exceptions
- Logging for important operations

## Data Access Layer

We use Spring Data JPA for data access:

- Repositories extend `JpaRepository` or similar interfaces.
- Custom queries use `@Query` annotations or method naming conventions.
- Database transactions are managed at the service level.

Example repository:

```

public interface HouseholdRepository extends JpaRepository<Household, UUID>
{
    List<Household> findByMembersUserId(UUID userId);
    Optional<Household> findByIdAndMembersUserId(UUID id, UUID userId);
}
```

## Testing Strategy

Our testing approach includes:

### Unit Tests

- Testing individual components in isolation
- Mocking dependencies
- Focus on business logic

### Integration Tests

- Testing interactions between components
- Using test configurations for services and repositories

### API Tests

- Testing controllers with MockMvc
- Verifying HTTP responses and payloads

## Local Development Setup

1. **Prerequisites**
    
    - Java JDK 23
    - Maven
    - Docker (for database)
    - IDE (IntelliJ IDEA recommended)
2. **Clone the repository**
    

```

   git clone https://github.com/your-org/cubord-backend.git
   cd cubord-backend
```

3. **Configuration**
    
    - Create a `application-local.properties` file for local configuration
    - Set up environment variables for sensitive values (JWT keys, etc.)
4. **Database Setup**
    
    - Start a PostgreSQL instance using Docker:

```

     docker run -d -p 5432:5432 -e POSTGRES_PASSWORD=password -e POSTGRES_USER=cubord postgres
```

5. **Run the application**

```

   mvn spring-boot:run -Dspring-boot.run.profiles=local
```

6. **Verify installation**
    - Access the API at `http://localhost:8080/api/public/health`

## Contribution Guidelines

1. **Branch naming convention**
    
    - Feature: `feature/short-description`
    - Bugfix: `fix/issue-description`
    - Hotfix: `hotfix/critical-issue`
2. **Commit messages**
    
    - Use clear, descriptive commit messages
    - Reference issue numbers when applicable
3. **Pull requests**
    
    - Provide a detailed description of changes
    - Ensure all tests pass
    - Request reviews from team members
4. **Code style**
    
    - Follow Java conventions
    - Use meaningful variable and method names
    - Document public APIs
    - Add appropriate logging
5. **Testing requirements**
    
    - Write unit tests for new features
    - Ensure test coverage remains high
    - Include integration tests for significant changes

## Common Development Tasks

### Adding a New Entity

1. Create the entity class in the `domain` package
2. Create a repository interface in the `repository` package
3. Create DTOs in the `dto` package
4. Implement service logic in the `service` package
5. Create a controller in the `controller` package
6. Write tests for all layers

### Implementing a New Feature

1. Understand the requirements
2. Design the API and data model
3. Implement the feature from bottom up (entity → repository → service → controller)
4. Write tests for each component
5. Document the API

## Troubleshooting

### Common Issues

1. **Authentication failures**
    
    - Check JWT token configuration
    - Verify security settings in test classes
2. **Database connection issues**
    
    - Verify database credentials
    - Check database connection properties
3. **Test failures**
    
    - Ensure test environment has all required properties
    - Mock external dependencies properly
