# 🧪 CampusLab

Plataforma unificada para la reserva de laboratorios y equipos académicos en una red de 20 laboratorios de instituciones de educación superior.

## 📋 Contexto

Actualmente los laboratorios se reservan por correo y el stock de insumos se lleva en planillas, generando choques de horario, equipos no devueltos y nula visibilidad de ocupación. CampusLab centraliza:

- Reserva de laboratorios y equipos vía web
- Administración de stock de insumos
- Notificación a estudiantes (email/push) y técnicos (ticket de preparación)
- Panel de operaciones en tiempo real (reservas por hora, tiempo de ciclo, equipos ocupados)
- Auditoría de eventos académicos (quién solicitó, aprobó, entregó o recibió de vuelta un equipo)

## 🏗️ Arquitectura

Sistema de **microservicios** con frontend Angular, backend Spring Boot, autenticación federada vía Azure AD, mensajería asíncrona con RabbitMQ y streaming de eventos con Kafka.

```
                    ┌─────────────┐
                    │   Angular   │
                    │  (MSAL)     │
                    └──────┬──────┘
                           │ JWT (Bearer)
                           ▼
                  ┌────────────────┐
                  │ AWS API Gateway│
                  │ (JWT Authorizer)│
                  └────────┬───────┘
                           ▼
                  ┌────────────────┐
                  │ ms-campuslab-bff│
                  │ (Spring Security)│
                  └────────┬───────┘
                           ▼
        ┌──────────────────┼──────────────────┐
        ▼                  ▼                  ▼
┌───────────────┐  ┌───────────────┐  ┌───────────────┐
│ ms-bookings    │  │ ms-catalog     │  │ ms-notify      │
│ (PostgreSQL)   │  │ (PostgreSQL)   │  │ (sin DB)       │
└───────┬───────┘  └───────────────┘  └───────┬───────┘
        │ eventos                              │ consume
        ▼                                       ▼
┌───────────────┐                      ┌───────────────┐
│ Kafka          │                      │ RabbitMQ       │
│ (bookings.events)│                    │ (colas cmd.*)  │
└───────┬───────┘                      └───────────────┘
        ▼
┌───────────────┐  ┌───────────────┐
│ ms-audit       │  │ ms-report      │
│ (PostgreSQL)   │  │ (PostgreSQL)   │
└───────────────┘  └───────────────┘
```

### Flujo de seguridad

```
JWT (Azure AD) → API Gateway → ms-campuslab-bff → microservicio de dominio
```

- **App Registration** "CampusLab" en Azure AD: `clientId`, `redirectUri`, `authority = https://login.microsoftonline.com/<TENANT_ID>/`
- **MSAL Angular**: protege rutas y adjunta `Bearer <access_token>` a cada request
- **AWS API Gateway** (HTTP API) con JWT Authorizer: `issuer = https://login.microsoftonline.com/<TENANT_ID>/v2.0`, `audience = api://<API_CLIENT_ID>`
- **Spring Security** en cada servicio: valida el JWT vía `security.oauth2.resourceserver.jwt.issuer-uri` y autoriza según rol

### Roles

| Rol | Responsabilidad |
|---|---|
| Admin | Administra el catálogo de labs/equipos y ve KPIs de ocupación |
| Técnico (Operador) | Aprueba reservas, prepara la sala y registra devoluciones |
| Estudiante (Cliente) | Solicita y sigue sus reservas |
| Auditor | Consulta el timeline. Solo lectura |

## 📦 Repositorios del proyecto

| Repositorio | Descripción |
|---|---|
| `frontend-campuslab` | Angular + MSAL |
| `ms-campuslab-bff` | Backend For Frontend (Spring Boot + Spring Security) |
| `ms-campuslab-bookings` | Microservicio de reservas (Spring Boot + PostgreSQL) |
| `ms-campuslab-catalog` | Microservicio de catálogo de labs/equipos/insumos (Spring Boot + PostgreSQL) |
| `ms-campuslab-notify` | Consumidor RabbitMQ para notificaciones (Spring Boot) |
| `ms-campuslab-report` | Consumidor Kafka para reportería/KPIs (Spring Boot + PostgreSQL) |
| `ms-campuslab-audit` | Consumidor Kafka para auditoría/timeline (Spring Boot + PostgreSQL) |
| `infra` | `apps/compose.yml` · `mq/compose.yml` · `kafka/compose.yml` |
| `docs` | Documentación del proyecto |

