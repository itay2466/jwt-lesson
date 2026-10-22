<div dir="rtl">

# שיעור: אבטחת REST API עם Spring Security ו‑JWT

**Java Spring · Spring Security · JWT · JPA · PostgreSQL · Lombok · IntelliJ HTTP Client**

>  לבנות שרת REST שבו משתמש מתחבר עם שם וסיסמה, מקבל JWT, ומשתמש בו כדי לגשת לנקודות קצה מוגנות לפי תפקיד (ROLE).

---

## תוכן העניינים

1. מה בונים – תמונה כללית
2. Java Spring – מבוא קצר
3. אבטחת מידע – מושגי יסוד (Authentication, Authorization, Hash, JWT)
4. ארכיטקטורת הפתרון – דיאגרמות
5. הכנת הסביבה (PostgreSQL, פרויקט, IntelliJ)
6. הקוד – קובץ אחר קובץ, עם הסבר ממוקד
7. הרצה ובדיקה – קובץ ‎.http‎ ל‑IntelliJ Ultimate
8. טעויות נפוצות ופתרונן
9. נקודות אבטחה לדיון בכיתה
10. תרגילים
11. סיכום – רשימת בדיקה

---

## 1. מה בונים – תמונה כללית

שרת Spring Boot עם שלושה אזורים:

| אזור | כתובות | מי נכנס |
|---|---|---|
| ציבורי | ‎`/api/auth/**`‎ , ‎`/api/public/**`‎ | כולם, בלי טוקן |
| משתמש | ‎`/api/user/**`‎ | מי שיש לו ‎`ROLE_USER`‎ |
| מנהל | ‎`/api/admin/**`‎ | מי שיש לו ‎`ROLE_ADMIN`‎ |

**הזרימה:**

1. המשתמש שולח שם וסיסמה ל‑‎`POST /api/auth/login`‎.
2. השרת בודק מול PostgreSQL (הסיסמה שמורה כ‑Hash של BCrypt).
3. אם נכון – השרת מחזיר **JWT** חתום.
4. בכל בקשה הבאה הלקוח שולח ‎`Authorization: Bearer <token>`‎.
5. מסנן (Filter) בודק את החתימה ואת התוקף, ו‑‎`SecurityConfig`‎ מחליט: מותר (200), לא מזוהה (401), או אין הרשאה (403).

**גרסאות בשיעור:** Java 21, ‏Spring Boot 4.1.1, ‏jjwt 0.13.0, ‏PostgreSQL 16 ומעלה.

---

## 2. Java Spring – מבוא קצר

### 2.1 Spring מול Spring Boot

| | Spring Framework | Spring Boot |
|---|---|---|
| מה זה | ספריית התשתית: IoC, ‏DI, ‏MVC, ‏Security, ‏Data | שכבה מעל Spring שמגדירה הכול אוטומטית |
| הגדרות | ידניות, הרבה קוד | "Starter" אחד לכל יכולת + ‎`application.properties`‎ |
| שרת | צריך Tomcat חיצוני | Tomcat מובנה – מריצים ‎`main`‎ וזהו |

### 2.2 IoC ו‑DI – הרעיון המרכזי

- **IoC (Inversion of Control):** לא אנחנו יוצרים אובייקטים עם ‎`new`‎ – Spring יוצר ומנהל אותם. אובייקט שמנוהל ע"י Spring נקרא **Bean**.
- **DI (Dependency Injection):** מחלקה מצהירה מה היא צריכה (בבנאי), ו‑Spring "מזריק" לה.
- בשיעור אנחנו משתמשים ב‑**Constructor Injection** דרך Lombok: ‎`@RequiredArgsConstructor`‎ יוצר בנאי לכל שדה ‎`final`‎.

| אנוטציה | תפקיד |
|---|---|
| ‎`@RestController`‎ | מקבל HTTP ומחזיר JSON |
| ‎`@Service`‎ | לוגיקה עסקית |
| ‎`@Repository`‎ / ממשק ‎`JpaRepository`‎ | גישה ל‑DB |
| ‎`@Component`‎ | Bean כללי |
| ‎`@Configuration`‎ + ‎`@Bean`‎ | מחלקת הגדרות שמייצרת Beans בעצמה |

### 2.3 שכבות האפליקציה

כל שכבה מדברת רק עם השכבה שמתחתיה. Controller לא ניגש ל‑DB ישירות.

</div>

```mermaid
flowchart TB
    C["@RestController<br/>receives HTTP, returns JSON"] --> S["@Service<br/>business logic"]
    S --> R["JpaRepository<br/>database access"]
    R --> DB[("PostgreSQL")]
```

<div dir="rtl">

---

## 3. אבטחת מידע – מושגי יסוד

### 3.1 Authentication מול Authorization

| מושג | השאלה | בפרויקט | קוד שגיאה |
|---|---|---|---|
| **Authentication** – זיהוי | מי אתה? | שם + סיסמה בלוגין, אחר כך JWT | **401** Unauthorized |
| **Authorization** – הרשאה | מה מותר לך? | ‎`hasRole("ADMIN")`‎ ב‑‎`SecurityConfig`‎ | **403** Forbidden |

> כלל זיכרון: **401** = "אני לא יודע מי אתה". ‏**403** = "אני יודע מי אתה, ואסור לך".

### 3.2 Hash מול הצפנה – למה סיסמה נשמרת כ‑Hash

| | הצפנה (Encryption) | גיבוב (Hash) |
|---|---|---|
| כיוון | דו‑כיווני – אפשר לפענח עם מפתח | חד‑כיווני – אי אפשר לחזור לסיסמה |
| לסיסמאות? | לא – מי שגונב את המפתח מקבל את כל הסיסמאות | כן |

**BCrypt** (מה שמשמש בפרויקט):

- מוסיף **Salt** אקראי לכל סיסמה – לשתי סיסמאות זהות יש Hash שונה.
- **איטי בכוונה** (cost factor, ברירת מחדל 10) – מקשה על ניחוש המוני (Brute Force).
- אורך קבוע של 60 תווים. מבנה: ‎`$2a$10$`‎ + ‏22 תווי Salt + ‏31 תווי Hash.
- בלוגין לא "מפענחים" – מחשבים Hash לסיסמה שהוקלדה, עם אותו Salt, ומשווים: ‎`passwordEncoder.matches(raw, hash)`‎.
- BCrypt משתמש רק ב‑72 הבתים הראשונים של הסיסמה – לכן הגבלנו סיסמה ל‑72 תווים.

### 3.3 Session מול JWT

| | Session (קלאסי) | JWT (בשיעור) |
|---|---|---|
| איפה המידע | בזיכרון השרת | בתוך הטוקן אצל הלקוח |
| מה הלקוח שולח | Cookie עם מזהה Session | כותרת ‎`Authorization: Bearer ...`‎ |
| מצב בשרת | Stateful | **Stateless** – השרת לא זוכר כלום |
| כמה שרתים | צריך לשתף Sessions | כל שרת עם אותו Secret מאמת לבד |
| ניתוק (Logout) | מוחקים את ה‑Session | קשה – הטוקן תקף עד שפג (ראו סעיף 9) |

### 3.4 מבנה JWT

JWT הוא מחרוזת של שלושה חלקים מופרדים בנקודה: ‎`header.payload.signature`‎. כל חלק מקודד ב‑Base64Url.

</div>

```mermaid
flowchart LR
    H["Header<br/>alg = HS256"] --- P["Payload (claims)<br/>sub = admin<br/>roles = ROLE_ADMIN, ROLE_USER<br/>iat = issued at<br/>exp = expires at"] --- S["Signature<br/>HMAC-SHA256 of header.payload<br/>with the server secret"]
```

<div dir="rtl">

דוגמה לטוקן (נוצר עם ה‑Secret של השיעור):

</div>

```text
eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJhZG1pbiIsInJvbGVzIjpbIlJPTEVfQURNSU4iLCJST0xFX1VTRVIiXSwiaWF0IjoxNzkxMzIwNDAwLCJleHAiOjE3OTEzMjQwMDB9.eTcTM0FPgynBQCYcH420b_VFMXUuOaxcFopPgVwjOns
```

<div dir="rtl">

אחרי פענוח Base64Url של שני החלקים הראשונים:

</div>

```json
{ "alg": "HS256" }
```

```json
{
  "sub": "admin",
  "roles": ["ROLE_ADMIN", "ROLE_USER"],
  "iat": 1791320400,
  "exp": 1791324000
}
```

<div dir="rtl">

**שלוש נקודות שחובה להבין:**

1. **ה‑Payload לא מוצפן.** כל אחד יכול לקרוא אותו. לעולם לא שמים בו סיסמה או מידע רגיש.
2. **החתימה מונעת שינוי.** מי שמשנה אות אחת ב‑Payload (למשל ‎`sub`‎ ל‑‎`admin`‎) – החתימה כבר לא מתאימה והשרת דוחה (בדיקה 12 בקובץ הבדיקות מדגימה בדיוק את זה).
3. **הסוד (Secret) נמצא רק בשרת.** ב‑HS256 הוא חייב להיות לפחות 256 ביט (32 בתים). מי שמחזיק אותו יכול לזייף טוקנים – לכן הוא לא נכנס ל‑Git.

---

## 4. ארכיטקטורת הפתרון

### 4.1 רכיבי המערכת

</div>

```mermaid
flowchart LR
    Client["Client<br/>IntelliJ HTTP Client / web / mobile"]
    subgraph App["Spring Boot application"]
        subgraph Sec["Security layer"]
            JF["JwtAuthFilter"]
            SCF["SecurityConfig rules"]
        end
        subgraph Web["Controller layer"]
            LC["UserLoginController"]
            RC["ResourceController"]
        end
        subgraph Svc["Service layer"]
            LS["UserLoginService"]
            US["UserService"]
            UDS["CustomUserDetailsService"]
            JU["JwtUtil"]
        end
        subgraph Data["Repository layer"]
            UR["UserRepository"]
            RR["RoleRepository"]
        end
    end
    DB[("PostgreSQL")]

    Client --> JF --> SCF
    SCF --> LC
    SCF --> RC
    LC --> LS
    RC --> US
    LS --> JU
    LS --> UR
    LS --> RR
    JF --> JU
    JF --> UDS
    UDS --> UR
    US --> UR
    UR --> DB
    RR --> DB
```

