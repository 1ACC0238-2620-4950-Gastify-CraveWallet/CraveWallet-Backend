# CraveWallet Backend

Base del backend de Gastify, preparada para IntelliJ IDEA con Java 21, Spring Boot
3.5.16 y Maven Wrapper. Sigue la separación modular del
[informe del equipo](https://github.com/1ACC0238-2620-4950-Gastify-CraveWallet/CraveWallet-Report/blob/develop/docs/chapter_2.md),
secciones 2.5 y 2.6.

## Despliegue del AV2

La rama `feature/av2-premium-backend` está preparada para Render con el perfil
`prod` y un PostgreSQL administrado por el equipo. Consulta los pasos para
[conectar la base, desplegar y verificar el API público](docs/cloud-deployment.md).
Allí también se conservan las evidencias del despliegue TB1.

## Estado actual

El proyecto arranca, comprueba su estado en `/actuator/health` y ofrece Swagger
local. Implementa TS01: registro, login, JWT de acceso, token de renovación con
rotación, consulta/actualización del perfil en PEN y logout con revocación real.
La migración V1 crea usuarios y sesiones; las contraseñas se almacenan con BCrypt
y los tokens de renovación como hash SHA-256, nunca como texto plano.

Implementa registro, listado, detalle, edición y cancelación local de suscripciones
con propietario autenticado, historial y totales mensuales separados por moneda.
Flyway V2 crea su persistencia; el baseline Free permite cinco registros activos.
Ver el [contrato de suscripciones](docs/subscriptions-api.md).

Delivery implementa registro idempotente, resumen mensual, presupuesto con
acumulado transaccional y sugerencias de comercios mediante Google Places.
Ver [contrato de Delivery](docs/delivery-api.md).

Cotización USD/PEN con caché, conversión del resumen a soles y datos para el
recordatorio están implementados. Ver [contrato](docs/exchange-and-reminders-api.md).

Premium implementa estado del plan, checkout recurrente, cancelación, historial
y recepción de webhooks firmados. El acceso se activa después de verificar un
pago aprobado. Ver [contrato de Premium](docs/premium-api.md).

Swagger documenta las 22 operaciones del
[inventario de servicios](docs/tb1-endpoint-coverage.md), que conserva el corte
histórico del TB1. Las pruebas del proveedor y el despliegue del AV2 se verifican
con los pasos de la guía de despliegue.

## Abrir en IntelliJ

1. Clonar este repositorio y seleccionar **Open** sobre `pom.xml`; abrirlo como proyecto.
2. Seleccionar un **JDK 21** en Project SDK y Maven Runner.
3. Importar Maven. Se incluye Wrapper, por lo que no hace falta instalar Maven global.
4. Ejecutar `CraveWalletBackendApplication`. El perfil predeterminado `local` usa
   H2 en `./.local-data/cravewallet` y escucha en `127.0.0.1`. La suite usa su
   propia base H2 en memoria.

En PowerShell, con `JAVA_HOME` apuntando a JDK 21:

```powershell
.\mvnw.cmd verify
.\mvnw.cmd spring-boot:run
```

- Estado: <http://localhost:8080/actuator/health>
- Swagger local: <http://localhost:8080/swagger-ui.html>
- OpenAPI: <http://localhost:8080/v3/api-docs>

Las pruebas comprueban registro, login, hashing, perfil por propietario, renovación,
concurrencia de renovación, revocación y rechazo de tokens manipulados o vencidos.
Ver los [contratos de autenticación](docs/auth-api.md) y las
[observaciones de integración móvil](docs/mobile-integration.md).

## PostgreSQL local

Con Docker Compose instalado:

```powershell
Copy-Item .env.example .env
# Editar POSTGRES_PASSWORD en .env antes de iniciar.
docker compose up -d
# Usar en DATABASE_PASSWORD la misma contraseña que configuraron en .env.
$env:DATABASE_PASSWORD = 'tu-password-local'
$env:JWT_SECRET = 'reemplazar-por-un-secreto-aleatorio-de-al-menos-32-bytes'
.\mvnw.cmd spring-boot:run '-Dspring-boot.run.profiles=postgres'
```

Compose lee `.env` para el contenedor. Spring Boot lee las variables de su proceso;
no carga `.env` automáticamente. Si cambian puerto, usuario o nombre de la base,
definir también `DATABASE_URL` y `DATABASE_USERNAME` en IntelliJ o PowerShell.
Guarda las credenciales en las variables del proceso. Flyway aplica V1–V5
y JPA valida el esquema al iniciar.

Los perfiles `postgres` y `prod` exigen `JWT_SECRET` de al menos 32 bytes UTF-8.
`prod` también exige `DATABASE_URL`, `DATABASE_USERNAME` y `DATABASE_PASSWORD`,
y habilita Swagger público. En `local`, si no se configura el secreto, se genera
una clave aleatoria en memoria: los tokens anteriores no sirven tras reiniciar.

## Organización del código

Paquete raíz: `pe.edu.upc.gastify.cravewallet`.

| Módulo | Responsabilidad | Informe |
| --- | --- | --- |
| `subscriptions` | Portafolio y renovaciones de servicios registrados por el usuario | 2.6.1 |
| `delivery` | Gastos de delivery y presupuesto mensual | 2.6.2 |
| `premium` | Plan propio de CraveWallet y facturación | 2.6.3 |
| `iam` | Soporte técnico para autenticación y perfil; no es un cuarto contexto de negocio | TS01 |
| `shared.infrastructure` | Configuración transversal de seguridad y documentación | 4.1 |

Los tres módulos de negocio tienen `domain`, `application`, `interfaces` e
`infrastructure`. El dominio no debe importar Spring, JPA ni SDK de proveedores.
Compartir el identificador del propietario no establece un Shared Kernel entre
Suscripciones y Gastos. Los contratos entre módulos deben ser explícitos.

## Colaboración

`main` conserva la base original del repositorio; `develop` reúne esta preparación.
Crear `feature/<historia>-<nombre>` desde `develop`, dividir las historias en tareas,
verificar con `./mvnw verify` y solicitar revisión antes de integrar. GitHub Actions
ejecuta esas pruebas con Java 21 en cada push o pull request de las ramas previstas.
Las evidencias del sprint deben corresponder a funciones y pruebas realmente ejecutadas.
