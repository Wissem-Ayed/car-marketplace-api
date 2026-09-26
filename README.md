# Car Marketplace API

[![CI](https://github.com/Wissem-Ayed/car-marketplace-api/actions/workflows/ci.yml/badge.svg)](https://github.com/Wissem-Ayed/car-marketplace-api/actions/workflows/ci.yml)
![Java 21](https://img.shields.io/badge/Java-21-orange)
![Spring Boot 4](https://img.shields.io/badge/Spring%20Boot-4.1-brightgreen)
![MongoDB 8](https://img.shields.io/badge/MongoDB-8.0-green)

REST API for a car marketplace targeting the Tunisian market: sellers publish car listings, buyers search them with
rich filters. Every listing is validated against a curated **brand → model → generation catalog**, so a seller can't
publish a car that doesn't exist.

## Highlights

- **Industry-standard authentication** – OpenID Connect with Keycloak; the API is an OAuth 2 resource server that
  validates signed JWTs and never handles passwords. Sellers own their listings, administrators can moderate any.
- **Catalog-validated listings** – brand, model and generation come from reference data; the production years of the
  generation are checked, and the generation is inferred from the year when it's unambiguous.
- **Rich domain model** – immutable records, value objects (`Vehicle`, `Engine`, `History`, `Price`) and business
  rules enforced in the domain, not scattered across controllers.
- **Secure photo pipeline** – up to 10 photos per listing, checked by their real content, stripped of metadata
  (including the GPS location phones embed), resized to three sizes and served straight from S3-compatible storage.
- **Listing lifecycle** – `AVAILABLE ⇄ RESERVED → SOLD`, with sold listings locked against edits.
- **Optimistic locking** – concurrent edits are detected with `@Version` instead of silently overwriting each other.
- **Indexed search** – filters on catalog ids, price, year, mileage, fuel, transmission, equipment…, paginated and
  sortable, backed by MongoDB indexes.
- **Consistent errors** – every error follows [RFC 9457](https://www.rfc-editor.org/rfc/rfc9457) Problem Details,
  including the list of invalid fields on validation errors.
- **Tested at every level** – 115 tests: domain unit tests, controller slice tests, repository tests and end-to-end
  tests against real MongoDB, SeaweedFS and Keycloak containers started by Testcontainers.

## Tech stack

| Area          | Choice                                                         |
|---------------|----------------------------------------------------------------|
| Language      | Java 21 (records, switch expressions, text blocks)             |
| Framework     | Spring Boot 4.1 (Spring MVC, Bean Validation, Actuator)        |
| Database      | MongoDB 8.0 with Spring Data MongoDB                           |
| Security      | Spring Security OAuth 2 Resource Server, Keycloak 26 (OIDC)    |
| File storage  | S3 API (AWS SDK v2), SeaweedFS locally                         |
| Images        | Thumbnailator, TwelveMonkeys ImageIO (WebP)                    |
| API docs      | OpenAPI 3 / Swagger UI (springdoc)                             |
| Tests         | JUnit 5, AssertJ, Mockito, MockMvcTester, Testcontainers       |
| Local runtime | Docker Compose, started automatically by Spring Boot           |
| CI            | GitHub Actions                                                 |

## Architecture

The code is organised **by feature**, and each feature is split into layers with a strict dependency direction:

```
com.carmarketplace
├── car/                  car listings
│   ├── api/              REST controllers and request DTOs
│   ├── application/      use cases (CarService)
│   ├── domain/           Car aggregate, value objects, business rules
│   └── infrastructure/   MongoDB repository and dynamic search queries
├── catalog/              reference data: brands, models, generations
│   ├── api/ application/ domain/ infrastructure/
├── common/               shared error handling and exception types
└── config/               MongoDB auditing
```

```mermaid
flowchart LR
    Client -->|logs in| KC[Keycloak]
    Client -->|HTTP + JWT| API[api]
    API --> APP[application]
    APP --> DOMAIN[domain]
    APP --> INFRA[infrastructure]
    INFRA --> DOMAIN
    INFRA --> DB[(MongoDB)]
    INFRA --> S3[(S3 storage)]
    Client -->|downloads photos| S3
    APP -->|validates vehicle| CATALOG[catalog]
```

- **`api`** knows HTTP: it validates the request shape and maps DTOs to application objects.
- **`application`** orchestrates a use case: load, apply domain behaviour, save.
- **`domain`** holds the rules and knows nothing about HTTP.
- **`infrastructure`** knows MongoDB.
- The `car` feature depends on `catalog`, never the other way around.

## Domain model

A listing is a single MongoDB document built from value objects:

```json
{
  "id": "6ab5...",
  "vehicle": {
    "brand": { "id": "mercedes-benz", "name": "Mercedes-Benz" },
    "model": { "id": "mercedes-benz-cla", "name": "CLA" },
    "generation": { "id": "c118", "name": "C118, X118" },
    "trim": "250e AMG Line",
    "year": 2022,
    "bodyType": "SEDAN",
    "doors": 4,
    "seats": 5,
    "color": "GREY"
  },
  "engine": {
    "fuelType": "PLUG_IN_HYBRID",
    "transmission": "AUTOMATIC",
    "powerHp": 218,
    "fiscalPower": 8,
    "cylinders": 4,
    "displacementCc": 1332
  },
  "history": {
    "mileageKm": 28000,
    "mileageCertified": true,
    "condition": "LIKE_NEW",
    "origin": "IMPORTED",
    "registeredInTunisia": false,
    "previousOwners": 1
  },
  "price": { "amount": 185000.000, "currency": "TND", "negotiable": true },
  "equipment": ["ABS", "ESP", "APPLE_CARPLAY_ANDROID_AUTO", "PANORAMIC_ROOF"],
  "description": "Burmester sound system, Alcantara interior",
  "status": "AVAILABLE",
  "version": 0,
  "createdAt": "2026-09-25T10:00:00Z",
  "updatedAt": "2026-09-25T10:00:00Z"
}
```

### Business rules

| Rule                                                                  | Enforced in        | HTTP status |
|-----------------------------------------------------------------------|--------------------|-------------|
| Brand, model and generation must exist and belong together            | `CatalogService`   | 422         |
| The year must fall within the generation's production years           | `CatalogService`   | 422         |
| An electric car has no cylinders or engine displacement               | `Engine`           | 422         |
| A new car has at most 100 km and no previous owners                   | `History`          | 422         |
| A price is positive, in TND, with at most 3 decimals (millimes)       | `Price`            | 422         |
| A sold car can't be edited or put back on sale                        | `Car`, `CarStatus` | 409         |
| An edit based on an outdated version is rejected                      | `@Version`         | 409         |

## Design decisions

**Catalog in our own database, not an external API at runtime.**
Free car APIs are US-centric or outdated, and scraping sites like auto-data.net breaks their terms of use. The catalog
is a curated JSON file (`src/main/resources/catalog/catalog.json`: 21 brands, 111 models, 238 generations) loaded into
MongoDB at startup. Stable slug ids (`mercedes-benz-cla`) make the load idempotent: edit the file, restart, and the
database is in sync. A licensed dataset could later be plugged in through an importer without touching the rest of
the application.

**Catalog down to the generation, not the engine.**
A reliable engine catalog requires paid data. Engine details are structured fields filled in by the seller, plus a
free-text trim.

**Listings store a snapshot of catalog names.**
MongoDB has no joins. Each listing stores `{ id, name }` for its brand, model and generation, so listing pages need a
single query. Catalog names almost never change, which makes this denormalisation safe.

**Filters use ids and enums, not free text.**
Exact matches on indexed fields replace slow case-insensitive regular expressions, and codes such as
`APPLE_CARPLAY_ANDROID_AUTO` keep the API language-neutral: the frontend translates them.

**Photos in object storage, metadata in MongoDB.**
Image bytes never go into MongoDB documents (16 MB limit, heavy queries and backups) nor through the API when they are
viewed. They are stored with the S3 API, which works unchanged with SeaweedFS locally, AWS S3, Cloudflare R2 or
Backblaze B2, and browsers download them directly from the storage. The car document only keeps each photo's id,
size and order; URLs are built at response time from a configured base URL, so moving to a CDN is a configuration change.

**Every uploaded photo is untrusted.**
The format is detected from the file's first bytes, not its name or `Content-Type`. Dimensions are read from the header
and anything above 40 megapixels is rejected before decoding, to defeat decompression bombs. Photos are re-encoded as
JPEG, which applies the EXIF rotation and then discards all metadata, including the GPS position of the phone. Each
photo is stored in three sizes (400, 1024 and 1920 px) so search pages load small thumbnails. Storage keys use random
ids, never the uploaded file name.

**Two systems, no distributed transaction.**
Files are stored before the car is saved; if saving fails, the files just written are deleted. On deletion, the car is
updated first and files are removed afterwards, so the worst case is an orphaned file, never a broken image. Photo files
are immutable and cached for a year by browsers and CDNs.

**Authentication is delegated to an identity provider.**
Passwords, registration, email verification, account lockout and password reset live in Keycloak, a dedicated and battle-tested product.
The API is an OAuth 2 *resource server*: on every request it checks the JWT's signature (with Keycloak's published
keys), issuer, audience and expiry, then trusts the claims. It is stateless (no session, no cookie, no CSRF surface) and
stores no credentials. The code only speaks standard OpenID Connect, so Auth0, Okta or Cognito could replace Keycloak
by changing the issuer URL. Browsers and Swagger UI log in with the authorization code flow and PKCE.

**Ownership is a domain rule.**
The seller of a listing is always taken from the token (`sub` claim), never from the request body. Services load the
listing and call `car.ensureManageableBy(user)`: the seller or an `ADMIN` may manage it, anyone else gets `403`. The
domain receives a plain `CurrentUser`, not a Spring Security type.

**Immutable records for the domain.**
State changes return a new instance (`car.changeStatus(SOLD)`), which keeps the rules in one place and works
naturally with Spring Data's support for immutable entities, versioning and auditing.

## Getting started

### Prerequisites

- Java 21
- Docker (Docker Desktop on Windows/macOS)

### Run the application

```bash
./mvnw spring-boot:run
```

Spring Boot starts MongoDB, SeaweedFS, Keycloak and Mailpit from `compose.yaml` automatically, loads the catalog, creates the
photo bucket and serves the API on port **8050**. Keycloak takes about 40 seconds on its first start.

| URL                                           | What                         |
|-----------------------------------------------|------------------------------|
| http://localhost:8050/swagger-ui.html         | Interactive API documentation |
| http://localhost:8050/v3/api-docs             | OpenAPI specification         |
| http://localhost:8050/actuator/health         | Health check                  |
| http://localhost:8180                         | Keycloak (admin console: `admin` / `admin`) |
| http://localhost:8025                         | Mailpit inbox: every email sent by Keycloak (verification, password reset) |
| http://localhost:8888                         | SeaweedFS file browser (photos under `buckets/car-photos`) |

MongoDB is exposed on `localhost:27018` (`mongodb://root:secret@localhost:27018/?authSource=admin`), database
`car_marketplace`. SeaweedFS serves photos on `localhost:8333`: anyone can read a photo, only the API can write or delete.
Both keep their data in Docker volumes; `docker compose down -v` resets them.

### Demo accounts

The Keycloak realm is imported from `docker/keycloak/car-marketplace-realm.json` with three development accounts:

| Username  | Password           | Roles         |
|-----------|--------------------|---------------|
| `seller1` | `seller1-password` | USER          |
| `seller2` | `seller2-password` | USER          |
| `admin`   | `admin-password`   | USER, ADMIN   |

New accounts register from the Keycloak login page ("Register" link). They must confirm their email address before
logging in, and get the `USER` role. "Forgot password?" sends a reset link. Locally, no email leaves your machine:
Keycloak sends them to Mailpit, where you can open them and click the links at http://localhost:8025. Users manage their
profile, password and sessions in the account console:
http://localhost:8180/realms/car-marketplace/account

In Swagger UI, click **Authorize**, then **Authorize** again in the `keycloak` dialog and log in: every request then
carries the access token. From a terminal, get a token with the development client (password grant, never enable it
in production):

```bash
TOKEN=$(curl -s http://localhost:8180/realms/car-marketplace/protocol/openid-connect/token \
  -d grant_type=password -d client_id=car-marketplace-cli -d username=seller1 -d password=seller1-password \
  | sed -E 's/.*"access_token":"([^"]+)".*/\1/')

curl -H "Authorization: Bearer $TOKEN" http://localhost:8050/api/v1/me
```

### Run the tests

```bash
./mvnw verify
```

Docker must be running: repository and end-to-end tests start throwaway MongoDB, SeaweedFS and Keycloak containers
with Testcontainers.

## API

Base path: `/api/v1`

Browsing (`GET` on listings and reference data) is public. Every other endpoint requires an access token:
`Authorization: Bearer <token>`.

### Catalog

| Method | Path                          | Description                                 |
|--------|-------------------------------|---------------------------------------------|
| GET    | `/brands`                     | All brands, sorted by name                  |
| GET    | `/brands/{brandId}/models`    | Models of a brand, with their generations   |
| GET    | `/equipment`                  | Equipment codes grouped by category         |

### Cars

| Method | Path                    | Description                             | Success |
|--------|-------------------------|-----------------------------------------|---------|
| POST   | `/cars`                 | Publish a listing; you become the seller | 201 + `Location` |
| GET    | `/cars/{id}`            | Get a listing                           | 200     |
| GET    | `/cars`                 | Search listings (filters + pagination)  | 200     |
| PUT    | `/cars/{id}`            | Replace a listing's details (seller or admin) | 200 |
| PATCH  | `/cars/{id}/status`     | Change status (`AVAILABLE`, `RESERVED`, `SOLD`) | 200 |
| DELETE | `/cars/{id}`            | Delete a listing and its photos         | 204     |

### My account

| Method | Path          | Description                                   |
|--------|---------------|-----------------------------------------------|
| GET    | `/me`         | The logged-in user: id, name and roles        |
| GET    | `/me/cars`    | The logged-in user's listings, any status     |

### Photos

| Method | Path                               | Description                                    | Success |
|--------|------------------------------------|------------------------------------------------|---------|
| POST   | `/cars/{id}/photos`                | Upload photos (`multipart/form-data`, field `files`) | 201 |
| PUT    | `/cars/{id}/photos/order`          | Reorder photos; the first is the cover         | 200     |
| DELETE | `/cars/{id}/photos/{photoId}`      | Delete a photo and its files                   | 204     |

Rules: JPEG, PNG or WebP, at most 10 MB and 40 megapixels per file, at most 10 photos per listing. HEIC must be
converted by the client first.

```bash
curl -X POST http://localhost:8050/api/v1/cars/{id}/photos -F "files=@front.jpg" -F "files=@interior.jpg"
```

Every listing response includes its photos:

```json
"photos": [
  {
    "id": "3f2c9a1e-8b4d-4c1a-9f0e-2d6b7a5c1e90",
    "width": 1920,
    "height": 1080,
    "uploadedAt": "2026-09-25T10:00:00Z",
    "urls": {
      "thumbnail": "http://localhost:8333/car-photos/cars/6ab5.../3f2c9a1e-.../thumbnail.jpg",
      "medium": "http://localhost:8333/car-photos/cars/6ab5.../3f2c9a1e-.../medium.jpg",
      "large": "http://localhost:8333/car-photos/cars/6ab5.../3f2c9a1e-.../large.jpg"
    }
  }
]
```

### Creating a listing

The client sends catalog **ids**; the server resolves the names. `generationId` is optional when only one generation
of the model was produced that year.

```bash
curl -X POST http://localhost:8050/api/v1/cars \
  -H "Content-Type: application/json" \
  -d '{
    "vehicle": { "brandId": "peugeot", "modelId": "peugeot-208", "year": 2021,
                 "bodyType": "HATCHBACK", "doors": 5, "seats": 5, "color": "WHITE", "trim": "Allure" },
    "engine":  { "fuelType": "PETROL", "transmission": "MANUAL", "powerHp": 100, "fiscalPower": 5,
                 "cylinders": 3, "displacementCc": 1199 },
    "history": { "mileageKm": 45000, "mileageCertified": true, "condition": "VERY_GOOD",
                 "origin": "LOCAL", "registeredInTunisia": true, "previousOwners": 1 },
    "price":   { "amount": 62000, "negotiable": true },
    "equipment": ["ABS", "ESP", "APPLE_CARPLAY_ANDROID_AUTO", "REAR_PARKING_SENSORS"],
    "description": "First hand, full service history."
  }'
```

### Searching

| Parameter                   | Example                           |
|-----------------------------|-----------------------------------|
| `brandId`, `modelId`, `generationId` | `brandId=peugeot`        |
| `minPrice`, `maxPrice`      | `minPrice=30000&maxPrice=80000`   |
| `minYear`, `maxYear`        | `minYear=2018`                    |
| `maxMileageKm`              | `maxMileageKm=100000`             |
| `fuelType`, `transmission`, `bodyType`, `condition`, `status` | `fuelType=DIESEL` |
| `equipment` (all required)  | `equipment=ABS,REAR_CAMERA`       |
| `sellerId`                  | `sellerId=5f0c1a2e-0000-4000-8000-000000000001` |
| `page`, `size` (max 100), `sort` | `sort=price.amount,asc`      |

```bash
curl "http://localhost:8050/api/v1/cars?brandId=peugeot&maxPrice=80000&equipment=ABS&sort=price.amount,asc&size=10"
```

Results are paginated, newest first by default:

```json
{
  "content": [ { "id": "...", "vehicle": { ... }, "price": { ... } } ],
  "page": { "size": 10, "number": 0, "totalElements": 12, "totalPages": 2 }
}
```

### Errors

All errors use the Problem Details format:

```json
{
  "type": "about:blank",
  "title": "Business rule violated",
  "status": 422,
  "detail": "Mercedes-Benz CLA C118, X118 was produced in 2019–present, not in 2015",
  "instance": "/api/v1/cars"
}
```

| Status | Meaning                                                                    |
|--------|----------------------------------------------------------------------------|
| 400    | Malformed request or invalid fields (listed in `errors`)                   |
| 401    | Missing, invalid or expired access token (`WWW-Authenticate: Bearer`)      |
| 403    | Logged in, but not the seller of the listing nor an administrator          |
| 404    | Unknown car or brand                                                       |
| 409    | Illegal status change, sold car edited, or concurrent modification        |
| 413    | Uploaded file larger than 10 MB                                            |
| 422    | Business rule violated (catalog mismatch, inconsistent engine, invalid photo, …) |

## Testing strategy

| Level        | Scope                                             | Tooling                           |
|--------------|---------------------------------------------------|-----------------------------------|
| Unit         | Domain rules, catalog validation, image pipeline  | JUnit 5, AssertJ, Mockito         |
| Web slice    | Status codes, validation, JSON, error format      | `@WebMvcTest`, `MockMvcTester`    |
| Persistence  | Every filter, storage format, versioning, indexes | `@DataMongoTest`, Testcontainers  |
| Security     | 401/403 rules, ownership, roles                   | `spring-security-test` JWTs       |
| End-to-end   | Real catalog, lifecycle, search, photo upload and public access | `@SpringBootTest`, Testcontainers |
| Identity     | Real Keycloak logins, roles, forged-token rejection, password-reset email | `@SpringBootTest`, Keycloak and Mailpit containers |

## Roadmap

- Seller contact details and messaging between buyers and sellers
- Direct-to-storage uploads with presigned URLs, for very high traffic
- Location (governorate) filter
- Keyset pagination for very deep result pages