<div dir="rtl">

### 4.2 מודל הנתונים – רבים לרבים

משתמש יכול להחזיק כמה תפקידים, ותפקיד יכול להיות של כמה משתמשים. במסד הנתונים זה נשמר בטבלת קישור ‎`user_roles`‎.

</div>

```mermaid
erDiagram
    USERS ||--o{ USER_ROLES : "has"
    ROLES ||--o{ USER_ROLES : "assigned in"
    USERS {
        bigint id PK
        varchar username UK
        varchar password "BCrypt hash, 60 chars"
        boolean enabled
    }
    ROLES {
        bigint id PK
        varchar name UK "ROLE_USER or ROLE_ADMIN"
    }
    USER_ROLES {
        bigint user_id FK
        bigint role_id FK
    }
```

```mermaid
classDiagram
    class User {
        -Long id
        -String username
        -String password
        -boolean enabled
        -Set~Role~ roles
    }
    class Role {
        -Long id
        -RoleName name
        -Set~User~ users
    }
    class RoleName {
        <<enumeration>>
        ROLE_USER
        ROLE_ADMIN
    }
    User "*" --> "*" Role : roles (owning side, user_roles)
    Role "*" ..> "*" User : users (mappedBy, @JsonIgnore)
    Role --> RoleName
```

<div dir="rtl">

איך זה נראה בטבלאות אחרי ההרצה הראשונה (admin מחזיק שני תפקידים, ‎`ROLE_USER`‎ שייך לשני משתמשים):

| users.username | roles.name |
|---|---|
| admin | ROLE_ADMIN |
| admin | ROLE_USER |
| user | ROLE_USER |

### 4.3 עליית השרת – ‎`DataInitializer`‎

</div>

```mermaid
flowchart TD
    S(["Application starts"]) --> R1{"ROLE_ADMIN exists?"}
    R1 -- No --> C1["Create ROLE_ADMIN"] --> R2
    R1 -- Yes --> R2{"ROLE_USER exists?"}
    R2 -- No --> C2["Create ROLE_USER"] --> U1
    R2 -- Yes --> U1{"user 'admin' exists?"}
    U1 -- No --> C3["Create admin<br/>password = BCrypt hash<br/>roles = ADMIN + USER"] --> U2
    U1 -- Yes --> U2{"user 'user' exists?"}
    U2 -- No --> C4["Create user<br/>password = BCrypt hash<br/>roles = USER"] --> E
    U2 -- Yes --> E(["Done - initial data exists"])
```

<div dir="rtl">

### 4.4 תהליך הלוגין

</div>

```mermaid
sequenceDiagram
    autonumber
    actor C as Client
    participant F as JwtAuthFilter
    participant LC as UserLoginController
    participant LS as UserLoginService
    participant AM as AuthenticationManager
    participant UDS as CustomUserDetailsService
    participant DB as PostgreSQL
    participant PE as BCryptPasswordEncoder
    participant JU as JwtUtil

    C->>F: POST /api/auth/login (username, password)
    F->>LC: no Bearer header, pass through (permitAll)
    LC->>LS: login(request)
    LS->>AM: authenticate(username, password)
    AM->>UDS: loadUserByUsername(username)
    UDS->>DB: SELECT user JOIN roles
    DB-->>UDS: row with password hash and roles
    UDS-->>AM: UserDetails
    AM->>PE: matches(rawPassword, hash)
    alt password matches
        PE-->>AM: true
        AM-->>LS: Authentication (principal = UserDetails)
        LS->>JU: generateToken(userDetails)
        JU-->>LS: header.payload.signature
        LS-->>LC: LoginResponse
        LC-->>C: 200 OK + token
    else wrong password or unknown user
        AM-->>LS: BadCredentialsException
        LS-->>C: 401 Invalid username or password
    end
```

<div dir="rtl">

### 4.5 בקשה מוגנת עם טוקן

</div>

```mermaid
sequenceDiagram
    autonumber
    actor C as Client
    participant F as JwtAuthFilter
    participant JU as JwtUtil
    participant UDS as CustomUserDetailsService
    participant SC as SecurityContext
    participant AZ as SecurityConfig rules
    participant RC as ResourceController

    C->>F: GET /api/admin/users + Authorization Bearer token
    F->>JU: extractUsername(token)
    JU-->>F: admin (signature and expiry OK)
    F->>UDS: loadUserByUsername(admin)
    UDS-->>F: UserDetails with ROLE_ADMIN, ROLE_USER
    F->>SC: setAuthentication(user, roles)
    F->>AZ: continue the filter chain
    AZ->>AZ: /api/admin/** requires ROLE_ADMIN
    alt user has ROLE_ADMIN
        AZ->>RC: allUsers()
        RC-->>C: 200 OK + JSON list of DTOs
    else authenticated, no ROLE_ADMIN
        AZ-->>C: 403 Forbidden
    end
```

<div dir="rtl">

### 4.6 איך נקבעת התשובה: ‏200, ‏401 או 403

</div>

```mermaid
flowchart TD
    A["Request arrives"] --> B{"Header starts with 'Bearer '?"}
    B -- No --> AN["Anonymous request"]
    B -- Yes --> C{"Signature valid and not expired?"}
    C -- No --> AN
    C -- Yes --> D{"User exists in DB and enabled?"}
    D -- No --> AN
    D -- Yes --> AU["Authenticated: user + roles in SecurityContext"]
    AN --> P{"URL is permitAll?"}
    P -- Yes --> OK["200 - controller runs"]
    P -- No --> E401["401 Unauthorized"]
    AU --> R{"URL rule satisfied by the user's roles?"}
    R -- Yes --> OK
    R -- No --> E403["403 Forbidden"]
```

<div dir="rtl">

---

## 5. הכנת הסביבה

### 5.1 PostgreSQL – יצירת מסד נתונים

אפשרות א' – PostgreSQL מותקן מקומית:

</div>

```bash
psql -U postgres -c "CREATE DATABASE jwt_lesson;"
```

<div dir="rtl">

אפשרות ב' – Docker:

</div>

```bash
docker run --name pg-lesson -e POSTGRES_PASSWORD=postgres -e POSTGRES_DB=jwt_lesson -p 5432:5432 -d postgres:16
```

<div dir="rtl">

את הטבלאות **לא** יוצרים ידנית – Hibernate יוצר אותן מה‑Entities ‏(‎`ddl-auto=update`‎).

### 5.2 הדגמה בכיתה: למה הטבלה לא נקראת ‎`user`‎

ב‑PostgreSQL המילה ‎`user`‎ שמורה. הריצו ב‑psql:

</div>

```sql
CREATE TABLE user (id bigint);
-- ERROR:  syntax error at or near "user"

SELECT user;
-- מחזיר את שם המשתמש של החיבור למסד (למשל postgres), לא טבלה!
```

<div dir="rtl">

**שני פתרונות:**

1. **שם אחר לטבלה** – ‎`@Table(name = "users")`‎. זה הפתרון בשיעור, והמקובל.
2. מרכאות – ‎`@Table(name = "\"user\"")`‎. עובד, אבל מחייב מרכאות בכל שאילתת SQL ידנית. לא מומלץ.

### 5.3 יצירת הפרויקט

