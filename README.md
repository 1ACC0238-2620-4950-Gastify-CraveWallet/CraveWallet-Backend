# CraveWallet Backend

Base del backend de Gastify, preparada para IntelliJ IDEA con Java 21, Spring Boot
3.5.16 y Maven Wrapper. Sigue la separación modular del
[informe del equipo](https://github.com/1ACC0238-2620-4950-Gastify-CraveWallet/CraveWallet-Report/blob/develop/docs/chapter_2.md),
secciones 2.5 y 2.6.

## Estado actual

El proyecto arranca, comprueba su estado en `/actuator/health` y ofrece Swagger
local. Incluye configuración para PostgreSQL, JPA, Flyway, validación, seguridad y
pruebas de arranque. Las carpetas de las capas contienen documentación de sus
responsabilidades para orientar el trabajo del equipo.

**Todavía no implementa autenticación JWT, CRUD de negocio, cobros, cotizaciones,
notificaciones ni resultados de los spikes.** No representa el 70 % de endpoints
de TB1 ni una versión móvil integrada. Las rutas de negocio están bloqueadas
hasta desarrollar TS01; no existe una cuenta de acceso predeterminada.

## Abrir en IntelliJ

1. Clonar este repositorio y seleccionar **Open** sobre `pom.xml`; abrirlo como proyecto.
2. Seleccionar un **JDK 21** en Project SDK y Maven Runner. En la máquina de Anghelo
   está instalado en `C:\Users\ANGHELO\.jdks\jdk-21.0.12.1+1`.
3. Importar Maven. Se incluye Wrapper, por lo que no hace falta instalar Maven global.
4. Ejecutar `CraveWalletBackendApplication`. El perfil predeterminado `local` usa
   H2 en memoria y escucha solo en `127.0.0.1`, para arrancar sin Docker.

H2 permite probar el arranque; **PostgreSQL es la base prevista para el producto**.
Los datos locales de H2 se pierden al cerrar y sus pruebas no validan concurrencia
ni comportamiento específico de PostgreSQL.

En PowerShell, con `JAVA_HOME` apuntando a JDK 21:

```powershell
$env:JAVA_HOME = 'C:\Users\ANGHELO\.jdks\jdk-21.0.12.1+1'
.\mvnw.cmd verify
.\mvnw.cmd spring-boot:run
```

- Estado: <http://localhost:8080/actuator/health>
- Swagger local: <http://localhost:8080/swagger-ui.html>
- OpenAPI: <http://localhost:8080/v3/api-docs>

Swagger no contiene operaciones de negocio hasta implementar sus controladores.
La prueba de seguridad comprueba que una consulta no autenticada se rechaza.

## PostgreSQL local

Con Docker Compose instalado:

```powershell
Copy-Item .env.example .env
# Editar POSTGRES_PASSWORD en .env antes de iniciar.
docker compose up -d
# Usar en DATABASE_PASSWORD la misma contraseña que configuraron en .env.
$env:DATABASE_PASSWORD = 'tu-password-local'
.\mvnw.cmd spring-boot:run '-Dspring-boot.run.profiles=postgres'
```

Compose lee `.env` para el contenedor. Spring Boot lee las variables de su proceso;
no carga `.env` automáticamente. Si cambian puerto, usuario o nombre de la base,
definir también `DATABASE_URL` y `DATABASE_USERNAME` en IntelliJ o PowerShell.
Nunca subir `.env` ni credenciales a Git. Todavía no existen entidades ni
migraciones de negocio; añadir migraciones versionadas en
`src/main/resources/db/migration` junto con cada funcionalidad. JPA usa `validate`
para evitar cambios automáticos del esquema.

El perfil `prod` exige `DATABASE_URL`, `DATABASE_USERNAME` y `DATABASE_PASSWORD`;
desactiva Swagger público. La autenticación y el despliegue deben completarse
antes de exponer funcionalidades a usuarios.

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

1. **TS01 / US01–US03, US33:** registro, login, perfil y token; obtener el propietario
   desde la autenticación y comprobar autorización en cada recurso.
2. **TS02 / US04–US11:** suscripciones, persistencia y portafolio; acordar antes las
   reglas Free/Premium que condicionan el alta.
3. **SP01–SP04 / TS03–TS04:** investigar y probar cotización y recordatorios. El
   backend prepara datos; el calendario y sus permisos pertenecen al dispositivo.
4. **TS05:** gastos y presupuesto, con deduplicación y actualización transaccional.
5. **SP05–SP06 / TS06:** Stripe; verificar firma, correlación y deduplicación del
   webhook antes de cambiar acceso. El retorno de checkout no confirma el pago.

El equipo debe confirmar el alcance oficial de TB1 y ajustar su Sprint Backlog.
El límite Free, el tratamiento del exceso tras cancelar Premium, horarios de
renovación y políticas de pago fallido siguen pendientes en el informe.

## Colaboración

`main` conserva la base original del repositorio; `develop` reúne esta preparación.
Crear `feature/<historia>-<nombre>` desde `develop`, dividir las historias en tareas,
verificar con `./mvnw verify` y solicitar revisión antes de integrar. GitHub Actions
ejecuta esas pruebas con Java 21 en cada push o pull request de las ramas previstas.
Las evidencias del sprint deben corresponder a funciones y pruebas realmente ejecutadas.