> Se optó por **multi-repo** (un repositorio por microservicio) en línea con la arquitectura de microservicios del caso, permitiendo pipelines y despliegues independientes por servicio.

## 🗄️ Base de Datos

Se utiliza **PostgreSQL** como motor relacional para los microservicios `bookings`, `catalog`, `audit` y `report`. `notify` no requiere base de datos (solo consume mensajes).

## 🚀 Quick Start (por repositorio de backend)

### Requisitos Previos
- Java 21 (JDK Temurin)
- Maven 3.8+
- Docker & Docker Compose
- PostgreSQL 16+
- Acceso a RabbitMQ y Kafka (levantados desde `infra/`)

### Instalación

```bash
git clone <url-del-repositorio>
cd <nombre-microservicio>

mvn clean compile
mvn test
mvn package
```

### Ejecutar Localmente

```bash
# Con Maven
mvn spring-boot:run

# Con Docker
docker build -t <nombre-microservicio>:latest .
docker run -p <puerto>:<puerto> <nombre-microservicio>:latest
```

---

## 📋 Estrategia de Ramificación: GitFlow

Cada repositorio del proyecto (BFF, cada microservicio, frontend) sigue la misma estrategia GitFlow.

### ¿Por qué GitFlow?

- **Control y estabilidad**: `main` contiene siempre código estable y desplegable; `develop` integra los avances antes de llegar a producción.
- **Trabajo en equipo sin bloqueos**: con múltiples microservicios y 2 integrantes, cada quien puede avanzar en `feature/*` de su servicio sin interferir con el resto.
- **Corrección de errores urgente**: `hotfix/*` permite reparar bugs críticos en producción sin interrumpir el desarrollo en curso en `develop`.
- **Releases controlados**: al ser un sistema usado por 20 laboratorios en producción, GitFlow permite preparar y validar versiones mediante `release/*` antes de liberarlas.
- **Trazabilidad para auditoría**: dado que el propio sistema audita eventos académicos, es coherente mantener también un historial de cambios de código claro y trazable.

### Estructura de Ramas

```
main (producción)
└─ release/x.y.z
    └─ hotfix/<nombre-descriptivo>

develop (integración)
└─ feature/<nombre-descriptivo>
└─ bugfix/<nombre-descriptivo>
```

| Rama | Propósito |
|---|---|
| `main` | Código en producción, siempre estable |
| `develop` | Integración de features antes de pasar a producción |
| `feature/<nombre>` | Desarrollo de nuevas funcionalidades |
| `bugfix/<nombre>` | Corrección de errores detectados en desarrollo |
| `release/<version>` | Preparación y estabilización de una nueva versión |
| `hotfix/<nombre>` | Corrección urgente directamente sobre producción |

### Workflow Típico

**Crear nueva funcionalidad:**
```bash
git checkout develop
git pull origin develop
git checkout -b feature/nombre-descriptivo
# ... cambios ...
git commit -m "feat: descripcion del cambio"
git push -u origin feature/nombre-descriptivo
# Pull Request -> develop
```

**Hotfix de emergencia:**
```bash
git checkout main
git pull origin main
git checkout -b hotfix/nombre-descriptivo
# ... corrección ...
git commit -m "fix: descripcion de la correccion"
git push -u origin hotfix/nombre-descriptivo
# Pull Request -> main Y develop
```

---

## 📐 Guía de Buenas Prácticas

### Naming de ramas
- `feature/<nombre-descriptivo>` — ej: `feature/booking-status-transition`
- `bugfix/<nombre-descriptivo>`
- `hotfix/<nombre-descriptivo>` — ej: `hotfix/fix-jwt-audience-validation`
- `release/<version>` — ej: `release/1.0.0`