ב‑[start.spring.io](https://start.spring.io) בוחרים: Maven, ‏Java 21, ‏Spring Boot 4.1.x, ואת התלויות:
Spring Web, ‏Spring Security, ‏Spring Data JPA, ‏PostgreSQL Driver, ‏Validation, ‏Lombok.
את **jjwt** מוסיפים ידנית ל‑‎`pom.xml`‎ (הוא לא קיים ב‑Initializr).

**IntelliJ ו‑Lombok:** בפתיחת הפרויקט IntelliJ מציע "Enable annotation processing" – לאשר. בלי זה, ‎`getUsername()`‎ ודומיו יסומנו באדום.

### 5.4 מבנה התיקיות

</div>

```text
jwt-lesson/
├── pom.xml
├── http/
│   ├── http-client.env.json
│   └── security-tests.http
└── src/main/
    ├── resources/
    │   └── application.properties
    └── java/com/example/jwtlesson/
        ├── JwtLessonApplication.java
        ├── entity/
        │   ├── RoleName.java
        │   ├── Role.java
        │   └── User.java
        ├── repository/
        │   ├── UserRepository.java
        │   └── RoleRepository.java
        ├── dto/
        │   ├── LoginRequest.java
        │   ├── RegisterRequest.java
        │   ├── LoginResponse.java
        │   └── UserResponse.java
        ├── security/
        │   ├── CustomUserDetailsService.java
        │   ├── JwtUtil.java
        │   └── JwtAuthFilter.java
        ├── config/
        │   ├── SecurityConfig.java
        │   └── DataInitializer.java
        ├── service/
        │   ├── UserLoginService.java
        │   └── UserService.java
        ├── controller/
        │   ├── UserLoginController.java
        │   └── ResourceController.java
        └── exception/
            ├── GlobalExceptionHandler.java
            └── UsernameTakenException.java
```

<div dir="rtl">

---

## 6. הקוד – קובץ אחר קובץ

> סדר הכתיבה בכיתה: הגדרות ← Entities ← Repositories ← DTOs ← Security ← Services ← Controllers.

### 6.1 ‎`pom.xml`‎

**מה:** התלויות של הפרויקט.

**שימו לב:**
- ב‑Spring Boot 4 שם ה‑Starter ל‑REST הוא ‎`spring-boot-starter-webmvc`‎ (השם הישן ‎`spring-boot-starter-web`‎ עדיין עובד אבל מסומן כמיושן).
- ‏jjwt מגיע בשלושה חלקים: ‎`api`‎ לקומפילציה, ‎`impl`‎ ו‑‎`jackson`‎ רק בזמן ריצה.
- ‏Lombok מוגדר גם כ‑Annotation Processor – בגרסאות JDK חדשות זה חובה.

</div>

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>

    <parent>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-parent</artifactId>
        <version>4.1.1</version>
        <relativePath/>
    </parent>

    <groupId>com.example</groupId>
    <artifactId>jwt-lesson</artifactId>
    <version>1.0.0</version>
    <name>jwt-lesson</name>
    <description>Spring Security + JWT + PostgreSQL - lesson project</description>

    <properties>
        <java.version>21</java.version>
        <jjwt.version>0.13.0</jjwt.version>
    </properties>

    <dependencies>
        <!-- REST controllers (Spring MVC). In Boot 4 this replaces spring-boot-starter-web -->
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-webmvc</artifactId>
        </dependency>

        <!-- Spring Security: filter chain, authentication, authorization -->
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-security</artifactId>
        </dependency>

        <!-- JPA + Hibernate + Spring Data repositories -->
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-data-jpa</artifactId>
        </dependency>

        <!-- @Valid, @NotBlank, @Size on request DTOs -->
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-validation</artifactId>
        </dependency>

        <!-- PostgreSQL JDBC driver -->
        <dependency>
            <groupId>org.postgresql</groupId>
            <artifactId>postgresql</artifactId>
            <scope>runtime</scope>
        </dependency>

        <!-- JJWT: create and verify JWT tokens -->
        <dependency>
            <groupId>io.jsonwebtoken</groupId>
            <artifactId>jjwt-api</artifactId>
            <version>${jjwt.version}</version>
        </dependency>
        <dependency>
            <groupId>io.jsonwebtoken</groupId>
            <artifactId>jjwt-impl</artifactId>
            <version>${jjwt.version}</version>
            <scope>runtime</scope>
        </dependency>
        <dependency>
            <groupId>io.jsonwebtoken</groupId>
            <artifactId>jjwt-jackson</artifactId>
            <version>${jjwt.version}</version>
            <scope>runtime</scope>
        </dependency>

        <!-- Lombok: getters/setters/constructors/builder generated at compile time -->
        <dependency>
            <groupId>org.projectlombok</groupId>
            <artifactId>lombok</artifactId>
            <optional>true</optional>
        </dependency>
    </dependencies>

    <build>
        <plugins>
            <!-- Run Lombok as an annotation processor (required explicitly on newer JDKs) -->
            <plugin>
                <groupId>org.apache.maven.plugins</groupId>
                <artifactId>maven-compiler-plugin</artifactId>
                <configuration>
                    <annotationProcessorPaths>
                        <path>
                            <groupId>org.projectlombok</groupId>
                            <artifactId>lombok</artifactId>
                            <version>${lombok.version}</version>
                        </path>
                    </annotationProcessorPaths>
                </configuration>
            </plugin>
            <plugin>
                <groupId>org.springframework.boot</groupId>
                <artifactId>spring-boot-maven-plugin</artifactId>
                <configuration>
                    <excludes>
                        <exclude>
                            <groupId>org.projectlombok</groupId>
                            <artifactId>lombok</artifactId>
                        </exclude>
                    </excludes>
                </configuration>
            </plugin>
        </plugins>
    </build>
</project>
```

<div dir="rtl">

### 6.2 ‎`application.properties`‎

**מה:** חיבור ל‑DB, הגדרות JPA, סוד ה‑JWT, ומשתמשים ראשוניים.

**שימו לב:**
- התחביר ‎`${JWT_SECRET:ברירת_מחדל}`‎ = קח ממשתנה סביבה, ואם אין – ברירת מחדל. בייצור מגדירים משתנה סביבה ולא שומרים סודות בקוד.
- ליצירת סוד חדש: ‎`openssl rand -base64 32`‎.
- ‎`open-in-view=false`‎ – החיבור ל‑DB לא נשאר פתוח לכל אורך הבקשה. כל טעינה מה‑DB קורית בתוך Service.

</div>

```properties
spring.application.name=jwt-lesson
server.port=8080

# ---------- PostgreSQL ----------
spring.datasource.url=jdbc:postgresql://localhost:5432/jwt_lesson
spring.datasource.username=${DB_USER:postgres}
spring.datasource.password=${DB_PASSWORD:postgres}

# ---------- JPA / Hibernate ----------
# update = create/alter tables from the entities (fine for a lesson; use Flyway/Liquibase in production)
spring.jpa.hibernate.ddl-auto=update
# No DB session held open during the web request - lazy loading must happen inside services
spring.jpa.open-in-view=false
spring.jpa.show-sql=true
spring.jpa.properties.hibernate.format_sql=true

# ---------- JWT ----------
# Base64 of at least 32 random bytes (256 bits) for HS256. Generate: openssl rand -base64 32
# The default below is for the classroom only - in production set JWT_SECRET as an environment variable
jwt.secret=${JWT_SECRET:47o9ZwPSwJH0HIGIpS8wuGOJOLfiN40HPSvI/k+kv88=}
# 1 hour
jwt.expiration-ms=3600000

# ---------- Initial users (created by DataInitializer if missing) ----------
app.init.admin.username=admin
app.init.admin.password=${ADMIN_PASSWORD:Admin123!}
app.init.user.username=user
app.init.user.password=${USER_PASSWORD:User123!}
```

<div dir="rtl">

### 6.3 ‎`RoleName`‎ – ‏enum לשמות התפקידים

**למה ‎`ROLE_`‎ בתחילת השם:** ב‑Spring Security, ‎`hasRole("ADMIN")`‎ מחפש בפועל הרשאה בשם ‎`ROLE_ADMIN`‎. לכן התפקידים נקראים ‎`ROLE_ADMIN`‎ ו‑‎`ROLE_USER`‎, וב‑‎`SecurityConfig`‎ כותבים ‎`hasRole("ADMIN")`‎ – בלי הקידומת.

**למה enum ולא String:** אי אפשר לטעות באיות, והקומפיילר בודק.

</div>

```java
package com.example.jwtlesson.entity;

/**
 * Role names. The "ROLE_" prefix is a Spring Security convention:
 * hasRole("ADMIN") in SecurityConfig looks for the authority "ROLE_ADMIN".
 */
public enum RoleName {
    ROLE_USER,
    ROLE_ADMIN
}
```

<div dir="rtl">

### 6.4 ‎`Role`‎ – ‏Entity של תפקיד

**מה:** טבלת ‎`roles`‎. הצד ה"הפוך" של היחס רבים‑לרבים.

**שימו לב:**
- ‎`mappedBy = "roles"`‎ – אומר ל‑Hibernate: "את טבלת הקישור מנהל השדה ‎`roles`‎ במחלקה ‎`User`‎". רק צד אחד הוא הבעלים.
- ‎`@JsonIgnore`‎ על ‎`users`‎ – שובר את הלולאה ב‑JSON (פירוט בסעיף 6.6).
- ‎`@EqualsAndHashCode`‎ רק על ‎`name`‎ – השם ייחודי, ולכן מזהה את התפקיד גם לפני שנשמר ב‑DB (כש‑‎`id`‎ עדיין ‎`null`‎).

</div>

```java
package com.example.jwtlesson.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.*;

import java.util.HashSet;
import java.util.Set;

@Entity
@Table(name = "roles")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(onlyExplicitlyIncluded = true) // NOT @Data - see the lesson
@ToString(onlyExplicitlyIncluded = true)
public class Role {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @ToString.Include
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, unique = true, length = 30)
    @EqualsAndHashCode.Include   // natural key: the role name is unique
    @ToString.Include
    private RoleName name;

    // Inverse side of the many-to-many (the owner is User.roles).
    // @JsonIgnore breaks the JSON loop: User -> roles -> users -> roles -> ...
    @ManyToMany(mappedBy = "roles")
    @JsonIgnore
    @Builder.Default
    private Set<User> users = new HashSet<>();
}
```

<div dir="rtl">

### 6.5 ‎`User`‎ – ‏Entity של משתמש

**מה:** טבלת ‎`users`‎ – הצד ה"בעלים" של היחס.

**שימו לב:**
- ‎`@Table(name = "users")`‎ – כי ‎`user`‎ שמורה ב‑PostgreSQL (סעיף 5.2).
- ‎`@JoinTable`‎ מגדיר את טבלת הקישור ‎`user_roles`‎ ואת שתי העמודות שלה.
- ‎`password`‎ שומר **Hash** בלבד, ומסומן ‎`@JsonIgnore`‎ – הגנה נוספת שלא יצא בטעות ב‑JSON.
- ‎`@ManyToMany`‎ הוא LAZY כברירת מחדל: התפקידים לא נטענים עד שצריך. ה‑Repository טוען אותם במפורש (סעיף 6.7).
- ‎`@Builder.Default`‎ – בלי זה, ה‑Builder של Lombok היה משאיר את ‎`roles`‎ כ‑‎`null`‎ ואת ‎`enabled`‎ כ‑‎`false`‎.

</div>

```java
package com.example.jwtlesson.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.*;

import java.util.HashSet;
import java.util.Set;

