# ems-mapper

A lightweight ORM-style data mapping framework built on top of MyBatis Spring Boot. It provides automatic CRUD operations, pagination support, and entity-to-table mapping through annotations.

## Features

- **Automatic CRUD Operations** - Insert, update, delete, and select without writing SQL
- **Entity-to-Table Mapping** - Map Java entities to database tables using annotations
- **Multiple ID Generation Strategies** - AUTO, UUID, and SNOWFLAKE support
- **Pagination Support** - Built-in pagination with automatic COUNT queries
- **Batch Operations** - Batch insert support
- **Soft Delete** - Logical deletion support via `@Deleted` annotation
- **Optimistic Locking** - Version control support via `@Version` annotation
- **Query by Entity** - Query methods that accept entity objects as conditions
- **Dialect Support** - MySQL, Oracle, PostgreSQL

## Requirements

- Java 17+
- Spring Boot 4.x
- MyBatis Spring Boot Starter 4.0.1+

## Installation

Add the dependency to your `pom.xml`:

```xml
<dependency>
    <groupId>com.ngcin.ems</groupId>
    <artifactId>ems-mapper</artifactId>
    <version>1.0</version>
</dependency>
```

## Quick Start

### 1. Enable the Framework

Add `@EnableDataMapper` annotation to your Spring Boot application class:

```java
@SpringBootApplication
@EnableDataMapper
public class Application {
    public static void main(String[] args) {
        SpringApplication.run(Application.class, args);
    }
}
```

### 2. Create an Entity

Map your database table to a Java entity using annotations:

```java
@Table("t_user")
public class User {

    @Id(type = IdType.AUTO)
    private Long id;
    
    private String username;

    private String email;
    
    private Integer age;

    @Column(name = "create_time")
    private LocalDateTime createTime;

    // getters and setters
}
```

### 3. Create a Mapper Interface

Extend `BaseMapper<T>` to inherit all CRUD methods:

```java
@Mapper
public interface UserMapper extends BaseMapper<User> {
}
```

### 4. Use the Mapper

```java
@Service
public class UserService {

    @Autowired
    private UserMapper userMapper;

    // Insert
    public void createUser() {
        User user = new User("john", "john@example.com", 25);
        userMapper.insert(user);
        // ID is automatically set back to the entity
        Long id = user.getId();
    }

    // Select by ID
    public User getUser(Long id) {
        return userMapper.getById(id);
    }

    // Update
    public void updateUser(User user) {
        userMapper.updateById(user);
    }

    // Delete
    public void deleteUser(Long id) {
        userMapper.deleteById(id);
    }
}
```

## Annotations

| Annotation | Description |
|------------|-------------|
| `@Table("table_name")` | Marks entity class and specifies table name |
| `@Id` | Marks primary key field |
| `@Column(name = "column_name")` | Customizes column mapping (name, JDBC type) |
| `@Version` | Optimistic locking support |
| `@Deleted` | Soft delete marker (logical deletion) |
| `@Ignore` | Excludes field from SQL generation |

## ID Generation Strategies

```java
// Auto-increment (database generated)
@Id(type = IdType.AUTO)
private Long id;

// UUID string
@Id(type = IdType.UUID)
private String id;

// Snowflake ID
@Id(type = IdType.SNOWFLAKE)
private Long id;
```

## BaseMapper API

### Insert Operations

| Method | Description |
|--------|-------------|
| `int insert(T entity)` | Insert entity (all fields) |
| `int insertSelective(T entity)` | Insert entity (non-null fields only) |
| `int insertBatch(List<T> entities)` | Batch insert entities |

### Update Operations

| Method | Description |
|--------|-------------|
| `int updateById(T entity)` | Update by ID (all fields) |
| `int updateSelectiveById(T entity)` | Update by ID (non-null fields only) |

### Select Operations

| Method | Description |
|--------|-------------|
| `T getById(Serializable id)` | Select by ID |
| `List<T> selectBatchIds(Collection ids)` | Select by multiple IDs |
| `List<T> selectAll()` | Select all records |
| `List<T> selectList(T query)` | Select by query entity |
| `T selectOne(T query)` | Select one record by query entity |
| `long selectCount(T query)` | Count by query entity |

### Delete Operations

| Method | Description |
|--------|-------------|
| `int deleteById(Serializable id)` | Delete by ID (physical delete) |
| `int delete(T entity)` | Delete by query entity (physical delete) |
| `int removeById(Serializable id)` | Remove by ID (logical delete with `@Deleted`) |
| `int remove(T entity)` | Remove by query entity (logical delete with `@Deleted`) |

### Pagination

| Method | Description |
|--------|-------------|
| `IPage<T> page(IPage<T> page, T query)` | Paginated query |

```java
// Create page object
Page<User> page = new Page<>(1, 10); // current=1, size=10

// Query with conditions
User query = new User();
query.setAge(25);

IPage<User> result = userMapper.page(page, query);
System.out.println("Total: " + result.getTotal());
System.out.println("Records: " + result.getRecords());
```

## Configuration

Configure the database dialect in `application.properties`:

```properties
# Default dialect is mysql
ems.mapper.dialect=mysql

# Supported dialects: mysql, oracle, postgresql
```

## Example: Query by Entity

```java
// Query users with age = 25
User query = new User();
query.setAge(25);
List<User> users = userMapper.selectList(query);

// Query with multiple conditions
User query = new User();
query.setUsername("john");
query.setEmail("john@example.com");
User user = userMapper.selectOne(query);
```