### Convención de mensajes de commit (Conventional Commits)

| Prefijo | Uso |
|---|---|
| `feat:` | Nueva funcionalidad |
| `fix:` | Corrección de bug |
| `docs:` | Cambios en documentación |
| `refactor:` | Refactorización sin cambio de comportamiento |
| `test:` | Agregar o modificar tests |
| `chore:` | Tareas de mantenimiento (configs, dependencias) |

Ejemplo: `feat: agregar endpoint de creacion de reserva`

### Estructura de carpetas (por microservicio)

```
src/
├── main/
│   ├── java/org/campuslab/<dominio>/
│   │   ├── controller/
│   │   ├── service/
│   │   ├── repository/
│   │   ├── entity/
│   │   ├── dto/
│   │   ├── messaging/       # productores/consumidores RabbitMQ o Kafka
│   │   └── security/
│   └── resources/
│       └── application.yml
└── test/
    └── java/org/campuslab/<dominio>/
```

### Control de versiones (Semantic Versioning)

- **MAJOR** (1.x.x → 2.0.0): cambios incompatibles en la API
- **MINOR** (1.0.x → 1.1.0): nueva funcionalidad compatible
- **PATCH** (1.0.0 → 1.0.1): corrección de bugs

## 🔑 Pull Requests y Revisión de Código

- Todo cambio hacia `develop` o `main` pasa por **Pull Request**, nunca push directo.
- Cada PR describe: qué cambia, por qué y cómo probarlo.
- El pipeline CI/CD (build + tests) debe pasar en verde antes de mergear.

## 👥 Equipo

- *(completar con integrantes del equipo)*

---

**Última actualización:** *(actualizar en cada cambio relevante)*
# ms-campuslab-notify

Microservicio **consumidor de comandos** de CampusLab: recibe por RabbitMQ lo que publica `ms-campuslab-bookings` y ejecuta el trabajo
(email al estudiante, ticket de preparación para el técnico, PDF de vale o acta). Expone solo un endpoint interno de estado de las DLQ.

## Colas y consumidores

| Caso de uso | Cola | Consumidor (`@RabbitListener`, ACK manual) | Servicio de negocio | DLQ |
|---|---|---|---|---|
| Aviso al estudiante | `q.cmd.email` | `EmailConsumer` | `EmailNotificationService` | `q.cmd.email.dlq` |
| Preparación de sala / equipo | `q.cmd.prep` | `PrepTicketConsumer` | `PrepTicketService` | `q.cmd.prep.dlq` |
| Vale de retiro / acta de devolución | `q.cmd.voucher` | `VoucherConsumer` | `PdfGeneratorService` | `q.cmd.voucher.dlq` |

Los nombres de exchanges, colas y routing keys viven solo en `application.yml` (`campuslab.messaging.*`) y el bloque debe ser idéntico en bookings.
Cuándo se usa cada exchange: `cmd.direct` para comandos punto a punto (email, preparación) y `cmd.topic` para el voucher, que tiene subtipos
(`voucher.retiro`, `voucher.devolucion`); el detalle está en el README de `ms-campuslab-mq-admin`.

## Confirmación (ACK) y errores

Cada mensaje termina siempre en una confirmación explícita al broker (política en `AbstractCommandConsumer`):

| Situación | Decisión | Destino |
|---|---|---|
| Procesado correctamente | `basicAck` | se elimina de la cola |
| `eventId` ya procesado (re-entrega) | `basicAck`, sin reprocesar | se elimina |
| Payload inválido o error de negocio | `basicNack` sin reencolar, sin reintentos | DLX → DLQ |
| Falla recuperable (timeout, servicio externo) | 3 intentos con backoff exponencial (1 s, 2 s); si se agotan, `basicNack` | DLX → DLQ |
| Apagado durante un reintento | `basicNack` con reencolado | vuelve a la cola |
| JSON ilegible | lo rechaza el contenedor de Spring | DLX → DLQ |