@Entity
@Table(name = "users") // "user" is a reserved word in PostgreSQL - CREATE TABLE user fails
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(onlyExplicitlyIncluded = true) // NOT @Data - see the lesson
@ToString(onlyExplicitlyIncluded = true)
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @ToString.Include
    private Long id;

    @Column(nullable = false, unique = true, length = 50)
    @EqualsAndHashCode.Include   // natural key: the username is unique
    @ToString.Include
    private String username;

    // BCrypt hash (60 chars), never the plain password. Never sent to the client.
    @Column(nullable = false, length = 100)
    @JsonIgnore
    private String password;

    @Column(nullable = false)
    @Builder.Default
    private boolean enabled = true;

    // Owning side of the many-to-many: Hibernate writes the join table from here.
    // LAZY (the default) - the repository loads roles explicitly with @EntityGraph.
    @ManyToMany
    @JoinTable(
            name = "user_roles",
            joinColumns = @JoinColumn(name = "user_id"),
            inverseJoinColumns = @JoinColumn(name = "role_id"))
    @Builder.Default
    private Set<Role> roles = new HashSet<>();
}
```

<div dir="rtl">

### 6.6 שתי מלכודות ביחס רבים‑לרבים: Lombok ו‑JSON

**מלכודת 1 – ‎`@Data`‎ של Lombok על Entity:**
‎`@Data`‎ מייצר ‎`toString()`‎, ‎`equals()`‎ ו‑‎`hashCode()`‎ שעוברים על **כל** השדות. ב‑User יש ‎`roles`‎, וב‑Role יש ‎`users`‎:
‎`User.hashCode()`‎ ← ‎`Role.hashCode()`‎ ← ‎`User.hashCode()`‎ ← ... ← **StackOverflowError**.

**הפתרון בשיעור:** ‎`@Getter`‎ + ‎`@Setter`‎, ו‑‎`@EqualsAndHashCode`‎ / ‎`@ToString`‎ רק על שדות שבחרנו במפורש (‎`onlyExplicitlyIncluded = true`‎).

**מלכודת 2 – לולאה אינסופית ב‑JSON:**
אם מחזירים Entity מה‑Controller, ‏Jackson כותב ‎`user → roles → users → roles → ...`‎ עד קריסה.

**שלוש שכבות הגנה בפרויקט:**

| שכבה | איפה | מה עושה |
|---|---|---|
| 1. ‏DTO | ‎`UserResponse`‎ | ה‑API מחזיר רק DTO – אף פעם Entity. התפקידים יוצאים כרשימת מחרוזות. **זו ההגנה העיקרית.** |
| 2. ‎`@JsonIgnore`‎ | ‎`Role.users`‎ | גם אם מישהו יחזיר Entity בטעות – הצד ההפוך לא נכתב |
| 3. ‎`@JsonIgnore`‎ | ‎`User.password`‎ | ה‑Hash לא ייצא החוצה גם בטעות |

> חלופה מוכרת: ‎`@JsonManagedReference`‎ / ‎`@JsonBackReference`‎. היא פותרת את הלולאה אבל עדיין חושפת Entity החוצה – לכן DTO עדיף.

### 6.7 ‎`UserRepository`‎ ו‑‎`RoleRepository`‎

**מה:** ממשקים בלבד – Spring Data מייצר את המימוש בזמן ריצה.

**שימו לב:**
- ‎`findByUsername`‎ – ‏Spring בונה את השאילתה משם המתודה.
- ‎`@EntityGraph(attributePaths = "roles")`‎ – טוען את התפקידים **באותה שאילתה** (JOIN).
  - ב‑‎`findByUsername`‎ זה הכרחי: ה‑JWT Filter קורא לו מחוץ לטרנזקציה, ובלי זה גישה ל‑‎`roles`‎ זורקת ‎`LazyInitializationException`‎.
  - ב‑‎`findAll`‎ זה מונע את בעיית **N+1** (שאילתה אחת לרשימה + שאילתה נפרדת לכל משתמש).

</div>

```java
package com.example.jwtlesson.repository;

import com.example.jwtlesson.entity.User;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {

    // Spring Data builds the query from the method name:
    // SELECT ... FROM users WHERE username = ?
    // @EntityGraph loads the roles in the same query (JOIN) - needed because
    // the JWT filter calls this outside a transaction.
    @EntityGraph(attributePaths = "roles")
    Optional<User> findByUsername(String username);

    boolean existsByUsername(String username);

    // Override findAll so the admin list loads roles in one query (no N+1)
    @Override
    @EntityGraph(attributePaths = "roles")
    List<User> findAll();
}
```

```java
package com.example.jwtlesson.repository;

import com.example.jwtlesson.entity.Role;
import com.example.jwtlesson.entity.RoleName;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface RoleRepository extends JpaRepository<Role, Long> {

    Optional<Role> findByName(RoleName name);
}
```

<div dir="rtl">

### 6.8 ‏DTOs – מה נכנס ומה יוצא מה‑API

**מה:** ‎`record`‎ של Java – מחלקה קטנה, בלתי ניתנת לשינוי, עם בנאי ו‑getters מובנים.

**שימו לב:**
- אנוטציות Validation ‏(‎`@NotBlank`‎, ‎`@Size`‎) נבדקות כשב‑Controller כתוב ‎`@Valid`‎. כשל = **400**.
- ב‑‎`RegisterRequest`‎ **אין** שדה ‎`roles`‎ – הלקוח לא בוחר לעצמו תפקיד (הגנה מפני Mass Assignment – בדיקה 19).
- ‎`UserResponse.from()`‎ ממיר Entity ל‑DTO: בלי סיסמה, תפקידים כמחרוזות.

</div>

```java
package com.example.jwtlesson.dto;

import jakarta.validation.constraints.NotBlank;

public record LoginRequest(
        @NotBlank String username,
        @NotBlank String password) {
}
```

```java
package com.example.jwtlesson.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

// No "roles" field on purpose: a client must never choose its own role
public record RegisterRequest(
        @NotBlank @Size(min = 3, max = 50) String username,
        @NotBlank @Size(min = 8, max = 72) String password) { // BCrypt uses at most 72 bytes
}
```

```java
package com.example.jwtlesson.dto;

import java.util.List;

public record LoginResponse(
        String token,
        String tokenType,     // "Bearer"
        long expiresInMs,
        String username,
        List<String> roles) {
}
```

```java
package com.example.jwtlesson.dto;

import com.example.jwtlesson.entity.User;

import java.util.List;

/**
 * What the API returns about a user. A DTO, not the entity:
 * no password, no back-references -> no JSON loop, no data leak.
 */
public record UserResponse(
        Long id,
        String username,
        boolean enabled,
        List<String> roles) {

    public static UserResponse from(User user) {
        List<String> roleNames = user.getRoles().stream()
                .map(role -> role.getName().name())
                .sorted()
                .toList();
        return new UserResponse(user.getId(), user.getUsername(), user.isEnabled(), roleNames);
    }
}
```

<div dir="rtl">

### 6.9 ‎`CustomUserDetailsService`‎ – הגשר בין ה‑DB ל‑Spring Security

**מה:** מממש את הממשק ‎`UserDetailsService`‎. מקבל שם משתמש, טוען מה‑DB, ומחזיר ‎`UserDetails`‎ – האובייקט ש‑Spring Security מבין (שם, ‏Hash, תפקידים, פעיל/חסום).

**דיוק חשוב:** המחלקה הזו **לא** בודקת את הסיסמה. היא רק טוענת. את ההשוואה עושה ‎`DaoAuthenticationProvider`‎ (מוגדר ב‑‎`SecurityConfig`‎) בעזרת ‎`BCryptPasswordEncoder.matches()`‎.

**שימו לב:**
- שתי מחלקות בשם ‎`User`‎: שלנו (Entity) ושל Spring. לכן כותבים את השם המלא ‎`org.springframework.security.core.userdetails.User`‎.
- אם המשתמש לא קיים – ‎`UsernameNotFoundException`‎. בלוגין, ‏Spring הופך אותה ל‑‎`BadCredentialsException`‎, כך שהתוקף לא יודע אם השם קיים או שהסיסמה שגויה.

</div>

```java
package com.example.jwtlesson.security;

import com.example.jwtlesson.entity.User;
import com.example.jwtlesson.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Bridge between OUR database and Spring Security.
 * Loads a user by name and returns a UserDetails (name + password hash + roles + enabled).
 * It does NOT compare passwords - DaoAuthenticationProvider does that with the PasswordEncoder.
 */
@Service
@RequiredArgsConstructor
public class CustomUserDetailsService implements UserDetailsService {

    private final UserRepository userRepository;

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new UsernameNotFoundException("User not found: " + username));

        List<SimpleGrantedAuthority> authorities = user.getRoles().stream()
                .map(role -> new SimpleGrantedAuthority(role.getName().name())) // "ROLE_ADMIN"
                .toList();

        // Spring's own User class (full name - our entity is also called User)
        return org.springframework.security.core.userdetails.User.builder()
                .username(user.getUsername())
                .password(user.getPassword())   // the BCrypt hash from the DB
                .disabled(!user.isEnabled())
                .authorities(authorities)
                .build();
    }
}
```

<div dir="rtl">

### 6.10 ‎`JwtUtil`‎ – יצירה ואימות של טוקן

**מה:** שתי פעולות – ‎`generateToken()`‎ בלוגין, ו‑‎`parseClaims()`‎ בכל בקשה.

**שימו לב:**
- ‎`Keys.hmacShaKeyFor()`‎ זורק ‎`WeakKeyException`‎ אם הסוד קצר מ‑256 ביט – Fail Fast כבר בעליית השרת.
- ‎`parseClaims()`‎ בודק **גם** חתימה **וגם** תוקף. טוקן ששונה ← ‎`SignatureException`‎. טוקן שפג ← ‎`ExpiredJwtException`‎. כולן יורשות מ‑‎`JwtException`‎.
- ה‑claim ‎`roles`‎ בטוקן הוא לנוחות הלקוח (להציג/להסתיר כפתורים). **השרת לא סומך עליו** – ה‑Filter טוען תפקידים עדכניים מה‑DB.

</div>

```java
package com.example.jwtlesson.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.util.Date;
import java.util.List;

/**
 * Creates and verifies JWT tokens (HMAC-SHA256 signature with a server-side secret).
 */
@Component
public class JwtUtil {

    private final SecretKey key;
    private final long expirationMs;

    public JwtUtil(@Value("${jwt.secret}") String base64Secret,
                   @Value("${jwt.expiration-ms}") long expirationMs) {
        // Throws WeakKeyException if the secret is shorter than 256 bits
        this.key = Keys.hmacShaKeyFor(Decoders.BASE64.decode(base64Secret));
        this.expirationMs = expirationMs;
    }

