# Publicación del REST API

La landing se publica por separado en Vercel. El API utiliza Java 21 y PostgreSQL;
el perfil H2 local no debe utilizarse para la entrega pública.

## Contenedor

`Dockerfile` ejecuta `mvnw verify` y empaqueta el JAR con un runtime Java 21.
El proceso se ejecuta sin privilegios de root. `prod` escucha en `0.0.0.0`,
lee `PORT` y habilita Swagger público en `/swagger-ui/index.html` para la demostración TB1.
La especificación OpenAPI está en `/v3/api-docs`. `/actuator/health` es la comprobación
de disponibilidad; los recursos del usuario requieren Bearer JWT.

Variables del servicio (solo en el proveedor, nunca en Git):

| Variable | Valor |
| --- | --- |
| `SPRING_PROFILES_ACTIVE` | `prod` |
| `DATABASE_URL` | `jdbc:postgresql://HOST_INTERNO:5432/cravewallet` |
| `DATABASE_USERNAME` | Usuario de PostgreSQL |
| `DATABASE_PASSWORD` | Contraseña de PostgreSQL |
| `JWT_SECRET` | Secreto aleatorio persistente de al menos 32 bytes UTF-8 |

Flyway aplica V1–V3 al iniciar y Hibernate verifica el esquema. El pool usa
cinco conexiones como máximo. La región del API y de la base debe ser la misma.

## Render

`render.yaml` prepara un servicio Docker y una base PostgreSQL 17, ambos con
plan `free`. La rama de despliegue es `feature/cloud-deployment` mientras se
valida el incremento. También pueden crearse manualmente en el panel con los
mismos valores. La conexión JDBC utiliza el hostname interno, sin copiar la
URL `postgresql://` directamente a `DATABASE_URL`.

El plan gratuito suspende el servicio tras inactividad y su base caduca a los
30 días. Es adecuado para una demostración temporal; conservar los datos y
revisar el plan antes del vencimiento. No se debe asumir disponibilidad continua.
Fuente: <https://render.com/docs/free> y <https://render.com/docs/blueprint-spec>.

## App y comprobación

Una vez que Render entregue la URL HTTPS, comprobar `/actuator/health`, registro,
login, recursos por propietario, renovación de sesión, suscripciones y Delivery.
No publicar tokens ni contraseñas en las evidencias. Verificar persistencia tras
un reinicio del servicio.

Compilar Android con `-PAPI_BASE_URL=https://URL_REAL_DEL_API` y verificar sus
pruebas contra esa dirección. No sustituirla por la URL de la landing.
La publicación queda acreditada cuando la URL responde y los flujos remotos
se ejecutan; los archivos de configuración por sí solos no prueban el despliegue.

## Despliegue verificado

- URL HTTPS: <https://cravewallet-api.onrender.com>.
- Corte publicado: `67ede2ae8c2b9cb02bb8151f5a163344a7ef3d4f`.
- Entorno: Render Free, Oregon, perfil `prod`, PostgreSQL 17; Flyway V1–V3.
- [Resultados HTTP remotos](evidence/cloud/remote-api-results.json): 22 comprobaciones antes del reinicio y siete después. Registro, login, aislamiento entre propietarios, renovación, suscripciones, cotización, presupuesto y deduplicación de Delivery.
- [Captura real del evento de reinicio](evidence/cloud/render-restart.png).
- Tras el reinicio solicitado en Render se conservaron la sesión, la suscripción editada y el presupuesto/gasto; logout revocó el acceso.
- [Captura real del servicio Live](evidence/cloud/render-live.png).
- La base gratuita muestra vencimiento el **7 de noviembre de 2026** en el panel. Su acceso externo se bloqueó; el API utiliza la red interna.

El cliente Android se probó contra este HTTPS con su suite de integración. Las
pruebas de interfaz usan Robolectric y no sustituyen la verificación en un
celular de permisos, calendario y notificaciones. Premium y búsqueda de comercios
continúan fuera del incremento implementado; este despliegue publica las 16 rutas
existentes, sin atribuir funciones nuevas.
