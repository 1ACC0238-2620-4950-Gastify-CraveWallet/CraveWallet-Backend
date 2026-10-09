# CraveWallet Backend

Base del backend de Gastify, preparada para IntelliJ IDEA con Java 21, Spring Boot
3.5.16 y Maven Wrapper. Sigue la separación modular del
[informe del equipo](https://github.com/1ACC0238-2620-4950-Gastify-CraveWallet/CraveWallet-Report/blob/develop/docs/chapter_2.md),
secciones 2.5 y 2.6.

## API pública

Servidor: **https://cravewallet-api.onrender.com**. Disponibilidad:
<https://cravewallet-api.onrender.com/actuator/health>. Ejecuta el perfil `prod`
con PostgreSQL 17 en Render; no requiere que la PC del equipo esté encendida.
Swagger está disponible en <https://cravewallet-api.onrender.com/swagger-ui/index.html>.
Las operaciones del usuario conservan la autenticación Bearer JWT. La configuración y las
[evidencias remotas](docs/cloud-deployment.md) están versionadas.

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

Delivery implementa registro idempotente, resumen mensual y presupuesto, con
acumulado transaccional. Ver [contrato de Delivery](docs/delivery-api.md).

Cotización USD/PEN con caché, conversión del resumen a soles y datos para el
recordatorio están implementados. Ver [contrato](docs/exchange-and-reminders-api.md).

**Todavía no implementa edición/eliminación de Delivery, búsqueda de comercios,
cobros, envío de notificaciones ni resultados de los spikes.** Hay 16 de las 22
rutas del [inventario de trabajo](docs/tb1-endpoint-coverage.md) (72.7 % por cantidad); esto no acredita el 70 %
de toda la entrega por sí solo el alcance completo de la integración móvil. Las rutas pendientes están bloqueadas;
no existe una cuenta de acceso predeterminada.

## Abrir en IntelliJ

1. Clonar este repositorio y seleccionar **Open** sobre `pom.xml`; abrirlo como proyecto.
2. Seleccionar un **JDK 21** en Project SDK y Maven Runner. En la máquina de Anghelo
   está instalado en `C:\Users\ANGHELO\.jdks\jdk-21.0.12.1+1`.
3. Importar Maven. Se incluye Wrapper, por lo que no hace falta instalar Maven global.
4. Ejecutar `CraveWalletBackendApplication`. El perfil predeterminado `local` usa
   H2 en memoria y escucha solo en `127.0.0.1`, para arrancar sin Docker.

H2 permite probar el arranque; **PostgreSQL es la base prevista para el producto**.
Los datos locales de H2 se pierden al cerrar. Hay pruebas de concurrencia en H2;
no validan el comportamiento específico de PostgreSQL.

En PowerShell, con `JAVA_HOME` apuntando a JDK 21:

```powershell
$env:JAVA_HOME = 'C:\Users\ANGHELO\.jdks\jdk-21.0.12.1+1'
.\mvnw.cmd verify
.\mvnw.cmd spring-boot:run
```

- Estado: <http://localhost:8080/actuator/health>
- Swagger local: <http://localhost:8080/swagger-ui.html>
- OpenAPI: <http://localhost:8080/v3/api-docs>

Swagger documenta los 16 endpoints de autenticación, perfil, suscripciones,
cotización y Delivery. Las pruebas
comprueban registro, login, hashing, perfil por propietario, renovación,
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
Nunca subir `.env` ni credenciales a Git. La migración V1 corresponde a IAM;
añadir las migraciones de los tres contextos de negocio en
`src/main/resources/db/migration` junto con cada funcionalidad. JPA usa `validate`
para evitar cambios automáticos del esquema.

Los perfiles `postgres` y `prod` exigen `JWT_SECRET` de al menos 32 bytes UTF-8.
`prod` también exige `DATABASE_URL`, `DATABASE_USERNAME` y `DATABASE_PASSWORD`,
y habilita Swagger público para la demostración TB1. En `local`, si no se configura el secreto, se genera
una clave aleatoria en memoria: los tokens anteriores no sirven tras reiniciar.
El corte `67ede2a` está desplegado en Render con PostgreSQL 17 y HTTPS.
Ver [publicación y resultados](docs/cloud-deployment.md).

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

## Orden propuesto de implementación

1. **TS01 / US01–US03, US33 — implementado:** registro, login, perfil y tokens;
   propietario desde la autenticación y revocación de la sesión al cerrar.
2. **TS02 / US04–US11:** CRUD, portafolio y conversión a PEN implementados;
   falta catálogo remoto; el cliente Android ya consume estos contratos. Baseline Free
   de cinco activas; las reglas Premium requieren confirmación e implementación.
3. **TS03–TS04:** cotización y datos de recordatorios implementados. SP01–SP04
   siguen pendientes como investigación documentada. Calendario en el dispositivo.
4. **TS05 — parcial:** registro, resumen y presupuesto implementados, con
   deduplicación y acumulado transaccional. Faltan edición e historial; la app integra registro, resumen y presupuesto.
5. **SP05–SP06 / TS06:** Stripe; verificar firma, correlación y deduplicación del
   webhook antes de cambiar acceso. El retorno de checkout no confirma el pago.

El equipo debe confirmar el alcance oficial de TB1 y ajustar su Sprint Backlog.
El backend usa provisionalmente el límite Free de cinco activas de la app. El
equipo debe confirmar esa regla, el tratamiento del exceso tras cancelar Premium,
horarios de renovación y políticas de pago fallido. El alcance de entrega es
70 % de lo exigido para TB1; estos bloques no certifican ese porcentaje hasta
contrastar el inventario de endpoints con la rúbrica y el Sprint Backlog.

## Colaboración

`main` conserva la base original del repositorio; `develop` reúne esta preparación.
Crear `feature/<historia>-<nombre>` desde `develop`, dividir las historias en tareas,
verificar con `./mvnw verify` y solicitar revisión antes de integrar. GitHub Actions
ejecuta esas pruebas con Java 21 en cada push o pull request de las ramas previstas.
Las evidencias del sprint deben corresponder a funciones y pruebas realmente ejecutadas.