    /** Called after a successful login. */
    public String generateToken(UserDetails userDetails) {
        Date now = new Date();
        List<String> roles = userDetails.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .toList();

        return Jwts.builder()
                .subject(userDetails.getUsername())                 // "sub"
                .claim("roles", roles)                              // custom claim, for the client to read
                .issuedAt(now)                                      // "iat"
                .expiration(new Date(now.getTime() + expirationMs)) // "exp"
                .signWith(key)                                      // HS256 for a 256-bit key
                .compact();                                         // header.payload.signature
    }

    /**
     * Verifies the signature and the expiry, then returns the payload.
     * Throws JwtException (SignatureException, ExpiredJwtException, MalformedJwtException ...)
     * if the token was changed, expired or is not a JWT.
     */
    public Claims parseClaims(String token) {
        return Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    public String extractUsername(String token) {
        return parseClaims(token).getSubject();
    }

    public long getExpirationMs() {
        return expirationMs;
    }
}
```

<div dir="rtl">

### 6.11 ‎`JwtAuthFilter`‎ – בודק את הטוקן בכל בקשה

**מה:** ‏Filter שרץ פעם אחת לכל בקשה, **לפני** ה‑Controller. אם יש טוקן תקין – מכניס את המשתמש ואת התפקידים ל‑‎`SecurityContext`‎.

**שימו לב:**
- ה‑Filter **לא מחזיר 401 בעצמו**. בטוקן לא תקין הוא משאיר את הבקשה אנונימית, ו‑‎`SecurityConfig`‎ מחליט. כך נקודות קצה ציבוריות עובדות גם עם טוקן פגום.
- הוא טוען את המשתמש מה‑DB בכל בקשה. המחיר: שאילתה. הרווח: משתמש שנמחק או נחסם – נחסם **מיד**, ושינוי תפקיד נכנס לתוקף מיד.
- **לא** ‎`@Component`‎: ‏Spring Boot רושם אוטומטית כל Bean מסוג Filter גם ישירות ב‑Tomcat. כך הוא היה רשום פעמיים – בשרשרת של Spring Security ובשרת עצמו (התיעוד של Spring Security מזהיר מזה). לכן ‎`SecurityConfig`‎ יוצר אותו עם ‎`new`‎.

</div>

```java
package com.example.jwtlesson.security;

import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Runs once per request, before Spring's login filter.
 * If the request carries a valid "Authorization: Bearer <token>" header,
 * it puts the user (with roles) into the SecurityContext.
 * It never rejects by itself - SecurityConfig's rules decide 401/403.
 *
 * Deliberately NOT a @Component: SecurityConfig creates it with "new",
 * otherwise Spring Boot would also register it as a regular servlet filter.
 */
@RequiredArgsConstructor
public class JwtAuthFilter extends OncePerRequestFilter {

    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtUtil jwtUtil;
    private final UserDetailsService userDetailsService;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

        String header = request.getHeader(HttpHeaders.AUTHORIZATION);

        // 1. No token -> continue as anonymous (public endpoints still work)
        if (header == null || !header.startsWith(BEARER_PREFIX)) {
            filterChain.doFilter(request, response);
            return;
        }

        String token = header.substring(BEARER_PREFIX.length());

        try {
            // 2. Verify signature + expiry and read the username ("sub")
            String username = jwtUtil.extractUsername(token);

            if (username != null && SecurityContextHolder.getContext().getAuthentication() == null) {
                // 3. Load the CURRENT user from the DB (deleted/disabled users are blocked,
                //    role changes take effect immediately)
                UserDetails userDetails = userDetailsService.loadUserByUsername(username);

                if (userDetails.isEnabled()) {
                    // 4. Mark this request as authenticated with the user's roles
                    UsernamePasswordAuthenticationToken authentication =
                            new UsernamePasswordAuthenticationToken(
                                    userDetails, null, userDetails.getAuthorities());
                    authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                    SecurityContextHolder.getContext().setAuthentication(authentication);
                }
            }
        } catch (JwtException | IllegalArgumentException | UsernameNotFoundException e) {
            // Forged / expired / malformed token, or user no longer exists:
            // leave the request anonymous -> protected endpoints answer 401
            SecurityContextHolder.clearContext();
        }

        filterChain.doFilter(request, response);
    }
}
```

<div dir="rtl">

### 6.12 ‎`SecurityConfig`‎ – הלב של האבטחה

**מה:** כל חוקי האבטחה במקום אחד. שלושה Beans:

| Bean | תפקיד |
|---|---|
| ‎`SecurityFilterChain`‎ | מי רשאי לגשת לאיזה URL, ‏Stateless, ‏JWT Filter, תשובות 401/403 |
| ‎`PasswordEncoder`‎ | ‏BCrypt – ל‑Hash בהרשמה ולבדיקה בלוגין |
| ‎`AuthenticationManager`‎ | ‎`DaoAuthenticationProvider`‎ = טעינת משתמש + השוואת סיסמה |

**שימו לב:**
- **סדר החוקים חשוב** – הראשון שמתאים קובע. ‎`anyRequest()`‎ תמיד אחרון.
- ‎`csrf.disable()`‎ בטוח כאן כי אין Cookies: הטוקן נשלח בכותרת, והדפדפן לא מצרף אותה אוטומטית.
- בלי ‎`authenticationEntryPoint`‎, ‏Spring מחזיר **403** גם כשאין טוקן בכלל. הגדרנו במפורש **401** – כדי ש‑401 ו‑403 ישמרו על המשמעות הנכונה.
- ‎`"/error"`‎ ב‑permitAll – אחרת שגיאות פנימיות היו מגיעות ללקוח כ‑401 מטעה.
- כל ההרשאות מוגדרות כאן בלבד. ב‑Controllers אין קוד אבטחה.

</div>

```java
package com.example.jwtlesson.config;

import com.example.jwtlesson.security.CustomUserDetailsService;
import com.example.jwtlesson.security.JwtAuthFilter;
import com.example.jwtlesson.security.JwtUtil;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

import java.io.IOException;

@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtUtil jwtUtil;
    private final CustomUserDetailsService userDetailsService;

    /** The security rules: who may call which URL. */
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                // No cookies/session -> CSRF attacks are not relevant (token is sent in a header)
                .csrf(AbstractHttpConfigurer::disable)

                // STATELESS: the server keeps no session - every request proves itself with the JWT
                .sessionManagement(session ->
                        session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))

                // Authorization rules - checked top to bottom, first match wins
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/api/auth/**", "/api/public/**", "/error").permitAll()
                        .requestMatchers("/api/admin/**").hasRole("ADMIN")   // needs ROLE_ADMIN
                        .requestMatchers("/api/user/**").hasRole("USER")     // needs ROLE_USER
                        .anyRequest().authenticated())

                // 401 = who are you? (no/invalid token)   403 = I know you, but no permission
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint((request, response, e) ->
                                writeJsonError(response, HttpServletResponse.SC_UNAUTHORIZED,
                                        "Unauthorized - missing, invalid or expired token"))
                        .accessDeniedHandler((request, response, e) ->
                                writeJsonError(response, HttpServletResponse.SC_FORBIDDEN,
                                        "Forbidden - your role is not allowed here")))

                // Our JWT filter runs before Spring's username/password filter
                .addFilterBefore(new JwtAuthFilter(jwtUtil, userDetailsService),
                        UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    /** One-way hash for passwords. BCrypt adds a random salt and is slow on purpose. */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    /**
     * Used by UserLoginService.login():
     * DaoAuthenticationProvider = loadUserByUsername() + passwordEncoder.matches(raw, hash).
     */
    @Bean
    public AuthenticationManager authenticationManager(PasswordEncoder passwordEncoder) {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider(userDetailsService);
        provider.setPasswordEncoder(passwordEncoder);
        return new ProviderManager(provider);
    }

    private static void writeJsonError(HttpServletResponse response, int status, String message)
            throws IOException {
        response.setStatus(status);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.getWriter().write("{\"status\":" + status + ",\"error\":\"" + message + "\"}");
    }
}
```

<div dir="rtl">

### 6.13 ‎`DataInitializer`‎ – יצירת נתונים התחלתיים

**מה:** ‎`CommandLineRunner`‎ רץ פעם אחת אחרי עליית ה‑Context. בודק אם התפקידים ו‑admin/user קיימים, ויוצר רק את מה שחסר.

**שימו לב:**
- **אידמפוטנטי** – אפשר להריץ את השרת מאה פעמים, ולא ייווצרו כפילויות.
- הסיסמאות נשמרות דרך ‎`passwordEncoder.encode()`‎ – אף פעם לא כטקסט גלוי.
- ‎`@Transactional`‎ – כל האתחול בטרנזקציה אחת.
- הסיסמאות ההתחלתיות מגיעות מ‑‎`application.properties`‎ (ממשתני סביבה בייצור), לא מהקוד.

</div>

```java
package com.example.jwtlesson.config;

import com.example.jwtlesson.entity.Role;
import com.example.jwtlesson.entity.RoleName;
import com.example.jwtlesson.entity.User;
import com.example.jwtlesson.repository.RoleRepository;
import com.example.jwtlesson.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.Set;

