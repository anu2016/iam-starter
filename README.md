# iam-starter: Keycloak + Spring Boot access management, step by step

A small project for learning how access tokens are **issued** (Keycloak) and **checked** (a Spring Boot API).
Local development only. Every password and secret here is a demo value.

```
 You / curl ──(1) login or client credentials──▶  Keycloak (port 8081)  ──▶ JWT access token
      │                                                                          │
      └─────────(2) call API with  Authorization: Bearer <token>  ───────────▶ Spring Boot API (port 8080)
                                                    checks signature, issuer, audience, expiry, then roles
```

## What you need

- Docker (with Compose)
- Java 17 or newer, and Maven
- A terminal on macOS, Linux, or WSL. `python3` is used to read JSON (no `jq` needed).

## 1. Start Keycloak

```bash
docker compose up -d
```

Wait about 30 seconds the first time, then open http://localhost:8081 and sign in to the admin console with `admin` / `admin`.
Choose the **demo** realm (top-left). It already has users, roles and two clients, loaded from `keycloak/demo-realm.json`.

| User  | Password | Role   | Can do                          |
| ----- | -------- | ------ | ------------------------------- |
| alice | alice123 | reader | list reports                    |
| bob   | bob123   | editor | list and create reports         |
| carol | carol123 | admin  | list, create and delete reports |

Clients: `demo-app` (a user logging in) and `demo-service` (a backend service, secret `demo-service-secret`).

## 2. Start the API

```bash
mvn spring-boot:run
```

Run the tests any time (they don't need Keycloak):

```bash
mvn test
```

## 3. Get tokens

```bash
get_token() {
  curl -s -X POST http://localhost:8081/realms/demo/protocol/openid-connect/token \
    -d grant_type=password -d client_id=demo-app \
    -d username="$1" -d password="$2" \
  | python3 -c "import sys,json; print(json.load(sys.stdin)['access_token'])"
}

ALICE=$(get_token alice alice123)
BOB=$(get_token bob bob123)
CAROL=$(get_token carol carol123)
```

> The password grant used here is only for learning. It is discouraged for real systems.
> In week 2 or 3, replace it with authorization code with PKCE.

A service (no user) uses the client credentials grant:

```bash
SERVICE=$(curl -s -X POST http://localhost:8081/realms/demo/protocol/openid-connect/token \
  -d grant_type=client_credentials -d client_id=demo-service -d client_secret=demo-service-secret \
  | python3 -c "import sys,json; print(json.load(sys.stdin)['access_token'])")
```

## 4. Look inside a token

```bash
decode() { python3 -c "import sys,json,base64; p=sys.stdin.read().strip().split('.')[1]; p+='='*(-len(p)%4); print(json.dumps(json.loads(base64.urlsafe_b64decode(p)),indent=2))"; }
echo "$ALICE" | decode
```

Find `iss` (issuer), `aud` (audience, should include `demo-api`), `exp` (expiry), and `roles`.
The token is signed, not encrypted. Anyone with it can read this.

## 5. Call the API and predict each answer first

```bash
curl -i http://localhost:8080/api/public
curl -i http://localhost:8080/api/reports                              # no token
curl -i -H "Authorization: Bearer $ALICE" http://localhost:8080/api/me
curl -i -H "Authorization: Bearer $ALICE" http://localhost:8080/api/reports
curl -i -X POST   -H "Authorization: Bearer $ALICE" http://localhost:8080/api/reports
curl -i -X POST   -H "Authorization: Bearer $BOB"   http://localhost:8080/api/reports
curl -i -X DELETE -H "Authorization: Bearer $BOB"   http://localhost:8080/api/reports/1
curl -i -X DELETE -H "Authorization: Bearer $CAROL" http://localhost:8080/api/reports/1
curl -i -H "Authorization: Bearer $SERVICE" http://localhost:8080/api/reports
```

| Call                                  | Expected      | Why                                             |
| ------------------------------------- | ------------- | ----------------------------------------------- |
| `/api/public`, no token               | 200           | open endpoint                                   |
| `/api/reports`, no token              | **401**       | not authenticated                               |
| `/api/me` as alice                    | 200           | any valid token                                 |
| list reports as alice                 | 200           | reader may read                                 |
| create report as alice                | **403**       | authenticated, but reader may not create        |
| create report as bob                  | 201           | editor may create                               |
| delete report as bob                  | **403**       | only admin may delete                           |
| delete report as carol                | 200           | admin                                           |
| list reports with the service token   | 200           | the service account has the reader role         |

## 6. Break things on purpose (this is where you learn)

1. In the Keycloak admin console, give alice the `editor` role. Call create again with the **old** token. Then fetch a **new** token and try again. Why did the first one still fail?
2. Wait for the token to expire (default is a few minutes) and call again. Which status do you get, and why?
3. Change `app.audience` in `application.yml` to something else and restart. What happens to every token?
4. Change `app.issuer` to a wrong value. Which check fails?

## Where each idea lives in the code

| Idea                                   | File                       |
| -------------------------------------- | -------------------------- |
| API only checks tokens, never issues   | `SecurityConfig.java`      |
| Signature checked with Keycloak's keys | `SecurityConfig.java`      |
| Issuer, expiry checks                  | `SecurityConfig.java`      |
| Audience check                         | `AudienceValidator.java`   |
| Role claim to Spring authority         | `RolesClaimConverter.java` |
| Role rules per endpoint                | `ApiController.java`       |
| Access rules tested without Keycloak   | `ApiSecurityTest.java`     |

## Next steps (from the plan)

- Weeks 2 to 3: replace the password grant with authorization code and PKCE; add your own roles and endpoints.
- Weeks 4 to 6: fine-grained permissions, an access-request and approval workflow, an audit log of who was granted or denied what.
- Week 7: dependency scanning, secrets kept out of code, a software bill of materials, a threat model, and publish.

## Stop and reset

```bash
docker compose down        # stop Keycloak (users and roles reload from the JSON on next start)
```
