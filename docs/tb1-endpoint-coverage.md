# Inventario de endpoints para TB1

Inventario de trabajo derivado de TS01–TS06 y las tablas de Interface Layer del
capítulo 2 del informe, incluyendo los seis contratos de sesión/perfil. El conteo
mide rutas y métodos, no esfuerzo, cobertura de criterios ni nota. Debe
contrastarse con la rúbrica oficial y el Sprint Backlog del equipo.

| Módulo | Implementados | Inventario | Pendientes |
| --- | ---: | ---: | --- |
| Autenticación y perfil | 6 | 6 | Integrar Android |
| Suscripciones y recordatorio | 6 | 6 | Integrar Android/calendario; no hay historial de cobros reales |
| Cotización | 1 | 1 | Persistir caché compartida y documentar spikes |
| Delivery | 3 | 4 | Sugerencias de comercios |
| Premium | 0 | 5 | Checkout, status, cancel, payments, webhook |
| **Total** | **16** | **22** | **72.7 % por cantidad de endpoints** |

## Rutas implementadas

| Método | Ruta |
| --- | --- |
| POST | `/api/v1/auth/register` |
| POST | `/api/v1/auth/login` |
| POST | `/api/v1/auth/refresh` |
| POST | `/api/v1/auth/logout` |
| GET | `/api/v1/users/me` |
| PATCH | `/api/v1/users/me` |
| POST | `/api/v1/subscriptions` |
| GET | `/api/v1/subscriptions` |
| GET | `/api/v1/subscriptions/{id}` |
| PATCH | `/api/v1/subscriptions/{id}` |
| POST | `/api/v1/subscriptions/{id}/cancel` |
| GET | `/api/v1/subscriptions/{id}/reminder` |
| GET | `/api/v1/exchange-rate` |
| POST | `/api/v1/delivery-expenses` |
| GET | `/api/v1/delivery-expenses/summary` |
| PUT | `/api/v1/delivery-expenses/budget` |

No se cuentan health, Swagger, OpenAPI, filtros ni reintentos como nuevos endpoints.
Los seis pendientes son GET delivery-expenses/merchants/suggestions y las cinco
rutas Premium. El catálogo remoto y edición/eliminación de Delivery aparecen en
otros casos de uso pero no se han añadido artificialmente a estas tablas: si el
docente exige otro inventario, el denominador y porcentaje deben recalcularse.

## Qué no acredita este porcentaje

La app todavía no consume el backend. No se verificó PostgreSQL, despliegue público
ni compilación Android. Los spikes requieren sus propias evidencias. Recordatorio
es preparación de datos, no envío de push ni escritura en calendario. Premium
no existe; el baseline de cinco suscripciones activas debe confirmarse con el equipo.

Se verifican migraciones y flujos con H2, JWT, pruebas de concurrencia y casos de
fallo del proveedor. Swagger se revisa con datos ficticios y cotización real.
El 8 de octubre de 2026, `mvnw verify` pasó 32 pruebas. Se ejercitaron las 16
rutas del servidor local: altas 201, consultas/ediciones 200, renovación 200 y
logout 204. El reintento de Delivery devolvió 200 sin duplicar el total y el
token revocado fue rechazado con 401. En Swagger se probaron registro, login,
alta/listado de suscripción USD, recordatorio, cotización, presupuesto, gasto y
resumen. La revisión corrigió la colisión de esquemas de alta y documentó 201.
La exportación del informe y las evidencias oficiales del sprint son trabajo
separado; este archivo no declara lista toda la entrega TB1.