/**
 * Runs once when the application starts.
 * Checks the DB: creates the roles and the initial admin/user ONLY if they are missing.
 * Safe to run on every start (idempotent).
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class DataInitializer implements CommandLineRunner {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;

    @Value("${app.init.admin.username}")
    private String adminUsername;
    @Value("${app.init.admin.password}")
    private String adminPassword;
    @Value("${app.init.user.username}")
    private String userUsername;
    @Value("${app.init.user.password}")
    private String userPassword;

    @Override
    @Transactional
    public void run(String... args) {
        Role adminRole = ensureRole(RoleName.ROLE_ADMIN);
        Role userRole = ensureRole(RoleName.ROLE_USER);

        // admin has two roles, ROLE_USER has two users -> many-to-many in action
        ensureUser(adminUsername, adminPassword, Set.of(adminRole, userRole));
        ensureUser(userUsername, userPassword, Set.of(userRole));
    }

    private Role ensureRole(RoleName name) {
        return roleRepository.findByName(name).orElseGet(() -> {
            log.info("Role {} not found - creating it", name);
            return roleRepository.save(Role.builder().name(name).build());
        });
    }

    private void ensureUser(String username, String rawPassword, Set<Role> roles) {
        if (userRepository.existsByUsername(username)) {
            log.info("User '{}' already exists - skipping", username);
            return;
        }
        User user = User.builder()
                .username(username)
                .password(passwordEncoder.encode(rawPassword)) // store the HASH only
                .roles(new HashSet<>(roles))
                .build();
        userRepository.save(user);
        log.info("User '{}' created with roles {}", username, roles);
    }
}
```

<div dir="rtl">

### 6.14 ‎`UserLoginService`‎ – לוגין והרשמה

**מה:** הלוגיקה של ‎`/api/auth`‎.

**לוגין:**
1. ‎`authenticationManager.authenticate()`‎ – בודק שם + סיסמה. שגוי ← ‎`BadCredentialsException`‎ ← **401** (דרך ‎`GlobalExceptionHandler`‎).
2. ‎`jwtUtil.generateToken()`‎ – יוצר טוקן חתום.

**הרשמה:** בודק שהשם פנוי (אחרת **409**), שומר Hash של הסיסמה, ונותן **רק** ‎`ROLE_USER`‎.

</div>

```java
package com.example.jwtlesson.service;

import com.example.jwtlesson.dto.LoginRequest;
import com.example.jwtlesson.dto.LoginResponse;
import com.example.jwtlesson.dto.RegisterRequest;
import com.example.jwtlesson.dto.UserResponse;
import com.example.jwtlesson.entity.Role;
import com.example.jwtlesson.entity.RoleName;
import com.example.jwtlesson.entity.User;
import com.example.jwtlesson.exception.UsernameTakenException;
import com.example.jwtlesson.repository.RoleRepository;
import com.example.jwtlesson.repository.UserRepository;
import com.example.jwtlesson.security.JwtUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class UserLoginService {

    private final AuthenticationManager authenticationManager;
    private final JwtUtil jwtUtil;
    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;

    /**
     * 1. AuthenticationManager checks name + password (throws BadCredentialsException if wrong)
     * 2. JwtUtil creates a signed token for the authenticated user
     */
    public LoginResponse login(LoginRequest request) {
        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.username(), request.password()));

        UserDetails userDetails = (UserDetails) authentication.getPrincipal();
        String token = jwtUtil.generateToken(userDetails);

        List<String> roles = userDetails.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .toList();

        return new LoginResponse(token, "Bearer", jwtUtil.getExpirationMs(),
                userDetails.getUsername(), roles);
    }

    /** New users always get ROLE_USER only. Admins are never created through this endpoint. */
    @Transactional
    public UserResponse register(RegisterRequest request) {
        if (userRepository.existsByUsername(request.username())) {
            throw new UsernameTakenException(request.username());
        }

        Role userRole = roleRepository.findByName(RoleName.ROLE_USER)
                .orElseThrow(() -> new IllegalStateException("ROLE_USER missing - did DataInitializer run?"));

        User user = User.builder()
                .username(request.username())
                .password(passwordEncoder.encode(request.password()))
                .roles(new HashSet<>(Set.of(userRole)))
                .build();

        return UserResponse.from(userRepository.save(user));
    }
}
```

<div dir="rtl">

### 6.15 ‎`UserService`‎ – שליפת משתמשים

**מה:** קריאה מה‑DB והמרה ל‑DTO, בתוך טרנזקציה לקריאה בלבד (‎`readOnly = true`‎).

</div>

```java
package com.example.jwtlesson.service;

import com.example.jwtlesson.dto.UserResponse;
import com.example.jwtlesson.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;

    @Transactional(readOnly = true)
    public UserResponse getByUsername(String username) {
        return userRepository.findByUsername(username)
                .map(UserResponse::from)
                .orElseThrow(() -> new UsernameNotFoundException("User not found: " + username));
    }

    @Transactional(readOnly = true)
    public List<UserResponse> getAll() {
        return userRepository.findAll().stream()
                .map(UserResponse::from)   // entity -> DTO inside the transaction
                .toList();
    }
}
```

<div dir="rtl">

### 6.16 ‎`UserLoginController`‎

**מה:** ‎`POST /api/auth/login`‎ ו‑‎`POST /api/auth/register`‎. רק מקבל HTTP ומעביר ל‑Service.

**שימו לב:** ‎`@Valid`‎ מפעיל את בדיקות ה‑DTO; ‎`@ResponseStatus(CREATED)`‎ מחזיר **201** בהרשמה.

</div>

```java
package com.example.jwtlesson.controller;

import com.example.jwtlesson.dto.LoginRequest;
import com.example.jwtlesson.dto.LoginResponse;
import com.example.jwtlesson.dto.RegisterRequest;
import com.example.jwtlesson.dto.UserResponse;
import com.example.jwtlesson.service.UserLoginService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

/**
 * Public endpoints (permitAll in SecurityConfig): login and register.
 * The controller only receives HTTP and delegates - the logic lives in UserLoginService.
 */
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class UserLoginController {

    private final UserLoginService userLoginService;

    // POST /api/auth/login   {"username":"admin","password":"Admin123!"}
    @PostMapping("/login")
    public LoginResponse login(@Valid @RequestBody LoginRequest request) {
        return userLoginService.login(request);
    }

    // POST /api/auth/register   {"username":"dana","password":"Dana1234!"}
    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public UserResponse register(@Valid @RequestBody RegisterRequest request) {
        return userLoginService.register(request);
    }
}
```

<div dir="rtl">

### 6.17 ‎`ResourceController`‎ – שלושה אזורי אבטחה

**מה:** נקודת קצה אחת לכל אזור, כדי להדגים את החוקים של ‎`SecurityConfig`‎.

**שימו לב:** ‎`Authentication authentication`‎ כפרמטר – ‏Spring מזריק את מה שה‑‎`JwtAuthFilter`‎ שם ב‑‎`SecurityContext`‎. ‎`authentication.getName()`‎ = שם המשתמש מהטוקן.

</div>

```java
package com.example.jwtlesson.controller;

import com.example.jwtlesson.dto.UserResponse;
import com.example.jwtlesson.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * Three endpoints, one per security zone in SecurityConfig.
 * Note: no security code here - the rules live only in SecurityConfig.
 */
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class ResourceController {

    private final UserService userService;

    // Zone 1: anyone
    @GetMapping("/public/hello")
    public Map<String, String> hello() {
        return Map.of("message", "Public endpoint - no token needed");
    }

    // Zone 2: ROLE_USER. Spring injects the Authentication that JwtAuthFilter created.
    @GetMapping("/user/me")
    public UserResponse me(Authentication authentication) {
        return userService.getByUsername(authentication.getName());
    }

    // Zone 3: ROLE_ADMIN only
    @GetMapping("/admin/users")
    public List<UserResponse> allUsers() {
        return userService.getAll();
    }
}
```

<div dir="rtl">

### 6.18 טיפול בשגיאות – ‎`GlobalExceptionHandler`‎

**מה:** ממיר Exceptions לתשובות JSON אחידות: ‏401 ללוגין שגוי, ‏409 לשם תפוס, ‏400 לשדות לא תקינים.

**שימו לב:** לסיסמה שגויה, למשתמש לא קיים ולמשתמש חסום – **אותה הודעה בדיוק**. הודעה שונה הייתה מגלה לתוקף אילו שמות קיימים (User Enumeration).

</div>

```java
package com.example.jwtlesson.exception;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Turns exceptions thrown by controllers/services into clean JSON answers.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    // Wrong password, unknown user, disabled user -> the same answer (don't reveal which)
    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<Map<String, Object>> handleAuthentication(AuthenticationException ex) {
        return error(HttpStatus.UNAUTHORIZED, "Invalid username or password");
    }

    @ExceptionHandler(UsernameTakenException.class)
    public ResponseEntity<Map<String, Object>> handleUsernameTaken(UsernameTakenException ex) {
        return error(HttpStatus.CONFLICT, ex.getMessage());
    }

    // @Valid failed: {"status":400,"error":"Validation failed","fields":{"password":"size must be ..."}}
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleValidation(MethodArgumentNotValidException ex) {
        Map<String, String> fields = new LinkedHashMap<>();
        ex.getBindingResult().getFieldErrors()
                .forEach(fe -> fields.put(fe.getField(), fe.getDefaultMessage()));

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("status", HttpStatus.BAD_REQUEST.value());
        body.put("error", "Validation failed");
        body.put("fields", fields);
        return ResponseEntity.badRequest().body(body);
    }

    private ResponseEntity<Map<String, Object>> error(HttpStatus status, String message) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("status", status.value());
        body.put("error", message);
        return ResponseEntity.status(status).body(body);
    }
}
```

```java
package com.example.jwtlesson.exception;

public class UsernameTakenException extends RuntimeException {

    public UsernameTakenException(String username) {
        super("Username already taken: " + username);
    }
}
```

<div dir="rtl">

### 6.19 מחלקת ההרצה

</div>

```java
package com.example.jwtlesson;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class JwtLessonApplication {

    public static void main(String[] args) {
        SpringApplication.run(JwtLessonApplication.class, args);
    }
}
```

<div dir="rtl">

---

## 7. הרצה ובדיקה

### 7.1 הרצת השרת

1. ודאו ש‑PostgreSQL רץ ושהמסד ‎`jwt_lesson`‎ קיים (סעיף 5.1).
2. ב‑IntelliJ: הריצו את ‎`JwtLessonApplication`‎.
3. בלוג תופיע הודעת ההפעלה של Tomcat על פורט 8080, ובהרצה ראשונה גם ההודעות של ‎`DataInitializer`‎, למשל ‎`User 'admin' created with roles ...`‎. בהרצה שנייה: ‎`User 'admin' already exists - skipping`‎.

### 7.2 בדיקה ב‑DB

</div>

```sql
-- מי מחזיק איזה תפקיד (רבים לרבים)
SELECT u.username, r.name AS role
FROM users u
JOIN user_roles ur ON ur.user_id = u.id
JOIN roles r       ON r.id = ur.role_id
ORDER BY u.username, r.name;