Un mensaje también llega a la DLQ **sin que el consumidor lo toque** cuando nadie lo consume dentro del TTL de la cola (30 s, motivo `expired`)
o cuando la cola supera su largo máximo (1000, motivo `maxlen`). Las DLQ conservan los mensajes 24 h.

## Qué se hace con los mensajes de las DLQ

`DlqMonitor` (paquete `messaging.dlq`) revisa las tres DLQ cada 30 s y deja en el log, **una sola vez por mensaje**, lo necesario para analizar la falla.
Los **inspecciona sin consumirlos**: cada mensaje leído se devuelve a la cola (`basicNack` con requeue). Así siguen disponibles para analizarlos o reprocesarlos
desde la Management UI y el endpoint de estado los sigue contando. Un listener que los confirmara los borraría y se perdería esa evidencia.

Línea de log (nivel `WARN`, una por mensaje):

```
[DLQ] mensaje fallido dlq=q.cmd.email.dlq origen=q.cmd.email motivo=rejected veces=1 exchange=cmd.direct routingKey=[email.send]
      eventId=e5dcab81-... type=email.send traceId=f8ed3e86-... encoladoEn=2026-10-27T03:04:23Z payload={"bookingId":1,"to":"esto-no-es-un-email"}
[DLQ] q.cmd.email.dlq: 1 mensaje(s) nuevo(s) registrado(s); 4 en total esperan análisis          (nivel INFO)
```

| Campo | Qué dice |
|---|---|
| `origen` | cola principal de la que venía |
| `motivo` | `rejected` (lo rechazó el consumidor), `expired` (venció el TTL) o `maxlen` (cola llena) |
| `veces` | cuántas veces murió por ese motivo |
| `exchange` / `routingKey` | por dónde se publicó originalmente |
| `eventId` / `type` / `traceId` | identifican el mensaje (`?` si el JSON era ilegible) |
| `encoladoEn` | cuándo llegó a la DLQ |
| `payload` | contenido, en una línea y acotado a 300 caracteres |

Si el broker no responde, el monitor lo anota (`no se pudo revisar ...`) y lo reintenta en la siguiente vuelta; nunca tumba la aplicación.

### Cómo reprocesar un mensaje
En la Management UI abrir la DLQ → *Get messages* para ver el original; corregir la causa y volver a publicarlo en `cmd.direct` con la routing key del caso de uso.
Para descartarlo, *Purge* de la DLQ (o esperar a que lo borre el TTL de 24 h).

## Configuración

| Variable | Por defecto | Para qué |
|---|---|---|
| `RABBITMQ_HOST` / `RABBITMQ_PORT` | `localhost` / `5672` | broker (un nodo) |
| `RABBITMQ_ADDRESSES` | vacío | varios nodos del clúster: `host1:5672,host2:5672` (reemplaza a host/port) |
| `RABBITMQ_USER` / `RABBITMQ_PASS` | — | credenciales |
| `RABBITMQ_QUEUE_TYPE` | `classic` | `classic` o `quorum` (igual en bookings; cambiarlo exige borrar las colas) |
| `NOTIFY_DLQ_MONITOR_ENABLED` | `true` | apaga el monitor de DLQ |
| `campuslab.notify.dlq-monitor.interval-ms` | `30000` | cada cuánto revisa las DLQ |
| `campuslab.notify.dlq-monitor.max-per-poll` | `200` | máximo de mensajes que lee de cada DLQ por revisión |
| `NOTIFY_SIMULATED_FAILURE_RATE` | `0` | solo para demostrar reintentos y DLQ (0.0 a 1.0) |
| `SERVER_PORT` | `8084` | endpoint de estado: `GET /api/notify/dlq` |

## Pruebas

`mvn test` ejecuta unitarias y, si hay un RabbitMQ en `RABBITMQ_HOST:RABBITMQ_PORT`, también las que usan broker real
(`NotifyBrokerFlowTest`: ACK, duplicados, DLQ, reintentos, JSON ilegible y registro del monitor). Sin broker esas pruebas se omiten solas.
Usan topología aislada y la borran al terminar.
