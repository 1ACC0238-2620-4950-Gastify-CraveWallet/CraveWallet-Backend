# Publicación del REST API

La landing se publica por separado en Vercel. El API utiliza Java 21 y PostgreSQL;
el perfil H2 local no debe utilizarse para la entrega pública.

## Contenedor

`Dockerfile` ejecuta `mvnw verify` y empaqueta el JAR con un runtime Java 21.
El proceso se ejecuta sin privilegios de root. `prod` escucha en `0.0.0.0`,
lee `PORT` y habilita Swagger público en `/swagger-ui/index.html`.
La especificación OpenAPI está en `/v3/api-docs`. `/actuator/health` es la comprobación
de disponibilidad; los recursos del usuario requieren Bearer JWT.

Variables del servicio (solo en el proveedor, nunca en Git):

| Variable | Valor |
| --- | --- |
| `SPRING_PROFILES_ACTIVE` | `prod` |
| `DATABASE_URL` | `jdbc:postgresql://HOST:PUERTO/NOMBRE_BD`, con el parámetro TLS que indique el proveedor |
| `DATABASE_USERNAME` | Usuario de PostgreSQL |
| `DATABASE_PASSWORD` | Contraseña de PostgreSQL |
| `JWT_SECRET` | Secreto aleatorio persistente de al menos 32 bytes UTF-8 |
| `FLUTTER_WEB_ORIGIN` | Origen HTTPS exacto del sitio estático Flutter Web asignado por Render, sin ruta ni `/` final. |
| `GOOGLE_PLACES_API_KEY` | Clave de Places API (New) para habilitar sugerencias |
| `MERCADOPAGO_ACCESS_TOKEN`, `MERCADOPAGO_WEBHOOK_SECRET`, `MERCADOPAGO_BACK_URL` | Credenciales y URL de retorno del entorno de prueba |
| `MERCADOPAGO_EXPECTED_LIVE_MODE` | Booleano según el pago consultado en el entorno del equipo. Ver [configuración de Premium](premium-api.md#configuración-y-despliegue-en-render). |

Flyway aplica V1–V5 sobre una base vacía. Si restauran un respaldo que incluye
`flyway_schema_history`, aplica las migraciones pendientes. Hibernate verifica
el esquema y el pool usa cinco conexiones como máximo. Los usuarios, gastos,
suscripciones y datos de facturación de la base vencida requieren un respaldo
para recuperarse. Una base vacía permite empezar con cuentas de demostración.

## Conectar la nueva base en Render

1. En **Settings** del servicio Docker, selecciona la rama
   `feature/av2-premium-backend`. Para un servicio nuevo, `render.yaml` describe
   el servicio que usa la base creada por el equipo.
2. En **Environment**, reemplaza juntos `DATABASE_URL`, `DATABASE_USERNAME` y
   `DATABASE_PASSWORD` con los datos de la nueva base. Si hay referencias
   automáticas al recurso vencido, usa los valores privados de la nueva base.
   Conserva `JWT_SECRET` si recuperaron las sesiones de un respaldo.
3. Usa el host interno cuando el servicio y PostgreSQL estén en Render en la
   misma cuenta y región. Para una conexión externa usa el host público y
   configura TLS según el proveedor, por ejemplo
   `jdbc:postgresql://HOST_PUBLICO:5432/NOMBRE_BD?sslmode=require`. Si recibes
   `postgresql://USUARIO:CONTRASEÑA@HOST:PUERTO/BASE`, arma la URL JDBC con
   host, puerto y base y configura usuario y contraseña en sus variables.
   Usa una conexión directa para las migraciones. Para probar desde IntelliJ
   con la base compartida, usa esas mismas variables y el perfil `postgres`.
4. Carga en **Environment** las variables de Google Places y Mercado Pago.
   Render pide los valores `sync: false` al crear un Blueprint y requiere
   cargarlos manualmente en un servicio existente. Registra en Mercado Pago
   la URL pública del webhook del nuevo despliegue.
5. Despliega el último commit de la rama. En **Logs**, verifica las migraciones
   y que el servicio inicia con `prod`. Comprueba que `/actuator/health`
   responde 200 con `{"status":"UP"}`.

Referencias de configuración: [conectar PostgreSQL en Render](https://render.com/docs/postgresql-creating-connecting)
y [variables de un Blueprint](https://render.com/docs/blueprint-spec#prompting-for-secret-values).

El plan gratuito suspende el servicio tras inactividad y su base caduca a los
30 días. Es adecuado para una demostración temporal; conservar los datos y
revisar el plan antes del vencimiento. No se debe asumir disponibilidad continua.
Fuente: <https://render.com/docs/free> y <https://render.com/docs/blueprint-spec>.

## App y comprobación

En la URL HTTPS del AV2, abre `/swagger-ui/index.html` y `/v3/api-docs`.
Con una cuenta de demostración, comprueba registro, login, renovación, logout,
portafolio, conversión a PEN y cancelación de suscripciones. Comprueba el
presupuesto, registro idempotente, resumen y sugerencias de Delivery. Ejecuta
el [flujo de pago de prueba](premium-api.md#prueba-reproducible-con-mercado-pago)
y verifica la persistencia de los registros tras reiniciar el servicio.

Compilar Android con `-PAPI_BASE_URL=https://URL_REAL_DEL_API` y verificar sus
pruebas contra esa dirección. No sustituirla por la URL de la landing.
Para el Sprint Review 2, guarda el SHA y la URL desplegada, el resultado de
`mvnw verify`, capturas de Swagger con solicitudes y respuestas de muestra,
y las capturas del despliegue y las migraciones. Oculta credenciales, tokens
y datos personales antes de incorporarlas al capítulo IV.

## Evidencia técnica para el Sprint Review 2

| Sección del informe | Evidencia del backend |
| --- | --- |
| 4.2.1.4 Development Evidence | `27ee5ed` documenta el resumen por categoría y el ahorro estimado al cancelar. `3a59d60` integra Mercado Pago, con migración de facturación en `1fa0158`. |
| 4.2.1.5 Testing Suite Evidence | 72 pruebas pasan con JDK 21 en la ejecución estándar y con los flujos de persistencia sobre PostgreSQL 18.4 local. `a8264f0` cubre el ahorro estimado y `80e55b0` el ciclo de facturación con el proveedor simulado. Las pruebas de Delivery cubren autenticación, validación y respuesta de sugerencias. |
| 4.2.1.7 Services Documentation Evidence | El inventario del TB1 suma 16 operaciones. El AV2 incluye además las [cinco rutas de Premium](premium-api.md#contrato-rest) y [sugerencias de comercios](delivery-api.md#sugerencias-de-comercios). Los contratos de [suscripciones](subscriptions-api.md) incluyen ejemplos de edición y cancelación. |
| 4.2.1.8 Software Deployment Evidence | El JAR con perfil `prod` aplicó V1–V5 sobre PostgreSQL 18.4 local, sirvió Swagger y conservó una suscripción y su sesión tras reiniciar. Completar con el SHA, URL HTTPS, capturas de Render y resultados de la prueba remota una vez conectada la nueva base. |

## Evidencia histórica del TB1

- URL HTTPS: <https://cravewallet-api.onrender.com>.
- Corte publicado: `67ede2ae8c2b9cb02bb8151f5a163344a7ef3d4f`.
- Entorno: Render Free, Oregon, perfil `prod`, PostgreSQL 17; Flyway V1–V3.
- [Resultados HTTP remotos](evidence/cloud/remote-api-results.json): 22 comprobaciones antes del reinicio y siete después. Registro, login, aislamiento entre propietarios, renovación, suscripciones, cotización, presupuesto y deduplicación de Delivery.
- [Captura real del evento de reinicio](evidence/cloud/render-restart.png).
- Tras el reinicio solicitado en Render se conservaron la sesión, la suscripción editada y el presupuesto/gasto; logout revocó el acceso.
- [Captura real del servicio Live](evidence/cloud/render-live.png).
- El panel registró el vencimiento de la base gratuita el **7 de noviembre de 2026**. El API usaba la red interna.

El cliente Android se probó contra este HTTPS con su suite de integración. Las
pruebas de interfaz usan Robolectric y no sustituyen la verificación en un
celular de permisos, calendario y notificaciones. Ese corte publicó las 16 rutas
del TB1. Premium y búsqueda de comercios se incorporaron en la rama del AV2.