-- הסיסמאות שמורות כ-Hash של BCrypt: מתחילות ב-$2a$10$ ואורכן 60
SELECT username, left(password, 7) AS hash_prefix, length(password) AS len
FROM users;
```

<div dir="rtl">

### 7.3 קובץ הבדיקות ל‑IntelliJ Ultimate

**איך מריצים:**

1. צרו תיקייה ‎`http`‎ בשורש הפרויקט, ובה שני הקבצים שלמטה.
2. פתחו את ‎`security-tests.http`‎.
3. בראש הקובץ, בתפריט הסביבות, בחרו **dev**.
4. לחצו **Run All Requests in File** (או ריצה אחת‑אחת מלמעלה למטה – בקשות 6 ו‑7 שומרות טוקנים שבקשות מאוחרות יותר משתמשות בהם).
5. התוצאות: לשונית **Tests** בחלון Services – ירוק/אדום לכל בדיקה.

**איך זה עובד:** אחרי כל בקשה רץ סקריפט JavaScript (בין ‎`> {%`‎ ל‑‎`%}`‎).
‎`client.test()`‎ מגדיר בדיקה, ‎`client.assert()`‎ בודק תנאי, ו‑‎`client.global.set("adminToken", ...)`‎ שומר ערך שבקשות הבאות משתמשות בו כ‑‎`{{adminToken}}`‎.

| # | בדיקה | צפוי |
|---|---|---|
| 1 | נקודת קצה ציבורית בלי טוקן | 200 |
| 2 | נקודת קצה מוגנת בלי טוקן | 401 |
| 3 | לוגין עם סיסמה שגויה | 401 |
| 4 | לוגין עם משתמש שלא קיים – אותה הודעה כמו סעיף 3 | 401 |
| 5 | לוגין עם שדות ריקים | 400 |
| 6 | לוגין admin – שמירת ‎`adminToken`‎ | 200 |
| 7 | לוגין user – שמירת ‎`userToken`‎ ובניית טוקן מזויף | 200 |
| 8 | ‏user ← ‎`/api/user/me`‎ | 200 |
| 9 | ‏user ← ‎`/api/admin/users`‎ | **403** |
| 10 | ‏admin ← ‎`/api/admin/users`‎ – בלי סיסמה, בלי לולאה, 2 תפקידים ל‑admin | 200 |
| 11 | ‏admin ← ‎`/api/user/me`‎ | 200 |
| 12 | טוקן מזויף: ‏Payload של admin עם החתימה של user | **401** |
| 13 | טוקן זבל | 401 |
| 14 | טוקן תקין בלי המילה ‎`Bearer`‎ | 401 |
| 15 | הרשמה של משתמש חדש – מקבל רק ‎`ROLE_USER`‎ | 201 |
| 16 | לוגין עם המשתמש החדש | 200 |
| 17 | הרשמה עם שם קיים (‎`admin`‎) | 409 |
| 18 | הרשמה עם סיסמה קצרה | 400 |
| 19 | ניסיון להירשם עם ‎`"roles": ["ROLE_ADMIN"]`‎ | אף פעם לא ROLE_ADMIN |
| 20 | טוקן שפג תוקף (ידני, מוסבר בסוף הקובץ) | 401 |

**‎`http/http-client.env.json`‎**

</div>

```json
{
  "dev": {
    "host": "http://localhost:8080"
  }
}
```

<div dir="rtl">

**‎`http/security-tests.http`‎**

</div>

```http
###############################################################################
# Spring Security + JWT - REST tests for the IntelliJ IDEA HTTP Client
#
# How to run:
#   1. Start PostgreSQL and the application (JwtLessonApplication).
#   2. Open this file in IntelliJ IDEA Ultimate.
#   3. Choose the environment "dev" (from http-client.env.json, same folder).
#   4. Click "Run All Requests in File" (or run one by one, top to bottom -
#      requests 6 and 7 save tokens that later requests use).
#   5. Results: the "Tests" tab in the Services tool window (green / red).
###############################################################################


### 1. Public endpoint - no token needed -> 200
GET {{host}}/api/public/hello

> {%
    client.test("1. public endpoint is open (200)", function () {
        client.assert(response.status === 200, "Expected 200, got " + response.status);
    });
%}


### 2. Protected endpoint WITHOUT a token -> 401
GET {{host}}/api/user/me

> {%
    client.test("2. no token -> 401", function () {
        client.assert(response.status === 401, "Expected 401, got " + response.status);
    });
%}


### 3. Login with a WRONG password -> 401
POST {{host}}/api/auth/login
Content-Type: application/json

{
  "username": "admin",
  "password": "wrong-password"
}

> {%
    client.test("3. wrong password -> 401", function () {
        client.assert(response.status === 401, "Expected 401, got " + response.status);
        client.assert(response.body.error === "Invalid username or password", "Unexpected message");
    });
%}


### 4. Login with a user that does NOT exist -> 401 with the SAME message (no user enumeration)
POST {{host}}/api/auth/login
Content-Type: application/json

{
  "username": "no_such_user",
  "password": "whatever123"
}

> {%
    client.test("4. unknown user -> same 401 message as wrong password", function () {
        client.assert(response.status === 401, "Expected 401, got " + response.status);
        client.assert(response.body.error === "Invalid username or password", "Message must not reveal that the user is missing");
    });
%}


### 5. Login with empty fields -> 400 (validation)
POST {{host}}/api/auth/login
Content-Type: application/json

{
  "username": "",
  "password": ""
}

> {%
    client.test("5. empty fields -> 400", function () {
        client.assert(response.status === 400, "Expected 400, got " + response.status);
        client.assert(response.body.fields.username !== undefined, "Expected an error on 'username'");
    });
%}


### 6. Login as ADMIN -> 200 + token (saved as adminToken)
POST {{host}}/api/auth/login
Content-Type: application/json

{
  "username": "admin",
  "password": "Admin123!"
}

> {%
    client.test("6. admin login -> 200 with a JWT", function () {
        client.assert(response.status === 200, "Expected 200, got " + response.status);
        client.assert(response.body.tokenType === "Bearer", "tokenType must be Bearer");
        client.assert(response.body.token.split(".").length === 3, "A JWT has 3 parts: header.payload.signature");
        client.assert(response.body.roles.indexOf("ROLE_ADMIN") >= 0, "admin must have ROLE_ADMIN");
    });
    client.global.set("adminToken", response.body.token);
%}


### 7. Login as USER -> 200 + token (saved as userToken) + build a FORGED token
POST {{host}}/api/auth/login
Content-Type: application/json

{
  "username": "user",
  "password": "User123!"
}

> {%
    client.test("7. user login -> 200 with ROLE_USER only", function () {
        client.assert(response.status === 200, "Expected 200, got " + response.status);
        client.assert(response.body.roles.length === 1, "user must have exactly one role");
        client.assert(response.body.roles[0] === "ROLE_USER", "user must have ROLE_USER");
    });
    client.global.set("userToken", response.body.token);

    // Attack simulation: the user takes the ADMIN payload (sub=admin, roles=[ROLE_ADMIN])
    // and glues it to HIS OWN signature. The signature no longer matches -> must be rejected.
    var userParts = response.body.token.split(".");
    var adminParts = client.global.get("adminToken").split(".");
    client.global.set("forgedToken", userParts[0] + "." + adminParts[1] + "." + userParts[2]);
%}


### 8. USER token on a USER endpoint -> 200
GET {{host}}/api/user/me
Authorization: Bearer {{userToken}}

> {%
    client.test("8. user token on /api/user/me -> 200", function () {
        client.assert(response.status === 200, "Expected 200, got " + response.status);
        client.assert(response.body.username === "user", "Expected username 'user'");
        client.assert(response.body.password === undefined, "Password must never be returned");
    });
%}


### 9. USER token on an ADMIN endpoint -> 403 (authenticated, but wrong role)
GET {{host}}/api/admin/users
Authorization: Bearer {{userToken}}

> {%
    client.test("9. user token on admin endpoint -> 403", function () {
        client.assert(response.status === 403, "Expected 403, got " + response.status);
    });
%}


### 10. ADMIN token on an ADMIN endpoint -> 200, many-to-many roles, no password, no JSON loop
GET {{host}}/api/admin/users
Authorization: Bearer {{adminToken}}

> {%
    client.test("10. admin lists users -> 200, clean JSON", function () {
        client.assert(response.status === 200, "Expected 200, got " + response.status);
        client.assert(Array.isArray(response.body), "Expected a JSON array");

        var admin = null;
        for (var i = 0; i < response.body.length; i++) {
            var u = response.body[i];
            client.assert(u.password === undefined, "Password leaked for " + u.username);
            client.assert(typeof u.roles[0] === "string", "roles must be plain strings (no nested objects -> no loop)");
            if (u.username === "admin") { admin = u; }
        }
        client.assert(admin !== null, "admin must be in the list");
        client.assert(admin.roles.length === 2, "admin must have 2 roles (many-to-many)");
    });
%}


### 11. ADMIN token on a USER endpoint -> 200 (admin also has ROLE_USER)
GET {{host}}/api/user/me
Authorization: Bearer {{adminToken}}

> {%
    client.test("11. admin token on /api/user/me -> 200", function () {
        client.assert(response.status === 200, "Expected 200, got " + response.status);
        client.assert(response.body.username === "admin", "Expected username 'admin'");
    });
%}


### 12. FORGED token (admin payload + user signature) -> 401
GET {{host}}/api/admin/users
Authorization: Bearer {{forgedToken}}

> {%
    client.test("12. forged token -> 401 (signature check works)", function () {
        client.assert(response.status === 401, "Expected 401, got " + response.status);
    });
%}


### 13. Garbage token -> 401
GET {{host}}/api/user/me
Authorization: Bearer this.is.not-a-jwt

> {%
    client.test("13. garbage token -> 401", function () {
        client.assert(response.status === 401, "Expected 401, got " + response.status);
    });
%}


