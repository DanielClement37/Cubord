package org.cubord.cubordbackend.domain;

public enum ProductDataSource {
    UPC_API,    // Product data retrieved from external UPC API
    MANUAL,     // Product data manually entered by user
    HYBRID      // Product data is combination of manual and API data
}