### 14. Valid token but WITHOUT the "Bearer " prefix -> 401
GET {{host}}/api/user/me
Authorization: {{userToken}}

> {%
    client.test("14. missing 'Bearer ' prefix -> 401", function () {
        client.assert(response.status === 401, "Expected 401, got " + response.status);
    });
%}


### 15. Register a new user -> 201, gets ROLE_USER only (saved as newUsername)
POST {{host}}/api/auth/register
Content-Type: application/json

{
  "username": "student_{{$timestamp}}",
  "password": "Student123!"
}

> {%
    client.test("15. register -> 201 with ROLE_USER", function () {
        client.assert(response.status === 201, "Expected 201, got " + response.status);
        client.assert(response.body.roles.length === 1 && response.body.roles[0] === "ROLE_USER", "New user must get ROLE_USER only");
        client.assert(response.body.password === undefined, "Password must never be returned");
    });
    client.global.set("newUsername", response.body.username);
%}


### 16. Login with the new user -> 200 (password was hashed and verified)
POST {{host}}/api/auth/login
Content-Type: application/json

{
  "username": "{{newUsername}}",
  "password": "Student123!"
}

> {%
    client.test("16. new user can log in -> 200", function () {
        client.assert(response.status === 200, "Expected 200, got " + response.status);
    });
%}


### 17. Register an EXISTING username -> 409
POST {{host}}/api/auth/register
Content-Type: application/json

{
  "username": "admin",
  "password": "TryToTakeOver1!"
}

> {%
    client.test("17. existing username -> 409", function () {
        client.assert(response.status === 409, "Expected 409, got " + response.status);
    });
%}


### 18. Register with a too-short password -> 400
POST {{host}}/api/auth/register
Content-Type: application/json

{
  "username": "shorty_{{$timestamp}}",
  "password": "123"
}

> {%
    client.test("18. short password -> 400", function () {
        client.assert(response.status === 400, "Expected 400, got " + response.status);
        client.assert(response.body.fields.password !== undefined, "Expected an error on 'password'");
    });
%}


### 19. Try to register yourself as ADMIN (mass assignment) -> never ROLE_ADMIN
# RegisterRequest has no "roles" field. Depending on the JSON settings, the extra field is
# either ignored (201 with ROLE_USER) or rejected (400). Both are safe; ROLE_ADMIN is not.
POST {{host}}/api/auth/register
Content-Type: application/json

{
  "username": "hacker_{{$timestamp}}",
  "password": "Hacker123!",
  "roles": ["ROLE_ADMIN"]
}

> {%
    client.test("19. client cannot choose its own role", function () {
        var safe = response.status === 400 ||
                   (response.status === 201 && response.body.roles.indexOf("ROLE_ADMIN") < 0);
        client.assert(safe, "SECURITY BUG: got status " + response.status + " body " + JSON.stringify(response.body));
    });
%}


###############################################################################
# 20. Expired token (manual test)
#   a. In application.properties set:  jwt.expiration-ms=60000   (1 minute)
#   b. Restart the application, run request 7 (user login).
#   c. Wait more than 1 minute, run request 8 -> expect 401.
#   d. Set jwt.expiration-ms back to 3600000 and restart.
###############################################################################
```

<div dir="rtl">

### 7.4 לראות מה יש בתוך הטוקן

העתיקו טוקן מתשובת הלוגין והדביקו ב‑[jwt.io](https://jwt.io). רואים את ה‑Header ואת ה‑Payload בלי שום סוד – זו ההוכחה שה‑Payload **לא מוצפן**.
> מותר לעשות את זה עם טוקנים של השיעור. טוקן אמיתי ממערכת אמיתית לא מדביקים באתר חיצוני.

---

## 8. טעויות נפוצות ופתרונן

| תסמין | הסיבה | הפתרון |
|---|---|---|
| ‎`syntax error at or near "user"`‎ | ‎`user`‎ מילה שמורה ב‑PostgreSQL | ‎`@Table(name = "users")`‎ |
| ‎`StackOverflowError`‎ ב‑‎`hashCode`‎ / ‎`toString`‎ | ‎`@Data`‎ על Entities עם יחס דו‑כיווני | ‎`@Getter @Setter`‎ + ‎`onlyExplicitlyIncluded`‎ |
| JSON אינסופי / ‎`Infinite recursion`‎ | החזרת Entity מה‑Controller | ‏DTO + ‎`@JsonIgnore`‎ על הצד ההפוך |
| ‎`LazyInitializationException`‎ | גישה ל‑‎`roles`‎ מחוץ לטרנזקציה | ‎`@EntityGraph`‎ או ‎`@Transactional`‎ ב‑Service |
| ‏403 במקום 401 כשאין טוקן | לא הוגדר ‎`authenticationEntryPoint`‎ | להגדיר כמו ב‑‎`SecurityConfig`‎ |
| שגיאה בעלייה: ‎`ROLE_ADMIN should not start with ROLE_ since ROLE_ is automatically prepended...`‎ | ‎`hasRole("ROLE_ADMIN")`‎ | ‎`hasRole("ADMIN")`‎ – הקידומת מתווספת לבד |
| כל בקשה של admin מקבלת 403 | התפקיד נשמר ב‑DB בשם ‎`ADMIN`‎ בלי ‎`ROLE_`‎ | שם התפקיד ב‑DB חייב להיות ‎`ROLE_ADMIN`‎ |
| ‎`WeakKeyException`‎ בעליית השרת | ‎`jwt.secret`‎ קצר מ‑256 ביט | ‎`openssl rand -base64 32`‎ |
| לוגין נכשל (401) עם סיסמה נכונה | הסיסמה הוכנסה ל‑DB ידנית כטקסט גלוי | ליצור משתמשים רק דרך הקוד (‎`passwordEncoder.encode`‎) |
| ‎`cannot find symbol: getUsername()`‎ | ‏Lombok לא רץ | ‏Enable annotation processing ב‑IntelliJ + ההגדרה ב‑‎`pom.xml`‎ |
| ה‑Filter רשום פעמיים: בשרשרת של Spring Security וגם ישירות ב‑Tomcat | ‎`JwtAuthFilter`‎ סומן ‎`@Component`‎ | ליצור אותו עם ‎`new`‎ ב‑‎`SecurityConfig`‎ |

---

## 9. נקודות אבטחה לדיון בכיתה

1. **HTTPS חובה.** טוקן שעובר ב‑HTTP רגיל נגנב בהאזנה לרשת – ומי שמחזיק אותו הוא המשתמש.
2. **ה‑Payload גלוי.** לא שמים בו מידע רגיש.
3. **הסוד מחוץ לקוד.** משתנה סביבה או Vault, לא Git. סוד שדלף = כל אחד מנפיק טוקן של admin.
4. **אין Logout אמיתי ב‑JWT טהור.** הטוקן תקף עד ה‑‎`exp`‎. דרכים להתמודד: תוקף קצר + ‏Refresh Token, רשימה שחורה של טוקנים, או מספר גרסה למשתמש. בפרויקט שלנו – משתמש שנחסם (‎`enabled=false`‎) נחסם מיד, כי ה‑Filter בודק מול ה‑DB.
5. **איפה הלקוח שומר את הטוקן:** ‎`localStorage`‎ חשוף ל‑XSS. ‏Cookie מסוג HttpOnly מוגן מ‑XSS, אבל אז CSRF חוזר להיות רלוונטי.
6. **הודעת שגיאה אחידה בלוגין** – לא לגלות אם שם המשתמש קיים.
7. **הגבלת ניסיונות לוגין (Rate Limiting)** – לא מומש בשיעור; בלעדיו אפשר לנחש סיסמאות בלי הגבלה.
8. **הלקוח לא בוחר תפקיד.** תפקידים נקבעים רק בצד השרת.

---

## 10. תרגילים 

1. **קל:** הוסיפו ‎`GET /api/admin/stats`‎ שמחזיר את מספר המשתמשים. ודאו ב‑‎`.http`‎ ש‑user מקבל 403.
2. **קל:** שנו את תוקף הטוקן ל‑60 שניות ובדקו ידנית שאחרי דקה מתקבל 401 (בדיקה 20).
3. **בינוני:** ‎`PUT /api/admin/users/{id}/roles`‎ – מנהל מוסיף תפקיד למשתמש. הוסיפו בדיקות ‎.http‎ שמוכיחות שה‑user קיבל גישה **בלי** לוגין מחדש. למה זה עובד?
4. **בינוני:** ‎`PUT /api/user/password`‎ – החלפת סיסמה; חובה לשלוח את הסיסמה הישנה.
5. **בינוני:** חסימת משתמש (‎`enabled=false`‎) ובדיקה שהטוקן שלו מפסיק לעבוד מיד.
6. **מתקדם:** ‎`@EnableMethodSecurity`‎ + ‎`@PreAuthorize("hasRole('ADMIN')")`‎ על מתודה ב‑Service. מה ההבדל מחוק ב‑‎`SecurityConfig`‎?
7. **מתקדם:** ‏Refresh Token – טוקן גישה ל‑15 דקות וטוקן חידוש ל‑7 ימים.

---

## 11. סיכום – רשימת בדיקה

- [ ] סיסמאות נשמרות כ‑Hash של BCrypt בלבד
- [ ] הטבלה נקראת ‎`users`‎, לא ‎`user`‎
- [ ] יחס רבים‑לרבים עם צד בעלים (‎`@JoinTable`‎) וצד הפוך (‎`mappedBy`‎)
- [ ] אין ‎`@Data`‎ על Entities
- [ ] ה‑API מחזיר DTOs בלבד – בלי סיסמה, בלי לולאה
- [ ] ‎`DataInitializer`‎ יוצר admin ו‑user רק אם חסרים
- [ ] ‎`SecurityConfig`‎: ‏Stateless, ‏JWT Filter, חוקים לפי תפקיד, ‏401 ו‑403 נכונים
- [ ] סוד ה‑JWT באורך 256 ביט לפחות ומחוץ לקוד בייצור
- [ ] כל 19 הבדיקות בקובץ ה‑‎`.http`‎ ירוקות

</div>
